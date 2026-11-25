package eu.kanade.presentation.kodi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// KMK -->
/** Small focusable symbol/text button in the Kodi Quartz look. A on a gamepad = click. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KodiIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val tint = when {
        !enabled -> Color(0xFF555555)
        hasFocus || selected -> Color(0xFF6CA9D9)
        else -> Color.White
    }
    Row(
        modifier = modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                BorderStroke(1.5.dp, if (hasFocus) Color(0xFFE0E0E0) else Color(0xFF444444)),
                RoundedCornerShape(4.dp),
            )
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        if (label != null) Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
// KMK <--
