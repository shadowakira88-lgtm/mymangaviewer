package eu.kanade.presentation.more.settings

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.kodi.KodiPreferencePage
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PreferenceScaffold(
    titleRes: StringResource,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    // KMK -->
    // Every settings page is drawn as a Kodi list (keypad friendly, mirrored in VR mode).
    KodiPreferencePage(
        title = stringResource(titleRes),
        items = itemsProvider(),
        actions = actions,
    )
    // KMK <--
}
