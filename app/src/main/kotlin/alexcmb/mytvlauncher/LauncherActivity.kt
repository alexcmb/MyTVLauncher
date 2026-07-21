package alexcmb.mytvlauncher

import alexcmb.mytvlauncher.browse.BrowseViewModel
import alexcmb.mytvlauncher.browse.CategoryOptions
import alexcmb.mytvlauncher.compose.HomeScreen
import alexcmb.mytvlauncher.compose.HomeTabs
import alexcmb.mytvlauncher.compose.MenuItem
import alexcmb.mytvlauncher.compose.MenuSpec
import alexcmb.mytvlauncher.compose.TvMenu
import alexcmb.mytvlauncher.compose.TvResizeOverlay
import alexcmb.mytvlauncher.compose.TvTextPrompt
import alexcmb.mytvlauncher.compose.WidgetTile
import alexcmb.mytvlauncher.compose.ClockOptions
import alexcmb.mytvlauncher.compose.LocalAccent
import alexcmb.mytvlauncher.media.NowPlaying
import alexcmb.mytvlauncher.media.NowPlayingController
import alexcmb.mytvlauncher.media.NowPlayingListenerService
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.source.TvSource
import alexcmb.mytvlauncher.source.TvSourceController
import alexcmb.mytvlauncher.repository.AccentColor
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.CardSize
import alexcmb.mytvlauncher.repository.SettingsRepository
import alexcmb.mytvlauncher.update.UpdateManager
import alexcmb.mytvlauncher.widget.MAX_SCALE_PERCENT
import alexcmb.mytvlauncher.widget.MIN_SCALE_PERCENT
import alexcmb.mytvlauncher.widget.WidgetAlignment
import alexcmb.mytvlauncher.widget.WidgetFit
import alexcmb.mytvlauncher.widget.WidgetShape
import alexcmb.mytvlauncher.widget.WidgetSlotController
import alexcmb.mytvlauncher.widget.layoutHeightDp
import alexcmb.mytvlauncher.widget.layoutWidthDp
import alexcmb.mytvlauncher.widget.tileHeightDp
import alexcmb.mytvlauncher.widget.tileWidthDp
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
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
    private val sourceController by lazy { TvSourceController(this) }
    private val nowPlayingController by lazy { NowPlayingController(this) }
    private val settings by lazy { SettingsRepository.getInstance(applicationContext) }

    // Menus nest by pushing onto a stack, so Back steps out one level at a time rather than
    // closing the lot. Assigning `menu` drives it: null clears everything (a leaf action ran);
    // a spec whose title matches the top refreshes that level (e.g. a toggle flipping); any
    // other title pushes a new level.
    private val menuStack = mutableStateListOf<MenuSpec>()
    private var menu: MenuSpec?
        get() = menuStack.lastOrNull()
        set(value) {
            when {
                value == null -> menuStack.clear()
                menuStack.lastOrNull()?.title == value.title ->
                    menuStack[menuStack.lastIndex] = value
                else -> menuStack.add(value)
            }
        }
    private var namingCategoryFor by mutableStateOf<Shortcut?>(null)
    private var widgets by mutableStateOf<List<WidgetTile>>(emptyList())

    /** A live widget resize in progress: its target, slider value, and panel label. */
    private data class ResizeSession(val id: Int, val percent: Int, val label: String)
    private var resizing by mutableStateOf<ResizeSession?>(null)
    private var sources by mutableStateOf<List<TvSource>>(emptyList())
    private var nowPlaying by mutableStateOf<NowPlaying?>(null)
    private var accent by mutableStateOf(AccentColor.INDIGO)
    private var accentAuto by mutableStateOf(false)
    private var clockShowSeconds by mutableStateOf(true)
    private var clockShowDate by mutableStateOf(false)
    private var showGreeting by mutableStateOf(true)
    private var background by mutableStateOf(BackgroundStyle.AMBIENT)
    private var cardSize by mutableStateOf(CardSize.MEDIUM)
    private var showUsageCount by mutableStateOf(true)
    private var showAppLabels by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        accent = settings.accent()
        accentAuto = settings.accentAuto()
        clockShowSeconds = settings.clockShowSeconds()
        clockShowDate = settings.clockShowDate()
        showGreeting = settings.showGreeting()
        background = settings.background()
        cardSize = settings.cardSize()
        showUsageCount = settings.showUsageCount()
        showAppLabels = settings.showAppLabels()
        widgetSlot.onChanged = { refreshWidgets() }
        sourceController.onChanged = { sources = sourceController.sources() }
        sources = sourceController.sources()
        nowPlayingController.onChanged = { nowPlaying = nowPlayingController.nowPlaying() }
        if (savedInstanceState == null) {
            // Cold start only: clean widget ids left over from cancelled/interrupted adds.
            // Never mid-session — an add's configure screen can stop/recreate us, and pruning
            // then would delete the widget being added.
            widgetSlot.pruneOrphans()
        } else {
            // Carry the in-flight add across a recreation so its configure result isn't lost.
            widgetSlot.pendingWidgetId =
                savedInstanceState.getInt(STATE_PENDING_WIDGET, WidgetSlotController.NO_WIDGET)
        }
        // A launcher is the home screen: Back must not leave it. The menus register their
        // own back handlers that take priority while open, so this only fires at the root.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent {
            val groups by viewModel.browseContent.observeAsState(emptyList())
            val tabs = HomeTabs.from(
                groups, stringResource(R.string.title_home), sources, stringResource(R.string.title_sources),
            )
            CompositionLocalProvider(LocalAccent provides Color(accent.argb)) {
                Box {
                    HomeScreen(
                        tabs = tabs,
                        widgets = widgets,
                        clock = ClockOptions(clockShowSeconds, clockShowDate, showGreeting),
                        background = background,
                        cardSize = cardSize,
                        accentAuto = accentAuto,
                        showUsageCount = showUsageCount,
                        showAppLabels = showAppLabels,
                        nowPlaying = nowPlaying,
                        onLaunch = ::launchShortcut,
                        onLongPress = ::showAppMenu,
                        onOpenSource = sourceController::open,
                        onPlayPause = nowPlayingController::playPause,
                        onOpenNowPlaying = nowPlayingController::openApp,
                        onSettings = ::showSettingsMenu,
                    )
                    // Back pops one level; when the stack empties the menu is gone.
                    menu?.let { spec -> TvMenu(spec) { menuStack.removeAt(menuStack.lastIndex) } }
                    resizing?.let { session ->
                        TvResizeOverlay(
                            label = session.label,
                            percent = session.percent,
                            onAdjust = { delta ->
                                val next = (session.percent + delta)
                                    .coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)
                                resizing = session.copy(percent = next)
                                refreshWidgets()
                            },
                            onCommit = {
                                widgetSlot.rescale(session.id, session.percent)
                                resizing = null
                            },
                            onCancel = {
                                resizing = null
                                refreshWidgets()
                            },
                        )
                    }
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
        sourceController.startListening()
        nowPlayingController.startListening()
        sources = sourceController.sources()
        nowPlaying = nowPlayingController.nowPlaying()
        refreshWidgets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PENDING_WIDGET, widgetSlot.pendingWidgetId)
    }

    override fun onStop() {
        super.onStop()
        widgetSlot.stopListening()
        sourceController.stopListening()
        nowPlayingController.stopListening()
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

    // Host views kept across tab switches: rebuilding an AppWidgetHostView on every return to
    // Home was what made the tab change hitch. Keyed by id only — a size or shape change keeps
    // the view and resizes it in place (applySize), which is what makes live resizing smooth.
    private val hostViews = HashMap<Int, View>()

    private fun refreshWidgets() {
        val hosted = widgetSlot.hostedForDisplay()
        hostViews.keys.retainAll(hosted.map { it.id }.toSet())
        widgets = hosted.map { stored ->
            // While this widget's slider is up, render at the slider value, not the stored one.
            val h = resizing?.takeIf { it.id == stored.id }
                ?.let { stored.copy(scalePercent = it.percent) } ?: stored
            val layoutW = h.layoutWidthDp()
            val layoutH = h.layoutHeightDp()
            WidgetTile(
                id = h.id,
                widthDp = h.tileWidthDp(),
                heightDp = h.tileHeightDp(),
                layoutWidthDp = layoutW,
                layoutHeightDp = layoutH,
                alignment = h.alignment,
                createView = {
                    val view = hostViews[h.id]
                        ?: widgetSlot.createHostView(h).also { hostViews[h.id] = it }
                    // It may still be attached to the AndroidView from the previous visit.
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                applySize = { view -> widgetSlot.tellSize(view, layoutW, layoutH) },
            )
        }
    }

    /** Opens the live resize slider for one widget; the menus close so the render is visible. */
    private fun startResize(id: Int) {
        val label = widgetSlot.hostedWidgets().firstOrNull { it.first == id }?.second?.toString()
            ?: getString(R.string.widget_resize)
        menu = null
        resizing = ResizeSession(id, widgetSlot.scaleOf(id), label)
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
            MenuItem(getString(R.string.action_widgets)) { showWidgetsMenu() },
            MenuItem(getString(R.string.action_android_settings)) {
                menu = null
                startAppIntent(Intent(Settings.ACTION_SETTINGS))
            },
            MenuItem(getString(R.string.action_check_updates)) {
                menu = null
                checkForUpdates()
            },
        )
        // Only offered until it's granted; once the card can read sessions there's nothing to do.
        if (!nowPlayingController.hasAccess()) {
            items += MenuItem(getString(R.string.action_now_playing)) { showNowPlayingHelp() }
        }
        val hidden = viewModel.hiddenApps
        if (hidden.isNotEmpty()) {
            items += MenuItem(getString(R.string.action_hidden_apps, hidden.size)) {
                showHiddenAppsMenu()
            }
        }
        menu = MenuSpec(getString(R.string.action_settings), items)
    }

    /** Spells out the adb grant for notification access, since the TV can't ask on screen. */
    private fun showNowPlayingHelp() {
        menu = null
        val component = ComponentName(this, NowPlayingListenerService::class.java)
        val command = "adb shell cmd notification allow_listener ${component.flattenToShortString()}"
        AlertDialog.Builder(this)
            .setTitle(R.string.now_playing_permission_title)
            .setMessage(getString(R.string.now_playing_permission_message, command))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Everything about the widget band, kept off the top-level settings menu. */
    private fun showWidgetsMenu() {
        val items = mutableListOf<MenuItem>()
        // The band is capped, so only offer "add" while there's room for another.
        if (widgetSlot.canAddMore()) {
            items += MenuItem(getString(R.string.widget_add)) { showWidgetPicker() }
        }
        if (widgetSlot.hasWidgets()) {
            items += MenuItem(getString(R.string.widget_resize)) {
                pickHostedWidget(R.string.widget_resize, ::startResize)
            }
            items += MenuItem(getString(R.string.widget_align)) {
                pickHostedWidget(R.string.widget_align, ::showAlignmentMenu)
            }
            items += MenuItem(getString(R.string.widget_shape)) {
                pickHostedWidget(R.string.widget_shape, ::showShapeMenu)
            }
            items += MenuItem(getString(R.string.widget_render)) {
                pickHostedWidget(R.string.widget_render, ::showFitMenu)
            }
            items += MenuItem(getString(R.string.widget_remove)) {
                pickHostedWidget(R.string.widget_remove) { menu = null; widgetSlot.remove(it) }
            }
        }
        menu = MenuSpec(getString(R.string.action_widgets), items)
    }

    private fun showAppearanceMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_appearance),
            items = listOf(
                MenuItem(getString(R.string.action_accent)) { showAccentMenu() },
                MenuItem(getString(R.string.action_clock)) { showClockMenu() },
                MenuItem(getString(R.string.action_background)) { showBackgroundMenu() },
                MenuItem(getString(R.string.action_card_size)) { showCardSizeMenu() },
                MenuItem(getString(R.string.action_display)) { showDisplayMenu() },
            ),
        )
    }

    private fun showDisplayMenu() {
        menu = MenuSpec(
            title = getString(R.string.action_display),
            items = listOf(
                toggleItem(R.string.display_usage_count, showUsageCount) {
                    settings.setShowUsageCount(it); showUsageCount = it; showDisplayMenu()
                },
                toggleItem(R.string.display_app_names, showAppLabels) {
                    settings.setShowAppLabels(it); showAppLabels = it; showDisplayMenu()
                },
            ),
        )
    }

    private fun showCardSizeMenu() {
        fun choice(label: Int, size: CardSize) = MenuItem(getString(label)) {
            menu = null
            settings.setCardSize(size)
            cardSize = size
        }
        menu = MenuSpec(
            title = getString(R.string.action_card_size),
            items = listOf(
                choice(R.string.widget_size_small, CardSize.SMALL),
                choice(R.string.widget_size_medium, CardSize.MEDIUM),
                choice(R.string.widget_size_large, CardSize.LARGE),
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
        val auto = MenuItem(getString(R.string.accent_auto)) {
            menu = null
            settings.setAccentAuto(true)
            accentAuto = true
        }
        menu = MenuSpec(
            title = getString(R.string.action_accent),
            items = listOf(auto) + AccentColor.entries.map { colour ->
                MenuItem(accentName(colour), swatch = Color(colour.argb)) {
                    menu = null
                    settings.setAccentAuto(false)
                    accentAuto = false
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

    private fun showAlignmentMenu(id: Int) {
        fun choice(label: Int, alignment: WidgetAlignment) = MenuItem(getString(label)) {
            menu = null
            widgetSlot.align(id, alignment)
        }
        menu = MenuSpec(
            title = getString(R.string.widget_align),
            items = listOf(
                choice(R.string.widget_align_start, WidgetAlignment.START),
                choice(R.string.widget_align_center, WidgetAlignment.CENTER),
                choice(R.string.widget_align_end, WidgetAlignment.END),
            ),
        )
    }

    private fun showShapeMenu(id: Int) {
        fun choice(label: Int, shape: WidgetShape) = MenuItem(getString(label)) {
            menu = null
            widgetSlot.reshape(id, shape)
        }
        menu = MenuSpec(
            title = getString(R.string.widget_shape),
            items = listOf(
                choice(R.string.widget_shape_wide, WidgetShape.WIDE),
                choice(R.string.widget_shape_panoramic, WidgetShape.PANORAMIC),
                choice(R.string.widget_shape_standard, WidgetShape.STANDARD),
                choice(R.string.widget_shape_square, WidgetShape.SQUARE),
            ),
        )
    }

    private fun showFitMenu(id: Int) {
        fun choice(label: Int, fit: WidgetFit) = MenuItem(getString(label)) {
            menu = null
            widgetSlot.refit(id, fit)
        }
        menu = MenuSpec(
            title = getString(R.string.widget_render),
            items = listOf(
                choice(R.string.widget_render_native, WidgetFit.NATIVE),
                choice(R.string.widget_render_fit, WidgetFit.FIT),
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
        const val STATE_PENDING_WIDGET = "pending_widget"
    }
}

