package eu.kanade.presentation.kodi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Queues every chapter of [manga] for download (chapters already downloaded are skipped by the downloader). */
suspend fun queueMangaDownload(manga: Manga): Int {
    val chapters = Injekt.get<GetChaptersByMangaId>().await(manga.id).sortedBy { it.chapterNumber }
    if (chapters.isNotEmpty()) Injekt.get<DownloadManager>().downloadChapters(manga, chapters)
    return chapters.size
}

/** Download queue: start / pause / clear in the header, one line per queued chapter with its progress. */
object KodiDownloadScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiDownloadScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val manager = remember { Injekt.get<DownloadManager>() }
        val queue by manager.queueState.collectAsState()
        val running by manager.isDownloaderRunning.collectAsState(false)
        val items = queue.map { download ->
            val pages = download.pages?.size ?: 0
            KodiListItem(
                title = download.manga.title,
                subtitle = download.chapter.name +
                    if (pages > 0) "  ${download.downloadedImages}/$pages" else "",
                cover = download.manga.asMangaCover(),
                onLongClick = { navigator.push(MangaScreen(download.manga.id)) },
            ) {
                manager.cancelQueuedDownloads(listOf(download))
            }
        }
        KodiListPage(
            title = stringResource(KMR.strings.latest_download),
            headerActions = listOf(
                if (running) {
                    KodiListItem(stringResource(KMR.strings.kodi_download_pause)) { manager.pauseDownloads() }
                } else {
                    KodiListItem(stringResource(KMR.strings.kodi_download_start)) { manager.startDownloads() }
                },
                KodiListItem(stringResource(KMR.strings.kodi_download_clear)) { manager.clearQueue() },
            ),
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
        )
    }
}
// KMK <--
