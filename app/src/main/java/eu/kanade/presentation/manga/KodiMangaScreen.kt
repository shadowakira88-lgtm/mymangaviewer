package eu.kanade.presentation.manga

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
private val KodiBlue = Color(0xFF6CA9D9)
private val KodiBorder = Color(0xFFE0E0E0)
private val KodiDim = Color(0xFF8C8C8C)

/**
 * Kodi-style manga page: poster on the left, details, action buttons and the chapter list on the
 * right. Controller: D-pad = move, A = open, X = update this manga, long-press = mark read/unread, Y = bookmark, B = back.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun KodiMangaScreen(
    state: MangaScreenModel.State.Success,
    onBack: () -> Unit,
    onChapterClicked: (Chapter) -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onContinueReading: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onMigrateClicked: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Chapter>, Boolean) -> Unit,
    onMultiMarkAsReadClicked: (List<Chapter>, Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val manga = state.manga
    val cover = remember(manga) { manga.asMangaCover() }
    val chapters = state.processedChapters
    val isReading = remember(state.chapters) { state.chapters.any { it.chapter.read } }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        eu.kanade.presentation.kodi.KodiMangaBackdrop(manga.id) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .alpha(0.28f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .onKeyEvent { event ->
                    // X (magnifier/update button): update this manga only
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonX) {
                        onRefresh()
                        true
                    } else {
                        false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Left: smaller poster (tap = update this manga), then the details underneath it
            Column(
                modifier = Modifier
                    .weight(0.34f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var coverFocused by remember { mutableStateOf(false) }
                MangaCover.Book(
                    data = cover,
                    modifier = Modifier
                        .height(130.dp)
                        .onFocusChanged { coverFocused = it.isFocused }
                        .border(
                            BorderStroke(2.dp, if (coverFocused) KodiBorder else Color.Transparent),
                            RoundedCornerShape(3.dp),
                        )
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                                onRefresh()
                                true
                            } else {
                                false
                            }
                        }
                        .combinedClickable(onClick = onRefresh),
                )
                if (state.isRefreshingData) {
                    Text("\u21BB ...", color = KodiBlue, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }

                Text(
                    text = manga.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val subline = listOfNotNull(
                    manga.author?.takeIf { it.isNotBlank() },
                    state.source.name,
                ).joinToString(" • ")
                Text(
                    text = subline,
                    color = KodiDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )

                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KodiButton(text = "\u25C0", onClick = onBack)
                    KodiButton(
                        text = stringResource(if (isReading) MR.strings.action_resume else MR.strings.action_start),
                        onClick = onContinueReading,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    KodiButton(
                        text = stringResource(
                            if (manga.favorite) MR.strings.in_library else MR.strings.add_to_library,
                        ),
                        onClick = onAddToLibraryClicked,
                    )
                    if (onMigrateClicked != null) {
                        KodiButton(text = stringResource(MR.strings.migrate), onClick = onMigrateClicked)
                    }
                    if (state.trackingCount > 0 || state.hasLoggedInTrackers) {
                        KodiButton(text = stringResource(MR.strings.manga_tracking_tab), onClick = onTrackingClicked)
                    }
                    if (onWebViewClicked != null) {
                        KodiButton(text = stringResource(MR.strings.action_web_view), onClick = onWebViewClicked)
                    }
                }

                manga.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        color = Color(0xFFCFCFCF),
                        fontSize = 11.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // Right: the chapter list fills the whole space
            Column(
                modifier = Modifier
                    .weight(0.66f)
                    .fillMaxHeight(),
            ) {
                Text(
                    text = "${chapters.size} ${stringResource(MR.strings.chapters)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(chapters, key = { it.id }) { item ->
                        KodiChapterRow(
                            title = item.chapter.name,
                            date = item.chapter.dateUpload,
                            isRead = item.chapter.read,
                            isBookmarked = item.chapter.bookmark,
                            isDownloaded = item.isDownloaded,
                            onClick = { onChapterClicked(item.chapter) },
                            onToggleRead = { onMultiMarkAsReadClicked(listOf(item.chapter), !item.chapter.read) },
                            onToggleBookmark = { onMultiBookmarkClicked(listOf(item.chapter), !item.chapter.bookmark) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Text(
        text = text,
        color = if (hasFocus) KodiBlue else Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                border = BorderStroke(1.dp, if (hasFocus) KodiBorder else Color(0xFF555555)),
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiChapterRow(
    title: String,
    date: Long,
    isRead: Boolean,
    isBookmarked: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                border = BorderStroke(1.5.dp, if (hasFocus) KodiBorder else Color.Transparent),
                shape = RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.ButtonA -> {
                        onClick()
                        true
                    }
                    Key.ButtonY -> {
                        onToggleBookmark()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleRead,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = (if (isBookmarked) "★ " else "") + title,
            color = when {
                hasFocus -> KodiBlue
                isRead -> KodiDim
                else -> Color.White
            },
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.height(0.dp))
        Text(
            text = (if (isDownloaded) "DL  " else "") + relativeDateText(date),
            color = KodiDim,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
// KMK <--
