package eu.kanade.presentation.kodi

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
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
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.asMangaCover
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
        val sources by produceState(initialValue = emptyList<tachiyomi.domain.source.model.Source>()) {
            Injekt.get<GetEnabledSources>().subscribe().collectLatest { value = it }
        }
        val items = sources
            .filter { !nsfwOnly || it.installedExtension?.isNsfw != false }
            .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
            .map { source ->
                KodiListItem(
                    title = source.name,
                    subtitle = source.lang,
                ) { navigator.push(BrowseSourceScreen(source.id, null)) }
            }
        if (searching) {
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
        } else {
            KodiListPage(
                title = stringResource(MR.strings.label_sources),
                items = items.ifEmpty { listOf(emptyItem()) },
                headerActions = listOf(
                    KodiListItem(stringResource(KMR.strings.kodi_sources_search)) { searching = true },
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
// KMK <--
