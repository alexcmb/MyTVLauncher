package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.widget.MAX_SCALE_PERCENT
import alexcmb.mytvlauncher.widget.MIN_SCALE_PERCENT
import alexcmb.mytvlauncher.widget.SCALE_STEP_PERCENT
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private val Panel = Color(0xF0202020)
private val Track = Color(0xFF3A3A44)
private val Muted = Color(0xFF9AA0B4)

/**
 * Live widget resizing: the menus are gone, the widget re-renders at each step, and a small
 * panel at the bottom shows the slider. Left/right adjust (holding repeats), OK commits,
 * Back cancels. All input is captured so the launcher behind doesn't react.
 */
@Composable
fun TvResizeOverlay(
    label: String,
    percent: Int,
    onAdjust: (Int) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        onAdjust(-SCALE_STEP_PERCENT); true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        onAdjust(SCALE_STEP_PERCENT); true
                    }
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        onCommit(); true
                    }
                    event.type == KeyEventType.KeyUp && event.key == Key.Back -> {
                        onCancel(); true
                    }
                    // Swallow everything else: the launcher behind must not react.
                    else -> true
                }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.width(12.dp))
                Text("$percent %", color = LocalAccent.current, fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            // The slider: a track with the accent filling up to the current value.
            val fraction =
                (percent - MIN_SCALE_PERCENT).toFloat() / (MAX_SCALE_PERCENT - MIN_SCALE_PERCENT)
            Box(
                Modifier
                    .width(320.dp)
                    .height(6.dp)
                    .background(Track, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(LocalAccent.current, RoundedCornerShape(3.dp)),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.resize_hint), color = Muted, fontSize = 12.sp)
        }
    }
}
