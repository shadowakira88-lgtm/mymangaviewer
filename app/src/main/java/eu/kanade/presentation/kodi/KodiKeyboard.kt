package eu.kanade.presentation.kodi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
private val KeyRows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm.-_")

/**
 * On-screen keyboard in the Kodi style. The typed text is always visible at the top and every key
 * is focusable, so a gamepad can type with the D-pad and A. B / back cancels.
 */
@Composable
fun KodiKeyboardDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var upper by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        KodiWallpaperBox(fallback = Color(0xFF0E1216)) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.5.dp, Color(0xFF6CA9D9)), RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = text + "│",
                        color = Color.White,
                        fontSize = 20.sp,
                        maxLines = 1,
                    )
                }
                KeyRows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { i, ch ->
                            val shown = if (upper) ch.uppercaseChar() else ch
                            KodiIconButton(
                                modifier = Modifier
                                    .width(KeyWidth)
                                    .let { if (rowIndex == 1 && i == 0) it.focusRequester(first) else it },
                                label = shown.toString(),
                                onClick = { text += shown },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KodiIconButton(label = "⇧ Shift", selected = upper, onClick = { upper = !upper })
                    KodiIconButton(
                        modifier = Modifier.width(150.dp).height(KeyHeight),
                        label = "Space",
                        onClick = { text += " " },
                    )
                    KodiIconButton(label = "⌫", onClick = { text = text.dropLast(1) })
                    KodiIconButton(label = "Clear", onClick = { text = "" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KodiIconButton(label = stringResource(MR.strings.action_ok), onClick = { onDone(text.trim()) })
                    KodiIconButton(label = stringResource(MR.strings.action_cancel), onClick = onDismiss)
                }
            }
        }
    }
}

private val KeyWidth: Dp = 44.dp
private val KeyHeight: Dp = 34.dp
// KMK <--
