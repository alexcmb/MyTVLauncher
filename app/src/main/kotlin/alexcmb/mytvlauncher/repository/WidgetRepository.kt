package alexcmb.mytvlauncher.repository

import alexcmb.mytvlauncher.util.SingletonHolder
import android.content.Context

/** Remembers which app widget the launcher hosts, if any. */
class WidgetRepository private constructor(context: Context) {
    companion object : SingletonHolder<WidgetRepository, Context>(::WidgetRepository) {
        private const val KEY_ID = "id"
        const val NO_WIDGET = -1
    }

    private val sharedPreferences = context.getSharedPreferences("widget", 0)

    fun queryId(): Int = sharedPreferences.getInt(KEY_ID, NO_WIDGET)

    fun updateId(id: Int) = sharedPreferences.edit().putInt(KEY_ID, id).apply()

    fun deleteId() = sharedPreferences.edit().remove(KEY_ID).apply()
}
