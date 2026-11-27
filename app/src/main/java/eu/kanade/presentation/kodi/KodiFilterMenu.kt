package eu.kanade.presentation.kodi

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.source.model.EXHSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/**
 * Kodi version of the source filter sheet: every filter is one row that a gamepad can press
 * (toggle / cycle / type). Drawn inside the window, so it follows the controller mapping and VR mirroring.
 */
@Composable
fun KodiFilterMenu(
    filters: FilterList,
    savedSearches: List<EXHSavedSearch>,
    canSave: Boolean,
    onUpdate: (FilterList) -> Unit,
    onReset: () -> Unit,
    onSearch: () -> Unit,
    onSave: () -> Unit,
    onSavedSearch: (EXHSavedSearch) -> Unit,
    onSavedSearchPress: (EXHSavedSearch) -> Unit,
    mangaDexRandom: (() -> Unit)?,
    mangaDexFollows: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf<Filter<*>?>(null) }
    BackHandler(onBack = onDismiss)

    fun changed() {
        version++
        onUpdate(filters)
    }

    typing?.let { target ->
        KodiKeyboardDialog(
            title = target.name,
            initial = when (target) {
                is Filter.Text -> target.state
                is Filter.AutoComplete -> target.state.joinToString(", ")
                else -> ""
            },
            onDismiss = { typing = null },
            onDone = { text ->
                when (target) {
                    is Filter.Text -> target.state = text
                    is Filter.AutoComplete -> target.state = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> Unit
                }
                typing = null
                changed()
            },
        )
        return
    }

    // Reading the version makes the rows rebuild after a filter changed in place.
    val rebuild = version
    val rows = buildList {
        flatten(filters.list, "", this)
    }
    val items = buildList {
        savedSearches.forEach { search ->
            add(KodiListItem("★ ${search.name}", onLongClick = { onSavedSearchPress(search) }) { onSavedSearch(search) })
        }
        rows.forEach { (prefix, filter) ->
            rowFor(prefix, filter, rebuild, { typing = it }, ::changed)?.let { add(it) }
        }
    }

    val actions = buildList {
        add(
            KodiListItem(stringResource(MR.strings.action_filter)) {
                onSearch()
                onDismiss()
            },
        )
        add(KodiListItem(stringResource(MR.strings.action_reset)) { onReset() })
        if (canSave) add(KodiListItem(stringResource(MR.strings.action_save)) { onSave() })
        mangaDexRandom?.let { add(KodiListItem("MangaDex random") { it() }) }
        mangaDexFollows?.let { add(KodiListItem("MangaDex follows") { it() }) }
        add(KodiListItem(stringResource(MR.strings.action_cancel)) { onDismiss() })
    }
    KodiListPage(
        title = stringResource(MR.strings.action_filter),
        items = items.ifEmpty { listOf(KodiListItem("-") {}) },
        headerActions = actions,
    )
}

private fun flatten(list: List<Filter<*>>, prefix: String, out: MutableList<Pair<String, Filter<*>>>) {
    list.forEach { filter ->
        if (filter is Filter.Group<*>) {
            flatten(filter.state.filterIsInstance<Filter<*>>(), "$prefix${filter.name} › ", out)
        } else {
            out.add(prefix to filter)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun rowFor(
    prefix: String,
    filter: Filter<*>,
    version: Int,
    onType: (Filter<*>) -> Unit,
    changed: () -> Unit,
): KodiListItem? {
    val name = prefix + filter.name
    return when (filter) {
        is Filter.Header -> KodiListItem("— $name —") {}
        is Filter.CheckBox -> KodiListItem((if (filter.state) "[✓] " else "[  ] ") + name) {
            filter.state = !filter.state
            changed()
        }
        is Filter.TriState -> KodiListItem(
            when (filter.state) {
                Filter.TriState.STATE_INCLUDE -> "[+] "
                Filter.TriState.STATE_EXCLUDE -> "[−] "
                else -> "[  ] "
            } + name,
        ) {
            filter.state = (filter.state + 1) % 3
            changed()
        }
        is Filter.Select<*> -> KodiListItem("$name: ${filter.values.getOrNull(filter.state)}") {
            if (filter.values.isNotEmpty()) filter.state = (filter.state + 1) % filter.values.size
            changed()
        }
        is Filter.Sort -> {
            val selection = filter.state
            val label = selection?.let { "${filter.values[it.index]} ${if (it.ascending) "↑" else "↓"}" } ?: "-"
            KodiListItem("$name: $label") {
                filter.state = when {
                    selection == null -> Filter.Sort.Selection(0, true)
                    selection.ascending -> Filter.Sort.Selection(selection.index, false)
                    selection.index + 1 < filter.values.size -> Filter.Sort.Selection(selection.index + 1, true)
                    else -> null
                }
                changed()
            }
        }
        is Filter.Text -> KodiListItem("$name: ${filter.state}") { onType(filter) }
        is Filter.AutoComplete -> KodiListItem("$name: ${filter.state.joinToString(", ")}") { onType(filter) }
        else -> null
    }
}
// KMK <--
