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

/** What fills the home screen behind everything. */
enum class BackgroundStyle {
    /** Plain dark. */
    SOLID,

    /** The focused app's banner, dimmed. */
    AMBIENT,

    /** A dark gradient tinted with the accent colour. */
    ACCENT_GRADIENT,
}

/** User appearance preferences. */
class SettingsRepository private constructor(context: Context) {
    companion object : SingletonHolder<SettingsRepository, Context>(::SettingsRepository) {
        private const val KEY_ACCENT = "accent"
        private const val KEY_CLOCK_SECONDS = "clock_seconds"
        private const val KEY_CLOCK_DATE = "clock_date"
        private const val KEY_GREETING = "greeting"
        private const val KEY_BACKGROUND = "background"
    }

    private val sharedPreferences = context.getSharedPreferences("settings", 0)

    fun accent(): AccentColor =
        sharedPreferences.getString(KEY_ACCENT, null)
            ?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.INDIGO

    fun setAccent(accent: AccentColor) =
        sharedPreferences.edit().putString(KEY_ACCENT, accent.name).apply()

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
}
