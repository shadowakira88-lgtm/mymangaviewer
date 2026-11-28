package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.widget.Toast
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource

/**
 * Display a toast in this context.
 *
 * @param resource the text resource.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(
    resource: StringResource,
    duration: Int = Toast.LENGTH_SHORT,
    block: (Toast) -> Unit = {},
): Toast {
    return toast(stringResource(resource), duration, block)
}

/**
 * Display a toast in this context.
 *
 * @param text the text to display.
 * @param duration the duration of the toast. Defaults to short.
 */
fun Context.toast(
    text: String?,
    duration: Int = Toast.LENGTH_SHORT,
    block: (Toast) -> Unit = {},
): Toast {
    // KMK -->
    // When a window with the Kodi toast host is on screen, show the message inside that window (so it is drawn
    // properly on both sides in VR mode). The returned Toast is never shown; cancel() on it is harmless.
    if (KodiToastBridge.handler?.invoke(text.orEmpty(), duration) == true) {
        return Toast(applicationContext)
    }
    // KMK <--
    return Toast.makeText(applicationContext, text.orEmpty(), duration).also {
        block(it)
        it.show()
    }
}

// KMK -->
object KodiToastBridge {
    /** Returns true when the message was shown by the foreground window. */
    @Volatile
    var handler: ((text: String, duration: Int) -> Boolean)? = null
}
// KMK <--
