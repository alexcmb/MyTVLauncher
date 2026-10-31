package alexcmb.mytvlauncher.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import alexcmb.mytvlauncher.model.Shortcut
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShortcutRepository(private val context: Context) {
    // Contain IO operations.
    // Using singleton mode to avoid problems
    // that may arise from repeated initialization
    // when using in multiple components at the same time.
    private val categoryData = CategoryRepository.getInstance(context)
    private val openCountData = OpenCountRepository.getInstance(context)
    private val hiddenData = HiddenAppsRepository.getInstance(context)
    private val cache = AppCache(context)

    // Each app as last read, by package. While its package is unchanged it's reused as is: a
    // reload then doesn't open every app's files again, and the cards keep the same pictures.
    private val known = HashMap<String, AppCache.Entry>()

    private val locale get() = Locale.getDefault().toLanguageTag()

    /**
     * The list saved at the last load, to show while the real one is read; null if there is
     * none (first start, or the TV's language changed since).
     */
    suspend fun cached(): Set<Shortcut>? = withContext(Dispatchers.IO) {
        val entries = cache.read(locale) ?: return@withContext null
        synchronized(known) { entries.forEach { entry -> known.getOrPut(entry.id) { entry } } }
        entries.mapTo(LinkedHashSet(), ::shortcut)
    }

    /** Every app with a launcher entry (phone-style or TV), read from the PackageManager. */
    suspend fun load(): Set<Shortcut> = withContext(Dispatchers.IO) {
        val entries = LinkedHashMap<String, AppCache.Entry>()
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in context.packageManager.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo
                // The first entry of a package wins (as an app appears under both categories).
                if (activity.packageName == context.packageName || activity.packageName in entries) continue
                entries[activity.packageName] = entry(activity)
            }
        }
        val changed = synchronized(known) {
            val changed = known.keys != entries.keys || entries.values.any { known[it.id] !== it }
            known.clear()
            known.putAll(entries)
            changed
        }
        if (changed) cache.write(locale, entries.values.toList())
        entries.values.mapTo(LinkedHashSet(), ::shortcut)
    }

    private fun entry(activity: ActivityInfo): AppCache.Entry {
        val app = activity.applicationInfo
        // An update installs a new APK (another path on Android 9+, a new date before).
        val stamp = "${activity.name}@${app.sourceDir}@${File(app.sourceDir).lastModified()}"
        synchronized(known) { known[activity.packageName] }?.takeIf { it.stamp == stamp }?.let { return it }
        val packageManager = context.packageManager
        val banner = activity.loadBanner(packageManager)?.let(::flatten)
        val icon = if (banner == null) activity.loadIcon(packageManager)?.let(::flatten) else null
        return AppCache.Entry(
            id = activity.packageName,
            title = activity.loadLabel(packageManager).toString(),
            stamp = stamp,
            system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            icon = icon,
            banner = banner,
        )
    }

    /**
     * The picture drawn once into a bitmap, here off the main thread, rather than by each card
     * as it first shows. A drawable without a size of its own stays as it is.
     */
    private fun flatten(drawable: Drawable): Drawable =
        if (drawable is BitmapDrawable && drawable.bitmap != null) drawable
        else runCatching { BitmapDrawable(context.resources, drawable.toBitmap()) }.getOrDefault(drawable)

    private fun shortcut(entry: AppCache.Entry) = Shortcut(entry.id, entry.title, entry.icon, entry.banner).apply {
        category = categoryData.query(id) ?: categoryData.default(entry.system)
        openCount = openCountData.query(id)
    }

    fun updateOpenCount(shortcut: Shortcut) {
        openCountData.update(shortcut.id, shortcut.openCount)
    }

    fun updateCategory(id: String, category: String) {
        categoryData.update(id, category)
    }

    /** Forgets the app's chosen category: it goes back to its default (Apps or System). */
    fun resetCategory(id: String) = categoryData.delete(id)

    fun hiddenIds(): Set<String> = hiddenData.query()

    fun hide(id: String) = hiddenData.add(id)

    fun unhide(id: String) = hiddenData.remove(id)

    fun deleteById(id: String) {
        categoryData.delete(id)
        openCountData.delete(id)
        hiddenData.remove(id)
    }

}
