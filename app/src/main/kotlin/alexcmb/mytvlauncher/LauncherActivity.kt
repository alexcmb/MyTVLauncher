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
import alexcmb.mytvlauncher.compose.ClockOptions
import alexcmb.mytvlauncher.compose.LocalAccent
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.AccentColor
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.SettingsRepository
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The launcher home, in Compose for TV. Overlay state (the menus, the rename prompt) lives
 * on the activity rather than in the composition so the flows that drive it — an update
 * check, an intent that may not resolve — read as plain sequential code.
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var viewModel: BrowseViewModel
    private val updateManager by lazy { UpdateManager(applicationContext) }
    private val widgetSlot by lazy { WidgetSlotController(this) }
    private val settings by lazy { SettingsRepository.getInstance(applicationContext) }

    private var menu by mutableStateOf<MenuSpec?>(null)
    private var namingCategoryFor by mutableStateOf<Shortcut?>(null)
    private var widgets by mutableStateOf<List<WidgetTile>>(emptyList())
    private var accent by mutableStateOf(AccentColor.INDIGO)
    private var clockShowSeconds by mutableStateOf(true)
    private var clockShowDate by mutableStateOf(false)
    private var showGreeting by mutableStateOf(true)
    private var background by mutableStateOf(BackgroundStyle.AMBIENT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        accent = settings.accent()
        clockShowSeconds = settings.clockShowSeconds()
        clockShowDate = settings.clockShowDate()
        showGreeting = settings.showGreeting()
        background = settings.background()
        widgetSlot.onChanged = { refreshWidgets() }
        // A launcher is the home screen: Back must not leave it. The menus register their
        // own back handlers that take priority while open, so this only fires at the root.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent {
            val groups by viewModel.browseContent.observeAsState(emptyList())
            val tabs = HomeTabs.from(groups, stringResource(R.string.title_home))
            CompositionLocalProvider(LocalAccent provides Color(accent.argb)) {
                Box {
                    HomeScreen(
                        tabs = tabs,
                        widgets = widgets,
                        clock = ClockOptions(clockShowSeconds, clockShowDate, showGreeting),
                        background = background,
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
            MenuItem(getString(R.string.action_appearance)) { showAppearanceMenu() },
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

    private fun showAppearanceMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_appearance),
            items = listOf(
                MenuItem(getString(R.string.action_accent)) { showAccentMenu() },
                MenuItem(getString(R.string.action_clock)) { showClockMenu() },
                MenuItem(getString(R.string.action_background)) { showBackgroundMenu() },
            ),
        )
    }

    private fun showBackgroundMenu() {
        fun choice(label: Int, style: BackgroundStyle) = MenuItem(getString(label)) {
            menu = null
            settings.setBackground(style)
            background = style
        }
        menu = MenuSpec(
            title = getString(R.string.action_background),
            items = listOf(
                choice(R.string.background_ambient, BackgroundStyle.AMBIENT),
                choice(R.string.background_gradient, BackgroundStyle.ACCENT_GRADIENT),
                choice(R.string.background_solid, BackgroundStyle.SOLID),
            ),
        )
    }

    private fun showClockMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_clock),
            items = listOf(
                toggleItem(R.string.clock_seconds, clockShowSeconds) {
                    settings.setClockShowSeconds(it); clockShowSeconds = it; showClockMenu()
                },
                toggleItem(R.string.clock_date, clockShowDate) {
                    settings.setClockShowDate(it); clockShowDate = it; showClockMenu()
                },
                toggleItem(R.string.clock_greeting, showGreeting) {
                    settings.setShowGreeting(it); showGreeting = it; showClockMenu()
                },
            ),
        )
    }

    /** A menu row that shows its on/off state and flips it, leaving the menu open. */
    private fun toggleItem(@StringRes label: Int, value: Boolean, onToggle: (Boolean) -> Unit): MenuItem {
        val state = getString(if (value) R.string.state_on else R.string.state_off)
        return MenuItem("${getString(label)}  ·  $state") { onToggle(!value) }
    }

    private fun showAccentMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_accent),
            items = AccentColor.entries.map { colour ->
                MenuItem(accentName(colour), swatch = Color(colour.argb)) {
                    menu = null
                    settings.setAccent(colour)
                    accent = colour
                }
            },
        )
    }

    private fun accentName(colour: AccentColor): String = getString(
        when (colour) {
            AccentColor.INDIGO -> R.string.accent_indigo
            AccentColor.TEAL -> R.string.accent_teal
            AccentColor.CORAL -> R.string.accent_coral
            AccentColor.PINK -> R.string.accent_pink
            AccentColor.AMBER -> R.string.accent_amber
            AccentColor.BLUE -> R.string.accent_blue
        }
    )

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

