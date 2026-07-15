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

    // Apps hidden from the grid, kept aside so they can be restored.
    var hiddenApps: List<Shortcut> = emptyList()
        private set

    init {
        loadShortcutGroupList()
    }

    /**
     * Full (re)load: queries the PackageManager off the main thread, splits out the
     * hidden apps, then publishes the grouped/sorted visible ones. Any in-flight load
     * is cancelled first.
     */
    fun loadShortcutGroupList() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val all = shortcutRepository.load()
            val hiddenIds = withContext(Dispatchers.IO) { shortcutRepository.hiddenIds() }
            val (hidden, visible) = all.partition { it.id in hiddenIds }
            hiddenApps = hidden.sortedBy { it.title }
            browseContent.value = groupAndSort(visible)
        }
    }

    fun hideApp(shortcut: Shortcut) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.hide(shortcut.id) }
            loadShortcutGroupList()
        }
    }

    fun showApp(shortcut: Shortcut) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.unhide(shortcut.id) }
            loadShortcutGroupList()
        }
    }

    private fun groupAndSort(shortcuts: Collection<Shortcut>): List<ShortcutGroup> =
        ShortcutGrouping.groupAndSort(shortcuts)

    fun incrementOpenCount(shortcut: Shortcut) {
        Log.v(TAG, "${shortcut.id}: ${shortcut.openCount} + 1")
        shortcut.openCount++
        pendingSelection = shortcut
        // Re-sort from the already-loaded shortcuts; no PackageManager re-scan.
        val shortcuts = browseContent.value?.flatMap { it.shortcutList } ?: listOf(shortcut)
        browseContent.value = groupAndSort(shortcuts)
        // Persist the new count off the main thread.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.updateOpenCount(shortcut) }
        }
    }

    fun consumePendingSelection(): Shortcut? = pendingSelection?.also { pendingSelection = null }

    fun availableCategories(): List<String> =
        browseContent.value?.map { it.category }?.distinct() ?: emptyList()

    fun setCategory(shortcut: Shortcut, category: String) {
        shortcut.category = category
        viewModelScope.launch {
            withContext(Dispatchers.IO) { shortcutRepository.updateCategory(shortcut.id, category) }
            loadShortcutGroupList()
        }
    }

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
