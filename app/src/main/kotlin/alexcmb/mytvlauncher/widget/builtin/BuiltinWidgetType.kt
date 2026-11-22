package alexcmb.mytvlauncher.widget.builtin

import alexcmb.mytvlauncher.R
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Predesigned, TV-optimized native widgets that render directly in Compose without
 * requiring ADB permissions or AppWidgetHostView.
 */
enum class BuiltinWidgetType(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @DrawableRes val iconRes: Int,
) {
    WEATHER(
        id = -101,
        titleRes = R.string.widget_builtin_weather_title,
        descRes = R.string.widget_builtin_weather_desc,
        iconRes = R.drawable.ic_weather_sun,
    ),
    SYSTEM(
        id = -102,
        titleRes = R.string.widget_builtin_system_title,
        descRes = R.string.widget_builtin_system_desc,
        iconRes = R.drawable.ic_system_stats,
    ),
    SOLAR(
        id = -103,
        titleRes = R.string.widget_builtin_solar_title,
        descRes = R.string.widget_builtin_solar_desc,
        iconRes = R.drawable.ic_day_night,
    );

    companion object {
        fun fromId(id: Int): BuiltinWidgetType? = entries.firstOrNull { it.id == id }
        fun isBuiltin(id: Int): Boolean = fromId(id) != null
    }
}
