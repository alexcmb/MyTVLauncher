package alexcmb.mytvlauncher

import alexcmb.mytvlauncher.browse.BrowseFragment
import alexcmb.mytvlauncher.widget.WidgetSlotController
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.SearchOrbView
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : FragmentActivity() {
    /** Owned here: hosting a widget needs an Activity for the bind/configure dialogs. */
    lateinit var widgetSlot: WidgetSlotController
        private set

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView

    private val timeFormat: DateFormat by lazy {
        // Locale-aware, honours the system 12/24h setting, and always includes seconds.
        val skeleton =
            if (android.text.format.DateFormat.is24HourFormat(this)) "Hms" else "hms"
        val pattern =
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
        SimpleDateFormat(pattern, Locale.getDefault())
    }

    /** Ticks the clock every second, re-aligning on the whole second so it doesn't drift. */
    private val tickRunnable = object : Runnable {
        override fun run() {
            clock.text = timeFormat.format(Date())
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        clock = findViewById(R.id.clock)
        widgetSlot = WidgetSlotController(this, findViewById(R.id.widget_slot))
        setUpSettingsOrb()
        // A launcher is the home screen: Back must not leave it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    private fun setUpSettingsOrb() {
        val orb: SearchOrbView = findViewById(R.id.settings_orb)
        orb.orbColors = SearchOrbView.Colors(ContextCompat.getColor(this, R.color.menu_accent))
        orb.setOrbIcon(ContextCompat.getDrawable(this, R.drawable.ic_settings))
        orb.contentDescription = getString(R.string.action_settings)
        orb.setOnOrbClickedListener { browseFragment()?.showGlobalMenu() }
    }

    private fun browseFragment(): BrowseFragment? =
        supportFragmentManager.findFragmentById(R.id.browse_fragment) as? BrowseFragment

    override fun onStart() {
        super.onStart()
        handler.post(tickRunnable)
        widgetSlot.startListening()
        widgetSlot.restore()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tickRunnable)
        widgetSlot.stopListening()
    }

    @Deprecated("The widget bind and configure flows are driven by the legacy result API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val granted = resultCode == RESULT_OK
        when (requestCode) {
            // Only the outcome is read: the widget id comes from the controller, since
            // the bind dialog may return OK with no data.
            WidgetSlotController.REQUEST_BIND -> widgetSlot.onBindResult(granted)
            WidgetSlotController.REQUEST_CONFIGURE -> widgetSlot.onConfigureResult(granted)
            // The system picker allocates and binds its own id, so that one does matter.
            WidgetSlotController.REQUEST_PICK -> widgetSlot.onPickResult(
                granted,
                data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            )
        }
    }
}
