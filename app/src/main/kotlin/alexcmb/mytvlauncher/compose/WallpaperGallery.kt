package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.dynamic.DynamicSchedule
import alexcmb.mytvlauncher.repository.BackgroundStyle
import alexcmb.mytvlauncher.wallpaper.Wallpaper
import alexcmb.mytvlauncher.wallpaper.WallpaperKind
import alexcmb.mytvlauncher.wallpaper.WallpaperLibrary
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Base = Color(0xFF0E0E12)
private val Muted = Color(0xFF9AA0B4)
private val Shape = RoundedCornerShape(12.dp)

/** The gallery's filters, in the order of its pills. */
private enum class Filter(@StringRes val label: Int) {
    ALL(R.string.gallery_all),
    VIDEO(R.string.gallery_videos),
    DYNAMIC(R.string.gallery_dynamic),
    STILL(R.string.gallery_still),
    GENERATED(R.string.gallery_generated),
}

/** The backgrounds the launcher draws itself, as the gallery offers them. */
private val GeneratedStyles = listOf(
    BackgroundStyle.AURORA,
    BackgroundStyle.AMBIENT,
    BackgroundStyle.ACCENT_GRADIENT,
    BackgroundStyle.SOLID,
)

/** One card of the grid. */
private sealed interface Entry {
    val key: String

    data object Add : Entry {
        override val key = "#add"
    }

    data class Imported(val wallpaper: Wallpaper) : Entry {
        override val key get() = wallpaper.id
    }

    data class Generated(val style: BackgroundStyle) : Entry {
        override val key get() = generatedKey(style)
    }
}

/**
 * The wallpaper picker: every background in one place, sorted by what it is — looping
 * videos, day/night dynamic files, still images, and the backgrounds the launcher generates
 * — each a card with a picture of it and a badge saying what it is. OK applies a card at once
 * (the screen behind shows it, under this panel's lighter top); a long press on an imported
 * one opens its options. The card on screen wears a check and the accent's edge.
 *
 * [current] is the key of what's on screen: an imported wallpaper's id, or "#STYLE".
 * [thumbnail] is blocking and runs off the main thread.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WallpaperGallery(
    wallpapers: List<Wallpaper>,
    current: String,
    importing: Boolean,
    styleName: (BackgroundStyle) -> String,
    thumbnail: (Wallpaper, Int) -> Bitmap?,
    onAdd: () -> Unit,
    onApply: (Wallpaper) -> Unit,
    onApplyStyle: (BackgroundStyle) -> Unit,
    onOptions: (Wallpaper) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val entries = buildList {
        if (filter != Filter.GENERATED) add(Entry.Add)
        wallpapers.filter {
            when (filter) {
                Filter.ALL -> true
                Filter.VIDEO -> it.kind == WallpaperKind.VIDEO
                Filter.DYNAMIC -> it.kind == WallpaperKind.DYNAMIC
                Filter.STILL -> it.kind == WallpaperKind.STILL
                Filter.GENERATED -> false
            }
        }.forEach { add(Entry.Imported(it)) }
        if (filter == Filter.ALL || filter == Filter.GENERATED) GeneratedStyles.forEach { add(Entry.Generated(it)) }
    }

    // The focus starts on the card on screen (else the first), and comes back to the grid
    // after a card it was on goes away (removed from its menu).
    val initialFocus = remember { FocusRequester() }
    val focusKey = entries.firstOrNull { it.key == current }?.key ?: entries.firstOrNull()?.key
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(wallpapers.map { it.id }) {
        withFrameNanos { }
        if (!hasFocus) runCatching { initialFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .fadeInOnce()
            // Light at the top so the wallpaper just applied shows, dark where the cards sit.
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.3f to Color.Black.copy(alpha = 0.7f),
                    1f to Color.Black.copy(alpha = 0.88f),
                )
            )
            .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
            Row(Modifier.padding(horizontal = 56.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.wallpaper_gallery),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.gallery_hint), color = Muted, fontSize = 12.sp)
            }
            // Filters: one glass pill, a segment per kind; moving onto one selects it.
            Row(
                modifier = Modifier
                    .padding(start = 56.dp, top = 16.dp)
                    .glass(RoundedCornerShape(99.dp), opacity = 0.4f)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Filter.entries.forEach { f ->
                    FilterPill(stringResource(f.label), selected = f == filter) { filter = f }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                // Room around the cards for their lift on focus and their glow.
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.key }) { entry ->
                    val modifier = if (entry.key == focusKey) Modifier.focusRequester(initialFocus) else Modifier
                    when (entry) {
                        Entry.Add -> GalleryCard(
                            title = stringResource(if (importing) R.string.gallery_importing else R.string.gallery_add),
                            subtitle = stringResource(R.string.gallery_add_hint),
                            active = false,
                            onClick = { if (!importing) onAdd() },
                            modifier = modifier,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_add),
                                    contentDescription = null,
                                    tint = if (importing) Muted else Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                        is Entry.Imported -> {
                            val w = entry.wallpaper
                            GalleryCard(
                                title = w.name,
                                subtitle = null,
                                active = entry.key == current,
                                onClick = { onApply(w) },
                                onLongClick = { onOptions(w) },
                                modifier = modifier,
                            ) {
                                when (w.kind) {
                                    WallpaperKind.DYNAMIC -> DayStrip(w, thumbnail)
                                    else -> Thumb(w, 0, thumbnail, Modifier.fillMaxSize())
                                }
                                KindBadge(w)
                            }
                        }
                        is Entry.Generated -> GalleryCard(
                            title = styleName(entry.style),
                            subtitle = null,
                            active = entry.key == current,
                            onClick = { onApplyStyle(entry.style) },
                            modifier = modifier,
                        ) {
                            GeneratedPreview(entry.style)
                            Badge(
                                R.drawable.ic_sparkle,
                                stringResource(
                                    if (entry.style == BackgroundStyle.AURORA) R.string.badge_animated
                                    else R.string.badge_generated
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A filter segment: focus selects it (as the home screen's tabs), the selected one wears the accent. */
@Composable
private fun FilterPill(title: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = title,
        color = if (selected) Color.White else Muted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable { onSelect() }
            .background(
                when {
                    selected -> LocalAccent.current
                    focused -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(99.dp),
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/**
 * A card: the picture (16:9) with its badges, the name under it. The card on screen keeps a
 * thin accent edge and a check; focus lifts it and lights its glow, as on the home screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GalleryCard(
    title: String,
    subtitle: String?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    picture: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    Column(modifier) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(Shape),
            colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f)),
            border = CardDefaults.border(
                border = if (active) Border(BorderStroke(2.dp, accent.copy(alpha = 0.85f))) else Border.None,
                focusedBorder = Border(BorderStroke(2.dp, if (active) accent else Color.White)),
            ),
            glow = focusGlow(),
            modifier = Modifier
                .fillMaxWidth()
                .focusLift(focused)
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused }
                .trackFocus(),
        ) {
            Box(Modifier.fillMaxSize()) {
                picture()
                Box(Modifier.fillMaxSize().glassGloss(Shape))
                if (active) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.gallery_current),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(accent, CircleShape)
                            .padding(3.dp)
                            .size(14.dp),
                    )
                }
            }
        }
        Text(
            text = title,
            color = if (focused || active) Color.White else Muted,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        subtitle?.let {
            Text(it, color = Muted.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** What the card is, top left: loop, time or sun, image. */
@Composable
private fun BoxScope.KindBadge(wallpaper: Wallpaper) {
    when (wallpaper.kind) {
        WallpaperKind.VIDEO -> Badge(R.drawable.ic_loop, stringResource(R.string.badge_loop))
        WallpaperKind.DYNAMIC -> if (wallpaper.dynamicSchedule is DynamicSchedule.BySun) {
            Badge(R.drawable.ic_day_night, stringResource(R.string.badge_sun))
        } else {
            Badge(R.drawable.ic_schedule, stringResource(R.string.badge_time))
        }
        WallpaperKind.STILL -> Badge(R.drawable.ic_image, stringResource(R.string.badge_image))
    }
}

@Composable
private fun BoxScope.Badge(@DrawableRes icon: Int, label: String) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Text(label, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * A dynamic wallpaper's day at a glance: its pictures side by side, from morning to night,
 * each labelled with the hour it starts (by time) or the moment of the sun (by sun).
 */
@Composable
private fun DayStrip(wallpaper: Wallpaper, thumbnail: (Wallpaper, Int) -> Bitmap?) {
    val previews = remember(wallpaper.schedule) {
        wallpaper.dynamicSchedule?.let(WallpaperLibrary::previews).orEmpty()
    }
    Row(Modifier.fillMaxSize()) {
        previews.forEachIndexed { i, preview ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.6f)))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Thumb(wallpaper, preview.image, thumbnail, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                    )
                )
                Text(
                    text = previewLabel(preview),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun previewLabel(preview: WallpaperLibrary.DayPreview): String {
    preview.minutes?.let { m ->
        val h = m / 60
        val min = m % 60
        return if (min == 0) stringResource(R.string.gallery_hour, h) else stringResource(R.string.gallery_hour_min, h, min)
    }
    return stringResource(
        when (preview.phase) {
            WallpaperLibrary.SunPhase.DAWN -> R.string.phase_dawn
            WallpaperLibrary.SunPhase.DAY -> R.string.phase_day
            WallpaperLibrary.SunPhase.DUSK -> R.string.phase_dusk
            else -> R.string.phase_night
        }
    )
}

/** A thumbnail, loaded off the main thread (a dark card until it's ready). */
@Composable
private fun Thumb(wallpaper: Wallpaper, index: Int, thumbnail: (Wallpaper, Int) -> Bitmap?, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, wallpaper.id, index) {
        value = withContext(Dispatchers.IO) { thumbnail(wallpaper, index)?.asImageBitmap() }
    }
    Box(modifier) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** A still picture of what a generated background looks like, in the accent's colours. */
@Composable
private fun GeneratedPreview(style: BackgroundStyle) {
    val accent = LocalAccent.current
    val brush = when (style) {
        BackgroundStyle.AURORA -> Brush.linearGradient(
            listOf(accent.copy(alpha = 0.9f), Color(0xFF1FB5A8).copy(alpha = 0.6f), Base),
        )
        BackgroundStyle.AMBIENT -> Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Base))
        BackgroundStyle.ACCENT_GRADIENT -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f), Base))
        else -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.18f)))
    }
    Box(Modifier.fillMaxSize().background(Base).background(brush))
}

/** The key [WallpaperGallery] marks as on screen for a generated style. */
fun generatedKey(style: BackgroundStyle) = "#" + style.name
