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

/**
 * Hosts a single app widget in a fixed slot.
 *
 * Android TV is hostile territory for widgets: it ships no widget picker, so the
 * provider list is built here, and a normal app can never hold BIND_APPWIDGET, so
 * binding falls back to the system consent dialog — which not every device has.
 * Every one of those steps is therefore guarded rather than assumed.
 */
class WidgetSlotController(
    private val activity: Activity,
    private val slot: ViewGroup,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val repository = WidgetRepository.getInstance(activity)

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
        if (appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrShow(id)
        } else {
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

    fun onBindResult(granted: Boolean, id: Int) {
        if (granted) configureOrShow(id) else host.deleteAppWidgetId(id)
    }

    fun onConfigureResult(configured: Boolean, id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (configured && info != null) show(id, info) else host.deleteAppWidgetId(id)
    }

    private fun configureOrShow(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id) ?: return
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
            host.deleteAppWidgetId(id)
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
    }

    companion object {
        private const val TAG = "WidgetSlotController"
        private const val HOST_ID = 1024
        const val REQUEST_BIND = 1001
        const val REQUEST_CONFIGURE = 1002
    }
}
