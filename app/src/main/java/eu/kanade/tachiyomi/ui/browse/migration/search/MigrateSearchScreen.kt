package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.MigrateSearchScreen
import eu.kanade.presentation.browse.components.BulkFavoriteDialogs
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import mihon.feature.migration.dialog.MigrateMangaDialog
import mihon.feature.migration.list.MigrationListScreen
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

class MigrateSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { MigrateSearchScreenModel(mangaId = mangaId) }
        val state by screenModel.state.collectAsState()

        // KMK -->
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

        BackHandler(enabled = bulkFavoriteState.selectionMode) {
            bulkFavoriteScreenModel.backHandler()
        }
        // KMK <--

        // KMK -->
        val kodi = androidx.compose.runtime.remember {
            uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>().kodiLayout().get()
        }
        val onPick: (tachiyomi.domain.manga.model.Manga) -> Unit = {
            val migrateListScreen = navigator.items
                .filterIsInstance<MigrationListScreen>()
                .lastOrNull()
            if (migrateListScreen == null) {
                screenModel.setMigrateDialog(mangaId, it)
            } else {
                migrateListScreen.addMatchOverride(current = mangaId, target = it.id)
                navigator.popUntil { screen -> screen is MigrationListScreen }
            }
        }
        if (kodi) {
            KodiMigrateSearch(
                state = state,
                onPick = onPick,
                onOpen = { navigator.push(MangaScreen(it.id, true)) },
                onBack = navigator::pop,
            )
        } else
        // KMK <--
        MigrateSearchScreen(
            state = state,
            fromSourceId = state.from?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onClickSource = { navigator.push(MigrateSourceSearchScreen(state.from!!, it.id, state.searchQuery)) },
            onClickItem = {
                // KMK -->
                if (bulkFavoriteState.selectionMode) {
                    bulkFavoriteScreenModel.toggleSelection(it)
                } else {
                    // KMK <--
                    val migrateListScreen = navigator.items
                        .filterIsInstance<MigrationListScreen>()
                        .lastOrNull()

                    if (migrateListScreen == null) {
                        screenModel.setMigrateDialog(mangaId, it)
                    } else {
                        migrateListScreen.addMatchOverride(current = mangaId, target = it.id)
                        navigator.popUntil { screen -> screen is MigrationListScreen }
                    }
                }
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
            // KMK -->
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            hasPinnedSources = screenModel.hasPinnedSources(),
            // KMK <--
        )

        when (val dialog = state.dialog) {
            is SearchScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.current] so we show [dialog.target].
                    onClickTitle = { navigator.push(MangaScreen(dialog.target.id, true)) },
                    onDismissRequest = { screenModel.clearDialog() },
                    onComplete = {
                        if (navigator.lastItem is MangaScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(MangaScreen(dialog.target.id))
                        } else {
                            navigator.replace(MangaScreen(dialog.target.id))
                        }
                    },
                )
            }
            else -> {}
        }

        // KMK -->
        // Bulk-favorite actions only
        BulkFavoriteDialogs(
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            dialog = bulkFavoriteState.dialog,
        )
        // KMK <--
    }
}

// KMK -->
/**
 * Kodi page of the migration search: the left side shows progress and Back, the right side lists every manga found
 * (source under the title). Sources still searching are shown as one line each.
 */
@Composable
private fun KodiMigrateSearch(
    state: SearchScreenModel.State,
    onPick: (tachiyomi.domain.manga.model.Manga) -> Unit,
    onOpen: (tachiyomi.domain.manga.model.Manga) -> Unit,
    onBack: () -> Unit,
) {
    val searching = stringResource(KMR.strings.kodi_migrate_searching)
    val noResult = stringResource(KMR.strings.kodi_migrate_no_result)
    val items = buildList {
        state.items.forEach { (source, result) ->
            when (result) {
                is SearchItemResult.Success -> result.result.forEach { m ->
                    add(
                        eu.kanade.presentation.kodi.KodiListItem(
                            title = m.title,
                            subtitle = source.name,
                            cover = m.asMangaCover(),
                            onLongClick = { onOpen(m) },
                        ) { onPick(m) },
                    )
                }
                is SearchItemResult.Loading ->
                    add(eu.kanade.presentation.kodi.KodiListItem(source.name + " - " + searching) {})
                else -> Unit
            }
        }
    }
    eu.kanade.presentation.kodi.KodiListPage(
        title = (state.from?.title ?: "") + "  (" + state.progress + "/" + state.total + ")",
        headerActions = listOf(
            eu.kanade.presentation.kodi.KodiListItem(
                stringResource(tachiyomi.i18n.MR.strings.action_cancel),
            ) { onBack() },
        ),
        items = items.ifEmpty {
            listOf(
                eu.kanade.presentation.kodi.KodiListItem(if (state.progress < state.total) searching else noResult) {},
            )
        },
    )
}
// KMK <--
