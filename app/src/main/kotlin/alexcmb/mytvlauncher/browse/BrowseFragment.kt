package alexcmb.mytvlauncher.browse

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
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import alexcmb.mytvlauncher.LauncherActivity
import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.update.UpdateManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class BrowseFragment : BrowseSupportFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat: DateFormat by lazy {
        // Locale-aware, honours the system 12/24h setting, and always includes seconds.
        val skeleton =
            if (android.text.format.DateFormat.is24HourFormat(requireContext())) "Hms" else "hms"
        val pattern =
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    private val updateManager by lazy { UpdateManager(requireContext().applicationContext) }
    private lateinit var viewModel: BrowseViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headersState = HEADERS_DISABLED
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(BrowseViewModel::class.java)
        viewModel.browseContent.observe(this) {
            adapter = BrowseAdapter(
                it!!,
                onShortcutLongClick = { shortcut -> showContextMenu(shortcut); true }
            )
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Repurpose the Leanback title orb as the settings button.
        setOnSearchClickedListener { showGlobalMenu() }
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.menu_accent)
        (titleViewAdapter?.searchAffordanceView as? SearchOrbView)?.apply {
            setOrbIcon(ContextCompat.getDrawable(context, R.drawable.ic_settings))
            contentDescription = getString(R.string.action_settings)
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

    /** Ticks the clock every second, re-aligning on the whole second so it doesn't drift. */
    private val tickRunnable = object : Runnable {
        override fun run() {
            title = timeFormat.format(Date())
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
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

    private fun showGlobalMenu() {
        val dialog = MenuDialog(requireContext())
            .setTitle(getString(R.string.action_settings))
            .addItem(getString(R.string.action_android_settings)) {
                startAppIntent(Intent(Settings.ACTION_SETTINGS))
            }
            .addItem(getString(R.string.action_check_updates)) { checkForUpdates() }
        val widgetSlot = (requireActivity() as LauncherActivity).widgetSlot
        if (widgetSlot.hasWidget()) {
            dialog.addItem(getString(R.string.widget_remove)) { widgetSlot.remove() }
        } else {
            dialog.addItem(getString(R.string.widget_add)) { showWidgetPicker() }
        }
        val hiddenCount = viewModel.hiddenApps.size
        if (hiddenCount > 0) {
            dialog.addItem(getString(R.string.action_hidden_apps, hiddenCount)) {
                showHiddenAppsDialog()
            }
        }
        dialog.show()
    }

    /** Android TV ships no widget picker, so build one from the installed providers. */
    private fun showWidgetPicker() {
        val widgetSlot = (requireActivity() as LauncherActivity).widgetSlot
        val providers = widgetSlot.availableProviders()
        if (providers.isEmpty()) {
            toast(R.string.widget_none_available)
            return
        }
        val packageManager = requireContext().packageManager
        val dialog = MenuDialog(requireContext()).setTitle(getString(R.string.widget_add))
        providers.forEach { provider ->
            dialog.addItem(provider.loadLabel(packageManager)) { widgetSlot.add(provider) }
        }
        dialog.show()
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
        handler.post(tickRunnable)
        ContextCompat.registerReceiver(
            requireContext(), packageChangedReceiver, packageChangedReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
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
        private const val TAG = "BrowseFragment"
        private const val SCHEME_PACKAGE = "package"
    }
}