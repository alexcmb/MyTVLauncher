package alexcmb.mytvlauncher.repository

import android.content.Context
import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.util.SingletonHolder

class CategoryRepository private constructor(private val context: Context) {
    companion object : SingletonHolder<CategoryRepository, Context>(::CategoryRepository)

    private val sharedPreferences = context.getSharedPreferences("category", 0)
    private val editor = sharedPreferences.edit()
    /** Where an app goes until it's given a category: System for a system app, else Apps. */
    fun default(system: Boolean): String =
        context.getString(if (system) R.string.title_system else R.string.title_apps)

    fun query(id: String): String? {
        return sharedPreferences.getString(id, null)
    }

    fun update(id: String, category: String) {
        editor.putString(id, category).apply()
    }

    fun delete(id: String) {
        editor.remove(id).apply()
    }
}