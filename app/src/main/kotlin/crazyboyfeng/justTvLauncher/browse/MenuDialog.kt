package crazyboyfeng.justTvLauncher.browse

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import crazyboyfeng.justTvLauncher.R

/** A dark, TV-styled menu: a titled panel with a vertical list of focusable actions. */
class MenuDialog(context: Context) {
    private val dialog = Dialog(context)
    private val inflater = LayoutInflater.from(context)
    private val view = inflater.inflate(R.layout.dialog_menu, null)
    private val titleView: TextView = view.findViewById(R.id.menu_title)
    private val itemsContainer: LinearLayout = view.findViewById(R.id.menu_items)
    private var firstItem: View? = null

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    fun setTitle(title: CharSequence): MenuDialog {
        titleView.text = title
        return this
    }

    fun addItem(label: CharSequence, onClick: () -> Unit): MenuDialog {
        val item = inflater.inflate(R.layout.item_menu, itemsContainer, false) as TextView
        item.text = label
        item.setOnClickListener {
            dialog.dismiss()
            onClick()
        }
        itemsContainer.addView(item)
        if (firstItem == null) firstItem = item
        return this
    }

    fun show() {
        dialog.show()
        firstItem?.requestFocus()
    }
}
