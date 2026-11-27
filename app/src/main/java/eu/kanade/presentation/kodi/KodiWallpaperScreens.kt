package eu.kanade.presentation.kodi

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

// KMK -->
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

private fun hasStorageAccess(context: android.content.Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
    Environment.isExternalStorageManager()
} else {
    ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * File explorer for choosing a wallpaper from the phone, driven with the D-pad: folders first, then pictures
 * (the highlighted picture is previewed on the left). Press a folder to open it, back to go up.
 */
class KodiWallpaperFolderScreen(private val mangaId: Long?, private val path: String? = null) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val root = remember { Environment.getExternalStorageDirectory() }
        val dir = remember { File(path ?: root.absolutePath) }
        var refresh by remember { mutableIntStateOf(0) }
        val okText = stringResource(KMR.strings.wallpaper_set_ok)
        val failText = stringResource(KMR.strings.wallpaper_set_failed)

        val access = remember(refresh) { hasStorageAccess(context) }
        val entries = remember(dir, refresh) {
            dir.listFiles()
                ?.filter {
                    !it.name.startsWith(".") && (it.isDirectory || it.extension.lowercase() in IMAGE_EXTENSIONS)
                }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .orEmpty()
        }

        val items = entries.map { file ->
            if (file.isDirectory) {
                KodiListItem(title = "📁 ${file.name}") {
                    navigator.push(KodiWallpaperFolderScreen(mangaId, file.absolutePath))
                }
            } else {
                KodiListItem(title = file.name, subtitle = "${file.length() / 1024} KB", preview = file) {
                    val ok = KodiWallpaper.apply(context, mangaId, Uri.fromFile(file))
                    Toast.makeText(context, if (ok) okText else failText, Toast.LENGTH_SHORT).show()
                    if (ok) navigator.popUntilRoot()
                }
            }
        }

        val shortcuts = listOf("Pictures", "DCIM", "Download", "Pictures/Screenshots")
            .map { File(root, it) }
            .filter { it.isDirectory && it != dir }
            .map { folder ->
                KodiListItem("📁 ${folder.name}") {
                    navigator.push(KodiWallpaperFolderScreen(mangaId, folder.absolutePath))
                }
            }
        val actions = buildList {
            if (!access) {
                add(
                    KodiListItem(stringResource(KMR.strings.wallpaper_allow_access)) {
                        if (Build.VERSION.SDK_INT >= 30) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        }
                    },
                )
            }
            add(KodiListItem(stringResource(KMR.strings.wallpaper_refresh)) { refresh++ })
            addAll(shortcuts)
        }
        KodiListPage(
            title = dir.name.ifEmpty { "/" },
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
            headerActions = actions,
        )
    }
}

private data class WebImage(
    val thumb: String,
    val full: String,
    val title: String,
    val width: Int = 0,
    val height: Int = 0,
)

private object ImageSearch {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0 Safari/537.36"
    private val bingMeta = Regex("""\sm="(\{[^"]+\})"""")
    private val vqdRegex = Regex("""vqd=["']?([\d-]+)""")

    private fun get(url: okhttp3.HttpUrl, referer: String? = null): String {
        val request = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
        return Injekt.get<NetworkHelper>().client.newCall(request).execute().use { it.body.string() }
    }

    /** DuckDuckGo images: JSON with the real size of every picture, so wide big ones can be picked. */
    private fun duckDuckGo(query: String): List<WebImage> {
        val page = get(
            "https://duckduckgo.com/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("iax", "images")
                .addQueryParameter("ia", "images")
                .build(),
        )
        val vqd = vqdRegex.find(page)?.groupValues?.get(1) ?: return emptyList()
        val body = get(
            "https://duckduckgo.com/i.js".toHttpUrl().newBuilder()
                .addQueryParameter("l", "us-en")
                .addQueryParameter("o", "json")
                .addQueryParameter("q", query)
                .addQueryParameter("vqd", vqd)
                .addQueryParameter("f", ",,,,,")
                .addQueryParameter("p", "1")
                .build(),
            referer = "https://duckduckgo.com/",
        )
        val results = Json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            runCatching {
                val obj = element.jsonObject
                WebImage(
                    thumb = obj["thumbnail"]!!.jsonPrimitive.content,
                    full = obj["image"]!!.jsonPrimitive.content,
                    title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                    width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0,
                    height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.getOrNull()
        }
    }

    /** Bing images (async endpoint, results only - no landing page). */
    private fun bing(query: String): List<WebImage> {
        val html = get(
            "https://www.bing.com/images/async".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addEncodedQueryParameter("qft", "+filterui:imagesize-large+filterui:aspect-wide")
                .addQueryParameter("first", "0")
                .addQueryParameter("count", "35")
                .addQueryParameter("mmasync", "1")
                .build(),
        )
        return bingMeta.findAll(html).mapNotNull { match ->
            runCatching {
                val json = match.groupValues[1]
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                val obj = Json.parseToJsonElement(json).jsonObject
                WebImage(
                    thumb = obj["turl"]!!.jsonPrimitive.content,
                    full = obj["murl"]!!.jsonPrimitive.content,
                    title = obj["t"]?.jsonPrimitive?.content.orEmpty(),
                )
            }.getOrNull()
        }.toList()
    }

    /** Big wide pictures (wallpaper shaped) first; relaxes the size when there are too few. */
    fun search(query: String): List<WebImage> {
        val ddg = runCatching { duckDuckGo(query) }.getOrDefault(emptyList())
        val wide = ddg.filter { it.width >= 1280 && it.width >= it.height * 1.3 }
        val relaxed = ddg.filter { it.width >= 800 && it.width > it.height }
        val picked = when {
            wide.size >= 5 -> wide
            relaxed.size >= 5 -> wide + relaxed
            else -> wide + relaxed + runCatching { bing(query) }.getOrDefault(emptyList()) + ddg
        }
        return picked.distinctBy { it.full }.take(40)
    }
}

/**
 * Wallpaper search on the web using the manga title. Only big wide pictures (wallpaper size) are listed; the
 * highlighted result is previewed on the left. Press one to download and use it.
 */
class KodiWallpaperWebScreen(private val mangaId: Long?, private val initialQuery: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf(initialQuery) }
        var editing by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val okText = stringResource(KMR.strings.wallpaper_set_ok)
        val failText = stringResource(KMR.strings.wallpaper_set_failed)
        val smallText = stringResource(KMR.strings.wallpaper_too_small)
        val downloadingText = stringResource(KMR.strings.wallpaper_downloading)

        if (editing) {
            KodiKeyboardDialog(
                title = stringResource(KMR.strings.wallpaper_search_change),
                initial = query,
                onDismiss = { editing = false },
                onDone = {
                    if (it.isNotBlank()) query = it
                    editing = false
                },
            )
            return
        }

        val results by produceState<List<WebImage>?>(initialValue = null, query) {
            value = null
            value = withContext(Dispatchers.IO) { runCatching { ImageSearch.search(query) }.getOrDefault(emptyList()) }
        }

        fun use(image: WebImage) {
            if (busy) return
            busy = true
            Toast.makeText(context, downloadingText, Toast.LENGTH_SHORT).show()
            scope.launch {
                val message = withContext(Dispatchers.IO) {
                    runCatching {
                        val temp = File(context.cacheDir, "wallpaper_download")
                        val request = Request.Builder()
                            .url(image.full)
                            .header("Referer", "https://www.bing.com/")
                            .build()
                        Injekt.get<NetworkHelper>().client.newCall(request).execute().use { response ->
                            check(response.isSuccessful)
                            temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(temp.absolutePath, bounds)
                        when {
                            bounds.outWidth <= 0 -> failText
                            bounds.outWidth < 1000 -> smallText
                            KodiWallpaper.apply(context, mangaId, Uri.fromFile(temp)) -> okText
                            else -> failText
                        }
                    }.getOrDefault(failText)
                }
                busy = false
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                if (message == okText) navigator.popUntilRoot()
            }
        }

        val items = results.orEmpty().mapIndexed { index, image ->
            KodiListItem(
                title = "${index + 1}. ${image.title.ifBlank { Uri.parse(image.full).host.orEmpty() }}",
                subtitle = listOfNotNull(
                    "${image.width}×${image.height}".takeIf { image.width > 0 },
                    Uri.parse(image.full).host,
                ).joinToString(" · "),
                preview = image.thumb,
            ) { use(image) }
        }
        KodiListPage(
            title = stringResource(KMR.strings.wallpaper_from_web_title),
            items = items.ifEmpty {
                listOf(
                    KodiListItem(
                        stringResource(
                            if (results == null) KMR.strings.wallpaper_searching else KMR.strings.wallpaper_no_results,
                        ),
                    ) {},
                )
            },
            headerActions = listOf(
                KodiListItem(stringResource(KMR.strings.wallpaper_search_change)) { editing = true },
            ),
        )
    }
}
/** Wallpaper chooser (same menu for one manga or the whole app): from phone, from web, reset. */
class KodiWallpaperChooserScreen(private val mangaId: Long?, private val query: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        KodiListPage(
            title = stringResource(KMR.strings.menu_change_wallpaper),
            items = listOf(
                KodiListItem(stringResource(KMR.strings.wallpaper_from_phone)) {
                    navigator.push(KodiWallpaperFolderScreen(mangaId))
                },
                KodiListItem(stringResource(KMR.strings.wallpaper_from_web)) {
                    navigator.push(KodiWallpaperWebScreen(mangaId, query))
                },
                KodiListItem(stringResource(KMR.strings.wallpaper_reset)) {
                    if (mangaId == null) {
                        KodiWallpaper.clear(context)
                    } else {
                        KodiWallpaper.clearForManga(context, mangaId)
                    }
                    navigator.pop()
                },
            ),
        )
    }
}
// KMK <--
