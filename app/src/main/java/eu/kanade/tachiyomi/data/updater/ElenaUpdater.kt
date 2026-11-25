package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.Headers
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Updates the app from the rolling GitHub release tagged `latest` that CI publishes on every push.
 * The release's `target_commitish` is the commit the APK was built from.
 */
object ElenaUpdater {

    const val DEFAULT_REPO = "shadowakira88-lgtm/mymangaviewer"

    data class Release(val commit: String, val apkUrl: String, val isNewer: Boolean)

    suspend fun check(repo: String): Release = withIOContext {
        val network = Injekt.get<NetworkHelper>()
        val json = Injekt.get<Json>()
        val url = "https://api.github.com/repos/${repo.trim().trim('/')}/releases/tags/latest"
        val request = GET(
            url,
            Headers.headersOf("Accept", "application/vnd.github+json"),
            CacheControl.FORCE_NETWORK,
        )
        network.client.newCall(request).await().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val obj = json.parseToJsonElement(response.body.string()).jsonObject
            val commit = obj["target_commitish"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val assets = obj["assets"] as? JsonArray ?: JsonArray(emptyList())
            val arm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
            val wanted = if (arm64) "ElenaManga.apk" else "ElenaManga-universal.apk"
            val apk = assets.map { it.jsonObject }.firstOrNull { it.str("name") == wanted }
                ?: assets.map { it.jsonObject }.firstOrNull { it.str("name").endsWith(".apk") }
            checkNotNull(apk) { "No APK in release" }
            val installed = BuildConfig.COMMIT_SHA
            val same = commit.isNotBlank() && installed.isNotBlank() &&
                (commit.startsWith(installed) || installed.startsWith(commit))
            Release(commit.take(7), apk.str("browser_download_url"), isNewer = !same)
        }
    }

    /**
     * Downloads the APK straight to the app cache, reporting progress (0-100), then opens the system installer.
     * Returns false if the "install unknown apps" permission is missing (its settings page is opened).
     */
    suspend fun downloadAndInstall(context: Context, release: Release, onProgress: (Int) -> Unit): Boolean {
        val file = withIOContext {
            val network = Injekt.get<NetworkHelper>()
            val target = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
            if (target.exists()) target.delete()
            val request = GET(release.apkUrl, cache = CacheControl.FORCE_NETWORK)
            network.client.newCall(request).await().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val total = response.body.contentLength()
                var done = 0L
                var last = -1
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
            target
        }

        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(file.getUriCompat(context), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private fun JsonObject.str(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
}
// KMK <--
