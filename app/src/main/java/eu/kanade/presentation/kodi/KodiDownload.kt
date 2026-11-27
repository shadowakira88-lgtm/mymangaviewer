package eu.kanade.presentation.kodi

import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Queues every chapter of [manga] for download (chapters already downloaded are skipped by the downloader). */
suspend fun queueMangaDownload(manga: Manga): Int {
    val chapters = Injekt.get<GetChaptersByMangaId>().await(manga.id).sortedBy { it.chapterNumber }
    if (chapters.isNotEmpty()) Injekt.get<DownloadManager>().downloadChapters(manga, chapters)
    return chapters.size
}
// KMK <--
