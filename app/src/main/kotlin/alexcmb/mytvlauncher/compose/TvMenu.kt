package alexcmb.mytvlauncher.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** One line of a [TvMenu]. */
data class MenuItem(val label: String, val onClick: () -> Unit)

/** A menu to show: nesting is just replacing the spec with the next one. */
data class MenuSpec(val title: String, val items: List<MenuItem>)

private val Panel = Color(0xF0202020)
private val Accent = Color(0xFF3D5AFE)
private val Muted = Color(0xFF9AA0B4)

/**
 * A menu over the home screen: scrim, dark panel, focusable rows. Back closes it, and the
 * first row takes the focus so the remote lands somewhere useful.
 */
@Composable
fun TvMenu(spec: MenuSpec, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(spec) { firstRow.requestFocus() }

    // A long press opens this menu, but the key-up that ends that press is still to come:
    // it would land on the row that just took focus and fire it. Waiting a fixed delay
    // wouldn't do — the user releases whenever they like — so swallow input until the key
    // is actually released. Preview events reach us before the focused row.
    var armed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (armed) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) armed = true
                true
            }
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 380.dp)
                .heightIn(max = 480.dp)
                .background(Panel, RoundedCornerShape(12.dp))
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
                    MenuRow(item, if (index == 0) Modifier.focusRequester(firstRow) else Modifier)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable { item.onClick() }
            .background(
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(item.label, color = Color.White, fontSize = 15.sp)
    }
}
