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

    /** Where the person currently is; each context has its own button assignments. */
    enum class Context { SYSTEM, READING, MAGNIFIER, READING_MENU }

    enum class Action(val context: Context, val canonical: Int, val default: Int) {
        // System
        UP(Context.SYSTEM, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP),
        DOWN(Context.SYSTEM, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        LEFT(Context.SYSTEM, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT(Context.SYSTEM, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
        CONFIRM(Context.SYSTEM, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_A),
        BACK(Context.SYSTEM, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_B),
        MENU(Context.SYSTEM, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_Y),

        // Reading
        NEXT_PAGE(Context.READING, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1),
        PREV_PAGE(Context.READING, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1),
        SCROLL_DOWN(Context.READING, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        SCROLL_UP(Context.READING, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP),
        OPTION_MENU(Context.READING, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_SELECT),
        MAGNIFIER(Context.READING, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_X),
        NEXT_CHAPTER(Context.READING, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_BUTTON_R2),
        PREV_CHAPTER(Context.READING, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_BUTTON_L2),

        // Magnifier
        MAG_UP(Context.MAGNIFIER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP),
        MAG_DOWN(Context.MAGNIFIER, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
        MAG_LEFT(Context.MAGNIFIER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
        MAG_RIGHT(Context.MAGNIFIER, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
        MAG_ENLARGE(Context.MAGNIFIER, KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.KEYCODE_BUTTON_R1),
        MAG_SHRINK(Context.MAGNIFIER, KeyEvent.KEYCODE_ZOOM_OUT, KeyEvent.KEYCODE_BUTTON_L1),
        MAG_SHAPE(Context.MAGNIFIER, KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Y),
    }

    /** Contexts whose assignments apply in [context], most specific first. */
    private fun chain(context: Context): List<Context> = when (context) {
        Context.SYSTEM -> listOf(Context.SYSTEM)
        Context.READING -> listOf(Context.READING, Context.SYSTEM)
        Context.MAGNIFIER -> listOf(Context.MAGNIFIER, Context.READING, Context.SYSTEM)
        // Reader menu open: system buttons drive the menu, the menu button closes it again.
        Context.READING_MENU -> listOf(Context.SYSTEM)
    }

    /** True while the mapping screen is waiting for a button press; events are then passed through raw. */
    @Volatile
    var capturing = false

    private val prefs by lazy { Injekt.get<UiPreferences>() }


    fun keyFor(action: Action): Int = prefs.controllerKey(action.name, action.default).get()

    fun setKey(action: Action, keyCode: Int) = prefs.controllerKey(action.name, action.default).set(keyCode)

    fun reset() = Action.entries.forEach { prefs.controllerKey(it.name, it.default).delete() }

    fun label(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

    /**
     * Returns the event to use for the rest of the app, or null if it must be swallowed
     * (a canonical controller key that has been re-assigned to another action).
     */
    fun translate(event: KeyEvent, context: Context = Context.SYSTEM): KeyEvent? {
        if (capturing) return event
        val code = event.keyCode
        val candidates = chain(context).flatMap { c -> Action.entries.filter { it.context == c } } +
            if (context == Context.READING_MENU) listOf(Action.OPTION_MENU) else emptyList()
        val action = candidates.firstOrNull { keyFor(it) == code }
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
        return if (candidates.any { it.canonical == code }) null else event
    }
}
// KMK <--
