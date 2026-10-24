package alexcmb.mytvlauncher.repository

import alexcmb.mytvlauncher.util.SingletonHolder
import android.content.Context

/** The launcher's accent colour choices. Stored as ARGB so no Compose types leak in here. */
enum class AccentColor(val argb: Long) {
    INDIGO(0xFF3D5AFE),
    TEAL(0xFF1D9E75),
    CORAL(0xFFD85A30),
    PINK(0xFFD4537E),
    AMBER(0xFFEF9F27),
    BLUE(0xFF378ADD),
}

/** How big the app cards are: fewer columns and wider favourites mean bigger cards. */
enum class CardSize(val columns: Int, val favouriteWidthDp: Int) {
    SMALL(6, 130),
    MEDIUM(5, 150),
    LARGE(4, 190),
}

/** What fills the home screen behind everything. */
enum class BackgroundStyle {
    /** A dark base tinted with the accent colour. */
    SOLID,

    /** The focused app's banner, dimmed. */
    AMBIENT,

    /** A dark gradient tinted with the accent colour. */
    ACCENT_GRADIENT,

    /** A user-picked image, dimmed by the brightness setting. */
    WALLPAPER,

    /** Soft blobs of the accent's colours, drifting slowly. */
    AURORA,

    /** A user-picked video, looping silently, dimmed by the brightness setting. */
    VIDEO,
}

/** How a wallpaper image is scaled onto the screen. */
enum class WallpaperFit {
    /** Fill the screen keeping aspect ratio, cropping the overflow. */
    FILL,

    /** Stretch to fill exactly, ignoring aspect ratio. */
    STRETCH,

    /** Fit the whole image on screen, letterboxed. */
    FIT,

    /** Native size, centred (no scaling). */
    CENTER,

    /** Spread to the full width, keeping aspect ratio. */
    SPAN,
}

/** How the apps of a category grid are ordered. */
enum class AppSort { USAGE, ALPHABETICAL, MANUAL }

/** User appearance preferences. */
class SettingsRepository private constructor(context: Context) {
    companion object : SingletonHolder<SettingsRepository, Context>(::SettingsRepository) {
        private const val KEY_ACCENT = "accent"
        private const val KEY_ACCENT_AUTO = "accent_auto"
        private const val KEY_CLOCK_SECONDS = "clock_seconds"
        private const val KEY_CLOCK_DATE = "clock_date"
        private const val KEY_GREETING = "greeting"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_BG_IMAGE = "background_image"
        private const val KEY_BG_VIDEO = "background_video"
        private const val KEY_BG_FIT = "background_fit"
        private const val KEY_BG_INTENSITY = "background_intensity"
        private const val KEY_CARD_SIZE = "card_size"
        /** How strongly the background colour/image shows through, as a percent. */
        const val BG_INTENSITY_DEFAULT = 50
        const val BG_INTENSITY_MIN = 0
        const val BG_INTENSITY_MAX = 100
        const val BG_INTENSITY_STEP = 10
        private const val KEY_USAGE_COUNT = "usage_count"
        private const val KEY_APP_LABELS = "app_labels"
        private const val KEY_TAB_ORDER = "tab_order"
        private const val KEY_TABS_HIDDEN = "tabs_hidden"
        private const val KEY_STARTUP_TAB = "startup_tab"
        private const val KEY_PINNED_APPS = "pinned_apps"
        private const val KEY_APP_SORT = "app_sort"
        private const val KEY_APP_ORDER = "app_order"
        private const val KEY_AMBIENT_MINUTES = "ambient_minutes"
        /** Choices for sleep mode's delay, in minutes; 0 turns it off. */
        val AMBIENT_CHOICES = listOf(0, 1, 2, 5, 10, 30)
        const val AMBIENT_DEFAULT = 5
        // List separator. Category names are free text; a newline is the one thing they can't
        // contain (and package names never do).
        private const val TAB_SEPARATOR = "\n"
    }

    private val sharedPreferences = context.getSharedPreferences("settings", 0)

    fun accent(): AccentColor =
        sharedPreferences.getString(KEY_ACCENT, null)
            ?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.INDIGO

    fun setAccent(accent: AccentColor) =
        sharedPreferences.edit().putString(KEY_ACCENT, accent.name).apply()

    /** When on, the accent tracks the focused app's banner; the fixed [accent] is the base. */
    fun accentAuto(): Boolean = sharedPreferences.getBoolean(KEY_ACCENT_AUTO, false)

    fun setAccentAuto(auto: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_ACCENT_AUTO, auto).apply()

    fun clockShowSeconds(): Boolean = sharedPreferences.getBoolean(KEY_CLOCK_SECONDS, true)

    fun setClockShowSeconds(show: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_CLOCK_SECONDS, show).apply()

    fun clockShowDate(): Boolean = sharedPreferences.getBoolean(KEY_CLOCK_DATE, false)

    fun setClockShowDate(show: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_CLOCK_DATE, show).apply()

    fun showGreeting(): Boolean = sharedPreferences.getBoolean(KEY_GREETING, true)

    fun setShowGreeting(show: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_GREETING, show).apply()

    fun background(): BackgroundStyle =
        sharedPreferences.getString(KEY_BACKGROUND, null)
            ?.let { runCatching { BackgroundStyle.valueOf(it) }.getOrNull() }
            ?: BackgroundStyle.AMBIENT

    fun setBackground(style: BackgroundStyle) =
        sharedPreferences.edit().putString(KEY_BACKGROUND, style.name).apply()

    /** The content URI of the user's chosen wallpaper, or null if none is set. */
    fun backgroundImageUri(): String? = sharedPreferences.getString(KEY_BG_IMAGE, null)

    /** The content URI of the user's chosen video background, or null if none is set. */
    fun backgroundVideoUri(): String? = sharedPreferences.getString(KEY_BG_VIDEO, null)

    fun setBackgroundVideoUri(uri: String?) =
        sharedPreferences.edit().apply {
            if (uri == null) remove(KEY_BG_VIDEO) else putString(KEY_BG_VIDEO, uri)
        }.apply()

    fun setBackgroundImageUri(uri: String?) =
        sharedPreferences.edit().apply {
            if (uri == null) remove(KEY_BG_IMAGE) else putString(KEY_BG_IMAGE, uri)
        }.apply()

    fun wallpaperFit(): WallpaperFit =
        sharedPreferences.getString(KEY_BG_FIT, null)
            ?.let { runCatching { WallpaperFit.valueOf(it) }.getOrNull() }
            ?: WallpaperFit.FILL

    fun setWallpaperFit(fit: WallpaperFit) =
        sharedPreferences.edit().putString(KEY_BG_FIT, fit.name).apply()

    /** How strongly the background colour/image shows, 0–100. */
    fun backgroundIntensity(): Int =
        sharedPreferences.getInt(KEY_BG_INTENSITY, BG_INTENSITY_DEFAULT)
            .coerceIn(BG_INTENSITY_MIN, BG_INTENSITY_MAX)

    fun setBackgroundIntensity(value: Int) =
        sharedPreferences.edit()
            .putInt(KEY_BG_INTENSITY, value.coerceIn(BG_INTENSITY_MIN, BG_INTENSITY_MAX))
            .apply()

    fun cardSize(): CardSize =
        sharedPreferences.getString(KEY_CARD_SIZE, null)
            ?.let { runCatching { CardSize.valueOf(it) }.getOrNull() }
            ?: CardSize.MEDIUM

    fun setCardSize(size: CardSize) =
        sharedPreferences.edit().putString(KEY_CARD_SIZE, size.name).apply()

    /** Whether the hero shows the "opened N times" line under a focused app. */
    fun showUsageCount(): Boolean = sharedPreferences.getBoolean(KEY_USAGE_COUNT, true)

    fun setShowUsageCount(show: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_USAGE_COUNT, show).apply()

    /** Whether every card carries its app name, not just the banner-less fallback ones. */
    fun showAppLabels(): Boolean = sharedPreferences.getBoolean(KEY_APP_LABELS, false)

    fun setShowAppLabels(show: Boolean) =
        sharedPreferences.edit().putBoolean(KEY_APP_LABELS, show).apply()

    /** The user's tab order, as tab keys (see HomeTabs); empty means the default order. */
    fun tabOrder(): List<String> =
        sharedPreferences.getString(KEY_TAB_ORDER, null)
            ?.split(TAB_SEPARATOR)?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setTabOrder(keys: List<String>) =
        sharedPreferences.edit().apply {
            if (keys.isEmpty()) remove(KEY_TAB_ORDER)
            else putString(KEY_TAB_ORDER, keys.joinToString(TAB_SEPARATOR))
        }.apply()

    /** Keys of the tabs the user has hidden from the bar. */
    fun hiddenTabs(): Set<String> =
        sharedPreferences.getStringSet(KEY_TABS_HIDDEN, emptySet())?.toSet() ?: emptySet()

    fun setHiddenTabs(keys: Set<String>) =
        sharedPreferences.edit().putStringSet(KEY_TABS_HIDDEN, keys).apply()

    /** Package names pinned to the hub's favourites row, in the user's order. */
    fun pinnedApps(): List<String> =
        sharedPreferences.getString(KEY_PINNED_APPS, null)
            ?.split(TAB_SEPARATOR)?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setPinnedApps(ids: List<String>) =
        sharedPreferences.edit().apply {
            if (ids.isEmpty()) remove(KEY_PINNED_APPS)
            else putString(KEY_PINNED_APPS, ids.joinToString(TAB_SEPARATOR))
        }.apply()

    fun appSort(): AppSort =
        sharedPreferences.getString(KEY_APP_SORT, null)
            ?.let { runCatching { AppSort.valueOf(it) }.getOrNull() }
            ?: AppSort.USAGE

    fun setAppSort(sort: AppSort) =
        sharedPreferences.edit().putString(KEY_APP_SORT, sort.name).apply()

    /** The manual order of the apps (package names), used by [AppSort.MANUAL]. */
    fun appOrder(): List<String> =
        sharedPreferences.getString(KEY_APP_ORDER, null)
            ?.split(TAB_SEPARATOR)?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setAppOrder(ids: List<String>) =
        sharedPreferences.edit().apply {
            if (ids.isEmpty()) remove(KEY_APP_ORDER)
            else putString(KEY_APP_ORDER, ids.joinToString(TAB_SEPARATOR))
        }.apply()

    /** Minutes without input before sleep mode shows; 0 = never. */
    fun ambientMinutes(): Int = sharedPreferences.getInt(KEY_AMBIENT_MINUTES, AMBIENT_DEFAULT).coerceAtLeast(0)

    fun setAmbientMinutes(minutes: Int) =
        sharedPreferences.edit().putInt(KEY_AMBIENT_MINUTES, minutes.coerceAtLeast(0)).apply()

    /** The key of the tab the home screen opens on (and returns to on HOME); null = the hub. */
    fun startupTab(): String? = sharedPreferences.getString(KEY_STARTUP_TAB, null)

    fun setStartupTab(key: String?) =
        sharedPreferences.edit().apply {
            if (key == null) remove(KEY_STARTUP_TAB) else putString(KEY_STARTUP_TAB, key)
        }.apply()
}
