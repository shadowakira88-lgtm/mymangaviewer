package eu.kanade.presentation.kodi

// KMK -->
/** One-shot hand-over between the Kodi home menu and the library screen. */
object KodiNav {
    /** Category (group) that the library should open directly, set by the home menu. */
    @Volatile
    var pendingCategoryId: Long? = null
}
// KMK <--
