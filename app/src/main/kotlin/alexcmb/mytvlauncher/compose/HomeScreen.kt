package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.media.NowPlaying
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.CardSize
import alexcmb.mytvlauncher.source.TvSource
import alexcmb.mytvlauncher.widget.WidgetAlignment
import android.graphics.drawable.Drawable
import android.view.View
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    tabs: List<HomeTab>,
    widgets: List<WidgetTile>,
    clock: ClockOptions,
    background: BackgroundStyle,
    cardSize: CardSize,
    accentAuto: Boolean,
    showUsageCount: Boolean,
    showAppLabels: Boolean,
    nowPlaying: NowPlaying?,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onOpenSource: (TvSource) -> Unit,
    onPlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSettings: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf<Shortcut?>(null) }
    val tab = tabs.getOrNull(selectedTab)
    // DPAD-up from the content lands back on the selected tab. The tabs are centred, so
    // spatial focus search would drift to the settings orb instead — this wires it directly.
    val tabsFocus = remember { FocusRequester() }

    // The accent normally comes from the activity's fixed choice. In "auto" mode it tracks
    // the focused app's banner; re-provided here since only this screen knows the focus.
    val base = LocalAccent.current
    val accent = if (accentAuto) rememberAutoAccent(focused, base) else base

    CompositionLocalProvider(LocalAccent provides accent) {
    MaterialTheme {
        val rootBackground = if (background == BackgroundStyle.ACCENT_GRADIENT) {
            Modifier.background(
                Brush.verticalGradient(listOf(LocalAccent.current.copy(alpha = 0.35f), Background))
            )
        } else {
            Modifier.background(Background)
        }
        Box(Modifier.fillMaxSize().then(rootBackground)) {
            if (background == BackgroundStyle.AMBIENT) AmbientBackdrop(focused)
            Column(Modifier.fillMaxSize()) {
                TopBar(tabs, selectedTab, clock, tabsFocus, onSettings) { selectedTab = it; focused = null }
                // The hero band reserves room for a focused app's name. On the hub its top rows
                // (now-playing, widgets) focus no app, so collapse it there to hand that space to
                // the content — otherwise a now-playing card plus this empty band push the
                // favourites row off the bottom. Other tabs keep it fixed to avoid a jump.
                val heroHeight by animateDpAsState(
                    targetValue = if (selectedTab == 0 && focused == null) 0.dp else 64.dp,
                    label = "hero",
                )
                Hero(focused, (tab as? AppsTab)?.shortcuts.orEmpty(), showUsageCount, heroHeight)
                when {
                    tab == null -> Unit
                    // The first tab is a hub: widgets and a few favourites, nothing endless.
                    selectedTab == 0 && tab is AppsTab -> Hub(
                        widgets = widgets,
                        favourites = tab.shortcuts.take(FAVOURITES),
                        cardSize = cardSize,
                        tabsFocus = tabsFocus,
                        showAppLabels = showAppLabels,
                        nowPlaying = nowPlaying,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onPlayPause = onPlayPause,
                        onOpenNowPlaying = onOpenNowPlaying,
                        onFocus = { focused = it },
                        onWidgetsFocused = { focused = null },
                    )
                    tab is SourcesTab -> SourcesGrid(tab.sources, cardSize, tabsFocus, onOpenSource)
                    tab is AppsTab -> AppsGrid(tab.shortcuts, cardSize, tabsFocus, showAppLabels, onLaunch, onLongPress) { focused = it }
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
    onSettings: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    // Each part is anchored on its own: settings hard left, clock hard right, tabs on the
    // true screen centre. Laying these out in a row with weighted spacers instead centres
    // the tabs *between* the orb and the clock, and since those two aren't the same width
    // the tabs end up off-centre by half the difference.
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp)) {
        SettingsOrb(onSettings, Modifier.align(Alignment.CenterStart))
        // A hand-rolled row rather than tv-material's TabRow: its sliding indicator
        // animation hitched on the tab change.
        if (tabs.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    // The selected chip is the up-target from the content below.
                    val chipModifier =
                        if (index == selected) Modifier.focusRequester(tabsFocus) else Modifier
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
        if (focused != null) {
            Text(focused.title, color = Color.White, fontSize = 22.sp)
            if (showUsageCount) {
                val rank = inTab.indexOf(focused) + 1
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
}

@Composable
private fun Hub(
    widgets: List<WidgetTile>,
    favourites: List<Shortcut>,
    cardSize: CardSize,
    tabsFocus: FocusRequester,
    showAppLabels: Boolean,
    nowPlaying: NowPlaying?,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onPlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onFocus: (Shortcut) -> Unit,
    onWidgetsFocused: () -> Unit,
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
    val density = LocalDensity.current
    val bandRisePx = remember(widgets) {
        if (widgets.isEmpty()) 0f
        else with(density) { (widgets.maxOf { it.heightDp } + 24).dp.toPx() }
    }
    // Slimmer favourites at rest so the shorter (16:9) row keeps clear of the screen's bottom
    // edge, which TV overscan can eat into. They grow back to full size as the band collapses
    // and the row rises into the freed space, where there's plenty of room.
    val restFavouriteWidth = (cardSize.favouriteWidthDp - 40).dp
    val cardWidth = lerp(restFavouriteWidth, (cardSize.favouriteWidthDp + 60).dp, collapse)

    Column(
        Modifier
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
                    .padding(bottom = 12.dp)
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationY = -collapse * bandRisePx
                    },
            )
        }
        if (widgets.isNotEmpty()) {
            val startTiles = widgets.filter { it.alignment == WidgetAlignment.START }
            val centerTiles = widgets.filter { it.alignment == WidgetAlignment.CENTER }
            val endTiles = widgets.filter { it.alignment == WidgetAlignment.END }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        translationY = -collapse * bandRisePx
                    }
                    .padding(start = 48.dp, end = 48.dp, bottom = 8.dp),
            ) {
                // Three equal thirds; each zone is centred in its own, so a CENTER widget sits
                // at screen centre and START/END sit centred on their sides. Widgets render at
                // their real chosen size — pick a size that fits if the band gets crowded.
                val onFocused = { id: Int -> focusedWidget = id }
                WidgetSlot(startTiles, spotlight, focusedWidget, onFocused, Modifier.weight(1f))
                WidgetSlot(centerTiles, spotlight, focusedWidget, onFocused, Modifier.weight(1f))
                WidgetSlot(endTiles, spotlight, focusedWidget, onFocused, Modifier.weight(1f))
            }
        }
        if (favourites.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) appsFocused = true }
                    // Rise into the space the widgets vacate; dim under the widget spotlight.
                    .graphicsLayer {
                        translationY = -collapse * bandRisePx
                        alpha = 1f - 0.75f * spotlight
                    },
            ) {
                Text(
                    text = stringResource(R.string.home_most_used),
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                )
                // contentPadding, not an outer margin, so the row clips at the screen edge and
                // the end cards can scale up on focus without their border being truncated.
                LazyRow(
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(favourites, key = { it.id }) { shortcut ->
                        AppCard(shortcut, showAppLabels, onLaunch, onLongPress, onFocus, Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

/**
 * One third of the band. Its zone is measured unbounded so a tile keeps its true size — a
 * plain size() would be clamped by the third's width, which silently cropped every widget
 * wider than it. A zone that fits is centred in its third; one that overflows is anchored to
 * its side (START left, END right) so it grows over free space, never past the screen edge.
 */
@Composable
private fun WidgetSlot(
    tiles: List<WidgetTile>,
    spotlight: Float,
    focusedWidget: Int?,
    onFocused: (Int) -> Unit,
    modifier: Modifier,
) {
    val alignment = tiles.firstOrNull()?.alignment
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
            )
            val width = constraints.maxWidth
            val x = when {
                placeable.width <= width -> (width - placeable.width) / 2
                alignment == WidgetAlignment.START -> 0
                alignment == WidgetAlignment.END -> width - placeable.width
                else -> (width - placeable.width) / 2
            }
            layout(width, placeable.height) { placeable.placeRelative(x, 0) }
        }
    ) {
        if (tiles.isNotEmpty()) {
            WidgetZone(tiles, spotlight, focusedWidget, onFocused)
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
                Box(
                    modifier = Modifier
                        .size(tile.widthDp.dp, tile.heightDp.dp)
                        .onFocusChanged { if (it.hasFocus) onFocused(tile.id) }
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
    onFocus: (Shortcut) -> Unit,
) {
    var focusedIndex by remember { mutableStateOf(-1) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cardSize.columns),
        // Top padding so the top row can scale up on focus without its border being clipped.
        contentPadding = PaddingValues(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // DPAD-up from the top row goes to the tabs, not the settings orb; deeper rows keep
        // their natural up navigation within the grid.
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
        itemsIndexed(shortcuts, key = { _, shortcut -> shortcut.id }) { index, shortcut ->
            AppCard(shortcut, showAppLabels, onLaunch, onLongPress, { focusedIndex = index; onFocus(it) }, Modifier)
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

/** The focused app's banner, dimmed behind the screen. No blur: RenderEffect is API 31+. */
@Composable
private fun AmbientBackdrop(focused: Shortcut?) {
    Crossfade(targetState = focused?.banner, label = "backdrop") { banner ->
        if (banner != null) {
            val bitmap = remember(banner) { banner.toBitmap().asImageBitmap() }
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.30f,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f),
                )
                Box(Modifier.fillMaxSize().background(Background.copy(alpha = 0.55f)))
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
    val locale = Locale.getDefault()
    val timeFormat = remember(context, options.seconds) {
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
@Composable
private fun rememberAutoAccent(focused: Shortcut?, fallback: Color): Color {
    val target by produceState(fallback, focused, fallback) {
        val art = focused?.banner ?: focused?.icon
        value = art?.let { paletteAccent(it) } ?: fallback
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
