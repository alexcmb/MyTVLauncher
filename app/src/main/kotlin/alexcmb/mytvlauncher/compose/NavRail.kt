package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

// Lighter than the rest of the launcher's muted text: the pill sits on the bare background,
// often bright, with only a thin glass under it.
private val Idle = Color.White.copy(alpha = 0.72f)

// The navigation menu and the settings panel are one menu: the settings open in its place,
// with its width, shape, glass and rows (TvMenu, docked), so opening them reads as the menu
// turning its page. Both are only as tall as their rows.
internal val RailShape = RoundedCornerShape(28.dp)
internal val RailRowShape = RoundedCornerShape(16.dp)

/** The pill's left edge; the content (dock, grids, widgets, names) lines up on it. */
internal val RailStart = 12.dp
internal val RailInset = PaddingValues(start = RailStart, top = 24.dp, bottom = 24.dp)
internal const val RailGlass = 0.88f
internal val RailExpanded = 340.dp

/**
 * The home screen's navigation, top left. At rest it's a pill — the current tab's icon and
 * name — that the focus can rest on without anything opening, so moving around the screen
 * never pops a menu up. OK opens the menu in its place: a panel with a row per tab and the
 * settings at the end. OK on a tab switches to it and closes the menu; the settings open in
 * this same panel (TvMenu, docked); Back closes it, back onto the pill.
 *
 * Right or Down from the pill go to the content ([onExit]): it sits above it, not beside it.
 */
@Composable
internal fun NavRail(
    tabs: List<HomeTab>,
    selected: Int,
    pillFocus: FocusRequester,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(RailInset)) {
        tabs.getOrNull(selected)?.let { tab ->
            Pill(tab.title, iconOf(tab, selected), pillFocus, onExit) { onOpenChange(true) }
        }
        if (open && tabs.isNotEmpty()) {
            Panel(
                tabs = tabs,
                selected = selected,
                onClose = {
                    runCatching { pillFocus.requestFocus() }
                    onOpenChange(false)
                },
                onSelect = { index ->
                    onOpenChange(false)
                    onSelect(index)
                },
                onSettings = {
                    // The pill takes the focus first, so closing the settings comes back to it.
                    runCatching { pillFocus.requestFocus() }
                    onOpenChange(false)
                    onSettings()
                },
            )
        }
    }
}

private fun iconOf(tab: HomeTab, index: Int): RailIcon = when {
    index == 0 -> RailIcon.Drawable(R.drawable.ic_home)
    tab is SourcesTab -> RailIcon.Drawable(R.drawable.ic_source_hdmi)
    else -> RailIcon.Monogram(tab.title)
}

/** The menu at rest: the current tab, in a capsule of glass. OK opens the menu. */
@Composable
private fun Pill(
    title: String,
    icon: RailIcon,
    focusRequester: FocusRequester,
    onExit: () -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val tint = if (focused) Color.White else Idle
    Row(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .trackFocus()
            .onPreviewKeyEvent { event ->
                val out = event.key == Key.DirectionRight || event.key == Key.DirectionDown
                if (event.type == KeyEventType.KeyDown && out) onExit()
                out
            }
            .clickable { onOpen() }
            .glass(RoundedCornerShape(99.dp), opacity = 0.5f)
            .background(if (focused) LocalAccent.current else Color.Transparent, RoundedCornerShape(99.dp))
            .padding(start = 8.dp, end = 18.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailIconView(icon, title, tint)
        Text(title, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}

/** The open menu: a row per tab, then the settings. The focus stays inside until it closes. */
@Composable
private fun Panel(
    tabs: List<HomeTab>,
    selected: Int,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val requesters = remember(tabs.size) { List(tabs.size + 1) { FocusRequester() } }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters[selected.coerceIn(0, tabs.lastIndex)].requestFocus() }
    }
    Column(
        modifier = Modifier
            .width(RailExpanded)
            .popIn()
            .glass(RailShape, opacity = RailGlass)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Up and Down go row to row by hand: the panel opens over the widgets, and spatial
        // search would otherwise jump from a row into a widget underneath it.
        fun Modifier.trapped(index: Int) = this
            .focusRequester(requesters[index])
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = requesters.getOrNull(index - 1) ?: FocusRequester.Cancel
                down = requesters.getOrNull(index + 1) ?: FocusRequester.Cancel
            }
        tabs.forEachIndexed { index, tab ->
            RailItem(tab.title, index == selected, iconOf(tab, index), Modifier.trapped(index)) { onSelect(index) }
        }
        // The settings right under the tabs, past a thin line.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )
        RailItem(
            label = stringResource(R.string.action_settings),
            selected = false,
            icon = RailIcon.Drawable(R.drawable.ic_settings),
            modifier = Modifier.trapped(tabs.size),
        ) { onSettings() }
    }
}

private sealed interface RailIcon {
    data class Drawable(@DrawableRes val id: Int) : RailIcon

    /** A category has no icon of its own: its initial, in a small outlined tile. */
    data class Monogram(val name: String) : RailIcon
}

@Composable
private fun RailIconView(icon: RailIcon, label: String, tint: Color) {
    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        when (icon) {
            is RailIcon.Drawable -> Icon(
                painterResource(icon.id),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            is RailIcon.Monogram -> Box(
                Modifier
                    .size(24.dp)
                    .border(1.5.dp, tint, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon.name.take(1).uppercase(), color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    icon: RailIcon,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    val tint = if (selected || focused) Color.White else Idle
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .background(
                when {
                    focused -> accent
                    selected -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                RailRowShape,
            )
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailIconView(icon, label, tint)
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp),
        )
    }
}
