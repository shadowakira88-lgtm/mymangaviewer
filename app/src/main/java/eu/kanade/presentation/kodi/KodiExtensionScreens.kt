package eu.kanade.presentation.kodi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Kodi-style extension list: updates first, then installed, then available.
 * Tap = install / update, long press = uninstall.
 */
object KodiExtensionsScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiExtensionsScreen

    @Composable
    override fun Content() {
        val manager = remember { Injekt.get<ExtensionManager>() }
        val scope = rememberCoroutineScope()
        var working by remember { mutableStateOf(emptySet<String>()) }

        val installed by produceState(initialValue = emptyList<Extension.Installed>()) {
            manager.installedExtensionsFlow.collectLatest { value = it }
        }
        val available by produceState(initialValue = emptyList<Extension.Available>()) {
            manager.availableExtensionsFlow.collectLatest { value = it }
        }
        // Make sure the extension store list is fetched at least once.
        produceState(initialValue = Unit) { runCatching { manager.findAvailableExtensions() } }

        val installedText = stringResource(KMR.strings.kodi_extension_installed)
        val updateText = stringResource(KMR.strings.kodi_extension_update)
        val installText = stringResource(KMR.strings.kodi_extension_install)
        val workingText = stringResource(KMR.strings.kodi_extension_working)

        fun run(key: String, block: suspend () -> Unit) {
            if (key in working) return
            working = working + key
            scope.launch(Dispatchers.IO) {
                runCatching { block() }
                working = working - key
            }
        }

        val installedPkgs = installed.map { it.pkgName }.toSet()
        val items = buildList {
            installed.sortedByDescending { it.hasUpdate }.forEach { ext ->
                val key = ext.pkgName
                add(
                    KodiListItem(
                        title = ext.name,
                        subtitle = when {
                            key in working -> workingText
                            ext.hasUpdate -> "${ext.versionName} · $updateText"
                            else -> "${ext.versionName} · $installedText"
                        },
                        onLongClick = { manager.uninstallExtension(ext) },
                    ) {
                        if (ext.hasUpdate) run(key) { manager.updateExtension(ext).collect() }
                    },
                )
            }
            available.filter { it.pkgName !in installedPkgs }
                .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
                .forEach { ext ->
                    val key = ext.pkgName
                    add(
                        KodiListItem(
                            title = ext.name,
                            subtitle = if (key in working) {
                                workingText
                            } else {
                                "${ext.lang.orEmpty()} · ${ext.versionName} · $installText"
                            },
                        ) { run(key) { manager.installExtension(ext).collect() } },
                    )
                }
        }
        KodiListPage(
            title = stringResource(MR.strings.label_extensions),
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
        )
    }
}

/** Migrate: sources that have library entries. Tap = open the list of manga to migrate. */
object KodiMigrateScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiMigrateScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sources by produceState(initialValue = emptyList<Pair<tachiyomi.domain.source.model.Source, Long>>()) {
            Injekt.get<GetSourcesWithFavoriteCount>().subscribe().collectLatest { value = it }
        }
        val countFmt = stringResource(KMR.strings.kodi_migrate_count, 0)
        val items = sources.map { (source, count) ->
            KodiListItem(
                title = source.name,
                subtitle = countFmt.replaceFirst("0", count.toString()),
            ) { navigator.push(MigrateMangaScreen(source.id)) }
        }
        KodiListPage(
            title = stringResource(MR.strings.label_migration),
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
        )
    }
}
// KMK <--
