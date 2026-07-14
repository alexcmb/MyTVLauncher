package crazyboyfeng.justTvLauncher.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import crazyboyfeng.justTvLauncher.R
import crazyboyfeng.justTvLauncher.databinding.PresenterShortcutCardBinding
import crazyboyfeng.justTvLauncher.model.UpdateAction

/** Presents a simple text card for an [UpdateAction], reusing the shortcut card layout. */
class ActionCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.presenter_shortcut_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as UpdateAction
        val binding = PresenterShortcutCardBinding.bind(viewHolder.view)
        binding.content.text = action.title
        binding.root.contentDescription = action.title
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val binding = PresenterShortcutCardBinding.bind(viewHolder.view)
        binding.content.text = null
    }
}
