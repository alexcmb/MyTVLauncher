package crazyboyfeng.justTvLauncher.browse

import android.util.Log
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import crazyboyfeng.justTvLauncher.model.ShortcutGroup
import crazyboyfeng.justTvLauncher.model.UpdateAction

class BrowseAdapter(
    shortcutGroupList: List<ShortcutGroup>,
    updateRowHeader: String,
    updateActionTitle: String,
) : ArrayObjectAdapter(ListRowPresenter()) {
    init {
        addShortcutGroupList(shortcutGroupList)
        addUpdateRow(updateRowHeader, updateActionTitle)
    }

    private fun addShortcutGroupList(shortcutGroupList: List<ShortcutGroup>) {
        val cardPresenter = ShortcutCardPresenter()
        shortcutGroupList.forEach {
            Log.v(TAG, "${it.category}: ${it.openCount}")
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            listRowAdapter.addAll(0, it.shortcutList)
            val headerItem = HeaderItem(it.category)
            add(ListRow(headerItem, listRowAdapter))
        }
    }

    private fun addUpdateRow(header: String, actionTitle: String) {
        val rowAdapter = ArrayObjectAdapter(ActionCardPresenter())
        rowAdapter.add(UpdateAction(actionTitle))
        add(ListRow(HeaderItem(header), rowAdapter))
    }

    companion object {
        private const val TAG = "BrowseAdapter"
    }
}
