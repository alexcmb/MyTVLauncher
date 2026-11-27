package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.dynamic.City
import alexcmb.mytvlauncher.dynamic.Cities
import alexcmb.mytvlauncher.widget.HostedWidget
import alexcmb.mytvlauncher.widget.WidgetAlignment
import alexcmb.mytvlauncher.widget.WidgetShape
import alexcmb.mytvlauncher.widget.builtin.BuiltinWidgetHost
import alexcmb.mytvlauncher.widget.builtin.BuiltinWidgetType
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
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

private enum class WidgetFilter(@StringRes val label: Int) {
    ALL(R.string.widget_filter_all),
    ACTIVE(R.string.widget_filter_active),
    BUILTIN(R.string.widget_filter_builtin),
    APPS(R.string.widget_filter_apps),
}

private sealed interface WidgetEntry {
    val key: String

    data class Builtin(val type: BuiltinWidgetType) : WidgetEntry {
        override val key get() = "builtin_${type.id}"
    }

    data class Provider(val provider: AppWidgetProviderInfo) : WidgetEntry {
        override val key get() = "provider_${provider.provider.flattenToString()}"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WidgetGallery(
    hostedWidgets: List<HostedWidget>,
    availableProviders: List<AppWidgetProviderInfo>,
    canAddMore: Boolean,
    showWidgets: Boolean,
    dynamicCity: City = Cities.DEFAULT,
    onOpenCityMenu: () -> Unit = {},
    onToggleShowWidgets: (Boolean) -> Unit,
    onAddBuiltin: (BuiltinWidgetType) -> Unit,
    onAddProvider: (AppWidgetProviderInfo) -> Unit,
    onOptions: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var filter by rememberSaveable { mutableStateOf(WidgetFilter.ALL) }
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    val entries = buildList {
        // Built-in widgets
        if (filter == WidgetFilter.ALL || filter == WidgetFilter.BUILTIN || filter == WidgetFilter.ACTIVE) {
            BuiltinWidgetType.entries.forEach { type ->
                val isPlaced = hostedWidgets.any { it.id == type.id }
                if (filter != WidgetFilter.ACTIVE || isPlaced) {
                    add(WidgetEntry.Builtin(type))
                }
            }
        }
        // App widgets
        if (filter == WidgetFilter.ALL || filter == WidgetFilter.APPS || filter == WidgetFilter.ACTIVE) {
            availableProviders.forEach { prov ->
                val isPlaced = hostedWidgets.any { hosted ->
                    !BuiltinWidgetType.isBuiltin(hosted.id) &&
                        appWidgetManager.getAppWidgetInfo(hosted.id)?.provider == prov.provider
                }
                if (filter != WidgetFilter.ACTIVE || isPlaced) {
                    add(WidgetEntry.Provider(prov))
                }
            }
        }
    }

    val initialFocus = remember { FocusRequester() }
    val locationFocus = remember { FocusRequester() }
    val toggleFocus = remember { FocusRequester() }
    val slotRequesters = remember { List(3) { FocusRequester() } }
    val filterRequesters = remember { List(WidgetFilter.entries.size) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!hasFocus) {
            val target = if (entries.isNotEmpty()) initialFocus else slotRequesters[0]
            runCatching { target.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .fadeInOnce()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.25f to Color.Black.copy(alpha = 0.75f),
                    1f to Color.Black.copy(alpha = 0.90f),
                ),
            )
            .focusGroup()
            .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp),
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.widget_gallery_title),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = stringResource(R.string.widget_gallery_hint),
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Placed count, Location pill & Toggle show
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "${hostedWidgets.size} / 3",
                        color = if (canAddMore) Color(0xFF81C784) else Color(0xFFFFB74D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    // Location pill
                    LocationPill(
                        cityName = dynamicCity.name,
                        onClick = onOpenCityMenu,
                        modifier = Modifier
                            .focusRequester(locationFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = toggleFocus
                                down = slotRequesters[0]
                            },
                    )

                    // Toggle button for showing on home screen
                    TogglePill(
                        label = stringResource(R.string.widgets_show),
                        active = showWidgets,
                        onClick = { onToggleShowWidgets(!showWidgets) },
                        modifier = Modifier
                            .focusRequester(toggleFocus)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                left = locationFocus
                                right = FocusRequester.Cancel
                                down = slotRequesters[2]
                            },
                    )
                }
            }

            // Visual Slots Band: Start / Center / End
            ActiveSlotsBar(
                hostedWidgets = hostedWidgets,
                slotRequesters = slotRequesters,
                locationFocus = locationFocus,
                toggleFocus = toggleFocus,
                filterRequesters = filterRequesters,
                onOptions = onOptions,
                onEmptyClick = { runCatching { initialFocus.requestFocus() } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp, vertical = 14.dp),
            )

            // Filter pills
            Row(
                modifier = Modifier
                    .padding(start = 56.dp, bottom = 12.dp)
                    .glass(RoundedCornerShape(99.dp), opacity = 0.4f)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WidgetFilter.entries.forEachIndexed { idx, f ->
                    FilterPill(
                        title = stringResource(f.label),
                        selected = f == filter,
                        modifier = Modifier
                            .focusRequester(filterRequesters[idx])
                            .focusProperties {
                                up = when (idx) {
                                    0 -> slotRequesters[0]
                                    1 -> slotRequesters[1]
                                    2 -> slotRequesters[1]
                                    else -> slotRequesters[2]
                                }
                            },
                    ) { filter = f }
                }
            }

            // Catalog Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.key }) { entry ->
                    val isFirst = entries.firstOrNull()?.key == entry.key
                    val cardModifier = if (isFirst) Modifier.focusRequester(initialFocus) else Modifier

                    when (entry) {
                        is WidgetEntry.Builtin -> {
                            val type = entry.type
                            val hosted = hostedWidgets.firstOrNull { it.id == type.id }
                            val isPlaced = hosted != null

                            WidgetCatalogCard(
                                title = stringResource(type.titleRes),
                                subtitle = stringResource(type.descRes),
                                isPlaced = isPlaced,
                                badgeText = stringResource(R.string.widget_badge_builtin),
                                badgeIcon = R.drawable.ic_sparkle,
                                onClick = {
                                    if (isPlaced) {
                                        onOptions(type.id)
                                    } else {
                                        onAddBuiltin(type)
                                    }
                                },
                                onLongClick = {
                                    if (isPlaced) onOptions(type.id)
                                },
                                modifier = cardModifier,
                            ) {
                                // Live Composable preview!
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                ) {
                                    BuiltinWidgetHost(type = type, shape = WidgetShape.WIDE, city = dynamicCity)
                                }
                            }
                        }

                        is WidgetEntry.Provider -> {
                            val provider = entry.provider
                            val title = provider.loadLabel(context.packageManager)
                            val hosted = hostedWidgets.firstOrNull { h ->
                                !BuiltinWidgetType.isBuiltin(h.id) &&
                                    appWidgetManager.getAppWidgetInfo(h.id)?.provider == provider.provider
                            }
                            val isPlaced = hosted != null

                            WidgetCatalogCard(
                                title = title,
                                subtitle = provider.provider.packageName,
                                isPlaced = isPlaced,
                                badgeText = stringResource(R.string.widget_badge_app),
                                badgeIcon = R.drawable.ic_launcher,
                                onClick = {
                                    if (isPlaced) {
                                        onOptions(hosted.id)
                                    } else {
                                        onAddProvider(provider)
                                    }
                                },
                                onLongClick = {
                                    if (isPlaced) onOptions(hosted.id)
                                },
                                modifier = cardModifier,
                            ) {
                                ProviderPreview(provider)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The active slots preview bar: displays the 3 zones of the TV Launcher. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActiveSlotsBar(
    hostedWidgets: List<HostedWidget>,
    slotRequesters: List<FocusRequester>,
    locationFocus: FocusRequester,
    toggleFocus: FocusRequester,
    filterRequesters: List<FocusRequester>,
    onOptions: (Int) -> Unit,
    onEmptyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .glass(RoundedCornerShape(16.dp), opacity = 0.35f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alignments = listOf(
            WidgetAlignment.START to R.string.widget_slot_start,
            WidgetAlignment.CENTER to R.string.widget_slot_center,
            WidgetAlignment.END to R.string.widget_slot_end,
        )

        alignments.forEachIndexed { index, (alignment, labelRes) ->
            val widget = hostedWidgets.firstOrNull { it.alignment == alignment }
            SlotCard(
                alignmentName = stringResource(labelRes),
                hostedWidget = widget,
                onClick = {
                    if (widget != null) onOptions(widget.id) else onEmptyClick()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(slotRequesters[index])
                    .focusProperties {
                        up = if (index < 2) locationFocus else toggleFocus
                        down = filterRequesters.getOrNull(index) ?: FocusRequester.Default
                        left = if (index > 0) slotRequesters[index - 1] else FocusRequester.Cancel
                        right = if (index < 2) slotRequesters[index + 1] else FocusRequester.Cancel
                    },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SlotCard(
    alignmentName: String,
    hostedWidget: HostedWidget?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = if (hostedWidget != null) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.03f),
        ),
        border = CardDefaults.border(
            border = if (focused) Border(BorderStroke(2.dp, accent)) else Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(BorderStroke(2.dp, accent)),
        ),
        modifier = modifier
            .height(52.dp)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = alignmentName,
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (hostedWidget != null) {
                        BuiltinWidgetType.fromId(hostedWidget.id)?.let { stringResource(it.titleRes) }
                            ?: "Widget #${hostedWidget.id}"
                    } else {
                        stringResource(R.string.widget_slot_empty)
                    },
                    color = if (hostedWidget != null) Color.White else Muted.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (hostedWidget != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hostedWidget != null) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${hostedWidget.scalePercent}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun WidgetCatalogCard(
    title: String,
    subtitle: String,
    isPlaced: Boolean,
    badgeText: String,
    @DrawableRes badgeIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    preview: @Composable BoxScope.() -> Unit,
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
                border = if (isPlaced) Border(BorderStroke(2.dp, accent.copy(alpha = 0.85f))) else Border.None,
                focusedBorder = Border(BorderStroke(2.dp, if (isPlaced) accent else Color.White)),
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
                preview()
                Box(Modifier.fillMaxSize().glassGloss(Shape))

                // Kind badge, top left
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(badgeIcon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                // Placed checkmark badge, top right
                if (isPlaced) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.widget_badge_active),
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
            color = if (focused || isPlaced) Color.White else Muted,
            fontSize = 13.sp,
            fontWeight = if (isPlaced) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Text(
            text = subtitle,
            color = Muted.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ProviderPreview(provider: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, provider) {
        value = withContext(Dispatchers.IO) {
            val drawable = provider.loadPreviewImage(context, 0)
                ?: provider.loadIcon(context, 0)
            drawable?.toBitmap()?.asImageBitmap()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun FilterPill(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = title,
        color = if (selected) Color.White else Muted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
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

@Composable
private fun TogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .glass(RoundedCornerShape(99.dp), opacity = 0.45f)
            .border(
                1.dp,
                if (focused) accent else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(99.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (active) accent else Color(0xFF757575), CircleShape),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LocationPill(
    cityName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .glass(RoundedCornerShape(99.dp), opacity = 0.45f)
            .border(
                1.dp,
                if (focused) accent else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(99.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location),
            contentDescription = null,
            tint = if (focused) accent else Color(0xFFD0D6E8),
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = cityName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
