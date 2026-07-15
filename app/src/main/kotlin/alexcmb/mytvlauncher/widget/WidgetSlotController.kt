package alexcmb.mytvlauncher.widget

import alexcmb.mytvlauncher.R
import alexcmb.mytvlauncher.repository.WidgetRepository
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Hosts a single app widget in a fixed slot.
 *
 * Binding is the hard part. BIND_APPWIDGET is signature|privileged, so no third-party
 * launcher can hold it; the usual escape hatch is a consent screen in Settings that
 * white-lists the caller, and the Android TV build of Settings doesn't ship it. So
 * binding is attempted every way the platform offers, and if none is available the
 * user is told about the one-time adb grant that TV launchers rely on.
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
            Log.i(TAG, "Bound widget $id directly")
            configureOrShow(id)
            return
        }
        // Not white-listed. Ask the system to ask the user (phone-style Settings).
        if (start(bindConsentIntent(id, provider), REQUEST_BIND)) return
        // No consent screen. Let the system picker bind on our behalf, if it has one.
        if (start(systemPickerIntent(id), REQUEST_PICK)) return
        // Out of options: the permission can only come from adb now.
        Log.w(TAG, "No way to bind widget $id on this device")
        host.deleteAppWidgetId(id)
        pendingId = WidgetRepository.NO_WIDGET
        showPermissionHelp()
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

    /** The system picker binds the provider itself, so its id wins over ours. */
    fun onPickResult(granted: Boolean, pickedId: Int) {
        val id = if (pickedId != WidgetRepository.NO_WIDGET) pickedId else pendingId
        if (id == WidgetRepository.NO_WIDGET) return
        if (granted) {
            pendingId = id
            configureOrShow(id)
        } else {
            abandon(id, R.string.widget_bind_refused)
        }
    }

    fun onConfigureResult(configured: Boolean) {
        val id = pendingId
        if (id == WidgetRepository.NO_WIDGET) return
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (configured && info != null) show(id, info)
        else abandon(id, R.string.widget_configure_failed)
    }

    private fun bindConsentIntent(id: Int, provider: AppWidgetProviderInfo) =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
        }

    private fun systemPickerIntent(id: Int) =
        Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            // Without these the picker offers to create its own custom entries.
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList())
        }

    private fun start(intent: Intent, requestCode: Int): Boolean = try {
        activity.startActivityForResult(intent, requestCode)
        true
    } catch (e: ActivityNotFoundException) {
        Log.i(TAG, "No activity for ${intent.action}")
        false
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
        if (!start(intent, REQUEST_CONFIGURE)) {
            // Some widgets name a configure activity that isn't reachable here.
            Log.w(TAG, "Cannot configure $configure; showing unconfigured")
            show(id, info)
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

    /** Spells out the adb grant, since the device offers no way to ask on screen. */
    private fun showPermissionHelp() {
        val command = "adb shell appwidget grantbind --package ${activity.packageName}"
        AlertDialog.Builder(activity)
            .setTitle(R.string.widget_permission_title)
            .setMessage(activity.getString(R.string.widget_permission_message, command))
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        const val REQUEST_PICK = 1003
    }
}
