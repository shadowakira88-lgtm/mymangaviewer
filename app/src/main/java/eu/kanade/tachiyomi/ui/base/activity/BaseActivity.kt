package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.ui.base.vr.VrSbsLayout
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

open class BaseActivity :
    AppCompatActivity(),
    SecureActivityDelegate by SecureActivityDelegateImpl(),
    ThemingDelegate by ThemingDelegateImpl() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.prepareTabletUiContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppTheme(this)
        super.onCreate(savedInstanceState)
    }

    // KMK -->
    private var vrLayout: VrSbsLayout? = null

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        installVrLayout()
    }

    /**
     * Wraps the activity content in a [VrSbsLayout] so the whole app (including the reader)
     * can be shown side by side for VR goggles. It is a plain pass-through while VR mode is off.
     */
    private fun installVrLayout() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.childCount == 1 && content.getChildAt(0) is VrSbsLayout) return

        val children = (0 until content.childCount).map { content.getChildAt(it) }
        val params = children.map { it.layoutParams }
        content.removeAllViews()

        val vr = VrSbsLayout(this)
        children.forEachIndexed { index, child -> vr.addView(child, params[index]) }
        content.addView(
            vr,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        vrLayout = vr

        var wasEnabled = false
        Injekt.get<UiPreferences>().vrMode().changes()
            .onEach { enabled ->
                vr.vrEnabled = enabled
                if (enabled) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (wasEnabled) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                wasEnabled = enabled
            }
            .launchIn(lifecycleScope)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Controller Start button toggles VR mode from anywhere in the app.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (event.action == KeyEvent.ACTION_UP) {
                val pref = Injekt.get<UiPreferences>().vrMode()
                pref.set(!pref.get())
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    // KMK <--
}
