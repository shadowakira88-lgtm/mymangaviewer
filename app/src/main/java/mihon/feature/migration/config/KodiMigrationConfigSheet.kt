package mihon.feature.migration.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.kodi.KodiKeyboardDialog
import eu.kanade.presentation.kodi.KodiListItem
import eu.kanade.presentation.kodi.KodiListPage
import mihon.domain.migration.models.MigrationFlag
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.preference.toggle
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
/**
 * Kodi Quartz version of the migration options: a full-screen list that a gamepad can drive.
 * Left: Continue / Cancel. Right: every option as a row (A = toggle).
 */
@Composable
fun KodiMigrationConfigSheet(
    preferences: SourcePreferences,
    onDismissRequest: () -> Unit,
    onStartMigration: (extraSearchQuery: String?) -> Unit,
    fullSettings: Boolean,
    isSingleEntry: Boolean,
) {
    var extraSearchQuery by rememberSaveable { mutableStateOf("") }
    var editingQuery by remember { mutableStateOf(false) }
    val flags by preferences.migrationFlags().collectAsState()
    val on = stringResource(MR.strings.on)
    val off = stringResource(MR.strings.off)
    fun state(value: Boolean) = if (value) "✓ $on" else off

    @Composable
    fun toggleItem(title: String, subtitle: String?, pref: Preference<Boolean>): KodiListItem {
        val value by pref.collectAsState()
        return KodiListItem(title, listOfNotNull(state(value), subtitle).joinToString(" - ")) { pref.toggle() }
    }

    val items = buildList {
        MigrationFlag.entries.filterNot { it == MigrationFlag.REMOVE_DOWNLOAD }.forEach { flag ->
            add(
                KodiListItem(stringResource(flag.getLabel()), state(flag in flags)) {
                    preferences.migrationFlags().getAndSet { if (flag in it) it - flag else it + flag }
                },
            )
        }
        add(
            KodiListItem(
                stringResource(MR.strings.migrationConfigScreen_removeDownloadsTitle),
                state(MigrationFlag.REMOVE_DOWNLOAD in flags),
            ) {
                preferences.migrationFlags().getAndSet {
                    if (MigrationFlag.REMOVE_DOWNLOAD in it) {
                        it - MigrationFlag.REMOVE_DOWNLOAD
                    } else {
                        it + MigrationFlag.REMOVE_DOWNLOAD
                    }
                }
            },
        )
        if (fullSettings) {
            add(
                KodiListItem(
                    stringResource(MR.strings.migrationConfigScreen_additionalSearchQueryLabel),
                    extraSearchQuery.ifBlank { stringResource(MR.strings.off) },
                ) { editingQuery = true },
            )
        }
        add(
            toggleItem(
                stringResource(MR.strings.migrationConfigScreen_hideUnmatchedTitle),
                null,
                preferences.migrationHideUnmatched(),
            ),
        )
        add(
            toggleItem(
                stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesTitle),
                stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesSubtitle),
                preferences.migrationHideWithoutUpdates(),
            ),
        )
        if (fullSettings) {
            val singlePref = preferences.migrationSmartSearchSingleEntry()
            val smartSingle by singlePref.collectAsState()
            if (isSingleEntry) {
                add(
                    toggleItem(
                        stringResource(KMR.strings.migrationConfigScreen_smartSearchSingleEntryTitle),
                        stringResource(KMR.strings.migrationConfigScreen_smartSearchSingleEntrySubtitle),
                        singlePref,
                    ),
                )
            }
            if (!isSingleEntry || smartSingle) {
                add(
                    toggleItem(
                        stringResource(MR.strings.migrationConfigScreen_deepSearchModeTitle),
                        stringResource(MR.strings.migrationConfigScreen_deepSearchModeSubtitle),
                        preferences.migrationDeepSearchMode(),
                    ),
                )
                add(
                    toggleItem(
                        stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersTitle),
                        stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersSubtitle),
                        preferences.migrationPrioritizeByChapters(),
                    ),
                )
            }
        }
    }

    // Drawn inside the window (not a Dialog) so it follows the controller mapping and is mirrored in VR mode.
    BackHandler(onBack = onDismissRequest)
    if (editingQuery) {
        KodiKeyboardDialog(
            title = stringResource(MR.strings.migrationConfigScreen_additionalSearchQueryLabel),
            initial = extraSearchQuery,
            onDismiss = { editingQuery = false },
            onDone = {
                extraSearchQuery = it
                editingQuery = false
            },
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            KodiListPage(
                title = stringResource(MR.strings.migrationConfigScreen_dataToMigrateHeader),
                items = items,
                headerActions = listOf(
                    KodiListItem(
                        stringResource(
                            if (fullSettings) {
                                MR.strings.migrationConfigScreen_continueButtonText
                            } else {
                                MR.strings.action_save
                            },
                        ),
                    ) { onStartMigration(extraSearchQuery.trim().ifBlank { null }) },
                    KodiListItem(stringResource(MR.strings.action_cancel)) { onDismissRequest() },
                ),
            )
        }
    }
}
// KMK <--
