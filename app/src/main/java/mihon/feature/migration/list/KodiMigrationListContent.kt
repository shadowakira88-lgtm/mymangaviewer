package mihon.feature.migration.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import eu.kanade.presentation.kodi.KodiIconButton
import eu.kanade.presentation.kodi.KodiWallpaperBox
import eu.kanade.presentation.manga.components.MangaCover
import kotlinx.collections.immutable.ImmutableList
import mihon.feature.migration.list.models.MigratingManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
private val CoverHeight = 84.dp

/**
 * Kodi Quartz look for the migration list: small covers, symbol buttons that a gamepad can reach and,
 * on the right, small covers of the same title found in other sources.
 */
@Composable
fun KodiMigrationListContent(
    items: ImmutableList<MigratingManga>,
    migrationComplete: Boolean,
    finishedCount: Int,
    onItemClick: (Manga) -> Unit,
    onSearchManually: (MigratingManga) -> Unit,
    onSkip: (Long) -> Unit,
    onMigrate: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    openMigrationDialog: (Boolean) -> Unit,
    onCancel: (Long) -> Unit,
    openOptionsDialog: () -> Unit,
    onLoadAlternatives: (Long) -> Unit,
    onPickAlternative: (Long, Long) -> Unit,
) {
    KodiWallpaperBox(fallback = Color(0xFF0E1216)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (items.isNotEmpty()) {
                        stringResource(MR.strings.migrationListScreenTitleWithProgress, finishedCount, items.size)
                    } else {
                        stringResource(MR.strings.migrationListScreenTitle)
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KodiIconButton(icon = Icons.Outlined.Settings, onClick = openOptionsDialog)
                    KodiIconButton(
                        icon = if (items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll,
                        enabled = migrationComplete,
                        onClick = { openMigrationDialog(true) },
                    )
                    KodiIconButton(
                        icon = if (items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll,
                        enabled = migrationComplete,
                        onClick = { openMigrationDialog(false) },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.manga.id }) { item ->
                    KodiMigrationRow(
                        item = item,
                        onItemClick = onItemClick,
                        onSearchManually = onSearchManually,
                        onSkip = onSkip,
                        onMigrate = onMigrate,
                        onCopy = onCopy,
                        onCancel = onCancel,
                        onLoadAlternatives = onLoadAlternatives,
                        onPickAlternative = onPickAlternative,
                    )
                }
            }
        }
    }
}

@Composable
private fun KodiMigrationRow(
    item: MigratingManga,
    onItemClick: (Manga) -> Unit,
    onSearchManually: (MigratingManga) -> Unit,
    onSkip: (Long) -> Unit,
    onMigrate: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onLoadAlternatives: (Long) -> Unit,
    onPickAlternative: (Long, Long) -> Unit,
) {
    val result by item.searchResult.collectAsState()
    val alternatives by item.alternatives.collectAsState()
    val loadingAlternatives by item.alternativesLoading.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2A333C), RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MiniManga(
            manga = item.manga,
            line1 = item.source,
            line2 = "${item.chapterCount}",
            onClick = { onItemClick(item.manga) },
        )
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
        when (val r = result) {
            MigratingManga.SearchResult.Searching -> {
                Box(Modifier.width(60.dp).height(CoverHeight), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            MigratingManga.SearchResult.NotFound -> {
                Box(Modifier.width(80.dp).height(CoverHeight), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(MR.strings.migrationListScreen_noMatchFoundText),
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                    )
                }
            }
            is MigratingManga.SearchResult.Success -> {
                MiniManga(
                    manga = r.manga,
                    line1 = r.source,
                    line2 = "${r.chapterCount}",
                    onClick = { onItemClick(r.manga) },
                    highlight = true,
                )
            }
        }

        // Actions
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (result == MigratingManga.SearchResult.Searching) {
                KodiIconButton(icon = Icons.Outlined.Close, onClick = { onCancel(item.manga.id) })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KodiIconButton(icon = Icons.Outlined.Search, onClick = { onSearchManually(item) })
                    KodiIconButton(icon = Icons.Outlined.Close, onClick = { onSkip(item.manga.id) })
                }
                if (result is MigratingManga.SearchResult.Success) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KodiIconButton(icon = Icons.Outlined.Done, onClick = { onMigrate(item.manga.id) })
                        KodiIconButton(icon = Icons.Outlined.ContentCopy, onClick = { onCopy(item.manga.id) })
                    }
                }
            }
        }

        // Other sources on the right
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (result != MigratingManga.SearchResult.Searching) {
                KodiIconButton(
                    label = if (loadingAlternatives) "…" else stringResource(KMR.strings.kodi_migrate_other_sources),
                    enabled = !loadingAlternatives,
                    onClick = { onLoadAlternatives(item.manga.id) },
                )
            }
            val alts = alternatives.orEmpty()
            if (alts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alts, key = { it.manga.id }) { alt ->
                        MiniManga(
                            manga = alt.manga,
                            line1 = alt.source,
                            line2 = "${alt.chapterCount}",
                            onClick = { onPickAlternative(item.manga.id, alt.manga.id) },
                        )
                    }
                }
            } else if (alternatives != null && !loadingAlternatives) {
                Text(stringResource(KMR.strings.kodi_empty_list), color = Color(0xFFB0B0B0), fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniManga(
    manga: Manga,
    line1: String,
    line2: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(70.dp)
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                BorderStroke(
                    1.5.dp,
                    when {
                        hasFocus -> Color(0xFFE0E0E0)
                        highlight -> Color(0xFF6CA9D9)
                        else -> Color.Transparent
                    },
                ),
                RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = onClick)
            .padding(2.dp),
    ) {
        MangaCover.Book(data = manga, modifier = Modifier.height(CoverHeight))
        Text(
            manga.title,
            color = if (hasFocus) Color(0xFF6CA9D9) else Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$line1 · $line2",
            color = Color(0xFFB0B0B0),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
// KMK <--
