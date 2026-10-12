package alexcmb.mytvlauncher.browse

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import alexcmb.mytvlauncher.repository.ShortcutRepository
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

    /** Every app the launcher knows, hidden ones included (they keep a category too). */
    private fun allShortcuts(): List<Shortcut> =
        browseContent.value.orEmpty().flatMap { it.shortcutList } + hiddenApps

    /** How many apps, hidden ones included, are filed under [category]. */
    fun appCount(category: String): Int = allShortcuts().count { it.category == category }

    /** Moves every app of [from] to [to]; onto an existing category, the two merge. */
    fun renameCategory(from: String, to: String) {
        val moved = allShortcuts().filter { it.category == from }
        // Applied in memory at once, so the tabs (and the menu listing them) show the new name
        // in the same frame; the reload after saving then just confirms it.
        moved.forEach { it.category = to }
        browseContent.value = groupAndSort(browseContent.value.orEmpty().flatMap { it.shortcutList })
        val ids = moved.map { it.id }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { shortcutRepository.updateCategory(it, to) } }
            loadShortcutGroupList()
        }
    }

    /** Dissolves [category]: its apps go back to their default category. */
    fun deleteCategory(category: String) {
        val ids = allShortcuts().filter { it.category == category }.map { it.id }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ids.forEach { shortcutRepository.resetCategory(it) } }
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
