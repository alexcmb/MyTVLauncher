package alexcmb.mytvlauncher

import alexcmb.mytvlauncher.browse.BrowseViewModel
import alexcmb.mytvlauncher.browse.CategoryOptions
import alexcmb.mytvlauncher.compose.AmbientScreen
import alexcmb.mytvlauncher.compose.AppOrder
import alexcmb.mytvlauncher.compose.HomeScreen
import alexcmb.mytvlauncher.compose.HomeTabs
import alexcmb.mytvlauncher.compose.LauncherTheme
import alexcmb.mytvlauncher.compose.MenuItem
import alexcmb.mytvlauncher.compose.MenuSpec
import alexcmb.mytvlauncher.compose.TvMenu
import alexcmb.mytvlauncher.compose.TvResizeOverlay
import alexcmb.mytvlauncher.compose.TvTextPrompt
import alexcmb.mytvlauncher.compose.WidgetTile
import alexcmb.mytvlauncher.compose.ClockOptions
import alexcmb.mytvlauncher.compose.FocusTracker
import alexcmb.mytvlauncher.compose.LocalAccent
import alexcmb.mytvlauncher.media.NowPlaying
import alexcmb.mytvlauncher.media.NowPlayingController
import alexcmb.mytvlauncher.media.NowPlayingListenerService
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.source.TvSource
import alexcmb.mytvlauncher.source.TvSourceController
import alexcmb.mytvlauncher.repository.AccentColor
import alexcmb.mytvlauncher.repository.AppSort
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.CardSize
import alexcmb.mytvlauncher.repository.SettingsRepository
import alexcmb.mytvlauncher.repository.WallpaperFit
import alexcmb.mytvlauncher.update.UpdateManager
import alexcmb.mytvlauncher.update.UpdateVerdict
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
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // closing the lot. The stack holds *builders*, not built specs: the visible level is rebuilt
    // on every recomposition, so it always shows live values — a check moving to the new choice,
    // "Fond · Dégradé" updating — and choosing something can leave the menu open.
    private val menuStack = mutableStateListOf<() -> MenuSpec>()
    // Last focused row (its label) per menu title, so Back lands on the row that opened the submenu.
    private val menuFocus = mutableMapOf<String, String>()

    /** Shows a menu level: refreshes the top level if it has the same title, else pushes. */
    private fun openMenu(build: () -> MenuSpec) {
        val title = build().title
        if (menuStack.lastOrNull()?.invoke()?.title == title) {
            menuStack[menuStack.lastIndex] = build
        } else {
            menuStack.add(build)
        }
    }

    /** A leaf action ran (launched something, changed what the menus are about): close all. */
    private fun closeMenus() {
        if (menuStack.isNotEmpty()) returnFocus()
        menuStack.clear()
        menuFocus.clear()
    }

    // Where the focus was on the home screen; overlays hand it back there when they close.
    // Called while the overlay still holds the focus, before it goes away.
    private val focusTracker = FocusTracker()

    private fun returnFocus() = focusTracker.restore()
    /** The name prompt on screen: its title, the text it starts with, and what to do with a name. */
    private data class NamePrompt(val title: String, val initial: String, val onName: (String) -> Unit)
    private var namePrompt by mutableStateOf<NamePrompt?>(null)
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
    private var backgroundIntensity by mutableStateOf(SettingsRepository.BG_INTENSITY_DEFAULT)
    private var backgroundImage by mutableStateOf<ImageBitmap?>(null)
    private var backgroundVideo by mutableStateOf<Uri?>(null)
    private var wallpaperFit by mutableStateOf(WallpaperFit.FILL)
    // Live value of the background-brightness slider while it's open, else null.
    private var backgroundAdjust by mutableStateOf<Int?>(null)
    private var cardSize by mutableStateOf(CardSize.MEDIUM)
    private var showUsageCount by mutableStateOf(true)
    private var showAppLabels by mutableStateOf(false)
    private var tabOrder by mutableStateOf<List<String>>(emptyList())
    private var hiddenTabs by mutableStateOf<Set<String>>(emptySet())
    private var startupTab by mutableStateOf(HomeTabs.HOME_KEY)
    private var pinnedApps by mutableStateOf<List<String>>(emptyList())
    private var appSort by mutableStateOf(AppSort.USAGE)
    private var appOrder by mutableStateOf<List<String>>(emptyList())
    // Sleep mode: shown after ambientMinutes without input (0 = never). lastInputAt is
    // uptimeMillis of the last key; swallowKeyUp eats the release of the key that woke it.
    private var ambient by mutableStateOf(false)
    private var ambientMinutes by mutableStateOf(SettingsRepository.AMBIENT_DEFAULT)
    private var lastInputAt = SystemClock.uptimeMillis()
    private var swallowKeyUp = false
    // Bumped on each HOME press; the home screen goes back to the startup tab when it changes.
    private var homeRequests by mutableStateOf(0)

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
        backgroundIntensity = settings.backgroundIntensity()
        wallpaperFit = settings.wallpaperFit()
        backgroundVideo = settings.backgroundVideoUri()?.let(Uri::parse)
        loadBackgroundImage()
        cardSize = settings.cardSize()
        showUsageCount = settings.showUsageCount()
        showAppLabels = settings.showAppLabels()
        tabOrder = settings.tabOrder()
        hiddenTabs = settings.hiddenTabs()
        startupTab = settings.startupTab() ?: HomeTabs.HOME_KEY
        pinnedApps = settings.pinnedApps()
        appSort = settings.appSort()
        appOrder = settings.appOrder()
        ambientMinutes = settings.ambientMinutes()
        watchForIdle()
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
                tabOrder, hiddenTabs, appSort, appOrder,
            )
            CompositionLocalProvider(LocalAccent provides Color(accent.argb)) {
            // Outfit and the tv-material theme for everything: home, menus, prompts, sliders.
            LauncherTheme {
                Box(Modifier.onPreviewKeyEvent { onAnyKey(it.nativeKeyEvent) }) {
                    HomeScreen(
                        tabs = tabs,
                        widgets = widgets,
                        clock = ClockOptions(clockShowSeconds, clockShowDate, showGreeting),
                        background = background,
                        backgroundImage = backgroundImage,
                        backgroundVideo = backgroundVideo,
                        backgroundIntensity = backgroundIntensity,
                        wallpaperFit = wallpaperFit,
                        cardSize = cardSize,
                        accentAuto = accentAuto,
                        showUsageCount = showUsageCount,
                        showAppLabels = showAppLabels,
                        nowPlaying = nowPlaying,
                        onLaunch = ::launchShortcut,
                        onLongPress = { showAppMenu(it, inGrid = false) },
                        onGridLongPress = { showAppMenu(it, inGrid = true) },
                        onWidgetLongPress = ::showWidgetMenu,
                        onOpenSource = sourceController::open,
                        onPlayPause = nowPlayingController::playPause,
                        onOpenNowPlaying = nowPlayingController::openApp,
                        onSettings = ::showSettingsMenu,
                        focusTracker = focusTracker,
                        startupTab = startupTab,
                        homeRequests = homeRequests,
                        pinnedApps = pinnedApps,
                        contentHidden = ambient,
                    )
                    // Back pops one level; when the stack empties the menu is gone. A live slider
                    // (widget size, background brightness) hides the menus so the result shows,
                    // and they come back at the same level once it's committed or cancelled. A
                    // name prompt opened from a menu hides it the same way.
                    if (resizing == null && backgroundAdjust == null && namePrompt == null) {
                        menuStack.lastOrNull()?.let { build ->
                            TvMenu(build(), menuFocus) {
                                if (menuStack.size == 1) closeMenus()
                                else menuStack.removeAt(menuStack.lastIndex)
                            }
                        }
                    }
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
                                if (menuStack.isEmpty()) returnFocus()
                                resizing = null
                            },
                            onCancel = {
                                if (menuStack.isEmpty()) returnFocus()
                                resizing = null
                                refreshWidgets()
                            },
                        )
                    }
                    backgroundAdjust?.let { value ->
                        TvResizeOverlay(
                            label = stringResource(R.string.background_brightness),
                            percent = value,
                            min = SettingsRepository.BG_INTENSITY_MIN,
                            max = SettingsRepository.BG_INTENSITY_MAX,
                            step = SettingsRepository.BG_INTENSITY_STEP,
                            onAdjust = { delta ->
                                val next = (value + delta).coerceIn(
                                    SettingsRepository.BG_INTENSITY_MIN,
                                    SettingsRepository.BG_INTENSITY_MAX,
                                )
                                backgroundAdjust = next
                                backgroundIntensity = next // live preview behind the slider
                            },
                            onCommit = {
                                settings.setBackgroundIntensity(value)
                                if (menuStack.isEmpty()) returnFocus()
                                backgroundAdjust = null
                            },
                            onCancel = {
                                if (menuStack.isEmpty()) returnFocus()
                                backgroundAdjust = null
                                backgroundIntensity = settings.backgroundIntensity()
                            },
                        )
                    }
                    namePrompt?.let { prompt ->
                        TvTextPrompt(
                            title = prompt.title,
                            hint = stringResource(R.string.category_hint),
                            initial = prompt.initial,
                            // A name that cleans up to nothing (blank, or a reserved '#…') is ignored.
                            onSubmit = { raw -> CategoryOptions.cleanName(raw)?.let(prompt.onName) },
                            onDismiss = {
                                // Opened from a menu, the menu comes back and takes the focus.
                                if (menuStack.isEmpty()) returnFocus()
                                namePrompt = null
                            },
                        )
                    }
                    AnimatedVisibility(
                        visible = ambient,
                        enter = fadeIn(tween(800)),
                        exit = fadeOut(tween(250)),
                    ) {
                        AmbientScreen(nowPlaying)
                    }
                }
            }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from an app: count from now, so sleep mode doesn't greet the user straight away.
        lastInputAt = SystemClock.uptimeMillis()
        ambient = false
        widgetSlot.startListening()
        sourceController.startListening()
        nowPlayingController.startListening()
        sources = sourceController.sources()
        nowPlaying = nowPlayingController.nowPlaying()
        refreshWidgets()
    }

    // singleTask: HOME while we're the task brings us here instead of a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Anything half-done is dropped, as Back would: menus, an uncommitted slider, a prompt.
            menuStack.clear()
            menuFocus.clear()
            if (resizing != null) {
                resizing = null
                refreshWidgets()
            }
            backgroundAdjust?.let { backgroundIntensity = settings.backgroundIntensity() }
            backgroundAdjust = null
            namePrompt = null
            ambient = false
            homeRequests++
        }
    }

    /**
     * Every key resets the idle clock. While sleep mode is up, the key that wakes the launcher
     * only does that: it (and its release) is swallowed, so it can't also open the focused app.
     * Called from the root of the screen, ahead of everything below it (widgets included).
     */
    private fun onAnyKey(event: KeyEvent): Boolean {
        lastInputAt = SystemClock.uptimeMillis()
        if (ambient && event.action == KeyEvent.ACTION_DOWN) {
            ambient = false
            swallowKeyUp = true
            return true
        }
        if (swallowKeyUp) {
            if (event.action == KeyEvent.ACTION_UP) swallowKeyUp = false
            return true
        }
        return false
    }

    /**
     * Shows sleep mode once the launcher has sat [ambientMinutes] without input, checked every
     * few seconds while it's in front. Never over an open menu, slider or prompt — someone is
     * in the middle of something there — nor while something else has the window's focus.
     *
     * The idle clock restarts whenever the launcher comes back to the front: after an app
     * (onStart), but also after a panel that only covered it (a settings side panel, the input
     * picker, voice search: onResume) or an overlay that only took the focus (volume, a
     * notification: onWindowFocusChanged). The keys pressed there never reach the launcher, so
     * without this it would count that time as idle and fall asleep seconds after coming back.
     */
    private fun watchForIdle() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                lastInputAt = SystemClock.uptimeMillis()
                while (true) {
                    delay(IDLE_CHECK_MS)
                    val busy = menuStack.isNotEmpty() || resizing != null ||
                        backgroundAdjust != null || namePrompt != null || !hasWindowFocus()
                    val idleMs = SystemClock.uptimeMillis() - lastInputAt
                    if (ambientMinutes > 0 && !ambient && !busy && idleMs >= ambientMinutes * 60_000L) {
                        ambient = true
                    }
                }
            }
        }
    }

    // Every key and click the activity receives (from any window of ours) restarts the idle clock.
    override fun onUserInteraction() {
        super.onUserInteraction()
        lastInputAt = SystemClock.uptimeMillis()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) lastInputAt = SystemClock.uptimeMillis()
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
            REQUEST_PICK_WALLPAPER -> if (granted) onWallpaperPicked(data)
            REQUEST_PICK_VIDEO -> if (granted) onVideoPicked(data)
        }
    }

    private fun onVideoPicked(data: Intent?) {
        val uri = data?.data ?: return
        // Persist read access so the video survives reboots.
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.setBackgroundVideoUri(uri.toString())
        settings.setBackground(BackgroundStyle.VIDEO)
        backgroundVideo = uri
        background = BackgroundStyle.VIDEO
    }

    private fun onWallpaperPicked(data: Intent?) {
        val uri = data?.data ?: return
        // Persist read access so the wallpaper survives reboots.
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.setBackgroundImageUri(uri.toString())
        settings.setBackground(BackgroundStyle.WALLPAPER)
        background = BackgroundStyle.WALLPAPER
        loadBackgroundImage()
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

    /** Opens the live resize slider for one widget; the menus hide so the render is visible. */
    private fun startResize(id: Int) {
        val label = widgetSlot.hostedWidgets().firstOrNull { it.first == id }?.second?.toString()
            ?: getString(R.string.widget_resize)
        resizing = ResizeSession(id, widgetSlot.scaleOf(id), label)
    }

    /**
     * An app's menu. [inGrid]: opened from a category grid rather than the hub's favourites
     * row — it decides which moves the menu offers: the favourites order on the hub, the
     * grid's manual order in a grid.
     */
    private fun showAppMenu(shortcut: Shortcut, inGrid: Boolean) = openMenu {
        val pinned = shortcut.id in pinnedApps
        val items = mutableListOf(
            MenuItem(
                getString(R.string.menu_change_category),
                value = shortcut.category,
                submenu = true,
            ) { showCategoryMenu(shortcut) },
            // A toggle with a fixed label, so the focus stays on it when the rows below
            // appear or go away.
            toggleItem(R.string.menu_pin, pinned) { pin ->
                savePinnedApps(if (pin) pinnedApps + shortcut.id else pinnedApps - shortcut.id)
            },
        )
        if (pinned && !inGrid) {
            // Same moves as the tabs: the menu stays open to move again, and the value is the
            // card's place in the favourites row.
            items += MenuItem(
                getString(R.string.tab_move_left),
                value = "${pinnedApps.indexOf(shortcut.id) + 1} / ${pinnedApps.size}",
            ) { savePinnedApps(HomeTabs.move(pinnedApps, shortcut.id, -1)) }
            items += MenuItem(getString(R.string.tab_move_right)) {
                savePinnedApps(HomeTabs.move(pinnedApps, shortcut.id, +1))
            }
        }
        if (inGrid && appSort == AppSort.MANUAL) items += gridMoveItems(shortcut)
        MenuSpec(
            title = shortcut.title,
            items = items + listOf(
                MenuItem(getString(R.string.menu_hide), divider = true) {
                    closeMenus()
                    viewModel.hideApp(shortcut)
                },
                MenuItem(getString(R.string.menu_uninstall), divider = true) {
                    closeMenus()
                    startAppIntent(Intent(Intent.ACTION_DELETE, packageUri(shortcut)))
                },
                MenuItem(getString(R.string.menu_app_info)) {
                    closeMenus()
                    startAppIntent(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(shortcut))
                    )
                },
            ),
        )
    }

    /** Left/right within the app's category grid, in manual order; the menu stays open. */
    private fun gridMoveItems(shortcut: Shortcut): List<MenuItem> {
        val group = viewModel.browseContent.value.orEmpty()
            .firstOrNull { it.category == shortcut.category } ?: return emptyList()
        val shown = AppOrder.sort(group.shortcutList, AppSort.MANUAL, appOrder).map { it.id }
        fun move(delta: Int) {
            appOrder = AppOrder.move(appOrder, shown, shortcut.id, delta).also(settings::setAppOrder)
        }
        return listOf(
            MenuItem(
                getString(R.string.tab_move_left),
                value = "${shown.indexOf(shortcut.id) + 1} / ${shown.size}",
            ) { move(-1) },
            MenuItem(getString(R.string.tab_move_right)) { move(+1) },
        )
    }

    private fun appSortName(sort: AppSort): String = getString(
        when (sort) {
            AppSort.USAGE -> R.string.app_sort_usage
            AppSort.ALPHABETICAL -> R.string.app_sort_alpha
            AppSort.MANUAL -> R.string.app_sort_manual
        }
    )

    private fun showAppSortMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.app_sort),
            items = AppSort.entries.map { sort ->
                MenuItem(appSortName(sort), selected = sort == appSort) {
                    settings.setAppSort(sort)
                    appSort = sort
                }
            },
        )
    }

    private fun savePinnedApps(ids: List<String>) {
        settings.setPinnedApps(ids)
        pinnedApps = ids
    }

    private fun showCategoryMenu(shortcut: Shortcut) = openMenu {
        val categories = CategoryOptions.candidates(
            current = shortcut.category,
            existing = viewModel.availableCategories(),
            defaults = listOf(getString(R.string.title_apps), getString(R.string.title_system)),
            presets = resources.getStringArray(R.array.category_presets).toList(),
        )
        MenuSpec(
            title = getString(R.string.menu_change_category),
            items = categories.map { category ->
                MenuItem(category, selected = category == shortcut.category) {
                    closeMenus()
                    viewModel.setCategory(shortcut, category)
                }
            } + MenuItem(getString(R.string.category_new), divider = true) {
                closeMenus()
                namePrompt = NamePrompt(getString(R.string.category_new_title), "") {
                    viewModel.setCategory(shortcut, it)
                }
            },
        )
    }

    private fun showSettingsMenu() = openMenu {
        // Launcher settings first, then — set apart by a divider — the system and one-shot actions.
        val items = mutableListOf(
            MenuItem(getString(R.string.action_appearance), submenu = true) { showAppearanceMenu() },
            MenuItem(getString(R.string.action_tabs), submenu = true) { showTabsMenu() },
            MenuItem(getString(R.string.action_widgets), submenu = true) { showWidgetsMenu() },
        )
        val hidden = viewModel.hiddenApps
        if (hidden.isNotEmpty()) {
            items += MenuItem(
                getString(R.string.action_hidden_apps_label),
                value = hidden.size.toString(),
                submenu = true,
            ) { showHiddenAppsMenu() }
        }
        // Only offered until it's granted; once the card can read sessions there's nothing to do.
        if (!nowPlayingController.hasAccess()) {
            items += MenuItem(getString(R.string.action_now_playing), divider = true) { showNowPlayingHelp() }
        }
        items += MenuItem(
            getString(R.string.action_check_updates),
            value = installedVersion(),
            divider = nowPlayingController.hasAccess(),
        ) {
            closeMenus()
            checkForUpdates()
        }
        items += MenuItem(getString(R.string.action_android_settings)) {
            closeMenus()
            startAppIntent(Intent(Settings.ACTION_SETTINGS))
        }
        MenuSpec(getString(R.string.action_settings), items)
    }

    /** Every tab after the hub in its current order, hidden ones included (they can come back). */
    private fun movableTabs() = HomeTabs.from(
        viewModel.browseContent.value.orEmpty(),
        getString(R.string.title_home),
        sources,
        getString(R.string.title_sources),
        tabOrder,
    ).drop(1)

    private fun tabTitle(key: String): String = when (key) {
        HomeTabs.HOME_KEY -> getString(R.string.title_home)
        HomeTabs.SOURCES_KEY -> getString(R.string.title_sources)
        else -> key
    }

    /** The tab bar: which tab the screen opens on, then each tab to show, hide or move. */
    private fun showTabsMenu() = openMenu {
        val tabs = movableTabs()
        val items = mutableListOf(
            MenuItem(getString(R.string.tabs_startup), value = tabTitle(startupTab), submenu = true) {
                showStartupTabMenu()
            },
            MenuItem(getString(R.string.app_sort), value = appSortName(appSort), submenu = true) {
                showAppSortMenu()
            },
        )
        tabs.forEachIndexed { index, tab ->
            items += MenuItem(
                tab.title,
                value = if (tab.key in hiddenTabs) getString(R.string.tab_hidden) else null,
                submenu = true,
                divider = index == 0,
            ) { showTabMenu(tab.key) }
        }
        if (tabOrder.isNotEmpty() || hiddenTabs.isNotEmpty()) {
            items += MenuItem(getString(R.string.tabs_reset), divider = true) {
                settings.setTabOrder(emptyList()); tabOrder = emptyList()
                settings.setHiddenTabs(emptySet()); hiddenTabs = emptySet()
            }
        }
        MenuSpec(getString(R.string.action_tabs), items)
    }

    private fun showStartupTabMenu() = openMenu {
        // Only the tabs actually in the bar: opening on a hidden one would land on the hub.
        val keys = listOf(HomeTabs.HOME_KEY) + movableTabs().map { it.key }.filterNot { it in hiddenTabs }
        MenuSpec(
            title = getString(R.string.tabs_startup),
            items = keys.map { key ->
                MenuItem(tabTitle(key), selected = key == startupTab) {
                    settings.setStartupTab(key.takeIf { it != HomeTabs.HOME_KEY })
                    startupTab = key
                }
            },
        )
    }

    /** One tab: show/hide it, or move it along the bar (the menu stays open to move again). */
    private fun showTabMenu(key: String) = openMenu {
        val keys = movableTabs().map { it.key }
        val position = keys.indexOf(key)
        fun move(delta: Int) {
            val next = HomeTabs.move(keys, key, delta)
            settings.setTabOrder(next)
            tabOrder = next
        }
        MenuSpec(
            title = tabTitle(key),
            items = listOf(
                toggleItem(R.string.tab_show, key !in hiddenTabs) { show ->
                    val next = if (show) hiddenTabs - key else hiddenTabs + key
                    settings.setHiddenTabs(next)
                    hiddenTabs = next
                    // A hidden tab can't be where the screen opens.
                    if (!show && startupTab == key) {
                        settings.setStartupTab(null)
                        startupTab = HomeTabs.HOME_KEY
                    }
                },
                MenuItem(
                    getString(R.string.tab_move_left),
                    value = "${position + 2} / ${keys.size + 1}",
                    divider = true,
                ) { move(-1) },
                MenuItem(getString(R.string.tab_move_right)) { move(+1) },
            ) + categoryItems(key),
        )
    }

    private fun defaultCategories() = listOf(getString(R.string.title_apps), getString(R.string.title_system))

    /**
     * A category tab can also be renamed, and a category of the user's own deleted (its apps
     * go back to Apps or System). The built-in ones can't be deleted: their apps would simply
     * fall back into them.
     */
    private fun categoryItems(key: String): List<MenuItem> {
        if (key == HomeTabs.HOME_KEY || key == HomeTabs.SOURCES_KEY) return emptyList()
        val items = mutableListOf(
            MenuItem(getString(R.string.category_rename), divider = true) {
                namePrompt = NamePrompt(getString(R.string.category_rename_title), key) { name ->
                    if (name != key) renameCategory(key, name)
                }
            },
        )
        if (key !in defaultCategories()) {
            items += MenuItem(getString(R.string.category_delete), submenu = true) {
                showDeleteCategoryMenu(key)
            }
        }
        return items
    }

    private fun renameCategory(from: String, to: String) {
        viewModel.renameCategory(from, to)
        // The tab keeps its place, visibility and startup role under the new name.
        tabOrder = HomeTabs.renameKey(tabOrder, from, to).also(settings::setTabOrder)
        hiddenTabs = HomeTabs.renameKey(hiddenTabs.toList(), from, to).toSet().also(settings::setHiddenTabs)
        if (startupTab == from) {
            startupTab = to
            settings.setStartupTab(to)
        }
        // This tab's menu is about the old name: step back to the tab list, on the renamed row.
        leaveTabMenu(focusOn = to)
    }

    private fun showDeleteCategoryMenu(category: String) = openMenu {
        MenuSpec(
            title = getString(R.string.category_delete_title, category),
            items = listOf(
                MenuItem(
                    getString(R.string.category_delete_confirm),
                    value = resources.getQuantityString(
                        R.plurals.category_app_count, viewModel.appCount(category), viewModel.appCount(category),
                    ),
                ) {
                    viewModel.deleteCategory(category)
                    tabOrder = (tabOrder - category).also(settings::setTabOrder)
                    hiddenTabs = (hiddenTabs - category).also(settings::setHiddenTabs)
                    if (startupTab == category) {
                        startupTab = HomeTabs.HOME_KEY
                        settings.setStartupTab(null)
                    }
                    menuStack.removeAt(menuStack.lastIndex) // the confirmation
                    leaveTabMenu(focusOn = null)
                },
                MenuItem(getString(android.R.string.cancel)) { menuStack.removeAt(menuStack.lastIndex) },
            ),
        )
    }

    /** Pops a category tab's own menu, back to the Tabs list, focused on [focusOn] if given. */
    private fun leaveTabMenu(focusOn: String?) {
        if (menuStack.size > 1) menuStack.removeAt(menuStack.lastIndex)
        val tabsTitle = getString(R.string.action_tabs)
        if (focusOn != null) menuFocus[tabsTitle] = focusOn else menuFocus.remove(tabsTitle)
    }

    private fun installedVersion(): String? = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()

    /** Spells out the adb grant for notification access, since the TV can't ask on screen. */
    private fun showNowPlayingHelp() {
        closeMenus()
        val component = ComponentName(this, NowPlayingListenerService::class.java)
        val command = "adb shell cmd notification allow_listener ${component.flattenToShortString()}"
        AlertDialog.Builder(this)
            .setTitle(R.string.now_playing_permission_title)
            .setMessage(getString(R.string.now_playing_permission_message, command))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * The widget band: add one, or open a hosted widget's own menu. (Long-pressing a widget on
     * the hub opens that same menu directly; this list is the discoverable way in.)
     */
    private fun showWidgetsMenu() = openMenu {
        val items = mutableListOf<MenuItem>()
        // The band is capped, so only offer "add" while there's room for another.
        if (widgetSlot.canAddMore()) {
            items += MenuItem(getString(R.string.widget_add), submenu = true) { showWidgetPicker() }
        }
        items += widgetSlot.hostedWidgets().mapIndexed { index, (id, label) ->
            MenuItem(
                label.toString(),
                value = hostedWidget(id)?.let { alignmentName(it.alignment) },
                submenu = true,
                divider = index == 0,
            ) { showWidgetMenu(id) }
        }
        MenuSpec(getString(R.string.action_widgets), items)
    }

    /** Everything about one hosted widget: opened by long-pressing it, or from Widgets. */
    private fun showWidgetMenu(id: Int) = openMenu {
        val label = widgetSlot.hostedWidgets().firstOrNull { it.first == id }?.second?.toString()
            ?: getString(R.string.action_widgets)
        val widget = hostedWidget(id)
        MenuSpec(
            title = label,
            items = listOf(
                MenuItem(getString(R.string.widget_menu_size), value = "${widgetSlot.scaleOf(id)} %") {
                    startResize(id)
                },
                MenuItem(
                    getString(R.string.widget_menu_align),
                    value = widget?.let { alignmentName(it.alignment) },
                    submenu = true,
                ) { showAlignmentMenu(id) },
                MenuItem(
                    getString(R.string.widget_menu_shape),
                    value = widget?.let { shapeName(it.shape) },
                    submenu = true,
                ) { showShapeMenu(id) },
                MenuItem(
                    getString(R.string.widget_render),
                    value = widget?.let { widgetFitName(it.fit) },
                    submenu = true,
                ) { showFitMenu(id) },
                MenuItem(getString(R.string.widget_menu_remove), divider = true) {
                    closeMenus()
                    widgetSlot.remove(id)
                },
            ),
        )
    }

    private fun alignmentName(alignment: WidgetAlignment): String = getString(
        when (alignment) {
            WidgetAlignment.START -> R.string.widget_align_start
            WidgetAlignment.CENTER -> R.string.widget_align_center
            WidgetAlignment.END -> R.string.widget_align_end
        }
    )

    private fun shapeName(shape: WidgetShape): String = getString(
        when (shape) {
            WidgetShape.WIDE -> R.string.widget_shape_wide
            WidgetShape.PANORAMIC -> R.string.widget_shape_panoramic
            WidgetShape.STANDARD -> R.string.widget_shape_standard
            WidgetShape.SQUARE -> R.string.widget_shape_square
        }
    )

    private fun widgetFitName(fit: WidgetFit): String = getString(
        when (fit) {
            WidgetFit.NATIVE -> R.string.widget_render_native_short
            WidgetFit.FIT -> R.string.widget_render_fit_short
        }
    )

    private fun showAppearanceMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_appearance),
            items = listOf(
                MenuItem(
                    getString(R.string.action_accent),
                    value = if (accentAuto) getString(R.string.accent_auto_short) else accentName(accent),
                    submenu = true,
                ) { showAccentMenu() },
                MenuItem(
                    getString(R.string.action_background),
                    value = backgroundStyleName(background),
                    submenu = true,
                ) { showBackgroundMenu() },
                // Colours above, layout of the screen below.
                MenuItem(
                    getString(R.string.action_card_size),
                    value = cardSizeName(cardSize),
                    submenu = true,
                    divider = true,
                ) { showCardSizeMenu() },
                MenuItem(getString(R.string.action_display), submenu = true) { showDisplayMenu() },
                MenuItem(getString(R.string.action_clock), submenu = true) { showClockMenu() },
                MenuItem(
                    getString(R.string.ambient_mode),
                    value = ambientName(ambientMinutes),
                    submenu = true,
                ) { showAmbientMenu() },
            ),
        )
    }

    private fun ambientName(minutes: Int): String =
        if (minutes == 0) getString(R.string.ambient_off) else getString(R.string.ambient_minutes, minutes)

    /** Sleep mode's delay: off, or how many minutes without input before it shows. */
    private fun showAmbientMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.ambient_mode),
            items = SettingsRepository.AMBIENT_CHOICES.map { minutes ->
                MenuItem(ambientName(minutes), selected = minutes == ambientMinutes) {
                    settings.setAmbientMinutes(minutes)
                    ambientMinutes = minutes
                }
            },
        )
    }

    private fun showDisplayMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_display),
            items = listOf(
                toggleItem(R.string.display_usage_count, showUsageCount) {
                    settings.setShowUsageCount(it); showUsageCount = it
                },
                toggleItem(R.string.display_app_names, showAppLabels) {
                    settings.setShowAppLabels(it); showAppLabels = it
                },
            ),
        )
    }

    private fun cardSizeName(size: CardSize): String = getString(
        when (size) {
            CardSize.SMALL -> R.string.widget_size_small
            CardSize.MEDIUM -> R.string.widget_size_medium
            CardSize.LARGE -> R.string.widget_size_large
        }
    )

    private fun showCardSizeMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_card_size),
            items = CardSize.entries.map { size ->
                MenuItem(cardSizeName(size), selected = size == cardSize) {
                    settings.setCardSize(size)
                    cardSize = size
                }
            },
        )
    }

    private fun backgroundStyleName(style: BackgroundStyle): String = getString(
        when (style) {
            BackgroundStyle.AMBIENT -> R.string.background_ambient
            BackgroundStyle.ACCENT_GRADIENT -> R.string.background_gradient
            BackgroundStyle.SOLID -> R.string.background_solid
            BackgroundStyle.WALLPAPER -> R.string.background_wallpaper
            BackgroundStyle.VIDEO -> R.string.background_video
            BackgroundStyle.AURORA -> R.string.background_aurora
        }
    )

    private fun wallpaperFitName(fit: WallpaperFit): String = getString(
        when (fit) {
            WallpaperFit.FILL -> R.string.wallpaper_fit_fill
            WallpaperFit.STRETCH -> R.string.wallpaper_fit_stretch
            WallpaperFit.FIT -> R.string.wallpaper_fit_fit
            WallpaperFit.CENTER -> R.string.wallpaper_fit_center
            WallpaperFit.SPAN -> R.string.wallpaper_fit_span
        }
    )

    /** Background: what fills it (style), the image behind it, and how strongly it shows. */
    private fun showBackgroundMenu() = openMenu {
        val hasImage = settings.backgroundImageUri() != null
        val hasVideo = backgroundVideo != null
        MenuSpec(
            title = getString(R.string.action_background),
            items = listOf(
                MenuItem(
                    getString(R.string.background_style),
                    value = backgroundStyleName(background),
                    submenu = true,
                ) { showBackgroundStyleMenu() },
                MenuItem(
                    getString(R.string.background_wallpaper),
                    value = getString(if (hasImage) R.string.background_image_set else R.string.background_image_none),
                    submenu = true,
                ) { showWallpaperMenu() },
                MenuItem(
                    getString(R.string.background_video),
                    value = getString(if (hasVideo) R.string.background_image_set else R.string.background_image_none),
                    submenu = true,
                ) { showVideoMenu() },
                MenuItem(
                    getString(R.string.background_brightness),
                    value = "$backgroundIntensity %",
                ) { backgroundAdjust = settings.backgroundIntensity() },
            ),
        )
    }

    private fun showBackgroundStyleMenu() = openMenu {
        // "Image" and "Video" are only styles once there's something to show.
        val styles = listOf(
            BackgroundStyle.AMBIENT,
            BackgroundStyle.AURORA,
            BackgroundStyle.ACCENT_GRADIENT,
            BackgroundStyle.SOLID,
            BackgroundStyle.WALLPAPER,
            BackgroundStyle.VIDEO,
        ).filter {
            (it != BackgroundStyle.WALLPAPER || settings.backgroundImageUri() != null) &&
                (it != BackgroundStyle.VIDEO || backgroundVideo != null)
        }
        MenuSpec(
            title = getString(R.string.background_style),
            items = styles.map { style ->
                MenuItem(backgroundStyleName(style), selected = style == background) {
                    settings.setBackground(style)
                    background = style
                }
            },
        )
    }

    private fun showWallpaperMenu() = openMenu {
        val hasImage = settings.backgroundImageUri() != null
        val items = mutableListOf(
            MenuItem(getString(R.string.background_image)) { pickWallpaper() },
        )
        if (hasImage) {
            items += MenuItem(
                getString(R.string.wallpaper_fit),
                value = wallpaperFitName(wallpaperFit),
                submenu = true,
            ) { showWallpaperFitMenu() }
            items += MenuItem(getString(R.string.background_image_remove), divider = true) {
                settings.setBackgroundImageUri(null)
                backgroundImage = null
                if (background == BackgroundStyle.WALLPAPER) {
                    settings.setBackground(BackgroundStyle.AMBIENT)
                    background = BackgroundStyle.AMBIENT
                }
            }
        }
        MenuSpec(getString(R.string.background_wallpaper), items)
    }

    /** The video background: pick one, frame it (same choices as an image), or remove it. */
    private fun showVideoMenu() = openMenu {
        val items = mutableListOf(
            MenuItem(getString(R.string.background_video_pick)) { pickVideo() },
        )
        if (backgroundVideo != null) {
            items += MenuItem(
                getString(R.string.video_fit),
                value = wallpaperFitName(wallpaperFit),
                submenu = true,
            ) { showWallpaperFitMenu() }
            items += MenuItem(getString(R.string.background_video_remove), divider = true) {
                settings.setBackgroundVideoUri(null)
                backgroundVideo = null
                if (background == BackgroundStyle.VIDEO) {
                    settings.setBackground(BackgroundStyle.AMBIENT)
                    background = BackgroundStyle.AMBIENT
                }
            }
        }
        MenuSpec(getString(R.string.background_video), items)
    }

    /** Opens the system document picker for a video (MP4, WebM…) to loop behind the launcher. */
    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_VIDEO)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No document picker on this device", e)
            toast(R.string.background_no_picker)
        }
    }

    private fun showWallpaperFitMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.wallpaper_fit),
            items = WallpaperFit.entries.map { fit ->
                MenuItem(wallpaperFitName(fit), selected = fit == wallpaperFit) {
                    settings.setWallpaperFit(fit)
                    wallpaperFit = fit
                }
            },
        )
    }

    /** Opens the system document picker for an image to use as the wallpaper. */
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_PICK_WALLPAPER)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No image picker on this device", e)
            toast(R.string.background_no_picker)
        }
    }

    /** Decodes the stored wallpaper off the main thread and publishes it to the UI. */
    private fun loadBackgroundImage() {
        val uri = settings.backgroundImageUri()?.let(Uri::parse)
        if (uri == null) {
            backgroundImage = null
            return
        }
        lifecycleScope.launch {
            backgroundImage = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth > 0 && bounds.outWidth / sample > 2560) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    contentResolver.openInputStream(uri)
                        ?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    private fun showClockMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_clock),
            items = listOf(
                toggleItem(R.string.clock_seconds, clockShowSeconds) {
                    settings.setClockShowSeconds(it); clockShowSeconds = it
                },
                toggleItem(R.string.clock_date, clockShowDate) {
                    settings.setClockShowDate(it); clockShowDate = it
                },
                toggleItem(R.string.clock_greeting, showGreeting) {
                    settings.setShowGreeting(it); showGreeting = it
                },
            ),
        )
    }

    /** A menu row that shows its on/off state on the right and flips it; the menu stays open. */
    private fun toggleItem(@StringRes label: Int, value: Boolean, onToggle: (Boolean) -> Unit): MenuItem =
        MenuItem(
            getString(label),
            value = getString(if (value) R.string.state_on else R.string.state_off),
        ) { onToggle(!value) }

    private fun showAccentMenu() = openMenu {
        val auto = MenuItem(getString(R.string.accent_auto), selected = accentAuto) {
            settings.setAccentAuto(true)
            accentAuto = true
        }
        MenuSpec(
            title = getString(R.string.action_accent),
            items = listOf(auto) + AccentColor.entries.map { colour ->
                MenuItem(
                    accentName(colour),
                    swatch = Color(colour.argb),
                    selected = !accentAuto && colour == accent,
                ) {
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
            closeMenus()
            toast(R.string.widget_none_available)
            return
        }
        openMenu {
            MenuSpec(
                title = getString(R.string.widget_add),
                items = providers.map { provider ->
                    MenuItem(provider.loadLabel(packageManager)) {
                        closeMenus()
                        widgetSlot.add(provider)
                    }
                },
            )
        }
    }

    /** The stored settings of one hosted widget, for showing its current choices. */
    private fun hostedWidget(id: Int) = widgetSlot.hostedForDisplay().firstOrNull { it.id == id }

    private fun showAlignmentMenu(id: Int) = openMenu {
        val current = hostedWidget(id)?.alignment
        fun choice(label: Int, alignment: WidgetAlignment) =
            MenuItem(getString(label), selected = alignment == current) {
                widgetSlot.align(id, alignment)
            }
        MenuSpec(
            title = getString(R.string.widget_align),
            items = listOf(
                choice(R.string.widget_align_start, WidgetAlignment.START),
                choice(R.string.widget_align_center, WidgetAlignment.CENTER),
                choice(R.string.widget_align_end, WidgetAlignment.END),
            ),
        )
    }

    private fun showShapeMenu(id: Int) = openMenu {
        val current = hostedWidget(id)?.shape
        fun choice(label: Int, shape: WidgetShape) =
            MenuItem(getString(label), selected = shape == current) {
                widgetSlot.reshape(id, shape)
            }
        MenuSpec(
            title = getString(R.string.widget_shape),
            items = listOf(
                choice(R.string.widget_shape_wide, WidgetShape.WIDE),
                choice(R.string.widget_shape_panoramic, WidgetShape.PANORAMIC),
                choice(R.string.widget_shape_standard, WidgetShape.STANDARD),
                choice(R.string.widget_shape_square, WidgetShape.SQUARE),
            ),
        )
    }

    private fun showFitMenu(id: Int) = openMenu {
        val current = hostedWidget(id)?.fit
        fun choice(label: Int, fit: WidgetFit) =
            MenuItem(getString(label), selected = fit == current) {
                widgetSlot.refit(id, fit)
            }
        MenuSpec(
            title = getString(R.string.widget_render),
            items = listOf(
                choice(R.string.widget_render_native, WidgetFit.NATIVE),
                choice(R.string.widget_render_fit, WidgetFit.FIT),
            ),
        )
    }

    private fun showHiddenAppsMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_hidden_apps_title),
            items = viewModel.hiddenApps.map { app ->
                MenuItem(app.title) {
                    closeMenus()
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
                else -> openMenu {
                    MenuSpec(
                        title = getString(R.string.update_available_title, release.versionName),
                        items = listOf(
                            MenuItem(getString(R.string.update_install)) {
                                closeMenus()
                                downloadAndInstall(release)
                            },
                            MenuItem(getString(android.R.string.cancel)) { closeMenus() },
                        ),
                    )
                }
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
            if (file == null) {
                toast(R.string.update_download_failed)
                return@launch
            }
            // Parsing the APK reads the whole file: off the main thread.
            val verdict = withContext(Dispatchers.IO) { updateManager.verify(file) }
            if (verdict == UpdateVerdict.OK) {
                updateManager.install(file)
            } else {
                Log.w(TAG, "Update rejected: $verdict")
                file.delete()
                toast(R.string.update_rejected)
            }
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
        const val REQUEST_PICK_WALLPAPER = 2001
        const val REQUEST_PICK_VIDEO = 2002
        const val IDLE_CHECK_MS = 5_000L
    }
}

