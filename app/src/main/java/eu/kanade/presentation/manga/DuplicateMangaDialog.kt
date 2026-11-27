package eu.kanade.presentation.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.kodi.KodiPopupLayer
import eu.kanade.presentation.manga.components.MangaCover
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * "Possible duplicates" popup in Kodi style: drawn inside the window, one entry per line, controlled with the
 * D-pad. A touch first highlights a line, a second touch confirms it.
 */
@Composable
fun DuplicateMangaDialog(
    duplicates: List<MangaWithChapterCount>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onOpenManga: (manga: Manga) -> Unit,
    onMigrate: (manga: Manga) -> Unit,
    modifier: Modifier = Modifier,
    targetManga: Manga,
    bulkFavoriteManga: Manga? = null,
    onAllowAllDuplicate: () -> Unit = {},
    onSkipAllDuplicate: () -> Unit = {},
    onSkipDuplicate: () -> Unit = {},
    stopRunning: () -> Unit = {},
) {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val close = {
        stopRunning()
        onDismissRequest()
    }

    class Line(val key: String, val label: String, val sub: String?, val cover: Manga?, val run: () -> Unit)

    val migrateLabel = stringResource(MR.strings.action_migrate)
    val lines = buildList {
        duplicates.forEach { dup ->
            val count = dup.chapterCount
            val chapters = pluralStringResource(MR.plurals.manga_num_chapters, count.toInt(), count)
            add(
                Line(
                    "d${dup.manga.id}",
                    dup.manga.title,
                    "${sourceManager.getOrStub(dup.manga.source).name} • $chapters • $migrateLabel",
                    dup.manga,
                ) { onMigrate(dup.manga) },
            )
        }
        add(Line("add", stringResource(MR.strings.action_add_anyway), null, null) {
            onDismissRequest()
            onConfirm()
        })
        if (bulkFavoriteManga != null) {
            add(
                Line("allow", stringResource(KMR.strings.action_allow_all_duplicate_manga), null, null) {
                    onDismissRequest()
                    onAllowAllDuplicate()
                },
            )
            add(
                Line("skip", stringResource(KMR.strings.action_skip_duplicate_manga), null, null) {
                    onDismissRequest()
                    onSkipDuplicate()
                },
            )
            add(
                Line("skipall", stringResource(KMR.strings.action_skip_all_duplicate_manga), null, null) {
                    onDismissRequest()
                    onSkipAllDuplicate()
                },
            )
            add(
                Line("show", stringResource(MR.strings.action_show_manga), null, null) {
                    onOpenManga(bulkFavoriteManga)
                },
            )
        }
        add(Line("cancel", stringResource(MR.strings.action_cancel), null, null) { close() })
    }

    val title = stringResource(MR.strings.possible_duplicates_title)
    val summary = stringResource(MR.strings.possible_duplicates_summary)
    KodiPopupLayer {
        BackHandler(onBack = close)
        var highlighted by remember { mutableIntStateOf(-1) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 340.dp)
                    .background(Color(0xF0101418))
                    .border(1.dp, Color(0xFF8A929A))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Text(
                    text = targetManga.title,
                    color = Color(0xFFB8B8B8),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
                Text(
                    text = summary.replace("\n", " ").trim(),
                    color = Color(0xFF8A929A),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .focusGroup(),
                ) {
                    lines.forEachIndexed { index, line ->
                        var hasFocus by remember { mutableIntStateOf(0) }
                        val active = hasFocus == 1 || highlighted == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                .onFocusChanged { hasFocus = if (it.isFocused) 1 else 0 }
                                .background(if (active) Color(0x40FFFFFF) else Color.Transparent)
                                .combinedClickable(
                                    onLongClick = line.cover?.let { m -> { onOpenManga(m) } },
                                    onClick = {
                                        if (hasFocus == 1 || highlighted == index) {
                                            highlighted = -1
                                            line.run()
                                        } else {
                                            highlighted = index
                                        }
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (line.cover != null) {
                                MangaCover.Book(modifier = Modifier.width(36.dp), data = line.cover)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = line.label,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (line.sub != null) {
                                    Text(
                                        text = line.sub,
                                        color = Color(0xFFB8B8B8),
                                        fontSize = 12.sp,
                                        maxLines = 1,
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
}
// KMK <--
