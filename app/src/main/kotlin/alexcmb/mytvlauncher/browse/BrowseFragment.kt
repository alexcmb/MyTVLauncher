package alexcmb.mytvlauncher.browse

import alexcmb.mytvlauncher.LauncherActivity
import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.update.UpdateManager
import alexcmb.mytvlauncher.widget.WidgetSize
import alexcmb.mytvlauncher.widget.WidgetsPageFragment
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import androidx.leanback.widget.SearchOrbView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The browse shell: one page per section in Leanback's left nav, plus the clock and the
 * settings orb it draws in its own title bar.
 */
class BrowseFragment : BrowseSupportFragment() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateManager by lazy { UpdateManager(requireContext().applicationContext) }
    private lateinit var viewModel: BrowseViewModel

    private val timeFormat: DateFormat by lazy {
        // Locale-aware, honours the system 12/24h setting, and always includes seconds.
        val skeleton =
            if (android.text.format.DateFormat.is24HourFormat(requireContext())) "Hms" else "hms"
        val pattern =
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    /** Ticks the clock every second, re-aligning on the whole second so it doesn't drift. */
    private val tickRunnable = object : Runnable {
        override fun run() {
            title = timeFormat.format(Date())
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headersState = HEADERS_ENABLED
        // Left at the default (true): it is what lets Back walk out of a page.
        // Activity-scoped: the apps page drives it, this shell reads it for the menu.
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[BrowseViewModel::class.java]
        mainFragmentRegistry.registerFragment(PageRow::class.java, PageFragmentFactory())
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(PageRow(HeaderItem(PAGE_APPS, getString(R.string.title_apps))))
            add(PageRow(HeaderItem(PAGE_WIDGETS, getString(R.string.title_widgets))))
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

    override fun onStart() {
        super.onStart()
        handler.post(tickRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
    }

    private fun showGlobalMenu() {
        val widgetSlot = (requireActivity() as LauncherActivity).widgetSlot
        val dialog = MenuDialog(requireContext())
            .setTitle(getString(R.string.action_settings))
            .addItem(getString(R.string.action_android_settings)) {
                startAppIntent(Intent(Settings.ACTION_SETTINGS))
            }
            .addItem(getString(R.string.action_check_updates)) { checkForUpdates() }
            .addItem(getString(R.string.widget_add)) { showWidgetPicker() }
        if (widgetSlot.hasWidgets()) {
            dialog.addItem(getString(R.string.widget_resize)) {
                pickHostedWidget(R.string.widget_resize, ::showSizePicker)
            }
            dialog.addItem(getString(R.string.widget_remove)) {
                pickHostedWidget(R.string.widget_remove, widgetSlot::remove)
            }
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

    /** Asks which hosted widget to act on, skipping the question when there's only one. */
    private fun pickHostedWidget(@StringRes title: Int, action: (Int) -> Unit) {
        val hosted = (requireActivity() as LauncherActivity).widgetSlot.hostedWidgets()
        when (hosted.size) {
            0 -> return
            1 -> action(hosted.first().first)
            else -> {
                val dialog = MenuDialog(requireContext()).setTitle(getString(title))
                hosted.forEach { (id, label) -> dialog.addItem(label) { action(id) } }
                dialog.show()
            }
        }
    }

    private fun showSizePicker(id: Int) {
        val widgetSlot = (requireActivity() as LauncherActivity).widgetSlot
        MenuDialog(requireContext())
            .setTitle(getString(R.string.widget_resize))
            .addItem(getString(R.string.widget_size_small)) {
                widgetSlot.resize(id, WidgetSize.SMALL)
            }
            .addItem(getString(R.string.widget_size_medium)) {
                widgetSlot.resize(id, WidgetSize.MEDIUM)
            }
            .addItem(getString(R.string.widget_size_large)) {
                widgetSlot.resize(id, WidgetSize.LARGE)
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

    private class PageFragmentFactory : FragmentFactory<Fragment>() {
        override fun createFragment(row: Any): Fragment = when ((row as PageRow).headerItem.id) {
            PAGE_APPS -> AppsPageFragment()
            PAGE_WIDGETS -> WidgetsPageFragment()
            else -> throw IllegalArgumentException("Unknown page row $row")
        }
    }

    companion object {
        private const val TAG = "BrowseFragment"
        private const val PAGE_APPS = 0L
        private const val PAGE_WIDGETS = 1L
    }
}
