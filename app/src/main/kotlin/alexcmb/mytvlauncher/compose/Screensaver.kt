package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.media.NowPlaying
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Sleep mode: the launcher's controls fade away (the activity hides them in HomeScreen), leaving
 * a large clock, the date and whatever is playing over the background (the aurora keeps drifting, a wallpaper stays). Shown by the
 * activity after a spell without input; any key brings the launcher back.
 *
 * The clock block moves to a new spot every minute, within a small box around the centre, so
 * a TV left on it doesn't keep the same pixels lit.
 */
@Composable
fun AmbientScreen(nowPlaying: NowPlaying?) {
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "Hm"), locale)
    }
    val dateFormat = remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    val now by produceState(Date()) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = Date()
        }
    }
    // A new nudge each minute (keyed on the minute, so it moves exactly when the time changes).
    val minute = now.time / 60_000
    val drift = Random(minute)
    val dx = (drift.nextInt(-60, 61)).dp
    val dy = (drift.nextInt(-40, 41)).dp

    Box(
        Modifier
            .fillMaxSize()
            // The launcher's controls have faded out underneath (HomeScreen's contentHidden); a
            // light veil, darker at the edges, keeps the background showing and the clock legible.
            .background(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(dx, dy),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = timeFormat.format(now),
                color = Color.White,
                fontSize = 120.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = dateFormat.format(now).replaceFirstChar { it.uppercase(locale) },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 24.sp,
            )
            nowPlaying?.takeIf { it.isPlaying }?.let { media ->
                // Under the date: the cover (if the app gives one), then title and artist.
                media.art?.let { art ->
                    val cover = remember(art) { art.asImageBitmap() }
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 28.dp)
                            .size(120.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                Text(
                    text = media.title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (media.art != null) 14.dp else 24.dp).widthIn(max = 600.dp),
                )
                media.subtitle?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp).widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}
