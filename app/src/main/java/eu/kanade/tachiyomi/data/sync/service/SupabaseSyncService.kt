package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.sync.SyncNotifier
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Syncs the backup as one protobuf object in a private Supabase Storage bucket ("sync"),
 * stored under the signed-in user's own folder: `<user id>/library.bin`.
 */
class SupabaseSyncService(
    context: Context,
    json: Json,
    syncPreferences: SyncPreferences,
    private val notifier: SyncNotifier,
    private val protoBuf: ProtoBuf = Injekt.get(),
) : SyncService(context, json, syncPreferences) {

    private val auth = SupabaseAuth(syncPreferences)
    private val network by lazy { Injekt.get<NetworkHelper>() }

    override suspend fun doSync(syncData: SyncData): Backup? {
        return try {
            check(auth.isConfigured && auth.isSignedIn) { "Supabase account not signed in" }
            notifier.showSyncProgress(progress = 10)
            val token = auth.freshToken()
            val base = syncPreferences.supabaseUrl().get().trim().trimEnd('/')
            val uid = syncPreferences.supabaseUserId().get()
            val objectUrl = "$base/storage/v1/object/$BUCKET/$uid/$OBJECT"

            val remote = pull("$base/storage/v1/object/authenticated/$BUCKET/$uid/$OBJECT", token)
            notifier.showSyncProgress(progress = 40)
            val finalData = if (remote != null) mergeSyncData(syncData, remote) else syncData

            val backup = finalData.backup ?: return null
            val bytes = protoBuf.encodeToByteArray(Backup.serializer(), backup)
            if (bytes.isEmpty()) throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))

            notifier.showSyncProgress(progress = 65)
            val headers = auth.authHeaders(token).newBuilder().add("x-upsert", "true").build()
            val req = POST(objectUrl, headers, bytes.toRequestBody("application/octet-stream".toMediaType()))
            network.client.newCall(req).await().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Failed to upload sync data: ${response.code} ${response.body.string()}"
                    notifier.showSyncError(msg)
                    logcat(LogPriority.ERROR) { msg }
                    return null
                }
            }
            notifier.showSyncProgress(progress = 90)
            finalData.backup
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR) { "Supabase sync failed: ${e.message}" }
            notifier.showSyncError(e.message)
            null
        }
    }

    private suspend fun pull(url: String, token: String): SyncData? {
        network.client.newCall(GET(url, auth.authHeaders(token))).await().use { response ->
            // Supabase answers 400/404 when the object does not exist yet
            if (response.code == 400 || response.code == 404) return null
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download sync data: ${response.code} ${response.body.string()}")
            }
            val bytes = response.body.bytes()
            return try {
                SyncData(backup = protoBuf.decodeFromByteArray(Backup.serializer(), bytes))
            } catch (_: SerializationException) {
                null
            }
        }
    }

    companion object {
        const val BUCKET = "sync"
        const val OBJECT = "library.bin"
    }
}
// KMK <--
