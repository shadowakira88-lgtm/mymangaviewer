package eu.kanade.presentation.kodi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetSourcesWithFavoriteCount
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Process-wide extension install jobs, so installs keep running (and their state is shared between the
 * Extensions and Missing sources screens) when the person leaves a screen. Extensions are installed inside the
 * app (private installer) - no system package installer prompt.
 */
private object KodiExtensionJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)
    private val manager by lazy { Injekt.get<ExtensionManager>() }
    val steps = mutableStateMapOf<String, InstallStep>()

    /** Install without the system package installer, unless Shizuku was chosen on purpose. */
    fun ensurePrivateInstaller() {
        val pref = Injekt.get<BasePreferences>().extensionInstaller()
        if (pref.get() != BasePreferences.ExtensionInstaller.SHIZUKU &&
            pref.get() != BasePreferences.ExtensionInstaller.PRIVATE
        ) {
            pref.set(BasePreferences.ExtensionInstaller.PRIVATE)
        }
    }

    fun isBusy(key: String) = steps[key]?.let { !it.isCompleted() } == true

    fun install(ext: Extension.Available) = run(ext.pkgName) { manager.installExtension(ext) }

    fun update(ext: Extension.Installed) = run(ext.pkgName) { manager.updateExtension(ext) }

    private fun run(key: String, flow: () -> Flow<InstallStep>) {
        if (isBusy(key)) return
        ensurePrivateInstaller()
        steps[key] = InstallStep.Pending
        scope.launch {
            try {
                gate.withPermit {
                    flow().onEach { steps[key] = it }.first { it.isCompleted() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                steps[key] = InstallStep.Error
            }
        }
    }
}

/** Status text of an install job (null when nothing is running / failed). */
@Composable
private fun extensionJobText(key: String, progress: Map<String, Int>): String? {
    val waiting = stringResource(KMR.strings.kodi_extension_waiting)
    val installing = stringResource(KMR.strings.kodi_extension_installing)
    val failed = stringResource(KMR.strings.kodi_extension_failed)
    val percent = progress[key]
    val downloading = stringResource(KMR.strings.kodi_extension_downloading, percent ?: 0)
    return when (KodiExtensionJobs.steps[key]) {
        InstallStep.Pending -> waiting
        InstallStep.Downloading -> if (percent != null) downloading else waiting
        InstallStep.Installing -> installing
        InstallStep.Error -> failed
        else -> null
    }
}

/**
 * Kodi-style extension list: updates first, then installed, then available.
 * Press = select / unselect (several can be installed at once), header button installs the selection.
 * Y / long press = uninstall.
 */
object KodiExtensionsScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiExtensionsScreen

    @Composable
    override fun Content() {
        val manager = remember { Injekt.get<ExtensionManager>() }
        var selected by remember { mutableStateOf(emptySet<String>()) }

        val installed by produceState(initialValue = emptyList<Extension.Installed>()) {
            manager.installedExtensionsFlow.collectLatest { value = it }
        }
        val available by produceState(initialValue = emptyList<Extension.Available>()) {
            manager.availableExtensionsFlow.collectLatest { value = it }
        }
        val progress by manager.installProgress.collectAsState()
        // Make sure the extension store list is fetched at least once.
        produceState(initialValue = Unit) { runCatching { manager.findAvailableExtensions() } }

        val installedText = stringResource(KMR.strings.kodi_extension_installed)
        val updateText = stringResource(KMR.strings.kodi_extension_update)
        val installText = stringResource(KMR.strings.kodi_extension_install)
        val selectedText = stringResource(KMR.strings.kodi_extension_selected)

        val installedPkgs = installed.map { it.pkgName }.toSet()
        val toInstall = available.filter { it.pkgName !in installedPkgs }
        val updatable = installed.filter { it.hasUpdate }

        fun toggle(key: String) {
            selected = if (key in selected) selected - key else selected + key
        }

        val items = buildList {
            installed.sortedByDescending { it.hasUpdate }.forEach { ext ->
                val key = ext.pkgName
                val job = extensionJobText(key, progress)
                add(
                    KodiListItem(
                        title = (if (key in selected) "✓ " else "") + ext.name,
                        subtitle = job ?: when {
                            key in selected -> selectedText
                            ext.hasUpdate -> "${ext.versionName} · $updateText"
                            else -> "${ext.versionName} · $installedText"
                        },
                        onLongClick = { manager.uninstallExtension(ext) },
                    ) {
                        if (ext.hasUpdate && !KodiExtensionJobs.isBusy(key)) toggle(key)
                    },
                )
            }
            toInstall
                .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
                .forEach { ext ->
                    val key = ext.pkgName
                    add(
                        KodiListItem(
                            title = (if (key in selected) "✓ " else "") + ext.name,
                            subtitle = extensionJobText(key, progress) ?: if (key in selected) {
                                selectedText
                            } else {
                                "${ext.lang.orEmpty()} · ${ext.versionName} · $installText"
                            },
                        ) { if (!KodiExtensionJobs.isBusy(key)) toggle(key) },
                    )
                }
        }

        val actions = buildList {
            if (selected.isNotEmpty()) {
                add(
                    KodiListItem(stringResource(KMR.strings.kodi_extension_install_selected, selected.size)) {
                        installed.filter { it.pkgName in selected && it.hasUpdate }.forEach(KodiExtensionJobs::update)
                        toInstall.filter { it.pkgName in selected }.forEach(KodiExtensionJobs::install)
                        selected = emptySet()
                    },
                )
                add(KodiListItem(stringResource(KMR.strings.kodi_extension_clear)) { selected = emptySet() })
            }
            if (updatable.isNotEmpty()) {
                add(
                    KodiListItem(stringResource(KMR.strings.kodi_extension_update_all)) {
                        updatable.forEach(KodiExtensionJobs::update)
                    },
                )
            }
        }
        KodiListPage(
            title = stringResource(MR.strings.label_extensions),
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_empty_list)) {}) },
            headerActions = actions,
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
/**
 * Library manga whose source is not installed on this phone (typically after syncing from another phone):
 * one row per manga with the extension that has to be installed. Press = select the extension, header button
 * installs all selected extensions (several at once, with progress).
 */
object KodiMissingSourcesScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KodiMissingSourcesScreen

    private data class Row(val title: String, val sourceId: Long, val ext: Extension.Available?)

    @Composable
    override fun Content() {
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val manager = remember { Injekt.get<ExtensionManager>() }
        var selected by remember { mutableStateOf(emptySet<String>()) }
        val library by produceState(initialValue = emptyList<tachiyomi.domain.library.model.LibraryManga>()) {
            Injekt.get<GetLibraryManga>().subscribe().collectLatest { value = it }
        }
        val available by produceState(initialValue = emptyList<Extension.Available>()) {
            manager.availableExtensionsFlow.collectLatest { value = it }
        }
        produceState(initialValue = Unit) { runCatching { manager.findAvailableExtensions() } }
        val progress by manager.installProgress.collectAsState()
        // Re-evaluate when sources are (un)installed.
        val sourcesVersion by produceState(initialValue = 0) {
            sourceManager.sources.collectLatest { value += 1 }
        }
        val rows = remember(library, sourcesVersion, available) {
            library
                .filter { sourceManager.get(it.manga.source) == null }
                .map { lm ->
                    Row(
                        title = lm.manga.title,
                        sourceId = lm.manga.source,
                        ext = available.firstOrNull { ext -> ext.sources.any { it.id == lm.manga.source } },
                    )
                }
                .sortedWith(compareBy({ it.ext?.name?.lowercase() ?: "~" }, { it.title.lowercase() }))
        }
        val selectedText = stringResource(KMR.strings.kodi_extension_selected)
        val installText = stringResource(KMR.strings.kodi_extension_install)

        val items = rows.map { row ->
            val ext = row.ext
            if (ext == null) {
                val name = sourceManager.getOrStub(row.sourceId).name.ifBlank { "Source ${row.sourceId}" }
                KodiListItem(
                    title = row.title,
                    subtitle = stringResource(KMR.strings.kodi_missing_not_found, name),
                ) {}
            } else {
                val key = ext.pkgName
                val needs = stringResource(KMR.strings.kodi_missing_needs, "${ext.name} (${ext.lang})")
                KodiListItem(
                    title = (if (key in selected) "✓ " else "") + row.title,
                    subtitle = extensionJobText(key, progress)?.let { "$needs · $it" }
                        ?: if (key in selected) "$needs · $selectedText" else "$needs · $installText",
                ) {
                    if (!KodiExtensionJobs.isBusy(key)) {
                        selected = if (key in selected) selected - key else selected + key
                    }
                }
            }
        }
        val needed = rows.mapNotNull { it.ext }.distinctBy { it.pkgName }
        val actions = buildList {
            if (selected.isNotEmpty()) {
                add(
                    KodiListItem(stringResource(KMR.strings.kodi_extension_install_selected, selected.size)) {
                        needed.filter { it.pkgName in selected }.forEach(KodiExtensionJobs::install)
                        selected = emptySet()
                    },
                )
                add(KodiListItem(stringResource(KMR.strings.kodi_extension_clear)) { selected = emptySet() })
            }
            if (needed.isNotEmpty()) {
                add(
                    KodiListItem(stringResource(KMR.strings.kodi_extension_select_all)) {
                        selected = needed.map { it.pkgName }.toSet()
                    },
                )
            }
        }
        KodiListPage(
            title = stringResource(KMR.strings.kodi_menu_browse_missing),
            items = items.ifEmpty { listOf(KodiListItem(stringResource(KMR.strings.kodi_missing_none)) {}) },
            headerActions = actions,
        )
    }
}
// KMK <--
