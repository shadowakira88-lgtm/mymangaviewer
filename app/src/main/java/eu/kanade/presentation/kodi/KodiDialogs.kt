package eu.kanade.presentation.kodi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

// KMK -->
/** Dialogs waiting to be drawn by the root of the window (see [KodiDialogRoot]). */
class KodiDialogHostState {
    class Entry(val content: State<@Composable () -> Unit>)

    val entries = mutableStateListOf<Entry>()
}

val LocalKodiDialogHost = staticCompositionLocalOf<KodiDialogHostState?> { null }

/**
 * Root of a window. Alert dialogs draw here (inside the window, as a Kodi popup over the dimmed page) instead of in
 * a separate dialog window, so they follow the controller mapping and are mirrored for both eyes in VR mode.
 */
@Composable
fun KodiDialogRoot(content: @Composable () -> Unit) {
    if (LocalKodiDialogHost.current != null) {
        content()
        return
    }
    val host = remember { KodiDialogHostState() }
    val toast = rememberKodiToastState()
    CompositionLocalProvider(LocalKodiDialogHost provides host) {
        Box {
            val modifier = if (host.entries.isNotEmpty()) {
                Modifier.focusProperties { enter = { FocusRequester.Cancel } }.focusGroup()
            } else {
                Modifier
            }
            Box(modifier) { content() }
            host.entries.forEach { entry ->
                key(entry) {
                    Box(Modifier.matchParentSize()) { entry.content.value() }
                }
            }
            KodiToastOverlay(toast)
        }
    }
}

/**
 * Drop-in replacement for the Material 3 AlertDialog (same parameters) that draws as a Kodi popup inside the
 * window. Falls back to the normal dialog when there is no [KodiDialogRoot].
 */
@Suppress("UNUSED_PARAMETER", "LongParameterList")
@Composable
fun KodiAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val host = LocalKodiDialogHost.current
    if (host == null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties,
        )
        return
    }
    val body: @Composable () -> Unit = {
        KodiAlertPanel(onDismissRequest, confirmButton, dismissButton, icon, title, text, properties)
    }
    val latest = rememberUpdatedState(body)
    val entry = remember { KodiDialogHostState.Entry(latest) }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

/** Draws [content] as a full-window popup layer (registered on the window's dialog host). */
@Composable
fun KodiPopupLayer(content: @Composable () -> Unit) {
    val host = LocalKodiDialogHost.current ?: return
    val latest = rememberUpdatedState(content)
    val entry = remember { KodiDialogHostState.Entry(latest) }
    DisposableEffect(host) {
        host.entries.add(entry)
        onDispose { host.entries.remove(entry) }
    }
}

@Composable
private fun KodiAlertPanel(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    properties: DialogProperties,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = properties.dismissOnBackPress, onBack = onDismissRequest)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .pointerInput(Unit) {
                detectTapGestures { if (properties.dismissOnClickOutside) onDismissRequest() }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 460.dp)
                    .heightIn(max = 340.dp)
                    .background(Color(0xF0101418))
                    .border(1.dp, Color(0xFF8A929A))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(16.dp)
                    .focusRequester(focus)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                icon?.invoke()
                if (title != null) {
                    ProvideTextStyle(TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) { title() }
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false)) {
                        ProvideTextStyle(TextStyle(fontSize = 14.sp)) { text() }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
// KMK <--

/** Current in-window toast message (null = none). */
class KodiToastState {
    var message by mutableStateOf<String?>(null)
    var serial by mutableStateOf(0)
    var long = false
}

@Composable
private fun rememberKodiToastState(): KodiToastState {
    val state = remember { KodiToastState() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, state) {
        val mine: (String, Int) -> Boolean = { text, duration ->
            if (text.isBlank()) {
                true
            } else {
                state.long = duration == android.widget.Toast.LENGTH_LONG
                state.message = text
                state.serial++
                true
            }
        }
        // Only the window that is on screen takes the toasts.
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    eu.kanade.tachiyomi.util.system.KodiToastBridge.handler = mine
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    if (eu.kanade.tachiyomi.util.system.KodiToastBridge.handler === mine) {
                        eu.kanade.tachiyomi.util.system.KodiToastBridge.handler = null
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            eu.kanade.tachiyomi.util.system.KodiToastBridge.handler = mine
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (eu.kanade.tachiyomi.util.system.KodiToastBridge.handler === mine) {
                eu.kanade.tachiyomi.util.system.KodiToastBridge.handler = null
            }
        }
    }
    return state
}

/** Small dark Kodi-style box at the bottom centre; drawn inside the window so VR mode mirrors it for both eyes. */
@Composable
private fun BoxScope.KodiToastOverlay(state: KodiToastState) {
    val serial = state.serial
    LaunchedEffect(serial) {
        if (serial > 0) {
            kotlinx.coroutines.delay(if (state.long) 3500L else 2000L)
            state.message = null
        }
    }
    val text = state.message ?: return
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 36.dp, start = 24.dp, end = 24.dp)
            .widthIn(max = 420.dp)
            .background(Color(0xE6101418), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF8A929A), RoundedCornerShape(6.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 15.sp, maxLines = 3)
    }
}
