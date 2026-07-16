package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.widget.DisplayWidget
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

private val Background = Color(0xFF0E0E12)
private val Accent = Color(0xFF3D5AFE)
private val Muted = Color(0xFF9AA0B4)
private const val COLUMNS = 5
private const val FAVOURITES = 8

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    tabs: List<HomeTab>,
    widgets: List<DisplayWidget>,
    clock: String,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onSettings: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf<Shortcut?>(null) }
    val tab = tabs.getOrNull(selectedTab)

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Background)) {
            AmbientBackdrop(focused)
            Column(Modifier.fillMaxSize()) {
                TopBar(tabs, selectedTab, clock, onSettings) { selectedTab = it; focused = null }
                Hero(focused, tab?.shortcuts.orEmpty())
                when {
                    tab == null -> Unit
                    // The first tab is a hub: widgets and a few favourites, nothing endless.
                    selectedTab == 0 -> Hub(
                        widgets = widgets,
                        favourites = tab.shortcuts.take(FAVOURITES),
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onFocus = { focused = it },
                    )
                    else -> AppsGrid(tab.shortcuts, onLaunch, onLongPress) { focused = it }
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
    clock: String,
    onSettings: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock, color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.width(28.dp))
        // Left-aligned so a tab sits directly above the content — that alignment is what
        // lets DPAD-up out of the grid land back on the tabs.
        if (tabs.isNotEmpty()) {
            TabRow(selectedTabIndex = selected) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = index == selected, onFocus = { onSelect(index) }) {
                        Text(
                            text = tab.title,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SettingsOrb(onSettings)
    }
}

@Composable
private fun SettingsOrb(onSettings: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSettings() }
            .background(if (focused) Accent else Color.Transparent, CircleShape)
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
    widgets: List<DisplayWidget>,
    favourites: List<Shortcut>,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onFocus: (Shortcut) -> Unit,
) {
    Column(Modifier.padding(start = 48.dp, end = 48.dp).focusGroup()) {
        if (widgets.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                widgets.forEach { widget ->
                    AndroidView(
                        factory = { widget.view },
                        modifier = Modifier.size(widget.widthDp.dp, widget.heightDp.dp),
                    )
                }
            }
        }
        if (favourites.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_most_used),
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(favourites, key = { it.id }) { shortcut ->
                    AppCard(shortcut, onLaunch, onLongPress, onFocus, Modifier.width(150.dp))
                }
            }
        }
    }
}

@Composable
private fun AppsGrid(
    shortcuts: List<Shortcut>,
    onLaunch: (Shortcut) -> Unit,
    onLongPress: (Shortcut) -> Unit,
    onFocus: (Shortcut) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
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

/** The focused app's banner, dimmed behind the screen. No blur: RenderEffect is API 31+. */
@Composable
private fun AmbientBackdrop(focused: Shortcut?) {
    Crossfade(targetState = focused?.banner, label = "backdrop") { banner ->
        if (banner != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = banner.toBitmap().asImageBitmap(),
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
            focusedBorder = Border(border = BorderStroke(2.dp, Accent))
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

/** App artwork arrives as Drawables from the PackageManager, not as Compose painters. */
@Composable
private fun DrawableImage(drawable: Drawable, modifier: Modifier, scale: ContentScale) {
    Image(
        bitmap = drawable.toBitmap().asImageBitmap(),
        contentDescription = null,
        contentScale = scale,
        modifier = modifier,
    )
}
