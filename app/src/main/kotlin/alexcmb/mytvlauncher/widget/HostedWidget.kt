package alexcmb.mytvlauncher.widget

/**
 * How big a hosted widget is shown. Every widget is hosted at its shape's base size (so its
 * RemoteViews lay out comfortably) and then scaled by this factor — scaling the rendered
 * result rather than shrinking the layout, which most widgets refuse to do (they'd just
 * clip). 1f is the base; below it shrinks, above it grows.
 */
enum class WidgetSize(val scale: Float) {
    XSMALL(0.6f),
    SMALL(0.8f),
    MEDIUM(1f),
    LARGE(1.3f),
    XLARGE(1.6f),
}

/**
 * The aspect a hosted widget is laid out at, before the size scale — widgets are built to
 * different proportions and forcing the wrong one distorts their content. Same base height,
 * so the choices line up in the band; only the width (the ratio) changes.
 */
enum class WidgetShape(val baseWidthDp: Int, val baseHeightDp: Int) {
    WIDE(320, 180),       // 16:9
    PANORAMIC(360, 180),  // 2:1
    STANDARD(240, 180),   // 4:3
    SQUARE(180, 180),     // 1:1
}

/** Where in the band a hosted widget sits. */
enum class WidgetAlignment {
    START,
    CENTER,
    END,
}

/**
 * The zone to drop a newly added widget into: the first one not already taken, so successive
 * adds fill left, then centre, then right. Falls back to START once all three are in use
 * (which the widget cap normally prevents).
 */
fun nextFreeAlignment(taken: Collection<WidgetAlignment>): WidgetAlignment =
    WidgetAlignment.entries.firstOrNull { it !in taken } ?: WidgetAlignment.START

/** A widget the launcher hosts, and the size, placement and shape the user gave it. */
data class HostedWidget(
    val id: Int,
    val size: WidgetSize,
    val alignment: WidgetAlignment = WidgetAlignment.START,
    val shape: WidgetShape = WidgetShape.WIDE,
)

/** Serialises the hosted widgets; kept pure so the round-trip can be unit-tested. */
object WidgetStorage {
    fun encode(widgets: List<HostedWidget>): String =
        widgets.joinToString(SEPARATOR) {
            "${it.id}$FIELD${it.size.name}$FIELD${it.alignment.name}$FIELD${it.shape.name}"
        }

    /** Skips entries it can't read rather than losing the whole band to one bad one. */
    fun decode(stored: String): List<HostedWidget> =
        stored.split(SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD)
            val id = fields.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val size = fields.getOrNull(1)
                ?.let { name -> WidgetSize.entries.firstOrNull { it.name == name } }
                ?: WidgetSize.MEDIUM
            // Fields added over time; entries stored before each default sensibly.
            val alignment = fields.getOrNull(2)
                ?.let { name -> WidgetAlignment.entries.firstOrNull { it.name == name } }
                ?: WidgetAlignment.START
            val shape = fields.getOrNull(3)
                ?.let { name -> WidgetShape.entries.firstOrNull { it.name == name } }
                ?: WidgetShape.WIDE
            HostedWidget(id, size, alignment, shape)
        }

    private const val SEPARATOR = ","
    private const val FIELD = ":"
}
