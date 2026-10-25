package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.dynamic.City
import alexcmb.mytvlauncher.dynamic.DynamicSchedule
import alexcmb.mytvlauncher.dynamic.SunPosition
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * A dynamic wallpaper: the picture for this moment of the day, cross-faded into the next one
 * as time passes (by the clock, or by where the sun really is from [city]). The mix is worked
 * out every 30 seconds and eased in, so the scene drifts from dawn to night without ever
 * redrawing in between — no cost at all between two updates.
 */
@Composable
fun DynamicBackdrop(
    schedule: DynamicSchedule,
    frame: (Int) -> File,
    city: City,
    scale: ContentScale,
    version: Int,
) {
    val mix by produceState(mixNow(schedule, city), schedule, city) {
        while (true) {
            value = mixNow(schedule, city)
            delay(30_000)
        }
    }
    // Decoded pictures kept (a file has 4–16, 1080p RGB_565: ~4 MB each), so moving on to the
    // next pair reuses the one already on screen instead of flashing while it decodes again.
    val cache = remember(version) { mutableStateMapOf<Int, ImageBitmap>() }
    val from = rememberFrame(mix.from, frame, cache)
    val to = rememberFrame(mix.to, frame, cache)
    val blend by animateFloatAsState(mix.blend, tween(durationMillis = 2_000), label = "dynamic")
    from?.let { Image(it, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize()) }
    if (mix.to != mix.from) {
        to?.let {
            Image(it, contentDescription = null, contentScale = scale, alpha = blend, modifier = Modifier.fillMaxSize())
        }
    }
}

/** One imported picture, decoded off the main thread (null until it's ready or if it's gone). */
@Composable
private fun rememberFrame(
    index: Int,
    frame: (Int) -> File,
    cache: SnapshotStateMap<Int, ImageBitmap>,
): ImageBitmap? {
    LaunchedEffect(index, cache) {
        if (index in cache) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(frame(index).path, BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                })?.asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) cache[index] = bitmap
    }
    return cache[index]
}

private fun mixNow(schedule: DynamicSchedule, city: City): DynamicSchedule.Mix = when (schedule) {
    is DynamicSchedule.ByTime -> {
        val c = Calendar.getInstance()
        val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0
        DynamicSchedule.mix(schedule, minutes / 1440.0)
    }
    is DynamicSchedule.BySun -> {
        val sky = SunPosition.at(System.currentTimeMillis(), city.latitude, city.longitude)
        DynamicSchedule.mix(schedule, sky.altitude, sky.azimuth)
    }
}
