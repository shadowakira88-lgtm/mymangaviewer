package eu.kanade.tachiyomi.ui.base.activity

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import eu.kanade.tachiyomi.ui.base.ControllerMapping
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegateImpl
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegate
import eu.kanade.tachiyomi.ui.base.delegate.ThemingDelegateImpl
import eu.kanade.tachiyomi.ui.base.vr.VrCalibration
import eu.kanade.tachiyomi.ui.base.vr.VrSbsLayout
import eu.kanade.tachiyomi.util.system.prepareTabletUiContext
import kotlinx.coroutines.flow.combine
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

        val uiPreferences = Injekt.get<UiPreferences>()
        val density = resources.displayMetrics.density
        val initialOrientation = requestedOrientation
        var wasEnabled = false

        combine(
            uiPreferences.vrUiScale().changes(),
            uiPreferences.vrEyeFill().changes(),
            uiPreferences.vrAspect().changes(),
            uiPreferences.vrSeparation().changes(),
            uiPreferences.vrOffsetY().changes(),
        ) { uiScale, eyeFill, aspect, separation, offsetY ->
            VrCalibration(
                uiScale = uiScale / 100f,
                eyeFill = eyeFill / 100f,
                aspect = aspect / 100f,
                separationPx = separation * density,
                offsetYPx = offsetY * density,
            )
        }
            .onEach { vr.calibration = it }
            .launchIn(lifecycleScope)

        uiPreferences.vrMode().changes()
            .onEach { enabled ->
                vr.vrEnabled = enabled
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (enabled) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
                } else if (wasEnabled) {
                    requestedOrientation = initialOrientation
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT)
                }
                wasEnabled = enabled
            }
            .launchIn(lifecycleScope)
    }

    private fun setCutoutMode(mode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = mode }
        }
    }

    final override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ControllerMapping.capturing) return super.dispatchKeyEvent(event)
        // Apply the user's controller mapping first.
        val e = ControllerMapping.translate(event, controllerContext()) ?: return true
        // Controller Start button toggles VR mode from anywhere in the app.
        if (e.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            if (e.action == KeyEvent.ACTION_UP) {
                val pref = Injekt.get<UiPreferences>().vrMode()
                pref.set(!pref.get())
            }
            return true
        }
        // Back button (B by default) acts as Back.
        if (e.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.action == KeyEvent.ACTION_UP) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        val handled = onMappedKeyEvent(e)
        // A (confirm) that nothing handled itself acts like D-pad center, so every ordinary clickable
        // element in the app (buttons, chips, cover grids, settings rows) can be pressed with the gamepad.
        if (!handled && confirmActsAsDpadCenter && e.keyCode == KeyEvent.KEYCODE_BUTTON_A &&
            e.action == KeyEvent.ACTION_UP
        ) {
            val now = android.os.SystemClock.uptimeMillis()
            fun center(action: Int) = KeyEvent(
                now,
                now,
                action,
                KeyEvent.KEYCODE_DPAD_CENTER,
                0,
                e.metaState,
                e.deviceId,
                0,
                e.flags,
                e.source,
            )
            super.dispatchKeyEvent(center(KeyEvent.ACTION_DOWN))
            return super.dispatchKeyEvent(center(KeyEvent.ACTION_UP))
        }
        return handled
    }

    /** Which button assignments apply right now (the reader switches between reading and magnifier). */
    protected open fun controllerContext(): ControllerMapping.Context = ControllerMapping.Context.SYSTEM

    /** Screens that handle the confirm button on their own (the reader) turn the fallback off. */
    protected open val confirmActsAsDpadCenter: Boolean = true

    /** Receives key events after the controller mapping has been applied. */
    protected open fun onMappedKeyEvent(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)
    // KMK <--
}
