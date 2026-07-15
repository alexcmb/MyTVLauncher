package alexcmb.mytvlauncher.repository

import android.content.Context
import alexcmb.mytvlauncher.util.SingletonHolder

/** Persists the set of package ids the user has hidden from the launcher. */
class HiddenAppsRepository private constructor(context: Context) {
    companion object : SingletonHolder<HiddenAppsRepository, Context>(::HiddenAppsRepository) {
        private const val KEY = "packages"
    }

    private val sharedPreferences = context.getSharedPreferences("hidden", 0)

    fun query(): Set<String> = sharedPreferences.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(id: String) = update { it.add(id) }

    fun remove(id: String) = update { it.remove(id) }

    private inline fun update(action: (MutableSet<String>) -> Unit) {
        val set = query().toMutableSet()
        action(set)
        // A new set instance must be stored; SharedPreferences must not keep the returned one.
        sharedPreferences.edit().putStringSet(KEY, set).apply()
    }
}
