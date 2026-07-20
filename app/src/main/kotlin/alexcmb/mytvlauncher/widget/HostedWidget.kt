package alexcmb.mytvlauncher.widget

/**
 * How big a hosted widget is shown. Every widget is hosted at one base size (so its
 * RemoteViews lay out comfortably) and then scaled by this factor — scaling the rendered
 * result rather than shrinking the layout, which most widgets refuse to do (they'd just
 * clip). 1f is the base; below it shrinks, above it grows.
 */
enum class WidgetSize(val scale: Float) {
    XSMALL(0.6f),
    SMALL(0.8f),
    MEDIUM(1f),
    LARGE(1.3f),
    XLARGE(1.6f);

    companion object {
        /** The size every widget is actually hosted (and told to lay out) at, before scaling. */
        const val BASE_WIDTH_DP = 320
        const val BASE_HEIGHT_DP = 180
    }
}

/** Where in the band a hosted widget sits. */
enum class WidgetAlignment {
    START,
    CENTER,
    END,
}

/** A widget the launcher hosts, and the size and placement the user gave it. */
data class HostedWidget(
    val id: Int,
    val size: WidgetSize,
    val alignment: WidgetAlignment = WidgetAlignment.START,
)

/** Serialises the hosted widgets; kept pure so the round-trip can be unit-tested. */
object WidgetStorage {
    fun encode(widgets: List<HostedWidget>): String =
        widgets.joinToString(SEPARATOR) { "${it.id}$FIELD${it.size.name}$FIELD${it.alignment.name}" }

    /** Skips entries it can't read rather than losing the whole band to one bad one. */
    fun decode(stored: String): List<HostedWidget> =
        stored.split(SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD)
            val id = fields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val size = fields.getOrNull(1)
                ?.let { name -> WidgetSize.entries.firstOrNull { it.name == name } }
                ?: WidgetSize.MEDIUM
            // Third field added later; entries stored before it default to START.
            val alignment = fields.getOrNull(2)
                ?.let { name -> WidgetAlignment.entries.firstOrNull { it.name == name } }
                ?: WidgetAlignment.START
            HostedWidget(id, size, alignment)
        }

    private const val SEPARATOR = ","
    private const val FIELD = ":"
}
