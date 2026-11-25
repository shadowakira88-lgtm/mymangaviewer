package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/** Kodi-style quick menu shown when the reader menu opens: pick the reading mode. */
@Composable
fun KodiReaderModeMenu(
    visible: Boolean,
    current: ReadingMode,
    onSelect: (ReadingMode) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(top = 72.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Chip(KMR.strings.reader_mode_rtl, current == ReadingMode.RIGHT_TO_LEFT) {
                    onSelect(ReadingMode.RIGHT_TO_LEFT)
                }
                Chip(KMR.strings.reader_mode_ltr, current == ReadingMode.LEFT_TO_RIGHT) {
                    onSelect(ReadingMode.LEFT_TO_RIGHT)
                }
                Chip(KMR.strings.reader_mode_strip, current == ReadingMode.WEBTOON) {
                    onSelect(ReadingMode.WEBTOON)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: StringResource, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(label),
        color = if (selected) Color(0xFF6CA9D9) else Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .border(
                BorderStroke(1.5.dp, if (selected) Color(0xFFE0E0E0) else Color(0xFF555555)),
                RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
// KMK <--
