package eu.kanade.presentation.kodi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.Preference
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
private val KodiBg = Color(0xFF0E1216)
private val KodiBlue = Color(0xFF6CA9D9)
private val KodiLine = Color(0xFFE0E0E0)
private val KodiDim = Color(0xFFB0B0B0)

private sealed interface PrefRow {
    class Header(val title: String) : PrefRow
    class Entry(
        val title: String,
        val subtitle: String?,
        val enabled: Boolean,
        val onClick: (() -> Unit)?,
    ) : PrefRow
    class Custom(val content: @Composable () -> Unit) : PrefRow
}

/**
 * Kodi-style page for a list of settings ([Preference]): title on the left, one focusable line per setting on the
 * right. Choices, text input and sliders open as Kodi popups inside the window, so the D-pad / A / B work and VR mode
 * mirrors them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KodiPreferencePage(
    title: String,
    items: List<Preference>,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var openRow by remember { mutableIntStateOf(-1) }
    val popups = remember { mutableMapOf<Int, @Composable () -> Unit>() }
    popups.clear()
    val rows = mutableListOf<PrefRow>()

    // Build the rows. The list shape is stable between recompositions, so state reads here are fine.
    items.forEach { p ->
        when (p) {
            is Preference.PreferenceGroup -> {
                if (!p.enabled) return@forEach
                rows += PrefRow.Header(p.title)
                p.preferenceItems.forEach { item ->
                    rows += buildRow(item, rows.size, scope, popups) { openRow = it }
                }
            }
            is Preference.PreferenceItem<*, *> -> rows += buildRow(p, rows.size, scope, popups) { openRow = it }
        }
    }

    KodiMenuHost(
        active = openRow >= 0,
        overlay = { popups[openRow]?.invoke() },
        content = {
            KodiWallpaperBox(fallback = KodiBg) {
                PrefPageBody(title, rows, actions)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrefPageBody(title: String, rows: List<PrefRow>, actions: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableIntStateOf(-1) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val firstEntry = rows.indexOfFirst { it is PrefRow.Entry }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(Modifier.width(220.dp).fillMaxHeight().padding(end = 16.dp)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
            (rows.getOrNull(focused) as? PrefRow.Entry)?.subtitle?.let {
                Text(it, color = KodiDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, Color(0xFF2A333C), RoundedCornerShape(4.dp)),
        ) {
            itemsIndexed(rows) { index, row ->
                when (row) {
                    is PrefRow.Header -> Text(
                        row.title,
                        color = KodiBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
                    )
                    is PrefRow.Custom -> Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) { row.content() }
                    is PrefRow.Entry -> {
                        var hasFocus by remember { mutableStateOf(false) }
                        val requester = if (index == firstEntry) first else remember { FocusRequester() }
                        val click = row.onClick
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(requester)
                                .onFocusChanged {
                                    hasFocus = it.isFocused
                                    if (it.isFocused) focused = index
                                }
                                .border(
                                    BorderStroke(1.5.dp, if (hasFocus) KodiLine else Color.Transparent),
                                    RoundedCornerShape(3.dp),
                                )
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA && click != null) {
                                        click()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .combinedClickable(
                                    enabled = row.enabled && click != null,
                                    onClick = { click?.invoke() },
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                row.title,
                                color = when {
                                    !row.enabled -> Color(0xFF707070)
                                    hasFocus -> KodiBlue
                                    else -> Color.White
                                },
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            row.subtitle?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    color = KodiDim,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildRow(
    item: Preference.PreferenceItem<*, *>,
    index: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    popups: MutableMap<Int, @Composable () -> Unit>,
    open: (Int) -> Unit,
): PrefRow {
    val close = { open(-1) }
    val sub = item.subtitle?.toString()
    return when (item) {
        is Preference.PreferenceItem.SwitchPreference -> {
            val value by item.preference.collectAsState()
            PrefRow.Entry(
                item.title,
                listOfNotNull(if (value) "[ON]" else "[OFF]", sub).joinToString("  "),
                item.enabled,
            ) {
                scope.launch { if (item.onValueChanged(!value)) item.preference.set(!value) }
            }
        }
        is Preference.PreferenceItem.TextPreference -> PrefRow.Entry(
            item.title,
            sub,
            item.enabled,
            item.onClick,
        )
        is Preference.PreferenceItem.InfoPreference -> PrefRow.Entry(item.title, null, false, null)
        is Preference.PreferenceItem.ListPreference<*> -> {
            val value by item.preference.collectAsState()
            val subtitle = item.internalSubtitleProvider(value, item.entries)
            popups[index] = {
                KodiContextMenu(
                    title = item.title,
                    items = item.entries.map { (k, label) ->
                        KodiListItem((if (k == value) "● " else "   ") + label) {
                            scope.launch { if (item.internalOnValueChanged(k!!)) item.internalSet(k!!) }
                            close()
                        }
                    },
                    onDismiss = close,
                )
            }
            PrefRow.Entry(item.title, subtitle, item.enabled) { open(index) }
        }
        is Preference.PreferenceItem.BasicListPreference -> {
            popups[index] = {
                KodiContextMenu(
                    title = item.title,
                    items = item.entries.map { (k, label) ->
                        KodiListItem((if (k == item.value) "● " else "   ") + label) {
                            scope.launch { item.onValueChanged(k) }
                            close()
                        }
                    },
                    onDismiss = close,
                )
            }
            PrefRow.Entry(item.title, item.subtitleProvider(item.value, item.entries), item.enabled) { open(index) }
        }
        is Preference.PreferenceItem.MultiSelectListPreference -> {
            val values by item.preference.collectAsState()
            popups[index] = {
                KodiContextMenu(
                    title = item.title,
                    items = item.entries.map { (k, label) ->
                        KodiListItem((if (k in values) "[x] " else "[   ] ") + label) {
                            val next = if (k in values) values - k else values + k
                            scope.launch { if (item.onValueChanged(next)) item.preference.set(next.toMutableSet()) }
                        }
                    } + KodiListItem("OK") { close() },
                    onDismiss = close,
                )
            }
            PrefRow.Entry(item.title, item.subtitleProvider(values, item.entries), item.enabled) { open(index) }
        }
        is Preference.PreferenceItem.EditTextPreference -> {
            val value by item.preference.collectAsState()
            popups[index] = {
                KodiKeyboardDialog(
                    title = item.title,
                    initial = value,
                    onDismiss = close,
                    onDone = { text ->
                        scope.launch { if (item.onValueChanged(text)) item.preference.set(text) }
                        close()
                    },
                )
            }
            PrefRow.Entry(item.title, item.subtitle?.format(value) ?: value, item.enabled) { open(index) }
        }
        is Preference.PreferenceItem.SliderPreference -> {
            popups[index] = {
                var current by remember { mutableIntStateOf(item.value) }
                val range = item.valueRange
                val step = if (range.step > 0) range.step else 1
                KodiContextMenu(
                    title = item.title + ": " + (item.valueString ?: current.toString()),
                    items = listOf(
                        KodiListItem("+") {
                            current = (current + step).coerceAtMost(range.last)
                            scope.launch { item.onValueChanged(current) }
                        },
                        KodiListItem("−") {
                            current = (current - step).coerceAtLeast(range.first)
                            scope.launch { item.onValueChanged(current) }
                        },
                        KodiListItem("OK") { close() },
                    ),
                    onDismiss = close,
                )
            }
            val shown = listOfNotNull(item.valueString ?: item.value.toString(), sub).joinToString("  ")
            PrefRow.Entry(item.title, shown, item.enabled) { open(index) }
        }
        is Preference.PreferenceItem.TrackerPreference -> {
            val loggedIn by item.tracker.isLoggedInFlow.collectAsState(item.tracker.isLoggedIn)
            val state = if (loggedIn) "Logged in - select to log out" else "Select to log in"
            PrefRow.Entry(item.tracker.name, state, true) {
                if (loggedIn) item.logout() else item.login()
            }
        }
        is Preference.PreferenceItem.ConnectionPreference -> {
            val loggedIn by item.service.isLoggedInFlow.collectAsState(item.service.isLogged)
            PrefRow.Entry(
                item.title,
                listOfNotNull(if (loggedIn) "Logged in" else "Not logged in", sub).joinToString("  "),
                true,
            ) {
                if (loggedIn) item.openSettings() else item.login()
            }
        }
        is Preference.PreferenceItem.CustomPreference -> PrefRow.Custom(item.content)
    }
}
// KMK <--
