package eu.kanade.presentation.library.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.presentation.kodi.KodiContextMenu
import eu.kanade.presentation.kodi.KodiKeyboardDialog
import eu.kanade.presentation.kodi.KodiListItem
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Menu button on a library entry (Kodi style): move to group, remove from library, create new group,
 * select several entries.
 */
@Composable
fun KodiLibraryMenu(
    manga: Manga,
    categories: List<Category>,
    onSetCategories: (add: List<Long>, remove: List<Long>) -> Unit,
    onRemove: () -> Unit,
    onSelectMultiple: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(MangaStep.MAIN) }
    var version by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val groups = categories.filter { it.id != 0L && !it.isSystemCategory }
    when (step) {
        MangaStep.MAIN -> KodiContextMenu(
            title = manga.title,
            onDismiss = onDismiss,
            items = listOf(
                KodiListItem(stringResource(KMR.strings.menu_move_to_group)) { step = MangaStep.MOVE },
                KodiListItem(stringResource(KMR.strings.menu_remove_from_library)) { step = MangaStep.REMOVE },
                KodiListItem(stringResource(KMR.strings.menu_create_group)) { step = MangaStep.CREATE },
                KodiListItem(stringResource(KMR.strings.menu_select_multiple)) {
                    onSelectMultiple()
                    onDismiss()
                },
                KodiListItem(stringResource(MR.strings.action_cancel)) { onDismiss() },
            ),
        )
        MangaStep.MOVE -> {
            val member by produceState(initialValue = emptySet<Long>(), version) {
                value = Injekt.get<GetCategories>().await(manga.id).map { it.id }.toSet()
            }
            KodiContextMenu(
                title = stringResource(KMR.strings.menu_move_to_group),
                onDismiss = { step = MangaStep.MAIN },
                items = groups.map { cat ->
                    val inGroup = cat.id in member
                    KodiListItem(
                        title = (if (inGroup) "✓ " else "") + cat.name,
                    ) {
                        if (inGroup) {
                            onSetCategories(emptyList(), listOf(cat.id))
                        } else {
                            onSetCategories(listOf(cat.id), emptyList())
                        }
                        // Give the database a moment, then re-read the membership.
                        scope.launch {
                            kotlinx.coroutines.delay(300)
                            version++
                        }
                    }
                }.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
            )
        }
        MangaStep.REMOVE -> KodiContextMenu(
            title = stringResource(KMR.strings.menu_confirm_remove, manga.title),
            onDismiss = { step = MangaStep.MAIN },
            items = listOf(
                KodiListItem(stringResource(KMR.strings.menu_remove_from_library)) {
                    onRemove()
                    onDismiss()
                },
                KodiListItem(stringResource(MR.strings.action_cancel)) { step = MangaStep.MAIN },
            ),
        )
        MangaStep.CREATE -> KodiKeyboardDialog(
            title = stringResource(KMR.strings.menu_create_group),
            onDismiss = { step = MangaStep.MAIN },
            onDone = { name ->
                if (name.isNotBlank()) scope.launch { Injekt.get<CreateCategoryWithName>().await(name) }
                step = MangaStep.MAIN
            },
        )
    }
}

private enum class MangaStep { MAIN, MOVE, REMOVE, CREATE }
// KMK <--
