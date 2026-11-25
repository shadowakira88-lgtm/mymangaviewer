package eu.kanade.presentation.kodi

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

// KMK -->
/** Custom wallpaper shared by the Kodi-style screens. */
object KodiWallpaper {
    fun file(context: Context) = File(context.filesDir, "kodi_wallpaper")

    /** Copies the picked image into app storage and bumps the version so screens reload it. */
    fun set(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file(context).outputStream().use { input.copyTo(it) }
        }
        Injekt.get<UiPreferences>().kodiWallpaperVersion().set(System.currentTimeMillis())
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        file(context).delete()
        Injekt.get<UiPreferences>().kodiWallpaperVersion().set(0L)
    }
}

/** Background for Kodi screens: the chosen wallpaper (dimmed) or a plain colour. */
@Composable
fun KodiWallpaperBox(
    fallback: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val versionPref = remember { Injekt.get<UiPreferences>().kodiWallpaperVersion() }
    val version by versionPref.collectAsState()
    Box(modifier = modifier.fillMaxSize().background(fallback)) {
        if (version != 0L) {
            val file = remember(version) { KodiWallpaper.file(context) }
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .memoryCacheKey("kodi_wallpaper_$version")
                        .diskCacheKey("kodi_wallpaper_$version")
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().alpha(0.6f).background(Color.Black))
            }
        }
        content()
    }
}
// KMK <--
