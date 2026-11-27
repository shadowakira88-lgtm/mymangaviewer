package eu.kanade.presentation.kodi

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.temporal.ChronoUnit

// KMK -->
private fun relative(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun emptyItem(): KodiListItem = KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}

/** Recently fetched chapters, newest first. Tap = read the chapter, hold / Y = open the manga. */
object KodiUpdatesScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiUpdatesScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val updates by produceState(initialValue = emptyList<tachiyomi.domain.updates.model.UpdatesWithRelations>()) {
            Injekt.get<GetUpdates>()
                .subscribe(Instant.now().minus(60, ChronoUnit.DAYS), null, null, null, false)
                .collectLatest { value = it.take(300) }
        }
        val items = updates.map { u ->
            KodiListItem(
                title = u.mangaTitle,
                subtitle = "${u.chapterName} • ${relative(u.dateFetch)}",
                cover = u.coverData,
                onLongClick = { navigator.push(MangaScreen(u.mangaId)) },
            ) {
                context.startActivity(ReaderActivity.newIntent(context, u.mangaId, u.chapterId))
            }
        }
        KodiListPage(
            title = stringResource(KMR.strings.latest_updates),
            items = items.ifEmpty { listOf(emptyItem()) },
        )
    }
}

/** Reading history. Tap = continue that chapter, hold / Y = open the manga. */
object KodiHistoryScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiHistoryScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val history by produceState(initialValue = emptyList<tachiyomi.domain.history.model.HistoryWithRelations>()) {
            Injekt.get<GetHistory>()
                .subscribe("", null, null, null)
                .collectLatest { value = it.take(300) }
        }
        val items = history.map { h ->
            val chapter = if (h.chapterNumber >= 0) {
                "Ch. " + h.chapterNumber.toString().removeSuffix(".0")
            } else {
                ""
            }
            KodiListItem(
                title = h.title,
                subtitle = listOfNotNull(chapter.ifBlank { null }, h.readAt?.let { relative(it.time) })
                    .joinToString(" • "),
                cover = h.coverData,
                onLongClick = { navigator.push(MangaScreen(h.mangaId)) },
            ) {
                context.startActivity(ReaderActivity.newIntent(context, h.mangaId, h.chapterId))
            }
        }
        KodiListPage(
            title = stringResource(MR.strings.label_recent_manga),
            items = items.ifEmpty { listOf(emptyItem()) },
        )
    }
}

/** Bookmarked chapters across the library. */
object KodiBookmarksScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiBookmarksScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val items by produceState(initialValue = emptyList<KodiListItem>()) {
            val getChapters = Injekt.get<GetChaptersByMangaId>()
            value = runCatching {
                Injekt.get<GetLibraryManga>().await()
                    .filter { it.bookmarkCount > 0 }
                    .flatMap { lm ->
                        getChapters.await(lm.manga.id).filter { it.bookmark }.map { ch ->
                            KodiListItem(
                                title = lm.manga.title,
                                subtitle = ch.name,
                                cover = lm.manga.asMangaCover(),
                                onLongClick = { navigator.push(MangaScreen(lm.manga.id)) },
                            ) {
                                context.startActivity(ReaderActivity.newIntent(context, lm.manga.id, ch.id))
                            }
                        }
                    }
            }.getOrDefault(emptyList())
        }
        KodiListPage(
            title = stringResource(KMR.strings.latest_bookmarks),
            items = items.ifEmpty { listOf(emptyItem()) },
        )
    }
}

/** Installed sources. Tap = browse the source. Buttons under the title: global search and 18+ filter. */
object KodiSourcesScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiSourcesScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var nsfwOnly by remember { mutableStateOf(false) }
        var searching by remember { mutableStateOf(false) }
        var filtering by remember { mutableStateOf(false) }
        var nameFilter by remember { mutableStateOf("") }
        val sources by produceState(initialValue = emptyList<tachiyomi.domain.source.model.Source>()) {
            Injekt.get<GetEnabledSources>().subscribe().collectLatest { value = it }
        }
        var menuSource by remember { mutableStateOf<tachiyomi.domain.source.model.Source?>(null) }
        var searchSource by remember { mutableStateOf<tachiyomi.domain.source.model.Source?>(null) }
        val context = LocalContext.current
        val cookiesCleared = stringResource(KMR.strings.menu_source_cookies_cleared)
        val sourcePrefs = remember { Injekt.get<SourcePreferences>() }
        val pinned by sourcePrefs.pinnedSources().changes().collectAsState(sourcePrefs.pinnedSources().get())
        val items = sources
            .filter { !nsfwOnly || it.installedExtension?.isNsfw != false }
            .filter { nameFilter.isBlank() || it.name.contains(nameFilter, ignoreCase = true) }
            .sortedWith(
                compareBy<tachiyomi.domain.source.model.Source>({ it.id.toString() !in pinned })
                    .thenBy { it.lang }
                    .thenBy { it.name.lowercase() },
            )
            .map { source ->
                KodiListItem(
                    title = (if (source.id.toString() in pinned) "★ " else "") + source.name,
                    subtitle = source.lang,
                    onLongClick = { menuSource = source },
                ) { navigator.push(BrowseSourceScreen(source.id, null)) }
            }
        if (searchSource != null) {
            KodiKeyboardDialog(
                title = stringResource(KMR.strings.menu_source_search_here),
                onDismiss = { searchSource = null },
                onDone = { query ->
                    val target = searchSource
                    searchSource = null
                    if (target != null && query.isNotBlank()) navigator.push(BrowseSourceScreen(target.id, query))
                },
            )
        } else if (filtering) {
            KodiKeyboardDialog(
                title = stringResource(KMR.strings.kodi_sources_filter),
                initial = nameFilter,
                onDismiss = { filtering = false },
                onDone = {
                    nameFilter = it
                    filtering = false
                },
            )
        } else if (searching) {
            KodiKeyboardDialog(
                title = stringResource(KMR.strings.kodi_sources_search),
                onDismiss = { searching = false },
                onDone = { query ->
                    searching = false
                    if (query.isNotBlank()) {
                        // Kodi search: search every source but only show those with results.
                        val prefs = Injekt.get<SourcePreferences>()
                        prefs.globalSearchFilterState().set(true)
                        prefs.globalSearchPinnedState().set(SourceFilter.All)
                        navigator.push(GlobalSearchScreen(query))
                    }
                },
            )
        } else KodiMenuHost(
            active = menuSource != null,
            overlay = {
                menuSource?.let { source ->
                    val isPinned = source.id.toString() in pinned
                    val ext = source.installedExtension
                    KodiContextMenu(
                        title = source.name,
                        onDismiss = { menuSource = null },
                        items = buildList {
                            add(
                                KodiListItem(stringResource(KMR.strings.menu_source_open)) {
                                    menuSource = null
                                    navigator.push(BrowseSourceScreen(source.id, null))
                                },
                            )
                            // KMK -->
                            add(
                                KodiListItem(stringResource(KMR.strings.menu_source_search_here)) {
                                    menuSource = null
                                    searchSource = source
                                },
                            )
                            if (source.supportsLatest) {
                                add(
                                    KodiListItem(stringResource(KMR.strings.menu_source_latest)) {
                                        menuSource = null
                                        navigator.push(BrowseSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
                                    },
                                )
                            }
                            add(
                                KodiListItem(stringResource(KMR.strings.menu_source_migrate)) {
                                    menuSource = null
                                    navigator.push(MigrateMangaScreen(source.id))
                                },
                            )
                            val httpSource = Injekt.get<SourceManager>().get(source.id) as? HttpSource
                            if (httpSource != null) {
                                add(
                                    KodiListItem(stringResource(KMR.strings.menu_source_clear_cookies)) {
                                        runCatching {
                                            Injekt.get<NetworkHelper>().cookieJar.remove(httpSource.baseUrl.toHttpUrl())
                                        }
                                        Toast.makeText(
                                            context,
                                            cookiesCleared,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        menuSource = null
                                    },
                                )
                            }
                            // KMK <--
                            add(
                                KodiListItem(
                                    stringResource(
                                        if (isPinned) {
                                            KMR.strings.menu_source_unpin
                                        } else {
                                            KMR.strings.menu_source_pin
                                        },
                                    ),
                                ) {
                                    val current = sourcePrefs.pinnedSources().get()
                                    val id = source.id.toString()
                                    sourcePrefs.pinnedSources().set(if (isPinned) current - id else current + id)
                                    menuSource = null
                                },
                            )
                            add(
                                KodiListItem(stringResource(KMR.strings.menu_source_blank)) {
                                    blankSourceAndUninstall(source.id)
                                    menuSource = null
                                },
                            )
                            if (ext != null) {
                                add(
                                    KodiListItem(stringResource(KMR.strings.menu_source_uninstall)) {
                                        Injekt.get<ExtensionManager>().uninstallExtension(ext)
                                        menuSource = null
                                    },
                                )
                            }
                            add(KodiListItem(stringResource(MR.strings.action_cancel)) { menuSource = null })
                        },
                    )
                }
            },
        ) {
            KodiListPage(
                title = stringResource(MR.strings.label_sources),
                items = items.ifEmpty { listOf(emptyItem()) },
                headerActions = listOf(
                    KodiListItem(stringResource(KMR.strings.kodi_sources_search)) { searching = true },
                    KodiListItem(
                        if (nameFilter.isBlank()) {
                            stringResource(KMR.strings.kodi_sources_filter)
                        } else {
                            stringResource(KMR.strings.kodi_sources_filter_on, nameFilter)
                        },
                    ) { filtering = true },
                    KodiListItem(
                        stringResource(
                            if (nsfwOnly) KMR.strings.kodi_sources_nsfw_on else KMR.strings.kodi_sources_nsfw_off,
                        ),
                    ) { nsfwOnly = !nsfwOnly },
                ),
            )
        }
    }
}

/**
 * Kodi-style browse list of one source: a few buttons (popular, latest, manga search, filter) on the left
 * and the manga as a list with small covers on the right, so a gamepad can pick everything.
 * Menu button (Y) on a manga adds it to / removes it from the library.
 */
@Composable
fun KodiBrowseSourceList(
    sourceName: String,
    popularSelected: Boolean,
    latestSelected: Boolean,
    supportsLatest: Boolean,
    filterable: Boolean,
    mangaItems: List<tachiyomi.domain.manga.model.Manga>,
    loading: Boolean,
    loadingMore: Boolean,
    error: String?,
    onTouch: (Int) -> Unit,
    onRetry: () -> Unit,
    onPopular: () -> Unit,
    onLatest: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: () -> Unit,
    statusOptions: List<Pair<String, () -> Unit>>,
    onOpen: (tachiyomi.domain.manga.model.Manga) -> Unit,
    onMenu: (tachiyomi.domain.manga.model.Manga) -> Unit,
) {
    var typing by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    if (typing) {
        KodiKeyboardDialog(
            title = stringResource(KMR.strings.kodi_sources_search),
            onDismiss = { typing = false },
            onDone = {
                typing = false
                if (it.isNotBlank()) onSearch(it)
            },
        )
        return
    }
    val addHint = stringResource(KMR.strings.kodi_browse_hint_add)
    val removeHint = stringResource(KMR.strings.kodi_browse_hint_remove)
    val items = buildList {
        mangaItems.forEach { m ->
            add(
                KodiListItem(
                    title = m.title,
                    subtitle = if (m.favorite) removeHint else addHint,
                    cover = tachiyomi.domain.manga.model.MangaCover(
                        mangaId = m.id,
                        sourceId = m.source,
                        isMangaFavorite = m.favorite,
                        ogUrl = m.thumbnailUrl,
                        lastModified = m.coverLastModified,
                    ),
                    onLongClick = { onMenu(m) },
                ) { onOpen(m) },
            )
        }
        if (error != null) {
            add(KodiListItem(stringResource(KMR.strings.kodi_browse_retry), error) { onRetry() })
        } else if (loading || loadingMore) {
            add(KodiListItem(stringResource(KMR.strings.kodi_browse_loading)) {})
        } else if (mangaItems.isEmpty()) {
            add(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {})
        }
    }
    val popularLabel = stringResource(MR.strings.popular)
    val latestLabel = stringResource(MR.strings.latest)
    KodiMenuHost(
        active = statusMenu,
        overlay = {
            KodiContextMenu(
                title = stringResource(KMR.strings.kodi_status_button),
                onDismiss = { statusMenu = false },
                items = statusOptions.map { (label, apply) ->
                    KodiListItem(label) {
                        statusMenu = false
                        apply()
                    }
                } + KodiListItem(stringResource(MR.strings.action_cancel)) { statusMenu = false },
            )
        },
    ) {
        KodiListPage(
            title = sourceName,
            items = items,
            onFocusItem = onTouch,
            headerActions = buildList {
                add(KodiListItem(if (popularSelected) "✓ $popularLabel" else popularLabel) { onPopular() })
                if (supportsLatest) {
                    add(KodiListItem(if (latestSelected) "✓ $latestLabel" else latestLabel) { onLatest() })
                }
                add(KodiListItem(stringResource(KMR.strings.kodi_sources_search)) { typing = true })
                if (filterable) {
                    add(KodiListItem(stringResource(MR.strings.action_filter)) { onFilter() })
                }
                if (statusOptions.isNotEmpty()) {
                    add(KodiListItem(stringResource(KMR.strings.kodi_status_button)) { statusMenu = true })
                }
            },
        )
    }
}
// KMK <--
