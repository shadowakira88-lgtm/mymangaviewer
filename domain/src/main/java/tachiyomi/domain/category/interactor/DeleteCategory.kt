package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.library.service.LibraryPreferences

class DeleteCategory(
    private val categoryRepository: CategoryRepository,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    // KMK -->
    private val mangaRepository: MangaRepository,
    // KMK <--
) {

    suspend fun await(
        categoryId: Long,
        // KMK -->
        /** Deleting a group also removes its manga from the library (instead of moving them to the default list). */
        removeManga: Boolean = true,
        // KMK <--
    ) = withNonCancellableContext {
        // KMK -->
        if (removeManga) {
            try {
                val ids = mangaRepository.getLibraryManga()
                    .filter { categoryId in it.categories }
                    .map { MangaUpdate(id = it.manga.id, favorite = false) }
                if (ids.isNotEmpty()) mangaRepository.updateAll(ids)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
        // KMK <--
        try {
            categoryRepository.delete(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAll()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        val defaultCategory = libraryPreferences.defaultCategory().get()
        if (defaultCategory == categoryId.toInt()) {
            libraryPreferences.defaultCategory().delete()
        }

        val categoryPreferences = listOf(
            libraryPreferences.updateCategories(),
            libraryPreferences.updateCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
            // KMK -->
            libraryPreferences.filterCategoriesInclude(),
            libraryPreferences.filterCategoriesExclude(),
            // KMK <--
        )
        val categoryIdString = categoryId.toString()
        categoryPreferences.forEach { preference ->
            val ids = preference.get()
            if (categoryIdString !in ids) return@forEach
            preference.set(ids.minus(categoryIdString))
        }

        try {
            categoryRepository.updatePartial(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
