package eu.kanade.presentation.kodi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// KMK -->
/** Kodi-like notification popup shown at the top centre, replacing the white status banners. */
@Composable
fun KodiStatusPopup(text: String?, modifier: Modifier = Modifier) {
    // Keep the last text while fading out.
    val shown = remember { arrayOf("") }
    if (text != null) shown[0] = text
    Box(modifier = modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = shown[0],
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Color(0xE6101418), RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, Color(0xFF6CA9D9)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
// KMK <--
