package alexcmb.mytvlauncher

import alexcmb.mytvlauncher.widget.WidgetSlotController
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

class LauncherActivity : FragmentActivity() {
    /** Owned here: hosting a widget needs an Activity for the bind/configure dialogs. */
    val widgetSlot: WidgetSlotController by lazy { WidgetSlotController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        // A launcher is the home screen: Back must not leave it.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    override fun onStart() {
        super.onStart()
        widgetSlot.startListening()
    }

    override fun onStop() {
        super.onStop()
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
