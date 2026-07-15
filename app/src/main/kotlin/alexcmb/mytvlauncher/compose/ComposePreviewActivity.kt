package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.browse.BrowseViewModel
import alexcmb.mytvlauncher.model.Shortcut
import alexcmb.mytvlauncher.model.ShortcutGroup
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A spike: the apps grid rebuilt in Compose for TV, to judge the look and — more to the
 * point — whether it stays fluid on the real TV before the whole UI is ported to it.
 * The Leanback launcher is untouched; this opens from Settings.
 */
class ComposePreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        val viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        setContent {
            val groups = viewModel.browseContent.observeAsState(emptyList())
            AppsScreen(groups.value) { shortcut -> launch(shortcut.id) }
        }
    }

    private fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        startActivity(intent)
    }
}

@Composable
private fun AppsScreen(groups: List<ShortcutGroup>, onClick: (Shortcut) -> Unit) {
    MaterialTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014)),
            contentPadding = PaddingValues(vertical = 32.dp),
        ) {
            items(groups) { group ->
                Text(
                    text = group.category,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 12.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp)) {
                    items(group.shortcutList) { shortcut ->
                        AppCard(shortcut, onClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppCard(shortcut: Shortcut, onClick: (Shortcut) -> Unit) {
    Card(
        onClick = { onClick(shortcut) },
        modifier = Modifier.padding(end = 16.dp),
        scale = CardDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(
            modifier = Modifier.size(width = 240.dp, height = 135.dp),
            contentAlignment = Alignment.Center,
        ) {
            val artwork = shortcut.banner ?: shortcut.icon
            if (artwork != null) {
                DrawableImage(artwork, shortcut.banner != null)
            }
            if (shortcut.banner == null) {
                Text(
                    text = shortcut.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                )
            }
        }
    }
}

/** App icons arrive as Drawables from the PackageManager, not as Compose painters. */
@Composable
private fun DrawableImage(drawable: Drawable, fills: Boolean) {
    Image(
        bitmap = drawable.toBitmap().asImageBitmap(),
        contentDescription = null,
        contentScale = if (fills) ContentScale.Crop else ContentScale.Fit,
        modifier = if (fills) Modifier.fillMaxSize() else Modifier.size(72.dp),
    )
}
