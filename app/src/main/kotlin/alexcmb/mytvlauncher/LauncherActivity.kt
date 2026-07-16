package alexcmb.mytvlauncher

import alexcmb.mytvlauncher.browse.BrowseViewModel
import alexcmb.mytvlauncher.browse.CategoryOptions
import alexcmb.mytvlauncher.compose.HomeScreen
import alexcmb.mytvlauncher.compose.HomeTabs
import alexcmb.mytvlauncher.compose.MenuItem
import alexcmb.mytvlauncher.compose.MenuSpec
import alexcmb.mytvlauncher.compose.TvMenu
import alexcmb.mytvlauncher.compose.TvTextPrompt
import alexcmb.mytvlauncher.compose.WidgetTile
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.update.UpdateManager
import alexcmb.mytvlauncher.widget.WidgetSize
import alexcmb.mytvlauncher.widget.WidgetSlotController
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The launcher home, in Compose for TV. Overlay state (the menus, the rename prompt) lives
 * on the activity rather than in the composition so the flows that drive it — an update
 * check, an intent that may not resolve — read as plain sequential code.
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var viewModel: BrowseViewModel
    private val updateManager by lazy { UpdateManager(applicationContext) }
    private val widgetSlot by lazy { WidgetSlotController(this) }

    private var menu by mutableStateOf<MenuSpec?>(null)
    private var namingCategoryFor by mutableStateOf<Shortcut?>(null)
    private var widgets by mutableStateOf<List<WidgetTile>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        widgetSlot.onChanged = { refreshWidgets() }
        // A launcher is the home screen: Back must not leave it. The menus register their
        // own back handlers that take priority while open, so this only fires at the root.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent {
            val groups by viewModel.browseContent.observeAsState(emptyList())
            val tabs = HomeTabs.from(groups, stringResource(R.string.title_home))
            Box {
                HomeScreen(
                    tabs = tabs,
                    widgets = widgets,
                    clock = rememberClock(),
                    onLaunch = ::launchShortcut,
                    onLongPress = ::showAppMenu,
                    onSettings = ::showSettingsMenu,
                )
                menu?.let { TvMenu(it) { menu = null } }
                namingCategoryFor?.let { shortcut ->
                    TvTextPrompt(
                        title = stringResource(R.string.category_new_title),
                        hint = stringResource(R.string.category_hint),
                        onSubmit = { viewModel.setCategory(shortcut, it) },
                        onDismiss = { namingCategoryFor = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetSlot.startListening()
        refreshWidgets()
    }

    override fun onStop() {
        super.onStop()
        widgetSlot.stopListening()
    }

    @Deprecated("The widget bind and configure flows are driven by the legacy result API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val granted = resultCode == RESULT_OK
        when (requestCode) {
            WidgetSlotController.REQUEST_BIND -> widgetSlot.onBindResult(granted)
            WidgetSlotController.REQUEST_CONFIGURE -> widgetSlot.onConfigureResult(granted)
            WidgetSlotController.REQUEST_PICK -> widgetSlot.onPickResult(
                granted, data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            )
        }
    }

    private fun refreshWidgets() {
        widgets = widgetSlot.hostedForDisplay().map { hosted ->
            WidgetTile(hosted.id, hosted.size.widthDp, hosted.size.heightDp) {
                widgetSlot.createHostView(hosted)
            }
        }
    }

    private fun showAppMenu(shortcut: Shortcut) {
        menu = MenuSpec(
            title = shortcut.title,
            items = listOf(
                MenuItem(getString(R.string.menu_change_category)) { showCategoryMenu(shortcut) },
                MenuItem(getString(R.string.menu_hide)) {
                    menu = null
                    viewModel.hideApp(shortcut)
                },
                MenuItem(getString(R.string.menu_uninstall)) {
                    menu = null
                    startAppIntent(Intent(Intent.ACTION_DELETE, packageUri(shortcut)))
                },
                MenuItem(getString(R.string.menu_app_info)) {
                    menu = null
                    startAppIntent(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(shortcut))
                    )
                },
            ),
        )
    }

    private fun showCategoryMenu(shortcut: Shortcut) {
        val categories = CategoryOptions.candidates(
            current = shortcut.category,
            existing = viewModel.availableCategories(),
            defaults = listOf(getString(R.string.title_apps), getString(R.string.title_system)),
            presets = resources.getStringArray(R.array.category_presets).toList(),
        )
        menu = MenuSpec(
            title = getString(R.string.menu_change_category),
            items = categories.map { category ->
                MenuItem(category) {
                    menu = null
                    viewModel.setCategory(shortcut, category)
                }
            } + MenuItem(getString(R.string.category_new)) {
                menu = null
                namingCategoryFor = shortcut
            },
        )
    }

    private fun showSettingsMenu() {
        val items = mutableListOf(
            MenuItem(getString(R.string.action_android_settings)) {
                menu = null
                startAppIntent(Intent(Settings.ACTION_SETTINGS))
            },
            MenuItem(getString(R.string.action_check_updates)) {
                menu = null
                checkForUpdates()
            },
            MenuItem(getString(R.string.widget_add)) { showWidgetPicker() },
        )
        if (widgetSlot.hasWidgets()) {
            items += MenuItem(getString(R.string.widget_resize)) {
                pickHostedWidget(R.string.widget_resize, ::showSizeMenu)
            }
            items += MenuItem(getString(R.string.widget_remove)) {
                pickHostedWidget(R.string.widget_remove) { menu = null; widgetSlot.remove(it) }
            }
        }
        val hidden = viewModel.hiddenApps
        if (hidden.isNotEmpty()) {
            items += MenuItem(getString(R.string.action_hidden_apps, hidden.size)) {
                showHiddenAppsMenu()
            }
        }
        menu = MenuSpec(getString(R.string.action_settings), items)
    }

    /** Android TV ships no widget picker, so build one from the installed providers. */
    private fun showWidgetPicker() {
        val providers = widgetSlot.availableProviders()
        if (providers.isEmpty()) {
            menu = null
            toast(R.string.widget_none_available)
            return
        }
        menu = MenuSpec(
            title = getString(R.string.widget_add),
            items = providers.map { provider ->
                MenuItem(provider.loadLabel(packageManager).toString()) {
                    menu = null
                    widgetSlot.add(provider)
                }
            },
        )
    }

    /** Picks which hosted widget to act on, skipping the question when there's only one. */
    private fun pickHostedWidget(@StringRes title: Int, action: (Int) -> Unit) {
        val hosted = widgetSlot.hostedWidgets()
        when (hosted.size) {
            0 -> menu = null
            1 -> action(hosted.first().first)
            else -> menu = MenuSpec(
                title = getString(title),
                items = hosted.map { (id, label) -> MenuItem(label.toString()) { action(id) } },
            )
        }
    }

    private fun showSizeMenu(id: Int) {
        menu = MenuSpec(
            title = getString(R.string.widget_resize),
            items = listOf(
                MenuItem(getString(R.string.widget_size_small)) {
                    menu = null; widgetSlot.resize(id, WidgetSize.SMALL)
                },
                MenuItem(getString(R.string.widget_size_medium)) {
                    menu = null; widgetSlot.resize(id, WidgetSize.MEDIUM)
                },
                MenuItem(getString(R.string.widget_size_large)) {
                    menu = null; widgetSlot.resize(id, WidgetSize.LARGE)
                },
            ),
        )
    }

    private fun showHiddenAppsMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_hidden_apps_title),
            items = viewModel.hiddenApps.map { app ->
                MenuItem(app.title) {
                    menu = null
                    viewModel.showApp(app)
                }
            },
        )
    }

    private fun checkForUpdates() {
        toast(R.string.update_checking)
        lifecycleScope.launch {
            val release = updateManager.fetchLatestRelease()
            when {
                release == null -> toast(R.string.update_check_failed)
                release.versionCode <= updateManager.currentVersionCode() ->
                    toast(R.string.update_up_to_date)
                else -> menu = MenuSpec(
                    title = getString(R.string.update_available_title, release.versionName),
                    items = listOf(
                        MenuItem(getString(R.string.update_install)) {
                            menu = null
                            downloadAndInstall(release)
                        },
                        MenuItem(getString(android.R.string.cancel)) { menu = null },
                    ),
                )
            }
        }
    }

    private fun downloadAndInstall(release: UpdateManager.Release) {
        toast(R.string.update_downloading)
        lifecycleScope.launch {
            val file = try {
                updateManager.download(release)
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                null
            }
            if (file == null) toast(R.string.update_download_failed) else updateManager.install(file)
        }
    }

    private fun packageUri(shortcut: Shortcut): Uri = Uri.fromParts("package", shortcut.id, null)

    private fun launchShortcut(shortcut: Shortcut) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(shortcut.id)
            ?: packageManager.getLaunchIntentForPackage(shortcut.id)
        if (intent == null) {
            Log.w(TAG, "No launch intent for ${shortcut.id}")
            return
        }
        startActivity(intent)
        viewModel.incrementOpenCount(shortcut)
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
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "LauncherActivity"
    }
}

/** Re-aligns on the whole second so the clock doesn't drift. */
@Composable
private fun rememberClock(): String {
    val context = LocalContext.current
    val format = remember(context) {
        // Locale-aware, honours the system 12/24h setting, and always includes seconds.
        val skeleton =
            if (android.text.format.DateFormat.is24HourFormat(context)) "Hms" else "hms"
        SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton),
            Locale.getDefault(),
        )
    }
    val time by produceState(initialValue = format.format(Date())) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = format.format(Date())
        }
    }
    return time
}
