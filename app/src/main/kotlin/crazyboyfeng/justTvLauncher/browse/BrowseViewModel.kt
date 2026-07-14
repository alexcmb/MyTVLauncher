package crazyboyfeng.justTvLauncher.browse

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import crazyboyfeng.justTvLauncher.model.Shortcut
import crazyboyfeng.justTvLauncher.model.ShortcutGroup
import crazyboyfeng.justTvLauncher.repository.ShortcutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The lifecycle of this view-model is consistent with the application.
 * Since `stateNotNeeded` is set, the caching of `ViewModel` doesn't work.
 * I wrote it this way just for logical separation.
 */
class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val shortcutRepository = ShortcutRepository(application)
    val browseContent = MutableLiveData<List<ShortcutGroup>>()
    private var loadJob: Job? = null

    // The shortcut just opened, to be re-focused once the re-sorted list is published.
    private var pendingSelection: Shortcut? = null

    init {
        loadShortcutGroupList()
    }

    /**
     * Full (re)load: queries the PackageManager off the main thread, then publishes
     * the grouped/sorted result. Any in-flight load is cancelled first.
     */
    fun loadShortcutGroupList() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val groups = groupAndSort(shortcutRepository.load())
            browseContent.value = groups
        }
    }

    private fun groupAndSort(shortcuts: Collection<Shortcut>): List<ShortcutGroup> {
        val shortcutGroupByCategory = HashMap<String, ShortcutGroup>()
        shortcuts.forEach {
            val category = it.category
            if (shortcutGroupByCategory.containsKey(category)) {
                shortcutGroupByCategory[category]!!.add(it)
            } else {
                shortcutGroupByCategory[category] = ShortcutGroup(category, mutableListOf(it))
            }
        }
        return shortcutGroupByCategory.values.sortedByDescending { it.openCount }
    }

    fun incrementOpenCount(shortcut: Shortcut) {
        Log.v(TAG, "${shortcut.id}: ${shortcut.openCount} + 1")
        shortcut.openCount++
        pendingSelection = shortcut
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.updateOpenCount(shortcut) }
            loadShortcutGroupList()
        }
    }

    fun consumePendingSelection(): Shortcut? = pendingSelection?.also { pendingSelection = null }

    fun findPosition(shortcut: Shortcut): Pair<Int, Int> {
        val shortcutGroupList = browseContent.value!!
        val x = shortcutGroupList.indexOfFirst { it.category == shortcut.category }
        val y = shortcutGroupList[x].shortcutList.indexOf(shortcut)
        Log.v(TAG, "${shortcut.id}: ($x, $y)")
        return Pair(x, y)
    }

    fun removePackage(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.deleteById(packageName) }
            loadShortcutGroupList()
        }
    }

    companion object {
        private const val TAG = "BrowseViewModel"
    }
}
