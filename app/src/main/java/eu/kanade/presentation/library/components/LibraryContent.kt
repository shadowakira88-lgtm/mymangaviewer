package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.activity.compose.BackHandler
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun LibraryContent(
    categories: List<Category>,
    // KMK -->
    activeCategoryIndex: Int,
    // KMK <--
    searchQuery: String?,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    currentPage: Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onClickManga: (Long) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onToggleSelection: (Category, LibraryManga) -> Unit,
    onToggleRangeSelection: (Category, LibraryManga) -> Unit,
    onRefresh: () -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getItemCountForCategory: (Category) -> Int?,
    getDisplayMode: (Int) -> PreferenceMutableState<LibraryDisplayMode>,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getItemsForCategory: (Category) -> List<LibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val pagerState = rememberPagerState(currentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        // KMK -->
        val kodiLayoutPref = remember { Injekt.get<UiPreferences>().kodiLayout() }
        val kodiLayout by kodiLayoutPref.collectAsState()
        var openGroup by rememberSaveable { mutableStateOf(false) }
        val hasGroups = categories.size > 1
        val showGroupList = kodiLayout && hasGroups && !openGroup && searchQuery.isNullOrEmpty()
        // Group picked from the home menu: jump straight into its manga list.
        LaunchedEffect(categories) {
            val id = eu.kanade.presentation.kodi.KodiNav.pendingCategoryId
            if (kodiLayout && id != null && categories.isNotEmpty()) {
                eu.kanade.presentation.kodi.KodiNav.pendingCategoryId = null
                val index = categories.indexOfFirst { it.id == id }
                if (index >= 0) {
                    pagerState.requestScrollToPage(index)
                    openGroup = true
                }
            }
        }
        // Back from a group's manga list returns to the list of groups.
        BackHandler(enabled = kodiLayout && hasGroups && openGroup) { openGroup = false }
        // KMK <--

        if (/* KMK --> */ !kodiLayout && /* KMK <-- */ showPageTabs && categories.isNotEmpty() && (categories.size > 1 || !categories.first().isSystemCategory)) {
            LaunchedEffect(categories) {
                // KMK -->
                val targetPage = when {
                    categories.isEmpty() -> 0
                    activeCategoryIndex != pagerState.currentPage -> activeCategoryIndex.coerceAtMost(categories.size - 1)
                    pagerState.currentPage >= categories.size -> categories.size - 1
                    else -> pagerState.currentPage
                }
                if (targetPage != pagerState.currentPage) {
                    pagerState.scrollToPage(targetPage)
                }
                // KMK <--
            }
            LibraryTabs(
                categories = categories,
                pagerState = pagerState,
                getItemCountForCategory = getItemCountForCategory,
                onTabItemClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(it)
                    }
                },
            )
        }

        // KMK -->
        if (showGroupList) {
            KodiGroupList(
                categories = categories,
                startIndex = pagerState.currentPage,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                getItemCount = getItemCountForCategory,
                getFirstCover = { category ->
                    getItemsForCategory(category).firstOrNull()?.libraryManga?.let {
                        MangaCover(
                            mangaId = it.manga.id,
                            sourceId = it.manga.source,
                            isMangaFavorite = it.manga.favorite,
                            ogUrl = it.manga.thumbnailUrl,
                            lastModified = it.manga.coverLastModified,
                        )
                    }
                },
                onClickGroup = { index ->
                    // The pager is not composed yet, so only request the page and open the group.
                    pagerState.requestScrollToPage(index)
                    openGroup = true
                },
            )
        } else {
            // KMK <--
            PullRefresh(
                refreshing = isRefreshing,
                enabled = selection.isEmpty(),
                onRefresh = {
                    val started = onRefresh()
                    if (!started) return@PullRefresh
                    scope.launch {
                        // Fake refresh status but hide it after a second as it's a long running task
                        isRefreshing = true
                        delay(1.seconds)
                        isRefreshing = false
                    }
                },
            ) {
                // KMK -->
                Box(
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        // Controller bumpers (L1 / R1) switch between library groups.
                        val step = when (event.key) {
                            Key.ButtonR1 -> 1
                            Key.ButtonL1 -> -1
                            else -> 0
                        }
                        if (step == 0) {
                            false
                        } else {
                            if (event.type == KeyEventType.KeyUp) {
                                val target = (pagerState.currentPage + step).coerceIn(0, categories.lastIndex)
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                            true
                        }
                    },
                ) {
                    LibraryPager(
                        state = pagerState,
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                        hasActiveFilters = hasActiveFilters,
                        selection = selection,
                        searchQuery = searchQuery,
                        onGlobalSearchClicked = onGlobalSearchClicked,
                        getCategoryForPage = { page -> categories[page] },
                        getDisplayMode = getDisplayMode,
                        getColumnsForOrientation = getColumnsForOrientation,
                        getItemsForCategory = getItemsForCategory,
                        onClickManga = { category, manga ->
                            if (selection.isNotEmpty()) {
                                onToggleSelection(category, manga)
                            } else {
                                onClickManga(manga.manga.id)
                            }
                        },
                        onLongClickManga = onToggleRangeSelection,
                        onClickContinueReading = onContinueReadingClicked,
                    )
                }
                // KMK <--
            }
            // KMK -->
        }
        // KMK <--

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}
