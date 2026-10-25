package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

// Lighter than the rest of the launcher's muted text: the resting rail sits on the bare
// background, often bright, with only a thin glass under its icons.
private val Idle = Color.White.copy(alpha = 0.72f)
// The rail and the settings panel are one menu: the settings open in the rail's place, with
// its width, shape, glass and rows (TvMenu, docked), so opening them reads as the rail turning
// its page. Both are only as tall as their rows.
internal val RailShape = RoundedCornerShape(28.dp)
internal val RailRowShape = RoundedCornerShape(16.dp)
/** The rail's left edge; the hub's dock and its names line up on it. */
internal val RailStart = 12.dp
internal val RailInset = PaddingValues(start = RailStart, top = 24.dp, bottom = 24.dp)
internal const val RailGlass = 0.88f

/** The rail at rest: icons only. The content starts past it (see [RailReserve]). */
internal val RailCollapsed = 64.dp
internal val RailExpanded = 340.dp

/** Room the content leaves on the left for the resting rail and its margin. */
internal val RailReserve = 64.dp

/**
 * The home screen's navigation, down the left edge: a tab per row and the settings at the
 * end. At rest it's a narrow strip of translucent glass with discrete icons; when the focus
 * comes in it widens into a panel with the names, over the content (which doesn't move) —
 * a deeper glass, so it reads as a layer above without hiding the screen.
 *
 * Moving along the rail only highlights a row; OK switches to that tab (so looking through
 * the rail never changes what's on screen behind it). The settings open in this same place,
 * as a panel down the left edge (TvMenu, docked). Coming in from the content (Left, or Back)
 * always lands on the selected row, whatever row is level with the focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NavRail(
    tabs: List<HomeTab>,
    selected: Int,
    tabsFocus: FocusRequester,
    homeTabFocus: FocusRequester,
    onSelect: (Int) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onRight: () -> Boolean = { false },
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailExpanded else RailCollapsed, tween(220), label = "rail")
    val labels by animateFloatAsState(if (expanded) 1f else 0f, tween(180), label = "labels")
    Column(
        modifier = modifier
            .padding(RailInset)
            .width(width)
            .onFocusChanged {
                // Not while the apps are still loading and the settings are all there is.
                expanded = it.hasFocus && tabs.isNotEmpty()
            }
            .focusProperties { enter = { tabsFocus } }
            // Right goes back to the content; [onRight] can send it somewhere spatial search
            // wouldn't find (the hub's dock lies below the rail, not beside it).
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && onRight()
            }
            .focusGroup()
            .glass(RailShape, opacity = 0.42f + (RailGlass - 0.42f) * labels)
            .clipToBounds()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val requesters = Modifier
                .then(if (index == selected) Modifier.focusRequester(tabsFocus) else Modifier)
                .then(if (index == 0) Modifier.focusRequester(homeTabFocus) else Modifier)
            RailItem(
                label = tab.title,
                selected = index == selected,
                labels = labels,
                icon = when {
                    index == 0 -> RailIcon.Drawable(R.drawable.ic_home)
                    tab is SourcesTab -> RailIcon.Drawable(R.drawable.ic_source_hdmi)
                    else -> RailIcon.Monogram(tab.title)
                },
                modifier = requesters,
            ) { onSelect(index) }
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
            labels = labels,
            icon = RailIcon.Drawable(R.drawable.ic_settings),
        ) { onSettings() }
    }
}

private sealed interface RailIcon {
    data class Drawable(@DrawableRes val id: Int) : RailIcon

    /** A category has no icon of its own: its initial, in a small outlined tile. */
    data class Monogram(val name: String) : RailIcon
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    labels: Float,
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
            .trackFocus()
            .clickable { onClick() }
            .background(
                when {
                    selected -> accent
                    focused -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                RailRowShape,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Centred in the resting rail's width, so the icons don't move as it opens.
        Box(Modifier.width(RailCollapsed - 16.dp), contentAlignment = Alignment.Center) {
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
                    Text(
                        icon.name.take(1).uppercase(),
                        color = tint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp).graphicsLayer { alpha = labels },
        )
    }
}
