package alexcmb.mytvlauncher.browse

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.model.Shortcut
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.ViewModelProvider

/** The apps page of the browse: the usage-sorted rows and their context menu. */
class AppsPageFragment : RowsSupportFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var viewModel: BrowseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Activity-scoped: the browse shell reads the same state for its settings menu.
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[BrowseViewModel::class.java]
        viewModel.browseContent.observe(this) {
            adapter = BrowseAdapter(it!!) { shortcut -> showContextMenu(shortcut); true }
            // Re-focus the just-opened shortcut at its new, re-sorted position.
            viewModel.consumePendingSelection()?.let(::setSelect)
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Shortcut) {
                launch(item.id)
                viewModel.incrementOpenCount(item)
            }
        }
    }

    private fun launch(packageName: String) {
        val packageManager = requireContext().packageManager
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Log.w(TAG, "No launch intent for $packageName")
        }
    }

    private fun showContextMenu(shortcut: Shortcut) {
        MenuDialog(requireContext())
            .setTitle(shortcut.title)
            .addItem(getString(R.string.menu_change_category)) { showCategoryPicker(shortcut) }
            .addItem(getString(R.string.menu_hide)) { viewModel.hideApp(shortcut) }
            .addItem(getString(R.string.menu_uninstall)) {
                startAppIntent(Intent(Intent.ACTION_DELETE, packageUri(shortcut)))
            }
            .addItem(getString(R.string.menu_app_info)) {
                startAppIntent(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(shortcut))
                )
            }
            .show()
    }

    private fun showCategoryPicker(shortcut: Shortcut) {
        val categories = CategoryOptions.candidates(
            current = shortcut.category,
            existing = viewModel.availableCategories(),
            defaults = listOf(getString(R.string.title_apps), getString(R.string.title_system)),
            presets = resources.getStringArray(R.array.category_presets).toList(),
        )
        val dialog = MenuDialog(requireContext())
            .setTitle(getString(R.string.menu_change_category))
        categories.forEach { category ->
            dialog.addItem(category) { viewModel.setCategory(shortcut, category) }
        }
        dialog.addItem(getString(R.string.category_new)) { promptNewCategory(shortcut) }
        dialog.show()
    }

    private fun promptNewCategory(shortcut: Shortcut) {
        TextInputDialog(requireContext())
            .setTitle(getString(R.string.category_new_title))
            .onSubmit { name -> viewModel.setCategory(shortcut, name) }
            .show()
    }

    private fun packageUri(shortcut: Shortcut): Uri = Uri.fromParts("package", shortcut.id, null)

    private fun startAppIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent", e)
            toast(R.string.action_open_failed)
        }
    }

    private fun toast(@StringRes resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private fun setSelect(shortcut: Shortcut) = handler.post {
        val position = viewModel.findPosition(shortcut)
        val task = ListRowPresenter.SelectItemViewHolderTask(position.second)
        task.isSmoothScroll = false
        setSelectedPosition(position.first, false, task)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireContext(), packageChangedReceiver, packageChangedReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        requireContext().unregisterReceiver(packageChangedReceiver)
    }

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (SCHEME_PACKAGE != intent.scheme) {
                return
            }
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return
            }
            if (Intent.ACTION_PACKAGE_REMOVED == intent.action) {
                val packageName = intent.data!!.schemeSpecificPart
                viewModel.removePackage(packageName)
            } else {
                viewModel.loadShortcutGroupList()
            }
        }

        fun getIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
            intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
            intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED)
            intentFilter.addDataScheme(SCHEME_PACKAGE)
            return intentFilter
        }
    }

    companion object {
        private const val TAG = "AppsPageFragment"
        private const val SCHEME_PACKAGE = "package"
    }
}
