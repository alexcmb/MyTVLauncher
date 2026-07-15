package alexcmb.mytvlauncher.browse

import android.util.Log
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup

class BrowseAdapter(
    shortcutGroupList: List<ShortcutGroup>,
    onShortcutLongClick: (Shortcut) -> Boolean,
) : ArrayObjectAdapter(ListRowPresenter()) {
    init {
        addShortcutGroupList(shortcutGroupList, onShortcutLongClick)
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

    companion object {
        private const val TAG = "BrowseAdapter"
    }
}
