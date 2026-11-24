package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * VR calibration, similar to Kodi's video calibration. Changes apply live, so they can be adjusted
 * while looking through the goggles.
 */
object SettingsVrScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsVrScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = KMR.strings.vr_settings

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val uiScale by uiPreferences.vrUiScale().collectAsState()
        val eyeFill by uiPreferences.vrEyeFill().collectAsState()
        val aspect by uiPreferences.vrAspect().collectAsState()
        val separation by uiPreferences.vrSeparation().collectAsState()
        val offsetY by uiPreferences.vrOffsetY().collectAsState()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(KMR.strings.vr_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = uiPreferences.vrMode(),
                        title = stringResource(KMR.strings.pref_vr_mode),
                        subtitle = stringResource(KMR.strings.pref_vr_mode_summary),
                    ),
                    slider(
                        value = uiScale,
                        range = 30..150,
                        step = 5,
                        title = stringResource(KMR.strings.vr_ui_size),
                        subtitle = stringResource(KMR.strings.vr_ui_size_summary),
                        unit = "%",
                    ) { uiPreferences.vrUiScale().set(it) },
                    slider(
                        value = eyeFill,
                        range = 50..100,
                        step = 5,
                        title = stringResource(KMR.strings.vr_eye_area),
                        subtitle = stringResource(KMR.strings.vr_eye_area_summary),
                        unit = "%",
                    ) { uiPreferences.vrEyeFill().set(it) },
                    slider(
                        value = aspect,
                        range = 50..150,
                        step = 5,
                        title = stringResource(KMR.strings.vr_pixel_ratio),
                        subtitle = stringResource(KMR.strings.vr_pixel_ratio_summary),
                        unit = "%",
                    ) { uiPreferences.vrAspect().set(it) },
                    slider(
                        value = separation,
                        range = -100..100,
                        step = 2,
                        title = stringResource(KMR.strings.vr_eye_separation),
                        subtitle = stringResource(KMR.strings.vr_eye_separation_summary),
                        unit = " dp",
                    ) { uiPreferences.vrSeparation().set(it) },
                    slider(
                        value = offsetY,
                        range = -100..100,
                        step = 2,
                        title = stringResource(KMR.strings.vr_vertical_shift),
                        subtitle = null,
                        unit = " dp",
                    ) { uiPreferences.vrOffsetY().set(it) },
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(KMR.strings.vr_reset),
                        onClick = {
                            uiPreferences.vrUiScale().delete()
                            uiPreferences.vrEyeFill().delete()
                            uiPreferences.vrAspect().delete()
                            uiPreferences.vrSeparation().delete()
                            uiPreferences.vrOffsetY().delete()
                        },
                    ),
                ),
            ),
        )
    }

    private fun slider(
        value: Int,
        range: IntRange,
        step: Int,
        title: String,
        subtitle: String?,
        unit: String,
        onChange: (Int) -> Unit,
    ) = Preference.PreferenceItem.SliderPreference(
        value = value,
        valueRange = range step step,
        title = title,
        subtitle = subtitle,
        valueString = "$value$unit",
        steps = (range.last - range.first) / step - 1,
        onValueChanged = { onChange(it) },
    )
}
// KMK <--
