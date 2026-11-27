package eu.kanade.presentation.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover as DomainMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
private val KodiFocusBlue = Color(0xFF6CA9D9)
private val KodiBorder = Color(0xFFE0E0E0)

private fun LibraryManga.toCover() = DomainMangaCover(
    mangaId = manga.id,
    sourceId = manga.source,
    isMangaFavorite = manga.favorite,
    ogUrl = manga.thumbnailUrl,
    lastModified = manga.coverLastModified,
)

/**
 * Kodi-like library page: a big cover of the highlighted manga on the left and a list of titles on
 * the right, over a blurred backdrop. Fully usable with D-pad / gamepad:
 * D-pad = move, A = open, X = continue reading, Y = select.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KodiLibraryList(
    items: List<LibraryItem>,
    stateKey: Long,
    requestFocus: Boolean,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    var focusedId by rememberSaveable(stateKey) { mutableStateOf(items.first().id) }
    val focused = items.firstOrNull { it.id == focusedId } ?: items.first()
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    val initialId = remember { focused.id }
    // KMK -->
    val sourceManager = remember { uy.kohesive.injekt.Injekt.get<tachiyomi.domain.source.service.SourceManager>() }
    // KMK <--

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { initialFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        eu.kanade.presentation.kodi.KodiMangaBackdrop(focused.libraryManga.manga.id) {
            AsyncImage(
                model = focused.libraryManga.toCover(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .alpha(0.30f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left: cover + title
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    MangaCover.Book(
                        data = focused.libraryManga.toCover(),
                        modifier = Modifier.fillMaxHeight(0.92f),
                    )
                }
                Text(
                    text = focused.libraryManga.manga.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // KMK -->
                Text(
                    text = sourceManager.getOrStub(focused.libraryManga.manga.source).name,
                    color = Color(0xFF8C8C8C),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // KMK <--
            }

            // Right: VR toggle + list
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
            ) {
                KodiVrToggle()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    itemsIndexed(
                        items = items,
                        key = { _, item -> item.id },
                    ) { _, item ->
                        KodiRow(
                            title = item.libraryManga.manga.title,
                            // KMK -->
                            source = sourceManager.getOrStub(item.libraryManga.manga.source).name,
                            // KMK <--
                            badge = item.unreadCount.takeIf { it > 0 }?.toString(),
                            isSelected = item.id in selection,
                            modifier = if (item.id == initialId) {
                                Modifier.focusRequester(initialFocus)
                            } else {
                                Modifier
                            },
                            onFocused = { focusedId = item.id },
                            onClick = { onClick(item.libraryManga) },
                            onLongClick = { onLongClick(item.libraryManga) },
                            onContinueReading = onClickContinueReading
                                ?.takeIf { item.unreadCount > 0 }
                                ?.let { { it(item.libraryManga) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiRow(
    title: String,
    // KMK -->
    source: String? = null,
    // KMK <--
    badge: String?,
    isSelected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContinueReading: (() -> Unit)?,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(3.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocused()
            }
            .border(
                border = if (hasFocus) {
                    BorderStroke(1.5.dp, KodiBorder)
                } else {
                    BorderStroke(1.5.dp, Color.Transparent)
                },
                shape = shape,
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
                        onLongClick()
                        true
                    }
                    Key.ButtonX -> {
                        onContinueReading?.invoke()
                        onContinueReading != null
                    }
                    else -> false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // KMK -->
        Column(modifier = Modifier.weight(1f)) {
            // KMK <--
            Text(
                text = title,
                color = when {
                    isSelected -> Color(0xFFFFC857)
                    hasFocus -> KodiFocusBlue
                    else -> Color.White
                },
                fontSize = 14.sp,
                fontWeight = if (hasFocus) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // KMK -->
            if (source != null) {
                Text(
                    text = source,
                    color = Color(0xFF8C8C8C),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // KMK <--
        if (badge != null) {
            Text(
                text = badge,
                color = Color(0xFFCCCCCC),
                fontSize = 10.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .border(1.dp, Color(0xFF888888), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * First step of the library: the list of groups (categories). Pressing A on a group opens its
 * manga list.
 */
@Composable
internal fun KodiGroupList(
    categories: List<Category>,
    startIndex: Int,
    contentPadding: PaddingValues,
    getItemCount: (Category) -> Int?,
    getFirstCover: (Category) -> DomainMangaCover?,
    onClickGroup: (Int) -> Unit,
) {
    var focusedIndex by rememberSaveable { mutableStateOf(startIndex.coerceIn(0, categories.lastIndex)) }
    val initialFocus = remember { FocusRequester() }
    val initialIndex = remember { focusedIndex }
    val focusedCover = categories.getOrNull(focusedIndex)?.let(getFirstCover)

    LaunchedEffect(Unit) {
        runCatching { initialFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (focusedCover != null) {
            AsyncImage(
                model = focusedCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .alpha(0.30f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                if (focusedCover != null) {
                    MangaCover.Book(
                        data = focusedCover,
                        modifier = Modifier.fillMaxHeight(0.92f),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                itemsIndexed(
                    items = categories,
                    key = { _, category -> category.id },
                ) { index, category ->
                    KodiRow(
                        title = category.visualName,
                        badge = getItemCount(category)?.takeIf { it > 0 }?.toString(),
                        isSelected = false,
                        modifier = if (index == initialIndex) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                        onFocused = { focusedIndex = index },
                        onClick = { onClickGroup(index) },
                        onLongClick = {},
                        onContinueReading = null,
                    )
                }
            }
        }
    }
}

/** Kodi-like "VR Mode ON/OFF" menu entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiVrToggle() {
    val pref = remember { Injekt.get<UiPreferences>().vrMode() }
    val enabled by pref.collectAsState()
    var hasFocus by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = stringResource(if (enabled) KMR.strings.vr_mode_on else KMR.strings.vr_mode_off),
            color = if (hasFocus) KodiFocusBlue else Color(0xFFB0B0B0),
            fontSize = 12.sp,
            modifier = Modifier
                .onFocusChanged { hasFocus = it.isFocused }
                .border(
                    border = BorderStroke(1.dp, if (hasFocus) KodiBorder else Color.Transparent),
                    shape = RoundedCornerShape(3.dp),
                )
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                        pref.set(!enabled)
                        true
                    } else {
                        false
                    }
                }
                .combinedClickable(onClick = { pref.set(!enabled) })
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
// KMK <--
