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
import alexcmb.mytvlauncher.repository.WallpaperFit
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
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
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
    // Last focused row per menu title, so Back lands on the row that opened the submenu.
    private val menuFocus = mutableMapOf<String, Int>()

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
        menuStack.clear()
        menuFocus.clear()
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
    private var backgroundIntensity by mutableStateOf(SettingsRepository.BG_INTENSITY_DEFAULT)
    private var backgroundImage by mutableStateOf<ImageBitmap?>(null)
    private var wallpaperFit by mutableStateOf(WallpaperFit.FILL)
    // Live value of the background-brightness slider while it's open, else null.
    private var backgroundAdjust by mutableStateOf<Int?>(null)
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
        backgroundIntensity = settings.backgroundIntensity()
        wallpaperFit = settings.wallpaperFit()
        loadBackgroundImage()
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
                        backgroundImage = backgroundImage,
                        backgroundIntensity = backgroundIntensity,
                        wallpaperFit = wallpaperFit,
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
                    // Back pops one level; when the stack empties the menu is gone. A live slider
                    // (widget size, background brightness) hides the menus so the result shows,
                    // and they come back at the same level once it's committed or cancelled.
                    if (resizing == null && backgroundAdjust == null) {
                        menuStack.lastOrNull()?.let { build ->
                            TvMenu(build(), menuFocus) {
                                menuStack.removeAt(menuStack.lastIndex)
                                if (menuStack.isEmpty()) menuFocus.clear()
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
                                resizing = null
                            },
                            onCancel = {
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
                                backgroundAdjust = null
                            },
                            onCancel = {
                                backgroundAdjust = null
                                backgroundIntensity = settings.backgroundIntensity()
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
            REQUEST_PICK_WALLPAPER -> if (granted) onWallpaperPicked(data)
        }
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

    private fun showAppMenu(shortcut: Shortcut) = openMenu {
        MenuSpec(
            title = shortcut.title,
            items = listOf(
                MenuItem(
                    getString(R.string.menu_change_category),
                    value = shortcut.category,
                    submenu = true,
                ) { showCategoryMenu(shortcut) },
                MenuItem(getString(R.string.menu_hide)) {
                    closeMenus()
                    viewModel.hideApp(shortcut)
                },
                MenuItem(getString(R.string.menu_uninstall)) {
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
            } + MenuItem(getString(R.string.category_new)) {
                closeMenus()
                namingCategoryFor = shortcut
            },
        )
    }

    private fun showSettingsMenu() = openMenu {
        // Launcher settings first, then the one-shot actions.
        val items = mutableListOf(
            MenuItem(getString(R.string.action_appearance), submenu = true) { showAppearanceMenu() },
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
            items += MenuItem(getString(R.string.action_now_playing)) { showNowPlayingHelp() }
        }
        items += MenuItem(getString(R.string.action_check_updates), value = installedVersion()) {
            closeMenus()
            checkForUpdates()
        }
        items += MenuItem(getString(R.string.action_android_settings)) {
            closeMenus()
            startAppIntent(Intent(Settings.ACTION_SETTINGS))
        }
        MenuSpec(getString(R.string.action_settings), items)
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

    /** Everything about the widget band, kept off the top-level settings menu. */
    private fun showWidgetsMenu() = openMenu {
        val items = mutableListOf<MenuItem>()
        // The band is capped, so only offer "add" while there's room for another.
        if (widgetSlot.canAddMore()) {
            items += MenuItem(getString(R.string.widget_add), submenu = true) { showWidgetPicker() }
        }
        if (widgetSlot.hasWidgets()) {
            items += MenuItem(getString(R.string.widget_resize)) {
                pickHostedWidget(R.string.widget_resize, ::startResize)
            }
            items += MenuItem(getString(R.string.widget_align), submenu = true) {
                pickHostedWidget(R.string.widget_align, ::showAlignmentMenu)
            }
            items += MenuItem(getString(R.string.widget_shape), submenu = true) {
                pickHostedWidget(R.string.widget_shape, ::showShapeMenu)
            }
            items += MenuItem(getString(R.string.widget_render), submenu = true) {
                pickHostedWidget(R.string.widget_render, ::showFitMenu)
            }
            items += MenuItem(getString(R.string.widget_remove)) {
                pickHostedWidget(R.string.widget_remove) { closeMenus(); widgetSlot.remove(it) }
            }
        }
        MenuSpec(getString(R.string.action_widgets), items)
    }

    private fun showAppearanceMenu() = openMenu {
        MenuSpec(
            title = getString(R.string.action_appearance),
            items = listOf(
                MenuItem(
                    getString(R.string.action_accent),
                    value = if (accentAuto) getString(R.string.accent_auto_short) else accentName(accent),
                    submenu = true,
                ) { showAccentMenu() },
                MenuItem(getString(R.string.action_clock), submenu = true) { showClockMenu() },
                MenuItem(
                    getString(R.string.action_background),
                    value = backgroundStyleName(background),
                    submenu = true,
                ) { showBackgroundMenu() },
                MenuItem(
                    getString(R.string.action_card_size),
                    value = cardSizeName(cardSize),
                    submenu = true,
                ) { showCardSizeMenu() },
                MenuItem(getString(R.string.action_display), submenu = true) { showDisplayMenu() },
            ),
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
                    getString(R.string.background_brightness),
                    value = "$backgroundIntensity %",
                ) { backgroundAdjust = settings.backgroundIntensity() },
            ),
        )
    }

    private fun showBackgroundStyleMenu() = openMenu {
        // "Image" is only a style once there's an image to show.
        val styles = listOf(
            BackgroundStyle.AMBIENT,
            BackgroundStyle.ACCENT_GRADIENT,
            BackgroundStyle.SOLID,
            BackgroundStyle.WALLPAPER,
        ).filter { it != BackgroundStyle.WALLPAPER || settings.backgroundImageUri() != null }
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
            items += MenuItem(getString(R.string.background_image_remove)) {
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
                    MenuItem(provider.loadLabel(packageManager).toString()) {
                        closeMenus()
                        widgetSlot.add(provider)
                    }
                },
            )
        }
    }

    /** Picks which hosted widget to act on, skipping the question when there's only one. */
    private fun pickHostedWidget(@StringRes title: Int, action: (Int) -> Unit) {
        val hosted = widgetSlot.hostedWidgets()
        when (hosted.size) {
            0 -> closeMenus()
            1 -> action(hosted.first().first)
            else -> openMenu {
                MenuSpec(
                    title = getString(title),
                    items = widgetSlot.hostedWidgets().map { (id, label) ->
                        MenuItem(label.toString()) { action(id) }
                    },
                )
            }
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
        const val REQUEST_PICK_WALLPAPER = 2001
    }
}

