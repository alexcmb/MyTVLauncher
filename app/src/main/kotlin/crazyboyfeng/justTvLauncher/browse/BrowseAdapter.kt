package crazyboyfeng.justTvLauncher.browse

import android.util.Log
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import crazyboyfeng.justTvLauncher.model.HiddenAppsAction
import crazyboyfeng.justTvLauncher.model.Shortcut
import crazyboyfeng.justTvLauncher.model.ShortcutGroup
import crazyboyfeng.justTvLauncher.model.UpdateAction

class BrowseAdapter(
    shortcutGroupList: List<ShortcutGroup>,
    actionsRowHeader: String,
    updateActionTitle: String,
    hiddenAppsActionTitle: String?,
    onShortcutLongClick: (Shortcut) -> Boolean,
) : ArrayObjectAdapter(ListRowPresenter()) {
    init {
        addShortcutGroupList(shortcutGroupList, onShortcutLongClick)
        addActionsRow(actionsRowHeader, updateActionTitle, hiddenAppsActionTitle)
    }

    private fun addShortcutGroupList(
        shortcutGroupList: List<ShortcutGroup>,
        onShortcutLongClick: (Shortcut) -> Boolean,
    ) {
        val cardPresenter = ShortcutCardPresenter(onShortcutLongClick)
        shortcutGroupList.forEach {
            Log.v(TAG, "${it.category}: ${it.openCount}")
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            listRowAdapter.addAll(0, it.shortcutList)
            val headerItem = HeaderItem(it.category)
            add(ListRow(headerItem, listRowAdapter))
        }
    }

    private fun addActionsRow(header: String, updateTitle: String, hiddenAppsTitle: String?) {
        val rowAdapter = ArrayObjectAdapter(ActionCardPresenter())
        rowAdapter.add(UpdateAction(updateTitle))
        if (hiddenAppsTitle != null) {
            rowAdapter.add(HiddenAppsAction(hiddenAppsTitle))
        }
        add(ListRow(HeaderItem(header), rowAdapter))
    }

    companion object {
        private const val TAG = "BrowseAdapter"
    }
}
