package alexcmb.mytvlauncher.repository

import alexcmb.mytvlauncher.util.SingletonHolder
import alexcmb.mytvlauncher.widget.HostedWidget
import alexcmb.mytvlauncher.widget.WidgetAlignment
import alexcmb.mytvlauncher.widget.WidgetSize
import alexcmb.mytvlauncher.widget.WidgetStorage
import android.content.Context

/** Remembers which app widgets the launcher hosts, in the order they were added. */
class WidgetRepository private constructor(context: Context) {
    companion object : SingletonHolder<WidgetRepository, Context>(::WidgetRepository) {
        private const val KEY = "hosted"
    }

    private val sharedPreferences = context.getSharedPreferences("widget", 0)

    fun query(): List<HostedWidget> =
        WidgetStorage.decode(sharedPreferences.getString(KEY, "").orEmpty())

    fun add(widget: HostedWidget) = save(query() + widget)

    fun remove(id: Int) = save(query().filterNot { it.id == id })

    fun updateSize(id: Int, size: WidgetSize) =
        save(query().map { if (it.id == id) it.copy(size = size) else it })

    fun updateAlignment(id: Int, alignment: WidgetAlignment) =
        save(query().map { if (it.id == id) it.copy(alignment = alignment) else it })

    private fun save(widgets: List<HostedWidget>) =
        sharedPreferences.edit().putString(KEY, WidgetStorage.encode(widgets)).apply()
}
