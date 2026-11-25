package eu.kanade.tachiyomi.ui.base

import android.view.KeyEvent
import eu.kanade.domain.ui.UiPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * User-configurable controller mapping. Incoming key events are translated to a canonical key code
 * per action, so the rest of the app (Compose lists, reader) only ever deals with the canonical codes.
 */
object ControllerMapping {

    enum class Action(val canonical: Int, val default: Int) {
        UP(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP),
        DOWN(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        LEFT(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
        CONFIRM(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A),
        BACK(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B),
        NEXT_PAGE(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1),
        PREV_PAGE(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1),
        MAGNIFIER(KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_X),
    }

    /** True while the mapping screen is waiting for a button press; events are then passed through raw. */
    @Volatile
    var capturing = false

    private val prefs by lazy { Injekt.get<UiPreferences>() }

    private val canonicalCodes = Action.entries.map { it.canonical }.toSet()

    fun keyFor(action: Action): Int = prefs.controllerKey(action.name, action.default).get()

    fun setKey(action: Action, keyCode: Int) = prefs.controllerKey(action.name, action.default).set(keyCode)

    fun reset() = Action.entries.forEach { prefs.controllerKey(it.name, it.default).delete() }

    fun label(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

    /**
     * Returns the event to use for the rest of the app, or null if it must be swallowed
     * (a canonical controller key that has been re-assigned to another action).
     */
    fun translate(event: KeyEvent): KeyEvent? {
        if (capturing) return event
        val code = event.keyCode
        val action = Action.entries.firstOrNull { keyFor(it) == code }
        if (action != null) {
            if (action.canonical == code) return event
            return KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                action.canonical,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source,
            )
        }
        return if (code in canonicalCodes) null else event
    }
}
// KMK <--
