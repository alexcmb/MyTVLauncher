package alexcmb.mytvlauncher.compose

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.browse.BrowseViewModel
import alexcmb.mytvlauncher.browse.CategoryOptions
import alexcmb.mytvlauncher.model.Shortcut
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Compose home screen, still behind Settings → "Preview new UI" while the settings
 * menu and the widgets page are ported. The Leanback launcher is untouched until then.
 */
class ComposePreviewActivity : ComponentActivity() {

    private lateinit var viewModel: BrowseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory)[BrowseViewModel::class.java]
        setContent {
            val groups by viewModel.browseContent.observeAsState(emptyList())
            val tabs = HomeTabs.from(groups, stringResource(R.string.title_all))
            // Nesting a menu is just swapping the spec; null means nothing is open.
            var menu by remember { mutableStateOf<MenuSpec?>(null) }
            var renaming by remember { mutableStateOf<Shortcut?>(null) }

            Box {
                HomeScreen(
                    tabs = tabs,
                    clock = rememberClock(),
                    onLaunch = { launch(it.id) },
                    onLongPress = { shortcut ->
                        menu = appMenu(
                            shortcut = shortcut,
                            onClose = { menu = null },
                            onChangeCategory = {
                                menu = categoryMenu(
                                    shortcut = shortcut,
                                    onClose = { menu = null },
                                    onNew = {
                                        menu = null
                                        renaming = shortcut
                                    },
                                )
                            },
                        )
                    },
                )
                menu?.let { TvMenu(it) { menu = null } }
                renaming?.let { shortcut ->
                    TvTextPrompt(
                        title = stringResource(R.string.category_new_title),
                        hint = stringResource(R.string.category_hint),
                        onSubmit = { viewModel.setCategory(shortcut, it) },
                        onDismiss = { renaming = null },
                    )
                }
            }
        }
    }

    private fun appMenu(
        shortcut: Shortcut,
        onClose: () -> Unit,
        onChangeCategory: () -> Unit,
    ) = MenuSpec(
        title = shortcut.title,
        items = listOf(
            MenuItem(getString(R.string.menu_change_category)) { onChangeCategory() },
            MenuItem(getString(R.string.menu_hide)) {
                onClose()
                viewModel.hideApp(shortcut)
            },
            MenuItem(getString(R.string.menu_uninstall)) {
                onClose()
                startAppIntent(Intent(Intent.ACTION_DELETE, packageUri(shortcut)))
            },
            MenuItem(getString(R.string.menu_app_info)) {
                onClose()
                startAppIntent(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(shortcut))
                )
            },
        ),
    )

    private fun categoryMenu(shortcut: Shortcut, onClose: () -> Unit, onNew: () -> Unit): MenuSpec {
        val categories = CategoryOptions.candidates(
            current = shortcut.category,
            existing = viewModel.availableCategories(),
            defaults = listOf(getString(R.string.title_apps), getString(R.string.title_system)),
            presets = resources.getStringArray(R.array.category_presets).toList(),
        )
        return MenuSpec(
            title = getString(R.string.menu_change_category),
            items = categories.map { category ->
                MenuItem(category) {
                    onClose()
                    viewModel.setCategory(shortcut, category)
                }
            } + MenuItem(getString(R.string.category_new)) { onNew() },
        )
    }

    private fun packageUri(shortcut: Shortcut): Uri = Uri.fromParts("package", shortcut.id, null)

    private fun launch(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        startActivity(intent)
    }

    private fun startAppIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("ComposePreview", "No activity for $intent", e)
            Toast.makeText(this, R.string.action_open_failed, Toast.LENGTH_SHORT).show()
        }
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
