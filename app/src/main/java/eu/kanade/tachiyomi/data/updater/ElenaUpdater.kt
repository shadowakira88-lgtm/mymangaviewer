package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
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

    fun download(context: Context, release: Release) {
        AppUpdateDownloadJob.start(context, release.apkUrl, "ElenaManga ${release.commit}")
    }

    private fun JsonObject.str(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
}
// KMK <--
