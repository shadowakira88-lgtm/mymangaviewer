package eu.kanade.presentation.kodi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.interactor.DeleteCategory
import tachiyomi.domain.category.interactor.RenameCategory
import tachiyomi.domain.category.interactor.ReorderCategory
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
            Box(Modifier.fillMaxSize()) { overlay() }
        }
    }
}

/**
 * A Kodi-style context menu: a small dark pop-up box in the middle of the screen over the dimmed page, one
 * entry per line. D-pad moves, confirm picks, back / tapping outside closes it.
 */
@Composable
fun KodiContextMenu(title: String, items: List<KodiListItem>, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .heightIn(max = 300.dp)
                .background(Color(0xF0101418))
                .border(1.dp, Color(0xFF8A929A))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color(0xFFB8B8B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .clickable(onClick = item.onClick)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Menu on a library group: move up / down, rename, delete, create new group. [group] is null when there are
 * no groups yet (then only "create" is offered).
 */
/** Label of the "create group" button in the Library menu. */
@Composable
fun createGroupLabel(): String = stringResource(KMR.strings.menu_create_group)

@Composable
fun KodiGroupMenu(group: Category?, groups: List<Category>, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(if (group == null) GroupStep.CREATE else GroupStep.MAIN) }
    val scope = rememberCoroutineScope()
    val index = groups.indexOfFirst { it.id == group?.id }
    when (step) {
        GroupStep.MAIN -> KodiContextMenu(
            title = group?.name.orEmpty(),
            onDismiss = onDismiss,
            items = buildList {
                if (group != null && index > 0) {
                    add(
                        KodiListItem(stringResource(KMR.strings.menu_move_up)) {
                            scope.launch { Injekt.get<ReorderCategory>().await(group, index - 1) }
                        },
                    )
                }
                if (group != null && index in 0 until groups.lastIndex) {
                    add(
                        KodiListItem(stringResource(KMR.strings.menu_move_down)) {
                            scope.launch { Injekt.get<ReorderCategory>().await(group, index + 1) }
                        },
                    )
                }
                add(KodiListItem(stringResource(KMR.strings.menu_rename_group)) { step = GroupStep.RENAME })
                add(KodiListItem(stringResource(KMR.strings.menu_delete_group)) { step = GroupStep.DELETE })
                add(KodiListItem(stringResource(KMR.strings.menu_create_group)) { step = GroupStep.CREATE })
                add(KodiListItem(stringResource(MR.strings.action_cancel)) { onDismiss() })
            },
        )
        GroupStep.RENAME -> KodiKeyboardDialog(
            title = stringResource(KMR.strings.menu_rename_group),
            initial = group?.name.orEmpty(),
            onDismiss = { step = GroupStep.MAIN },
            onDone = { name ->
                if (group != null && name.isNotBlank()) {
                    scope.launch { Injekt.get<RenameCategory>().await(group, name) }
                }
                onDismiss()
            },
        )
        GroupStep.CREATE -> KodiKeyboardDialog(
            title = stringResource(KMR.strings.menu_create_group),
            onDismiss = { if (group == null) onDismiss() else step = GroupStep.MAIN },
            onDone = { name ->
                if (name.isNotBlank()) {
                    scope.launch { Injekt.get<CreateCategoryWithName>().await(name) }
                }
                onDismiss()
            },
        )
        GroupStep.DELETE -> KodiContextMenu(
            title = stringResource(KMR.strings.menu_confirm_delete_group, group?.name.orEmpty()),
            onDismiss = { step = GroupStep.MAIN },
            items = listOf(
                KodiListItem(stringResource(MR.strings.action_delete)) {
                    if (group != null) scope.launch { Injekt.get<DeleteCategory>().await(group.id, removeManga = true) }
                    onDismiss()
                },
                KodiListItem(stringResource(MR.strings.action_cancel)) { step = GroupStep.MAIN },
            ),
        )
    }
}

private enum class GroupStep { MAIN, RENAME, CREATE, DELETE }
// KMK <--
