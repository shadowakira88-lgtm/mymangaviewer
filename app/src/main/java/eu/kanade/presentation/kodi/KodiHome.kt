package eu.kanade.presentation.kodi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.manga.model.MangaCover as DomainMangaCover

// KMK -->
private val QuartzBlue = Color(0xFF6CA9D9)
private val QuartzBorder = Color(0xFFE0E0E0)
private val QuartzDim = Color(0xFFB8B8B8)

data class KodiMenuAction(val title: String, val onClick: () -> Unit)

data class KodiMenuEntry(
    val title: String,
    val actions: List<KodiMenuAction>,
    /** When false, pressing the label only selects it and never runs the first sub-item. */
    val labelRunsFirstAction: Boolean = true,
    /** If set, pressing the label runs this instead of the first sub-item. */
    val labelOpensLibrary: (() -> Unit)? = null,
)

data class KodiPoster(val mangaId: Long, val cover: DomainMangaCover)

/**
 * First page, modelled after the Kodi "Quartz" home: a row of posters on top and a horizontal menu
 * below it. The sub-entries of the highlighted menu item are listed right under it.
 * D-pad moves, A opens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KodiHome(
    menu: List<KodiMenuEntry>,
    posters: List<KodiPoster>,
    onClickPoster: (Long) -> Unit,
    updateLabel: String,
    onUpdateAll: () -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val firstMenuFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstMenuFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .onKeyEvent { event ->
                // X: update the whole library
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonX) {
                    onUpdateAll()
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            KodiUpdateButton(label = updateLabel, onClick = onUpdateAll, modifier = Modifier)
        }
        if (posters.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
            ) {
                items(posters, key = { it.mangaId }) { poster ->
                    KodiPosterItem(poster = poster, onClick = { onClickPoster(poster.mangaId) })
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // The menu (labels + the sub-items of the selected one) scrolls if the list is long
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                menu.forEachIndexed { index, entry ->
                    KodiMenuColumn(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 132.dp),
                        entry = entry,
                        isSelected = index == selected,
                        onSelected = { selected = index },
                        labelModifier = if (index == 0) Modifier.focusRequester(firstMenuFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiUpdateButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    var hasFocus by remember { mutableStateOf(false) }
    Text(
        text = "\u21BB  $label",
        color = if (hasFocus) QuartzBlue else QuartzDim,
        fontSize = 13.sp,
        modifier = modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                BorderStroke(1.5.dp, if (hasFocus) QuartzBorder else Color.Transparent),
                RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiPosterItem(
    poster: KodiPoster,
    onClick: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                border = BorderStroke(2.dp, if (hasFocus) QuartzBorder else Color.Transparent),
                shape = RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick)
            .padding(2.dp),
    ) {
        MangaCover.Book(
            data = poster.cover,
            modifier = Modifier.height(96.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiMenuColumn(
    modifier: Modifier,
    entry: KodiMenuEntry,
    isSelected: Boolean,
    onSelected: () -> Unit,
    labelModifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var labelFocused by remember { mutableStateOf(false) }
        Text(
            text = entry.title,
            color = if (labelFocused) QuartzBlue else Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = labelModifier
                .fillMaxWidth()
                .onFocusChanged {
                    labelFocused = it.isFocused
                    if (it.isFocused) onSelected()
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                        onSelected()
                        labelPress(entry)
                        true
                    } else {
                        false
                    }
                }
                .combinedClickable(
                    onClick = {
                        onSelected()
                        labelPress(entry)
                    },
                )
                .padding(vertical = 8.dp),
        )

        if (isSelected) {
            entry.actions.forEach { action ->
                KodiSubItem(action = action, onSelected = onSelected)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiSubItem(
    action: KodiMenuAction,
    onSelected: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Text(
        text = action.title,
        color = if (hasFocus) QuartzBlue else QuartzDim,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onSelected()
            }
            .border(
                border = BorderStroke(1.dp, if (hasFocus) QuartzBorder else Color.Transparent),
                shape = RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    action.onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = action.onClick)
            .padding(vertical = 6.dp),
    )
}

private fun labelPress(entry: KodiMenuEntry) {
    val own = entry.labelOpensLibrary
    if (own != null) {
        own()
    } else if (entry.labelRunsFirstAction) {
        entry.actions.firstOrNull()?.onClick?.invoke()
    }
}
// KMK <--
