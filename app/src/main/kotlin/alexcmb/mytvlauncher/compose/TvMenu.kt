package alexcmb.mytvlauncher.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * One line of a [TvMenu]. [value] shows the current setting on the right, [selected] marks the
 * active choice of a pick-one list with a check, and [submenu] adds a chevron for rows that
 * open another level. An optional [swatch] shows a colour choice. [divider] draws a separator
 * above the row, to set a group apart (the system actions, a destructive "remove").
 */
data class MenuItem(
    val label: String,
    val swatch: Color? = null,
    val value: String? = null,
    val selected: Boolean = false,
    val submenu: Boolean = false,
    val divider: Boolean = false,
    val onClick: () -> Unit,
)

/** A menu to show: nesting is just replacing the spec with the next one. */
data class MenuSpec(val title: String, val items: List<MenuItem>)

private val Muted = Color(0xFF9AA0B4)

/**
 * A menu over the home screen: scrim, dark panel, focusable rows. Back steps out. [focusMemory]
 * remembers the last focused row (by label, so it survives rows moving) per menu title, so
 * stepping back lands on the row that opened the submenu; a fresh menu starts on its selected
 * choice, else its first row.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvMenu(spec: MenuSpec, focusMemory: MutableMap<String, String>, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val requesters = remember(spec.items.size) { List(spec.items.size) { FocusRequester() } }
    // Keyed on the title (a new level) and the rows' labels (rows added, removed, moved or
    // renamed — e.g. a category renamed while its list reloads), not on the spec itself: a
    // level that merely refreshes — a check moving, a value changing — must keep the focus
    // where the user is. The focus is re-placed by label, so it follows the remembered row.
    val labels = spec.items.map { it.label }
    LaunchedEffect(spec.title, labels) {
        if (requesters.isEmpty()) return@LaunchedEffect
        val target = focusMemory[spec.title]
            ?.let { label -> spec.items.indexOfFirst { it.label == label }.takeIf { it >= 0 } }
            ?: spec.items.indexOfFirst { it.selected }.takeIf { it >= 0 }
            ?: 0
        requesters[target.coerceIn(0, requesters.lastIndex)].requestFocus()
    }

    // A long press opens some menus while the key is still held: its auto-repeats and the
    // final key-up would land on the row that just took focus and fire it. Swallow those, but
    // only those — a fresh press (repeat count 0) is the user acting on the menu, so it goes
    // straight through. A menu opened by a plain click sees a fresh press first and never
    // loses a key.
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (armed) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    armed = true
                    false
                } else {
                    if (event.type == KeyEventType.KeyUp) armed = true
                    true
                }
            }
            // Lighter than an opaque dim, so the glass panel reads as see-through.
            .fadeInOnce()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 440.dp)
                .heightIn(max = 480.dp)
                // Each level pops in, so going into a submenu reads as moving somewhere.
                .popIn(spec.title)
                .glass(RoundedCornerShape(16.dp), opacity = 0.78f)
                .padding(vertical = 16.dp, horizontal = 12.dp),
        ) {
            Text(
                text = spec.title,
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                spec.items.forEachIndexed { index, item ->
                    if (item.divider && index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                    }
                    // Trap the focus inside the menu: left/right have nowhere to go here and
                    // would otherwise escape to the launcher behind the scrim, and so would up
                    // off the first row or down off the last. Cancel those moves; up/down
                    // between rows still works. Back is the only way out.
                    val rowModifier = Modifier
                        .focusRequester(requesters[index])
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (index == 0) up = FocusRequester.Cancel
                            if (index == spec.items.lastIndex) down = FocusRequester.Cancel
                        }
                    MenuRow(item, rowModifier) { focusMemory[spec.title] = item.label }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, modifier: Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable { item.onClick() }
            .background(
                color = if (focused) accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.swatch != null) {
            Box(Modifier.size(14.dp).background(item.swatch, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = item.label,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        item.value?.let {
            Spacer(Modifier.width(12.dp))
            Text(
                text = it,
                color = if (focused) Color.White.copy(alpha = 0.85f) else Muted,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        when {
            item.selected -> {
                Spacer(Modifier.width(12.dp))
                Text("✓", color = if (focused) Color.White else accent, fontSize = 16.sp)
            }
            item.submenu -> {
                Spacer(Modifier.width(10.dp))
                Text("›", color = if (focused) Color.White else Muted, fontSize = 18.sp)
            }
        }
    }
}
