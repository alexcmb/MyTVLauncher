package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.browse.BrowseViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Compose home screen, still behind Settings → "Preview new UI" while the menus and
 * the widgets page are ported. The Leanback launcher is untouched until then.
 */
class ComposePreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        val viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        setContent {
            val groups by viewModel.browseContent.observeAsState(emptyList())
            val tabs = HomeTabs.from(groups, stringResource(R.string.title_all))
            HomeScreen(tabs = tabs, clock = rememberClock()) { shortcut -> launch(shortcut.id) }
        }
    }

    private fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        startActivity(intent)
    }
}

/** Re-aligns on the whole second so the clock doesn't drift. */
@Composable
private fun rememberClock(): String {
    val context = LocalContext.current
    val format = remember(context) {
        // Locale-aware, honours the system 12/24h setting, and always includes seconds.
        val skeleton =
            if (android.text.format.DateFormat.is24HourFormat(context)) "Hms" else "hms"
        SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton),
            Locale.getDefault(),
        )
    }
    val time by produceState(initialValue = format.format(Date())) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = format.format(Date())
        }
    }
    return time
}
