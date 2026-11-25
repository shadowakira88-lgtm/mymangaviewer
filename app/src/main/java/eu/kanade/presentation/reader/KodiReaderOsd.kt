package eu.kanade.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
private val OsdBlue = Color(0xFF6CA9D9)
private val OsdLine = Color(0xFFE0E0E0)
private val OsdDim = Color(0xFFB0B0B0)

/**
 * Reader menu in the style of the Kodi Quartz video OSD: title on top, a seek bar and a row of symbol buttons
 * at the bottom. Everything is focusable so a gamepad can drive it (D-pad moves, A selects).
 */
@Composable
fun KodiReaderOsd(
    visible: Boolean,
    mangaTitle: String?,
    chapterTitle: String?,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    mode: ReadingMode,
    onCycleMode: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onChapterList: () -> Unit,
    onToggleCrop: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var calibrating by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) runCatching { firstFocus.requestFocus() } else calibrating = false
    }
    val modeLabel = when (mode) {
        ReadingMode.RIGHT_TO_LEFT -> stringResource(KMR.strings.reader_mode_rtl)
        ReadingMode.LEFT_TO_RIGHT -> stringResource(KMR.strings.reader_mode_ltr)
        ReadingMode.WEBTOON -> stringResource(KMR.strings.reader_mode_strip)
        else -> mode.name
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top: titles
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OsdButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = mangaTitle.orEmpty(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = chapterTitle.orEmpty(),
                        color = OsdDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom: VR panel (optional), seek bar and buttons
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (calibrating) {
                    KodiVrPanel(onClose = { calibrating = false })
                    Spacer(Modifier.height(8.dp))
                }
                if (totalPages > 0) {
                    OsdSeekBar(currentPage, totalPages, onPageIndexChange)
                    Spacer(Modifier.height(6.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OsdButton(
                        icon = Icons.Outlined.SkipPrevious,
                        enabled = hasPrevChapter,
                        onClick = onPrevChapter,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    OsdButton(
                        icon = Icons.Outlined.NavigateBefore,
                        onClick = { onPageIndexChange((currentPage - 2).coerceAtLeast(0)) },
                    )
                    OsdButton(
                        icon = Icons.Outlined.NavigateNext,
                        onClick = { onPageIndexChange(currentPage.coerceAtMost((totalPages - 1).coerceAtLeast(0))) },
                    )
                    OsdButton(icon = Icons.Outlined.SkipNext, enabled = hasNextChapter, onClick = onNextChapter)
                    Spacer(Modifier.width(18.dp))
                    OsdButton(icon = Icons.Outlined.ChromeReaderMode, label = modeLabel, onClick = onCycleMode)
                    OsdButton(
                        icon = if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        selected = bookmarked,
                        onClick = onToggleBookmark,
                    )
                    OsdButton(icon = Icons.Outlined.FormatListNumbered, onClick = onChapterList)
                    OsdButton(icon = Icons.Outlined.Crop, onClick = onToggleCrop)
                    OsdButton(label = "VR", selected = calibrating, onClick = { calibrating = !calibrating })
                    OsdButton(icon = Icons.Outlined.Settings, onClick = onSettings)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OsdButton(
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
        hasFocus || selected -> OsdBlue
        else -> Color.White
    }
    Row(
        modifier = modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                BorderStroke(1.5.dp, if (hasFocus) OsdLine else Color(0xFF444444)),
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
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        if (label != null) Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Kodi-like progress bar. Left/right on a gamepad changes the page; touch tap/drag seeks. */
@Composable
private fun OsdSeekBar(currentPage: Int, totalPages: Int, onPageIndexChange: (Int) -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun seek(x: Float) {
        if (size.width <= 0) return
        val page = (x / size.width * totalPages).toInt().coerceIn(0, totalPages - 1)
        onPageIndexChange(page)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$currentPage", color = Color.White, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(22.dp)
                .onFocusChanged { hasFocus = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else if (event.key == Key.DirectionLeft) {
                        onPageIndexChange((currentPage - 2).coerceAtLeast(0))
                        true
                    } else if (event.key == Key.DirectionRight) {
                        onPageIndexChange(currentPage.coerceAtMost((totalPages - 1).coerceAtLeast(0)))
                        true
                    } else {
                        false
                    }
                }
                .focusable()
                .onSizeChangedCompat { size = it }
                .pointerInput(totalPages) { detectTapGestures { seek(it.x) } }
                .pointerInput(totalPages) { detectHorizontalDragGestures { change, _ -> seek(change.position.x) } }
                .drawBehind {
                    val h = if (hasFocus) 8.dp.toPx() else 5.dp.toPx()
                    val y = (this.size.height - h) / 2f
                    drawRect(Color(0x66FFFFFF), Offset(0f, y), Size(this.size.width, h))
                    val frac = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
                    drawRect(
                        if (hasFocus) OsdBlue else Color(0xFFB8A04A),
                        Offset(0f, y),
                        Size(this.size.width * frac, h),
                    )
                },
        )
        Text("$totalPages", color = Color.White, fontSize = 13.sp)
    }
}

private fun Modifier.onSizeChangedCompat(onSize: (IntSize) -> Unit): Modifier = this.onSizeChanged(onSize)

/** VR calibration, drawn inside the reader UI so it is mirrored into both eyes. */
@Composable
private fun KodiVrPanel(onClose: () -> Unit) {
    val prefs = remember { Injekt.get<UiPreferences>() }
    val uiScale by prefs.vrUiScale().collectAsState()
    val eyeFill by prefs.vrEyeFill().collectAsState()
    val aspect by prefs.vrAspect().collectAsState()
    val separation by prefs.vrSeparation().collectAsState()
    val offsetY by prefs.vrOffsetY().collectAsState()

    Column(
        modifier = Modifier
            .background(Color(0xE6101418), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, OsdBlue), RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VrRow(stringResource(KMR.strings.vr_ui_size), uiScale, "%") {
            prefs.vrUiScale().set((uiScale + it * 5).coerceIn(30, 150))
        }
        VrRow(stringResource(KMR.strings.vr_eye_area), eyeFill, "%") {
            prefs.vrEyeFill().set((eyeFill + it * 5).coerceIn(50, 100))
        }
        VrRow(stringResource(KMR.strings.vr_pixel_ratio), aspect, "%") {
            prefs.vrAspect().set((aspect + it * 5).coerceIn(50, 150))
        }
        VrRow(stringResource(KMR.strings.vr_eye_separation), separation, " dp") {
            prefs.vrSeparation().set((separation + it * 2).coerceIn(-100, 100))
        }
        VrRow(stringResource(KMR.strings.vr_vertical_shift), offsetY, " dp") {
            prefs.vrOffsetY().set((offsetY + it * 2).coerceIn(-100, 100))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OsdButton(label = stringResource(KMR.strings.vr_reset), onClick = {
                prefs.vrUiScale().delete()
                prefs.vrEyeFill().delete()
                prefs.vrAspect().delete()
                prefs.vrSeparation().delete()
                prefs.vrOffsetY().delete()
            })
            OsdButton(label = stringResource(KMR.strings.reader_osd_close), onClick = onClose)
        }
    }
}

@Composable
private fun VrRow(label: String, value: Int, unit: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(150.dp), maxLines = 1)
        OsdButton(label = "−", onClick = { onChange(-1) })
        Text("$value$unit", color = OsdBlue, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        OsdButton(label = "+", onClick = { onChange(1) })
    }
}
// KMK <--
