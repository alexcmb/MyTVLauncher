package crazyboyfeng.justTvLauncher.browse

import android.app.AlertDialog
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
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import crazyboyfeng.justTvLauncher.R
import crazyboyfeng.justTvLauncher.model.HiddenAppsAction
import crazyboyfeng.justTvLauncher.model.Shortcut
import crazyboyfeng.justTvLauncher.model.UpdateAction
import crazyboyfeng.justTvLauncher.update.UpdateManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*


class BrowseFragment : BrowseSupportFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = DateFormat.getTimeInstance()
    private val updateManager by lazy { UpdateManager(requireContext().applicationContext) }
    private lateinit var viewModel: BrowseViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headersState = HEADERS_DISABLED
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(BrowseViewModel::class.java)
        viewModel.browseContent.observe(this) {
            val hiddenCount = viewModel.hiddenApps.size
            adapter = BrowseAdapter(
                it!!,
                getString(R.string.app_name),
                getString(R.string.action_check_updates),
                if (hiddenCount > 0) getString(R.string.action_hidden_apps, hiddenCount) else null,
                onShortcutLongClick = { shortcut -> showContextMenu(shortcut); true }
            )
            // Re-focus the just-opened shortcut at its new, re-sorted position.
            viewModel.consumePendingSelection()?.let(::setSelect)
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Shortcut -> {
                    launch(item.id)
                    viewModel.incrementOpenCount(item)
                }
                is UpdateAction -> checkForUpdates()
                is HiddenAppsAction -> showHiddenAppsDialog()
            }
        }
    }

    private fun checkForUpdates() {
        toast(R.string.update_checking)
        viewLifecycleOwner.lifecycleScope.launch {
            val release = updateManager.fetchLatestRelease()
            when {
                release == null -> toast(R.string.update_check_failed)
                release.versionCode <= updateManager.currentVersionCode() ->
                    toast(R.string.update_up_to_date)
                else -> promptInstall(release)
            }
        }
    }

    private fun promptInstall(release: UpdateManager.Release) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.update_available_title, release.versionName))
            .setMessage(R.string.update_available_message)
            .setPositiveButton(R.string.update_install) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(release: UpdateManager.Release) {
        toast(R.string.update_downloading)
        viewLifecycleOwner.lifecycleScope.launch {
            val file = try {
                updateManager.download(release)
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                null
            }
            if (file == null) {
                toast(R.string.update_download_failed)
            } else {
                updateManager.install(file)
            }
        }
    }

    private fun toast(@StringRes resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private fun setTick() = handler.post {
        title = dateFormat.format(Date())
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

    private fun showHiddenAppsDialog() {
        val hidden = viewModel.hiddenApps
        if (hidden.isEmpty()) {
            toast(R.string.no_hidden_apps)
            return
        }
        val dialog = MenuDialog(requireContext())
            .setTitle(getString(R.string.action_hidden_apps_title))
        hidden.forEach { app -> dialog.addItem(app.title) { viewModel.showApp(app) } }
        dialog.show()
    }

    private fun showCategoryPicker(shortcut: Shortcut) {
        val categories = (viewModel.availableCategories() +
                getString(R.string.title_apps) + getString(R.string.title_system))
            .distinct()
            .filter { it != shortcut.category }
        if (categories.isEmpty()) return
        val dialog = MenuDialog(requireContext())
            .setTitle(getString(R.string.menu_change_category))
        categories.forEach { category ->
            dialog.addItem(category) { viewModel.setCategory(shortcut, category) }
        }
        dialog.show()
    }

    private fun packageUri(shortcut: Shortcut): Uri =
        Uri.fromParts("package", shortcut.id, null)

    private fun startAppIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent", e)
            toast(R.string.action_open_failed)
        }
    }


    private fun setSelect(shortcut: Shortcut) = handler.post {
        val position = viewModel.findPosition(shortcut)
        val task = ListRowPresenter.SelectItemViewHolderTask(position.second)
        task.isSmoothScroll = false
        rowsSupportFragment.setSelectedPosition(position.first, false, task)
    }

    override fun onStart() {
        super.onStart()
        val context = requireContext()
        ContextCompat.registerReceiver(
            context, timeTickReceiver, timeTickReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            context, packageChangedReceiver, packageChangedReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        val context = requireContext()
        context.unregisterReceiver(timeTickReceiver)
        context.unregisterReceiver(packageChangedReceiver)
    }

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (Intent.ACTION_TIME_TICK == intent.action) {
                setTick()
            }
        }

        fun getIntentFilter(): IntentFilter {
            return IntentFilter(Intent.ACTION_TIME_TICK)
        }
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
        private const val TAG = "BrowseFragment"
        private const val SCHEME_PACKAGE = "package"
    }
}