package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.media.NowPlaying
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** How dark the veil over the background is: it still shows through. */
private const val VEIL = 0.62f

/** How fast the block drifts, per second. */
private val Speed = 40.dp

/** At most this often, the block moves (about 30 times a second). */
private const val FRAME_MS = 30L

/**
 * Sleep mode, and the system screensaver: one look. The background stays, under a dark veil,
 * and only the time and what's playing remain, in a block that drifts across the screen and
 * bounces off its edges, taking a new soft colour at each bounce. Always moving, it never
 * keeps the same pixels lit. In the launcher, its controls have faded away underneath
 * (HomeScreen's contentHidden); any key brings them back.
 */
@Composable
fun Screensaver(nowPlaying: NowPlaying?) {
    var hue by remember { mutableFloatStateOf(Random.nextFloat() * 360f) }
    val tint by animateColorAsState(Color.hsv(hue, 0.32f, 1f), tween(durationMillis = 700), label = "tint")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = VEIL))) {
        Bouncing(onBounce = { hue = (hue + Random.nextInt(80, 280)) % 360f }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = rememberTime(),
                    color = tint,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Light,
                )
                nowPlaying?.takeIf { it.isPlaying }?.let { NowPlayingLines(it) }
            }
        }
    }
}

/** What's playing, under the time: the cover (if the app gives one), then title and artist. */
@Composable
private fun NowPlayingLines(media: NowPlaying) {
    media.art?.let { art ->
        val cover = remember(art) { art.asImageBitmap() }
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
    Text(
        text = media.title,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = if (media.art != null) 12.dp else 4.dp).widthIn(max = 480.dp),
    )
    media.subtitle?.let {
        Text(
            text = it,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp).widthIn(max = 480.dp),
        )
    }
}

/** The time, in the TV's 12- or 24-hour format, ticking on the minute. */
@Composable
private fun rememberTime(): String {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val format = remember(context, locale) {
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "Hm" else "hm"
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    val now by produceState(Date()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = Date()
        }
    }
    return format.format(now)
}

/**
 * Moves [content] across the whole area at a steady [Speed], on a slant, bouncing off the
 * edges like the old DVD players' logo; [onBounce] at each bounce (once for a corner). It
 * starts at a random spot, and stays inside if its size changes (a song starts, a title is
 * longer). Moved in the drawing phase only: the block is never composed or laid out again
 * for it.
 */
@Composable
private fun Bouncing(onBounce: () -> Unit, content: @Composable () -> Unit) {
    val speed = with(LocalDensity.current) { Speed.toPx() }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var block by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(speed) {
        val angle = Math.toRadians(Random.nextDouble(28.0, 62.0))
        var dx = (cos(angle) * if (Random.nextBoolean()) 1 else -1).toFloat()
        var dy = (sin(angle) * if (Random.nextBoolean()) 1 else -1).toFloat()
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val maxX = (area.width - block.width).coerceAtLeast(0).toFloat()
                val maxY = (area.height - block.height).coerceAtLeast(0).toFloat()
                val seconds = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                if (area == IntSize.Zero || block == IntSize.Zero) return@withFrameNanos
                val from = position.takeIf { it.isSpecified }
                    ?: Offset(Random.nextFloat() * maxX, Random.nextFloat() * maxY)
                var x = from.x + dx * speed * seconds
                var y = from.y + dy * speed * seconds
                var bounced = false
                if (x <= 0f || x >= maxX) {
                    dx = if (x <= 0f) abs(dx) else -abs(dx)
                    x = x.coerceIn(0f, maxX)
                    bounced = from.isSpecified
                }
                if (y <= 0f || y >= maxY) {
                    dy = if (y <= 0f) abs(dy) else -abs(dy)
                    y = y.coerceIn(0f, maxY)
                    bounced = from.isSpecified
                }
                if (bounced && seconds > 0f) onBounce()
                position = Offset(x, y)
            }
            // Every other frame or so is plenty at this speed, and halves what it costs: on
            // this kind of TV, each frame redraws the whole screen.
            delay(FRAME_MS)
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { area = it }) {
        Box(
            Modifier
                .onSizeChanged { block = it }
                .graphicsLayer {
                    val p = position
                    // Hidden until it has a place: it would flash at the top left first.
                    alpha = if (p.isSpecified) 1f else 0f
                    if (p.isSpecified) {
                        translationX = p.x
                        translationY = p.y
                    }
                },
        ) {
            content()
        }
    }
}
