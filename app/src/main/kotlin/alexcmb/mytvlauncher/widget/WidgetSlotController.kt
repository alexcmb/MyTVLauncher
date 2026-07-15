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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * Hosts the launcher's app widgets in a fixed band.
 *
 * Binding is the hard part. BIND_APPWIDGET is signature|privileged, so no third-party
 * launcher can hold it; the usual escape hatch is a consent screen in Settings that
 * white-lists the caller, and the Android TV build of Settings doesn't ship it. So
 * binding is attempted every way the platform offers, and if none is available the
 * user is told about the one-time adb grant that TV launchers rely on.
 */
class WidgetSlotController(private val activity: Activity) {
    private val appWidgetManager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val repository = WidgetRepository.getInstance(activity)

    // The band belongs to the widgets page, which comes and goes as pages are switched.
    private var band: LinearLayout? = null
    private var emptyHint: View? = null

    /**
     * The widget being bound/configured. Held here rather than read back from the
     * result Intent: the bind dialog is allowed to return OK with no data at all.
     */
    private var pendingId = NO_WIDGET

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun hasWidgets(): Boolean = repository.query().isNotEmpty()

    /** Providers installed on the device; empty on a TV with no phone apps sideloaded. */
    fun availableProviders(): List<AppWidgetProviderInfo> = appWidgetManager.installedProviders

    /** The hosted widgets and their labels, for the manage menus. */
    fun hostedWidgets(): List<Pair<Int, CharSequence>> = repository.query().mapNotNull { hosted ->
        val info = appWidgetManager.getAppWidgetInfo(hosted.id) ?: return@mapNotNull null
        hosted.id to info.loadLabel(activity.packageManager)
    }

    fun attachBand(band: LinearLayout, emptyHint: View) {
        this.band = band
        this.emptyHint = emptyHint
        refresh()
    }

    fun detachBand() {
        band = null
        emptyHint = null
    }

    /** Rebuilds the band from what's stored, dropping widgets whose provider went away. */
    fun refresh() {
        val band = band ?: return
        band.removeAllViews()
        repository.query().forEach { hosted ->
            val info = appWidgetManager.getAppWidgetInfo(hosted.id)
            if (info == null) {
                Log.w(TAG, "Provider for widget ${hosted.id} is gone; dropping it")
                host.deleteAppWidgetId(hosted.id)
                repository.remove(hosted.id)
                return@forEach
            }
            band.addView(createView(hosted, info))
        }
        val empty = band.childCount == 0
        band.visibility = if (empty) View.GONE else View.VISIBLE
        emptyHint?.visibility = if (empty) View.VISIBLE else View.GONE
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
        pendingId = NO_WIDGET
        showPermissionHelp()
    }

    fun remove(id: Int) {
        host.deleteAppWidgetId(id)
        repository.remove(id)
        refresh()
    }

    fun resize(id: Int, size: WidgetSize) {
        repository.updateSize(id, size)
        refresh()
    }

    fun onBindResult(granted: Boolean) {
        val id = pendingId
        if (id == NO_WIDGET) return
        if (granted) configureOrShow(id) else abandon(id, R.string.widget_bind_refused)
    }

    /** The system picker binds the provider itself, so its id wins over ours. */
    fun onPickResult(granted: Boolean, pickedId: Int) {
        val id = if (pickedId != NO_WIDGET) pickedId else pendingId
        if (id == NO_WIDGET) return
        if (granted) {
            pendingId = id
            configureOrShow(id)
        } else {
            abandon(id, R.string.widget_bind_refused)
        }
    }

    fun onConfigureResult(configured: Boolean) {
        val id = pendingId
        if (id == NO_WIDGET) return
        if (configured && appWidgetManager.getAppWidgetInfo(id) != null) keep(id)
        else abandon(id, R.string.widget_configure_failed)
    }

    private fun createView(hosted: HostedWidget, info: AppWidgetProviderInfo): View {
        val view = host.createView(activity, hosted.id, info)
        val density = activity.resources.displayMetrics.density
        view.layoutParams = LinearLayout.LayoutParams(
            (hosted.size.widthDp * density).toInt(),
            (hosted.size.heightDp * density).toInt()
        ).apply { rightMargin = (WIDGET_GAP_DP * density).toInt() }
        view.updateAppWidgetSize(
            null, hosted.size.widthDp, hosted.size.heightDp, hosted.size.widthDp,
            hosted.size.heightDp
        )
        return view
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
            keep(id)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        if (!start(intent, REQUEST_CONFIGURE)) {
            // Some widgets name a configure activity that isn't reachable here.
            Log.w(TAG, "Cannot configure $configure; keeping it unconfigured")
            keep(id)
        }
    }

    private fun keep(id: Int) {
        repository.add(HostedWidget(id, WidgetSize.MEDIUM))
        pendingId = NO_WIDGET
        refresh()
        Log.i(TAG, "Hosting widget $id")
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
        pendingId = NO_WIDGET
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "WidgetSlotController"
        private const val HOST_ID = 1024
        private const val NO_WIDGET = -1
        private const val WIDGET_GAP_DP = 16
        const val REQUEST_BIND = 1001
        const val REQUEST_CONFIGURE = 1002
        const val REQUEST_PICK = 1003
    }
}
