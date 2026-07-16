package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.repository.CardSize
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
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
private const val FAVOURITES = 8

/** A widget to place on the hub: its size and a factory for its host view. */
data class WidgetTile(val id: Int, val widthDp: Int, val heightDp: Int, val createView: () -> View)

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
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onSettings: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf<Shortcut?>(null) }
    val tab = tabs.getOrNull(selectedTab)

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
                TopBar(tabs, selectedTab, clock, onSettings) { selectedTab = it; focused = null }
                Hero(focused, tab?.shortcuts.orEmpty())
                when {
                    tab == null -> Unit
                    // The first tab is a hub: widgets and a few favourites, nothing endless.
                    selectedTab == 0 -> Hub(
                        widgets = widgets,
                        favourites = tab.shortcuts.take(FAVOURITES),
                        cardSize = cardSize,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onFocus = { focused = it },
                        onWidgetsFocused = { focused = null },
                    )
                    else -> AppsGrid(tab.shortcuts, cardSize, onLaunch, onLongPress) { focused = it }
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
    onSettings: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Derived here so the once-a-second tick only recomposes the bar, not the screen.
        val values = rememberClockValues(clock)
        Column {
            values.greeting?.let { Text(it, color = Muted, fontSize = 12.sp) }
            Text(values.time, color = Color.White, fontSize = 20.sp)
            values.date?.let { Text(it, color = Muted, fontSize = 12.sp) }
        }
        Spacer(Modifier.width(28.dp))
        // Left-aligned so a tab sits directly above the content — that alignment is what
        // lets DPAD-up out of the grid land back on the tabs. A hand-rolled row rather than
        // tv-material's TabRow: its sliding indicator animation hitched on the tab change.
        if (tabs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { index, tab ->
                    TabChip(tab.title, selected = index == selected) { onSelect(index) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SettingsOrb(onSettings)
    }
}

/** One tab: focus selects it, the selected one wears the accent. No slide animation. */
@Composable
private fun TabChip(title: String, selected: Boolean, onSelect: () -> Unit) {
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
        modifier = Modifier
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
private fun SettingsOrb(onSettings: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
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
private fun Hero(focused: Shortcut?, inTab: List<Shortcut>) {
    Column(Modifier.fillMaxWidth().height(64.dp).padding(start = 48.dp)) {
        if (focused != null) {
            Text(focused.title, color = Color.White, fontSize = 22.sp)
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

@Composable
private fun Hub(
    widgets: List<WidgetTile>,
    favourites: List<Shortcut>,
    cardSize: CardSize,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onFocus: (Shortcut) -> Unit,
    onWidgetsFocused: () -> Unit,
) {
    // As focus moves down to the apps, the widgets slide up and fade and the app cards
    // grow. Driven by graphicsLayer only — the widgets stay in the layout and focusable,
    // so moving back up returns to them and expands the band again.
    var appsFocused by remember { mutableStateOf(false) }
    val collapse by animateFloatAsState(
        targetValue = if (appsFocused && widgets.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "collapse",
    )
    val density = LocalDensity.current
    val bandRisePx = remember(widgets) {
        if (widgets.isEmpty()) 0f else with(density) { (widgets.maxOf { it.heightDp } + 24).dp.toPx() }
    }
    val cardWidth = lerp(cardSize.favouriteWidthDp.dp, (cardSize.favouriteWidthDp + 60).dp, collapse)

    Column(Modifier.padding(start = 48.dp, end = 48.dp).focusGroup()) {
        if (widgets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .onFocusChanged {
                        if (it.hasFocus) {
                            appsFocused = false
                            // Widgets aren't apps: drop the app backdrop back to black.
                            onWidgetsFocused()
                        }
                    }
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationY = -collapse * bandRisePx
                    }
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                widgets.forEach { tile ->
                    // Key by size too: resizing gives a new node, so a fresh host view is
                    // built at the new size instead of the cached one being reused.
                    key(tile.id, tile.widthDp, tile.heightDp) {
                        WidgetTileView(tile)
                    }
                }
            }
        }
        if (favourites.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .onFocusChanged { if (it.hasFocus) appsFocused = true }
                    // Rise into the space the widgets vacate, so they read as pushed off top.
                    .graphicsLayer { translationY = -collapse * bandRisePx },
            ) {
                Text(
                    text = stringResource(R.string.home_most_used),
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(favourites, key = { it.id }) { shortcut ->
                        AppCard(shortcut, onLaunch, onLongPress, onFocus, Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsGrid(
    shortcuts: List<Shortcut>,
    cardSize: CardSize,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onFocus: (Shortcut) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(cardSize.columns),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.focusGroup(),
    ) {
        items(shortcuts, key = { it.id }) { shortcut ->
            AppCard(shortcut, onLaunch, onLongPress, onFocus, Modifier)
        }
    }
}

/** A hosted widget with a focus response of its own — it grows and takes an accent border,
 *  since the widget's own artwork rarely shows focus clearly. */
@Composable
private fun WidgetTileView(tile: WidgetTile) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "widgetScale")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 0.dp, tween(160), label = "widgetBorder")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(tile.widthDp.dp, tile.heightDp.dp)
            .onFocusChanged { focused = it.hasFocus }
            .border(borderWidth, LocalAccent.current, RoundedCornerShape(8.dp))
            // Clip: some widgets draw past the size they're handed.
            .clipToBounds(),
    ) {
        AndroidView(factory = { tile.createView() }, modifier = Modifier.fillMaxSize())
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
            } else {
                shortcut.icon?.let { DrawableImage(it, Modifier.size(64.dp), ContentScale.Fit) }
                Text(
                    text = shortcut.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                )
            }
        }
    }
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
