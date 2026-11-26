package eu.kanade.presentation.kodi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Draws [overlay] over [content] while [active]. The overlay is part of the normal window (so the
 * gamepad mapping applies and VR mode mirrors it) and the content below can no longer take focus.
 */
@Composable
fun KodiMenuHost(active: Boolean, overlay: @Composable () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (active) {
                        Modifier.focusProperties { enter = { FocusRequester.Cancel } }.focusGroup()
                    } else {
                        Modifier
                    },
                ),
        ) { content() }
        if (active) {
            Box(Modifier.fillMaxSize().background(Color.Black)) { overlay() }
        }
    }
}

/** A Kodi-style context menu: a title and a list of entries; B / back closes it. */
@Composable
fun KodiContextMenu(title: String, items: List<KodiListItem>, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    KodiListPage(title = title, items = items)
}

/**
 * Menu button on a library group: rename, delete, create new group. [group] is null when there are no
 * groups yet (then only "create" is offered).
 */
@Composable
fun KodiGroupMenu(group: Category?, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(if (group == null) GroupStep.CREATE else GroupStep.MAIN) }
    val scope = rememberCoroutineScope()
    when (step) {
        GroupStep.MAIN -> KodiContextMenu(
            title = group?.name.orEmpty(),
            onDismiss = onDismiss,
            items = listOf(
                KodiListItem(stringResource(KMR.strings.menu_rename_group)) { step = GroupStep.RENAME },
                KodiListItem(stringResource(KMR.strings.menu_delete_group)) { step = GroupStep.DELETE },
                KodiListItem(stringResource(KMR.strings.menu_create_group)) { step = GroupStep.CREATE },
                KodiListItem(stringResource(MR.strings.action_cancel)) { onDismiss() },
            ),
        )
        GroupStep.RENAME -> KodiKeyboardDialog(
            title = stringResource(KMR.strings.menu_rename_group),
            initial = group?.name.orEmpty(),
            onDismiss = onDismiss,
            onDone = { name ->
                if (group != null && name.isNotBlank()) {
                    scope.launch { Injekt.get<RenameCategory>().await(group, name) }
                }
                onDismiss()
            },
        )
        GroupStep.CREATE -> KodiKeyboardDialog(
            title = stringResource(KMR.strings.menu_create_group),
            onDismiss = onDismiss,
            onDone = { name ->
                if (name.isNotBlank()) {
                    scope.launch { Injekt.get<CreateCategoryWithName>().await(name) }
                }
                onDismiss()
            },
        )
        GroupStep.DELETE -> KodiContextMenu(
            title = stringResource(KMR.strings.menu_confirm_delete_group, group?.name.orEmpty()),
            onDismiss = onDismiss,
            items = listOf(
                KodiListItem(stringResource(MR.strings.action_delete)) {
                    if (group != null) scope.launch { Injekt.get<DeleteCategory>().await(group.id) }
                    onDismiss()
                },
                KodiListItem(stringResource(MR.strings.action_cancel)) { onDismiss() },
            ),
        )
    }
}

private enum class GroupStep { MAIN, RENAME, CREATE, DELETE }
// KMK <--
