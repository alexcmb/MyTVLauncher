@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.media.NowPlaying
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.CardSize
import alexcmb.mytvlauncher.repository.WallpaperFit
import alexcmb.mytvlauncher.source.TvSource
import alexcmb.mytvlauncher.widget.WidgetAlignment
import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val Background = Color(0xFF0E0E12)

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Mixes [tint] into [base] by [fraction]; result is always fully opaque. */
private fun blendColor(base: Color, tint: Color, fraction: Float): Color = Color(
    red = base.red + (tint.red - base.red) * fraction,
    green = base.green + (tint.green - base.green) * fraction,
    blue = base.blue + (tint.blue - base.blue) * fraction,
    alpha = 1f,
)
private val Muted = Color(0xFF9AA0B4)
private val SourceSurface = Color(0xFF1C1C26)
private val SourceSurfaceFocused = Color(0xFF2A2A38)
private const val FAVOURITES = 8

/**
 * A widget to place on the hub: its tile footprint, the size its host view is laid out at
 * (equal in NATIVE fit, larger in FIT so it can be scaled down), where it sits, a factory,
 * and a hook that tells the (kept-alive) view its current size — that's what lets a live
 * resize swap the widget's layout variants without rebuilding the view each step.
 */
data class WidgetTile(
    val id: Int,
    val widthDp: Int,
    val heightDp: Int,
    val layoutWidthDp: Int,
    val layoutHeightDp: Int,
    val alignment: WidgetAlignment,
    val createView: () -> View,
    val applySize: (View) -> Unit,
)

/** Title-bar clock preferences; the live values are derived inside the bar. */
data class ClockOptions(val seconds: Boolean, val date: Boolean, val greeting: Boolean)

/** The derived clock values shown on the left of the title bar. */
private data class ClockValues(val time: String, val date: String?, val greeting: String?)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    tabs: List<HomeTab>,
    widgets: List<WidgetTile>,
    clock: ClockOptions,
    background: BackgroundStyle,
    backgroundImage: ImageBitmap?,
    backgroundIntensity: Int,
    wallpaperFit: WallpaperFit,
    cardSize: CardSize,
    accentAuto: Boolean,
    showUsageCount: Boolean,
    showAppLabels: Boolean,
    nowPlaying: NowPlaying?,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onGridLongPress: (Shortcut) -> Unit,
    onWidgetLongPress: (Int) -> Unit,
    onOpenSource: (TvSource) -> Unit,
    onPlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSettings: () -> Unit,
    focusTracker: FocusTracker,
    startupTab: String,
    homeRequests: Int,
    pinnedApps: List<String>,
) {
    // The selection is held by key, not position, so a tab moved, hidden or added in front
    // doesn't silently switch to another one. A key that's gone (hidden) falls back to the hub.
    var selectedKey by remember { mutableStateOf(startupTab) }
    val selectedTab = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    var focused by remember { mutableStateOf<Shortcut?>(null) }
    val tab = tabs.getOrNull(selectedTab)
    // DPAD-up from the content lands back on the selected tab. The tabs are centred, so
    // spatial focus search would drift to the settings orb instead — this wires it directly.
    val tabsFocus = remember { FocusRequester() }
    // The Home chip, so Back from another tab's chips can return to it.
    val homeTabFocus = remember { FocusRequester() }
    var topBarFocused by remember { mutableStateOf(false) }

    // HOME pressed while the launcher is up (or to come back to it): the startup tab, with the
    // focus on its chip — the card that had it may belong to a tab that's no longer shown.
    LaunchedEffect(homeRequests) {
        if (homeRequests == 0) return@LaunchedEffect
        selectedKey = startupTab
        focused = null
        withFrameNanos { } // let the chip for the new selection take tabsFocus
        runCatching { tabsFocus.requestFocus() }
    }

    // Back walks up the screen: content -> the tabs -> the Home tab. On Home's tabs there's
    // nowhere left to go (a launcher must not exit). Menus and sliders register their own
    // handler later, so they still take Back first while they're open.
    BackHandler {
        when {
            !topBarFocused -> runCatching { tabsFocus.requestFocus() }
            selectedTab != 0 -> runCatching { homeTabFocus.requestFocus() }
        }
    }

    // Focus memory. Each list remembers its last focused card above the list itself, so it
    // survives the tab switch that disposes the list; scroll positions are kept the same way.
    val listMemory = remember { mutableMapOf<String, FocusMemory>() }
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val favouritesState = remember { LazyListState() }

    // The accent normally comes from the activity's fixed choice. In "auto" mode it tracks
    // the focused app's banner; re-provided here since only this screen knows the focus.
    val base = LocalAccent.current
    val accent = if (accentAuto) rememberAutoAccent(focused, base) else base

    CompositionLocalProvider(LocalAccent provides accent, LocalFocusTracker provides focusTracker) {
    MaterialTheme {
        // One intensity slider drives every mode: it deepens the SOLID tint, strengthens the
        // gradient wash, and brightens the wallpaper (less dim). A dark base always carries the
        // accent hue so even "solid" reads as coloured, not flat black.
        val intensity = (backgroundIntensity / 100f).coerceIn(0f, 1f)
        val accentColor = LocalAccent.current
        val base = if (background == BackgroundStyle.SOLID) {
            blendColor(Background, accentColor, lerpF(0.04f, 0.24f, intensity))
        } else {
            Background
        }
        Box(Modifier.fillMaxSize().background(base)) {
            // Background decoration, behind the content.
            when (background) {
                BackgroundStyle.AMBIENT -> AmbientBackdrop(focused, intensity)
                BackgroundStyle.ACCENT_GRADIENT -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = lerpF(0.18f, 0.65f, intensity)), Background)
                        )
                    )
                )
                BackgroundStyle.WALLPAPER -> if (backgroundImage != null) {
                    val scale = when (wallpaperFit) {
                        WallpaperFit.FILL -> ContentScale.Crop
                        WallpaperFit.STRETCH -> ContentScale.FillBounds
                        WallpaperFit.FIT -> ContentScale.Fit
                        WallpaperFit.CENTER -> ContentScale.None
                        WallpaperFit.SPAN -> ContentScale.FillWidth
                    }
                    Image(
                        bitmap = backgroundImage,
                        contentDescription = null,
                        contentScale = scale,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Brightness: more intensity → thinner scrim → brighter wallpaper.
                    Box(Modifier.fillMaxSize().background(Background.copy(alpha = lerpF(0.8f, 0.2f, intensity))))
                }
                BackgroundStyle.SOLID -> Unit // the tinted base is the whole look
            }
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    tabs, selectedTab, clock, tabsFocus, homeTabFocus, onSettings,
                    Modifier.onFocusChanged { topBarFocused = it.hasFocus },
                ) { selectedKey = tabs[it].key; focused = null }
                // The hero band reserves room for a focused app's name on the grid tabs. The hub
                // has none: it names the focused favourite right above its row instead, in a
                // header of fixed height, so its centred content never jumps as focus moves.
                val heroHeight by animateDpAsState(
                    targetValue = if (selectedTab == 0) 0.dp else 64.dp,
                    label = "hero",
                )
                Hero(focused, (tab as? AppsTab)?.shortcuts.orEmpty(), showUsageCount, heroHeight)
                when {
                    tab == null -> Unit
                    // The first tab is a hub: widgets and a few favourites, nothing endless.
                    selectedTab == 0 && tab is AppsTab -> Hub(
                        widgets = widgets,
                        favourites = Favourites.pick(tab.shortcuts, pinnedApps, FAVOURITES),
                        pinned = pinnedApps.toSet(),
                        cardSize = cardSize,
                        tabsFocus = tabsFocus,
                        showAppLabels = showAppLabels,
                        nowPlaying = nowPlaying,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onWidgetLongPress = onWidgetLongPress,
                        onPlayPause = onPlayPause,
                        onOpenNowPlaying = onOpenNowPlaying,
                        onFocus = { focused = it },
                        onWidgetsFocused = { focused = null },
                        favouritesState = favouritesState,
                        favouritesMemory = listMemory.getOrPut(tab.title) { FocusMemory() },
                        focused = focused,
                        allApps = tab.shortcuts,
                        showUsageCount = showUsageCount,
                    )
                    tab is SourcesTab -> SourcesGrid(tab.sources, cardSize, tabsFocus, onOpenSource)
                    tab is AppsTab -> AppsGrid(
                        tab.shortcuts, cardSize, tabsFocus, showAppLabels, onLaunch, onGridLongPress,
                        state = gridStates.getOrPut(tab.title) { LazyGridState() },
                        memory = listMemory.getOrPut(tab.title) { FocusMemory() },
                    ) { focused = it }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopBar(
    tabs: List<HomeTab>,
    selected: Int,
    clock: ClockOptions,
    tabsFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    onSettings: () -> Unit,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    // Each part is anchored on its own: settings hard left, clock hard right, tabs on the
    // true screen centre. Laying these out in a row with weighted spacers instead centres
    // the tabs *between* the orb and the clock, and since those two aren't the same width
    // the tabs end up off-centre by half the difference.
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp)) {
        SettingsOrb(onSettings, Modifier.align(Alignment.CenterStart).trackFocus())
        // A hand-rolled row rather than tv-material's TabRow: its sliding indicator
        // animation hitched on the tab change.
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    // The selected chip is the up-target from the content below.
                    // The Home chip also answers to homeTabFocus (Back from another tab).
                    val chipModifier = Modifier
                        .then(if (index == selected) Modifier.focusRequester(tabsFocus) else Modifier)
                        .then(if (index == 0) Modifier.focusRequester(homeTabFocus) else Modifier)
                        .trackFocus()
                    TabChip(tab.title, selected = index == selected, modifier = chipModifier) {
                        onSelect(index)
                    }
                }
            }
        }
        // Derived here so the once-a-second tick only recomposes the bar, not the screen.
        val values = rememberClockValues(clock)
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
        ) {
            values.greeting?.let { Text(it, color = Muted, fontSize = 12.sp) }
            Text(values.time, color = Color.White, fontSize = 20.sp)
            values.date?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
    }
}

/** One tab: focus selects it, the selected one wears the accent. No slide animation. */
@Composable
private fun TabChip(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        selected -> LocalAccent.current
        focused -> Color.White.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    Text(
        text = title,
        color = if (selected) Color.White else Muted,
        fontSize = 13.sp,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable { onSelect() }
            .background(background, RoundedCornerShape(99.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SettingsOrb(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSettings() }
            .background(if (focused) LocalAccent.current else Color.Transparent, CircleShape)
            .padding(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = stringResource(R.string.action_settings),
            tint = if (focused) Color.White else Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Surfaces the open count — data the launcher has always collected and never shown. */
@Composable
private fun Hero(focused: Shortcut?, inTab: List<Shortcut>, showUsageCount: Boolean, height: Dp) {
    Column(Modifier.fillMaxWidth().height(height).padding(start = 48.dp)) {
        if (focused != null) HeroText(focused, inTab, showUsageCount)
    }
}

/** The focused app's name and, if enabled, how much it's used. */
@Composable
private fun HeroText(focused: Shortcut, inTab: List<Shortcut>, showUsageCount: Boolean) {
    Column {
        Text(focused.title, color = Color.White, fontSize = 22.sp)
        if (showUsageCount) {
            // The usage rank, whatever order the grid is sorted in.
            val rank = inTab.sortedByDescending { it.openCount }.indexOf(focused) + 1
            val opens = focused.openCount
            Text(
                text = when {
                    opens == 0 -> stringResource(R.string.home_never_opened)
                    rank == 1 -> stringResource(R.string.home_opens_top, opens)
                    else -> stringResource(R.string.home_opens_ranked, opens, rank)
                },
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Hub(
    widgets: List<WidgetTile>,
    favourites: List<Shortcut>,
    pinned: Set<String>,
    cardSize: CardSize,
    tabsFocus: FocusRequester,
    showAppLabels: Boolean,
    nowPlaying: NowPlaying?,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onWidgetLongPress: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onFocus: (Shortcut) -> Unit,
    onWidgetsFocused: () -> Unit,
    favouritesState: LazyListState,
    favouritesMemory: FocusMemory,
    focused: Shortcut?,
    allApps: List<Shortcut>,
    showUsageCount: Boolean,
) {
    // As focus moves down to the apps, the widgets slide up and fade and the app cards
    // grow. Driven by graphicsLayer only — the widgets stay in the layout and focusable,
    // so moving back up returns to them and expands the band again.
    var appsFocused by remember { mutableStateOf(false) }
    // Which widget holds focus, if any — drives the spotlight that dims everything else.
    var focusedWidget by remember { mutableStateOf<Int?>(null) }
    // The now-playing card sits above the widgets; track its focus so DPAD-up chains correctly.
    var nowPlayingFocused by remember { mutableStateOf(false) }
    val nowPlayingFocus = remember { FocusRequester() }
    val collapse by animateFloatAsState(
        targetValue = if (appsFocused && widgets.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "collapse",
    )
    val spotlight by animateFloatAsState(
        targetValue = if (focusedWidget != null) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "spotlight",
    )
    // Heights of what sits above the favourites (now-playing card, widget band), as laid
    // out. When the focus goes down to the favourites those fade and slide away, and the row
    // rises by half their height: the hub is centred, so that puts the row in the middle.
    var nowPlayingHeight by remember { mutableIntStateOf(0) }
    var bandHeight by remember { mutableIntStateOf(0) }
    val abovePx = (if (nowPlaying != null) nowPlayingHeight else 0) +
        (if (widgets.isNotEmpty()) bandHeight else 0)
    // Slimmer favourites at rest so the shorter (16:9) row keeps clear of the screen's bottom
    // edge, which TV overscan can eat into. They grow back to full size as the band collapses
    // and the row rises into the freed space, where there's plenty of room.
    val restFavouriteWidth = (cardSize.favouriteWidthDp - 40).dp
    val fullFavouriteWidth = (cardSize.favouriteWidthDp + 60).dp
    // With no widgets the row is the whole hub: full-size cards from the start.
    val cardWidth = if (widgets.isEmpty()) fullFavouriteWidth
    else lerp(restFavouriteWidth, fullFavouriteWidth, collapse)

    Column(
        Modifier
            // The hub's rows sit as one block, centred in the space under the tabs: with few
            // rows (no widgets) it no longer clings to the top and leaves the screen empty.
            .fillMaxSize()
            // No horizontal padding on the group itself: the scrollable favourites row needs
            // its clip bounds out at the screen edge so a focused card can scale up without
            // being clipped. The 48dp margin is applied per child instead.
            .focusGroup()
            // DPAD-up walks one level up the hub: favourites → widgets → now-playing → tabs.
            // The now-playing card and the tabs are reached by an explicit request (the tabs
            // are centred, so plain spatial search would drift to the settings orb). A previewed
            // key so it wins even over an embedded widget view that might swallow the press.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
                    return@onPreviewKeyEvent false
                }
                val target = when {
                    nowPlayingFocused -> tabsFocus
                    focusedWidget != null -> if (nowPlaying != null) nowPlayingFocus else tabsFocus
                    widgets.isEmpty() -> if (nowPlaying != null) nowPlayingFocus else tabsFocus
                    else -> null // favourites with widgets above: let spatial nav reach them
                }
                if (target != null) {
                    target.requestFocus()
                    true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.Center,
    ) {
        if (nowPlaying != null) {
            NowPlayingCard(
                nowPlaying = nowPlaying,
                focusRequester = nowPlayingFocus,
                onPlayPause = onPlayPause,
                onOpen = onOpenNowPlaying,
                onFocusChanged = { focused ->
                    nowPlayingFocused = focused
                    if (focused) {
                        appsFocused = false
                        // Not an app: drop the app backdrop/hero back to their idle state.
                        onWidgetsFocused()
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .onSizeChanged { nowPlayingHeight = it.height }
                    .padding(bottom = 12.dp)
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationY = -collapse * abovePx
                    },
            )
        }
        if (widgets.isNotEmpty()) {
            val startTiles = widgets.filter { it.alignment == WidgetAlignment.START }
            val centerTiles = widgets.filter { it.alignment == WidgetAlignment.CENTER }
            val endTiles = widgets.filter { it.alignment == WidgetAlignment.END }
            WidgetBand(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { bandHeight = it.height }
                    .onFocusChanged {
                        if (it.hasFocus) {
                            appsFocused = false
                            // Widgets aren't apps: drop the app backdrop back to black.
                            onWidgetsFocused()
                        } else {
                            focusedWidget = null
                        }
                    }
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationY = -collapse * abovePx
                    }
                    .padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
            ) {
                // Always three zones, in this order (an empty one takes no room): see WidgetBand.
                val onFocused = { id: Int -> focusedWidget = id }
                WidgetZone(startTiles, spotlight, focusedWidget, onFocused, onWidgetLongPress)
                WidgetZone(centerTiles, spotlight, focusedWidget, onFocused, onWidgetLongPress)
                WidgetZone(endTiles, spotlight, focusedWidget, onFocused, onWidgetLongPress)
            }
        }
        // Pinning or moving a favourite reorders the row under the user's feet: keep the card
        // they were on in view, or it would be scrolled off — and the focus, sent back to it
        // when the menu closes, would land nowhere.
        val order = favourites.map { it.id }
        LaunchedEffect(order) {
            val index = order.indexOf(favouritesMemory.lastId)
            if (index < 0) return@LaunchedEffect
            val layout = favouritesState.layoutInfo
            val shown = layout.visibleItemsInfo.any {
                it.index == index && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset
            }
            if (!shown) favouritesState.scrollToItem(index)
        }
        if (favourites.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) appsFocused = true }
                    // Rise into the space the widgets vacate; dim under the widget spotlight.
                    .graphicsLayer {
                        translationY = -collapse * abovePx / 2f
                        alpha = 1f - 0.75f * spotlight
                    },
            ) {
                // The row's header: the focused favourite's name (the hub has no hero band), else
                // the row's title — "Favourites" once something is pinned, until then honestly
                // just the most used. A fixed height, so the centred hub never shifts.
                val pinnedAny = favourites.any { it.id in pinned }
                Box(
                    modifier = Modifier.height(56.dp).padding(start = 48.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (focused != null && focused in favourites) {
                        HeroText(focused, allApps, showUsageCount)
                    } else {
                        Text(
                            text = stringResource(if (pinnedAny) R.string.home_favourites else R.string.home_most_used),
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                // contentPadding, not an outer margin, so the row clips at the screen edge and
                // the end cards can scale up on focus without their border being truncated. The
                // vertical padding must clear the focused card's 1.08 scale plus its 2dp border,
                // or the row clips the top/bottom edges and the card loses its rounded corners.
                LazyRow(
                    state = favouritesState,
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 12.dp),
                    // Centred when the cards fit (few apps); from the left edge once it scrolls.
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().rememberFocus(favouritesMemory),
                ) {
                    items(favourites, key = { it.id }) { shortcut ->
                        AppCard(
                            shortcut, showAppLabels, onLaunch, onLongPress,
                            { favouritesMemory.lastId = it.id; onFocus(it) },
                            Modifier.width(cardWidth).focusTarget(favouritesMemory, shortcut.id).trackFocus(favouritesMemory),
                            pinned = shortcut.id in pinned,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The widget band. Its three zones (START, CENTER, END — always given in that order) line up
 * with the rest of the hub: START on the content's left edge, the same as the favourites'
 * first card, END on its right edge, CENTER on the screen's centre — nudged aside only if it
 * would run into a side zone. Zones are measured unbounded so a widget keeps its true size,
 * and are centred on each other vertically, so a short widget sits level with a tall one.
 */
@Composable
private fun WidgetBand(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 16.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0)
        val (start, center, end) = measurables.map { it.measure(loose) }
        val width = constraints.maxWidth
        val height = maxOf(start.height, center.height, end.height)
        val centreX = ((width - center.width) / 2)
            .coerceAtLeast(if (start.width > 0) start.width + gap else 0)
            .coerceAtMost(width - center.width - if (end.width > 0) end.width + gap else 0)
        layout(width, height) {
            start.placeRelative(0, (height - start.height) / 2)
            center.placeRelative(centreX, (height - center.height) / 2)
            end.placeRelative(width - end.width, (height - end.height) / 2)
        }
    }
}

/** One placement zone of the band. Its widgets sit together; the spotlight dims the rest. */
@Composable
private fun WidgetZone(
    tiles: List<WidgetTile>,
    spotlight: Float,
    focusedWidget: Int?,
    onFocused: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.forEach { tile ->
            // Keyed by id only: the host view survives size and shape changes and is resized
            // in place (applySize tells it each new size), which keeps a live slider resize
            // smooth and lets the widget swap its layout variants as it grows.
            key(tile.id) {
                // Spotlight: the focused widget stays lit, the others dim.
                val lit = focusedWidget == tile.id
                val tileAlpha = if (lit) 1f else 1f - 0.7f * spotlight
                // Long press: OK held down. The focus sits inside the widget's own views, so
                // watch the previewed key events here. The first press goes through untouched
                // (a short OK still clicks the widget's button); once the key auto-repeats it's
                // a long press — open this widget's menu and swallow the rest of the press,
                // including its release, so the widget never sees a click.
                var held by remember { mutableStateOf(false) }
                val tracker = LocalFocusTracker.current
                val viewFocus = remember { FocusRequester() }
                Box(
                    modifier = Modifier
                        .size(tile.widthDp.dp, tile.heightDp.dp)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                onFocused(tile.id)
                                tracker?.last = viewFocus
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            val ok = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                            when {
                                !ok -> false
                                event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount > 0 -> {
                                    if (!held) {
                                        held = true
                                        onLongPress(tile.id)
                                    }
                                    true
                                }
                                event.type == KeyEventType.KeyUp && held -> {
                                    held = false
                                    true
                                }
                                else -> false
                            }
                        }
                        .graphicsLayer { alpha = tileAlpha }
                        // Clip: some widgets draw past the size they're handed.
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    // The host view is laid out at its layout size and scaled to the tile: in
                    // NATIVE fit those are equal (scale 1, the widget owns the size and may
                    // clip); in FIT the layout is the larger base size scaled down so it shows
                    // whole.
                    val sx = tile.widthDp.toFloat() / tile.layoutWidthDp
                    val sy = tile.heightDp.toFloat() / tile.layoutHeightDp
                    AndroidView(
                        factory = { tile.createView() },
                        update = { tile.applySize(it) },
                        modifier = Modifier
                            .focusRequester(viewFocus)
                            .requiredSize(tile.layoutWidthDp.dp, tile.layoutHeightDp.dp)
                            .graphicsLayer { scaleX = sx; scaleY = sy },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsGrid(
    shortcuts: List<Shortcut>,
    cardSize: CardSize,
    tabsFocus: FocusRequester,
    showAppLabels: Boolean,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    state: LazyGridState,
    memory: FocusMemory,
    onFocus: (Shortcut) -> Unit,
) {
    var focusedIndex by remember { mutableStateOf(-1) }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(cardSize.columns),
        // Top padding so the top row can scale up on focus without its border being clipped.
        contentPadding = PaddingValues(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // DPAD-up from the top row goes to the tabs, not the settings orb; deeper rows keep
        // their natural up navigation within the grid.
        modifier = Modifier
            .rememberFocus(memory)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp &&
                    focusedIndex in 0 until cardSize.columns
                ) {
                    tabsFocus.requestFocus()
                    true
                } else {
                    false
                }
            },
    ) {
        itemsIndexed(shortcuts, key = { _, shortcut -> shortcut.id }) { index, shortcut ->
            AppCard(
                shortcut, showAppLabels, onLaunch, onLongPress,
                { focusedIndex = index; memory.lastId = it.id; onFocus(it) },
                Modifier.focusTarget(memory, shortcut.id).trackFocus(memory),
            )
        }
    }
}

/** The TV's external inputs (HDMI, AV, …); tapping one switches the TV to it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourcesGrid(
    sources: List<TvSource>,
    cardSize: CardSize,
    tabsFocus: FocusRequester,
    onOpen: (TvSource) -> Unit,
) {
    var focusedIndex by remember { mutableStateOf(-1) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cardSize.columns),
        contentPadding = PaddingValues(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp &&
                    focusedIndex in 0 until cardSize.columns
                ) {
                    tabsFocus.requestFocus()
                    true
                } else {
                    false
                }
            },
    ) {
        itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
            SourceCard(source, onOpen) { focusedIndex = index }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceCard(source: TvSource, onOpen: (TvSource) -> Unit, onFocus: () -> Unit) {
    Card(
        onClick = { onOpen(source) },
        scale = CardDefaults.scale(focusedScale = 1.08f),
        colors = CardDefaults.colors(
            containerColor = SourceSurface,
            focusedContainerColor = SourceSurfaceFocused,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, LocalAccent.current))
        ),
        modifier = Modifier
            .trackFocus()
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        val iconTint = when {
            source.isConnected -> Color.White
            source.isAvailable -> Color.White.copy(alpha = 0.85f)
            else -> Muted
        }
        Box(
            Modifier.fillMaxSize().alpha(if (source.isAvailable) 1f else 0.55f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (source.isHdmi) R.drawable.ic_source_hdmi else R.drawable.ic_source_av
                ),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(40.dp),
            )
            if (source.isConnected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .background(LocalAccent.current, CircleShape),
                )
            }
            Text(
                text = source.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}

/**
 * The TV's active media session on the hub: album art, title, artist, and its play state.
 * OK toggles play/pause; a long press opens the app that owns the session.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NowPlayingCard(
    nowPlaying: NowPlaying,
    focusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onPlayPause,
        onLongClick = onOpen,
        scale = CardDefaults.scale(focusedScale = 1.04f),
        colors = CardDefaults.colors(
            containerColor = SourceSurface,
            focusedContainerColor = SourceSurfaceFocused,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, LocalAccent.current))
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .trackFocus()
            .onFocusChanged { onFocusChanged(it.isFocused) },
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Background),
                contentAlignment = Alignment.Center,
            ) {
                val art = nowPlaying.art
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // Wraps its text so the card is only as wide as it needs, capped so a very long
            // title ellipsizes instead of stretching the card across the screen.
            Column(modifier = Modifier.widthIn(max = 360.dp)) {
                Text(
                    text = stringResource(R.string.now_playing).uppercase(),
                    color = LocalAccent.current,
                    fontSize = 9.sp,
                )
                Text(
                    text = nowPlaying.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowPlaying.subtitle?.let {
                    Text(
                        text = it,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(
                    if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The focused app's banner as hero art in the top-right corner, fading into the background
 * towards the left (so the hero title over there stays clean) and towards the bottom (no hard
 * edge). Drawn smaller than full-screen so a low-resolution banner doesn't blow up into a blur.
 * [intensity] (the background-brightness setting) sets how strongly it shows. No blur:
 * RenderEffect is API 31+.
 */
@Composable
private fun AmbientBackdrop(focused: Shortcut?, intensity: Float) {
    Crossfade(targetState = focused?.banner, label = "backdrop") { banner ->
        if (banner != null) {
            val bitmap = remember(banner) { banner.toBitmap().asImageBitmap() }
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth(0.62f)
                        .fillMaxHeight(0.62f),
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = lerpF(0.25f, 0.8f, intensity),
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Fade the art out into the plain background on its left and bottom edges.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Background,
                                0.45f to Background.copy(alpha = 0f),
                            )
                        )
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.35f to Background.copy(alpha = 0f),
                                1f to Background,
                            )
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppCard(
    shortcut: Shortcut,
    showLabel: Boolean,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onFocus: (Shortcut) -> Unit,
    modifier: Modifier,
    pinned: Boolean = false,
) {
    Card(
        onClick = { onLaunch(shortcut) },
        onLongClick = { onLongPress(shortcut) },
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, LocalAccent.current))
        ),
        modifier = modifier
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus(shortcut) },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val banner = shortcut.banner
            if (banner != null) {
                DrawableImage(banner, Modifier.fillMaxSize(), ContentScale.Crop)
                // Optional name over the banner, with a scrim across the bottom for legibility.
                if (showLabel) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.7f),
                            )
                        )
                    )
                    CardLabel(shortcut.title)
                }
            } else {
                // No banner: the icon needs its name regardless of the setting.
                shortcut.icon?.let { DrawableImage(it, Modifier.size(64.dp), ContentScale.Fit) }
                CardLabel(shortcut.title)
            }
            if (pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = stringResource(R.string.menu_pin),
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .padding(4.dp)
                        .size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CardLabel(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
    )
}

/** The live clock, re-aligned on the whole second so it doesn't drift. */
@Composable
private fun rememberClockValues(options: ClockOptions): ClockValues {
    val context = LocalContext.current
    // From Compose, not Locale.getDefault(): the clock then follows a change of the TV's
    // language without a restart.
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(context, options.seconds, locale) {
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val skeleton = (if (is24) "Hm" else "hm") + (if (options.seconds) "s" else "")
        SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale
        )
    }
    val dateFormat = remember(locale) {
        SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale
        )
    }
    val now by produceState(initialValue = Date()) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = Date()
        }
    }
    return ClockValues(
        time = timeFormat.format(now),
        date = if (options.date) dateFormat.format(now).replaceFirstChar { it.uppercase(locale) } else null,
        greeting = if (options.greeting) stringResource(greetingRes(now)) else null,
    )
}

private fun greetingRes(now: Date): Int {
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
}

/**
 * The "auto" accent: a colour pulled from the focused app's banner (or icon), eased in so
 * moving between apps glides rather than snaps. Falls back to [fallback] — the fixed base
 * accent — while nothing is focused or a banner yields no usable colour.
 */
// The producer does assign `value` (last line); the lint check still reports it — a false
// positive — so it's silenced for this function only, not for the whole project.
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun rememberAutoAccent(focused: Shortcut?, fallback: Color): Color {
    val target by produceState(fallback, focused, fallback) {
        val art = focused?.banner ?: focused?.icon
        val colour = if (art != null) paletteAccent(art) else null
        value = colour ?: fallback
    }
    val accent by animateColorAsState(target, tween(durationMillis = 300), label = "accent")
    return accent
}

/** Palette runs off the main thread; it down-samples internally, so this stays cheap. */
private suspend fun paletteAccent(drawable: Drawable): Color? = withContext(Dispatchers.Default) {
    runCatching {
        val palette = Palette.Builder(drawable.toBitmap()).resizeBitmapArea(64 * 64).generate()
        (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch)
            ?.let { Color(it.rgb) }
    }.getOrNull()
}

/**
 * App artwork arrives as Drawables from the PackageManager, not as Compose painters.
 * Cached per drawable: converting on every recomposition made scrolling — and, with it,
 * remote clicks — feel sluggish on a modest box.
 */
@Composable
private fun DrawableImage(drawable: Drawable, modifier: Modifier, scale: ContentScale) {
    val bitmap = remember(drawable) { drawable.toBitmap().asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = scale,
        modifier = modifier,
    )
}

/**
 * The card a list last had the focus on, kept above the list so it outlives it (a tab switch
 * disposes the list). [lastId] is state, but each card only watches whether it is the one
 * (derivedStateOf), so a focus move recomposes two cards, not the whole list.
 */
private class FocusMemory {
    var lastId by mutableStateOf<String?>(null)
    val requester = FocusRequester()
    var attached = false
}

/**
 * Focus entering this list with the DPAD (from the tabs, or from the row above) goes back to
 * its last focused card instead of the spatially nearest one. A programmatic request for a
 * particular card (FocusDirection.Enter) is left alone — redirecting that too is what made
 * putting the focus back after a menu land nowhere.
 */
private fun Modifier.rememberFocus(memory: FocusMemory): Modifier = this
    .focusProperties {
        enter = { direction ->
            if (direction != FocusDirection.Enter && memory.attached) memory.requester
            else FocusRequester.Default
        }
    }
    .focusGroup()

/** Tags the card [memory] remembers, so the list can send the focus back to it. */
@Composable
private fun Modifier.focusTarget(memory: FocusMemory, id: String): Modifier {
    val isTarget by remember(memory, id) { derivedStateOf { memory.lastId == id } }
    if (!isTarget) return this
    DisposableEffect(memory) {
        memory.attached = true
        onDispose { memory.attached = false }
    }
    return this.focusRequester(memory.requester)
}

/**
 * The last element of the home screen that had the focus. Opening a menu, slider or prompt
 * moves the focus away, and once it closes Compose parks the focus on the first focusable
 * (the settings orb) — so the activity calls [restore] just before closing it.
 */
class FocusTracker {
    internal var last: FocusRequester? = null

    /**
     * Moves the focus back to the last focused element (a no-op if it has gone). Call it
     * while the overlay still holds the focus, before it goes away.
     */
    fun restore() {
        runCatching { last?.requestFocus() }
    }
}

private val LocalFocusTracker = staticCompositionLocalOf<FocusTracker?> { null }

/** Records this element in the [FocusTracker] whenever it takes the focus. */
@Composable
private fun Modifier.trackFocus(): Modifier {
    val tracker = LocalFocusTracker.current ?: return this
    val requester = remember { FocusRequester() }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) tracker.last = requester }
}

/**
 * The same for a card of a list with a [FocusMemory]: the tracker keeps the list's requester,
 * which follows the remembered app rather than this particular card. A card that moves (a
 * favourite pinned or reordered from its menu) is recreated by the lazy list, and a requester
 * of its own would go with the old one — the focus would then fall back to the settings orb.
 */
@Composable
private fun Modifier.trackFocus(memory: FocusMemory): Modifier {
    val tracker = LocalFocusTracker.current ?: return this
    return this.onFocusChanged { if (it.isFocused) tracker.last = memory.requester }
}
