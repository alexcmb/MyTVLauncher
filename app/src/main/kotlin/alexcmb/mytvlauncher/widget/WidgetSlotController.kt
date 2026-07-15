package alexcmb.mytvlauncher.widget

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.repository.WidgetRepository
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Hosts a single app widget in a fixed slot.
 *
 * Android TV is hostile territory for widgets: it ships no widget picker, so the
 * provider list is built here, and a normal app can never hold BIND_APPWIDGET, so
 * binding falls back to the system consent dialog — which not every device has.
 * Every one of those steps is therefore guarded, and every failure is reported to
 * the user rather than leaving an empty slot with no explanation.
 */
class WidgetSlotController(
    private val activity: Activity,
    private val slot: ViewGroup,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val repository = WidgetRepository.getInstance(activity)

    /**
     * The widget being bound/configured. Held here rather than read back from the
     * result Intent: the bind dialog is allowed to return OK with no data at all.
     */
    private var pendingId = WidgetRepository.NO_WIDGET

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun hasWidget(): Boolean = repository.queryId() != WidgetRepository.NO_WIDGET

    /** Providers installed on the device; empty on a TV with no phone apps sideloaded. */
    fun availableProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    /** Re-attaches the stored widget, dropping it if its provider went away. */
    fun restore() {
        val id = repository.queryId()
        if (id == WidgetRepository.NO_WIDGET) return
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            Log.w(TAG, "Provider for widget $id is gone; dropping it")
            remove()
            return
        }
        show(id, info)
    }

    fun add(provider: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            Log.i(TAG, "Bound widget $id without asking")
            configureOrShow(id)
        } else {
            Log.i(TAG, "Not allowed to bind widget $id; asking the user")
            requestBindPermission(id, provider)
        }
    }

    fun remove() {
        val id = repository.queryId()
        if (id != WidgetRepository.NO_WIDGET) {
            host.deleteAppWidgetId(id)
        }
        repository.deleteId()
        slot.removeAllViews()
        slot.visibility = View.GONE
    }

    fun onBindResult(granted: Boolean) {
        val id = pendingId
        if (id == WidgetRepository.NO_WIDGET) return
        if (granted) configureOrShow(id) else abandon(id, R.string.widget_bind_refused)
    }

    fun onConfigureResult(configured: Boolean) {
        val id = pendingId
        if (id == WidgetRepository.NO_WIDGET) return
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (configured && info != null) show(id, info)
        else abandon(id, R.string.widget_configure_failed)
    }

    private fun configureOrShow(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            Log.w(TAG, "Widget $id has no info after binding")
            abandon(id, R.string.widget_bind_refused)
            return
        }
        val configure = info.configure
        if (configure == null) {
            show(id, info)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CONFIGURE)
        } catch (e: Exception) {
            // Some widgets declare a configure activity that isn't reachable here.
            Log.w(TAG, "Cannot configure $configure; showing unconfigured", e)
            show(id, info)
        }
    }

    private fun requestBindPermission(id: Int, provider: AppWidgetProviderInfo) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_BIND)
        } catch (e: Exception) {
            Log.w(TAG, "This device has no widget bind dialog", e)
            abandon(id, R.string.widget_bind_unavailable)
        }
    }

    private fun show(id: Int, info: AppWidgetProviderInfo) {
        val view = host.createView(activity, id, info)
        val metrics = activity.resources.displayMetrics
        val heightDp =
            (activity.resources.getDimensionPixelSize(R.dimen.widget_slot_height) / metrics.density)
                .toInt()
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        slot.removeAllViews()
        slot.addView(view)
        slot.visibility = View.VISIBLE
        repository.updateId(id)
        pendingId = WidgetRepository.NO_WIDGET
        Log.i(TAG, "Showing widget $id (${widthDp}x${heightDp}dp)")
    }

    private fun abandon(id: Int, @StringRes message: Int) {
        host.deleteAppWidgetId(id)
        pendingId = WidgetRepository.NO_WIDGET
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "WidgetSlotController"
        private const val HOST_ID = 1024
        const val REQUEST_BIND = 1001
        const val REQUEST_CONFIGURE = 1002
    }
}
