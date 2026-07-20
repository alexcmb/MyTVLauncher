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
 * How a hosted widget is fitted to its tile. NATIVE hands the widget its real size so a
 * responsive one picks the matching layout (but a widget that won't shrink gets cropped);
 * FIT lays the widget out at its shape's base size and scales the result down, so it always
 * shows whole (at the cost of that per-size native variant).
 */
enum class WidgetFit {
    NATIVE,
    FIT,
}

/**
 * The zone to drop a newly added widget into: the first one not already taken, so successive
 * adds fill left, then centre, then right. Falls back to START once all three are in use
 * (which the widget cap normally prevents).
 */
fun nextFreeAlignment(taken: Collection<WidgetAlignment>): WidgetAlignment =
    WidgetAlignment.entries.firstOrNull { it !in taken } ?: WidgetAlignment.START

/** A widget the launcher hosts, and the size, placement, shape and fit the user gave it. */
data class HostedWidget(
    val id: Int,
    val size: WidgetSize,
    val alignment: WidgetAlignment = WidgetAlignment.START,
    val shape: WidgetShape = WidgetShape.WIDE,
    val fit: WidgetFit = WidgetFit.NATIVE,
)

/** The footprint the widget takes in the band: shape base times the size scale. */
fun HostedWidget.tileWidthDp(): Int = (shape.baseWidthDp * size.scale).toInt()
fun HostedWidget.tileHeightDp(): Int = (shape.baseHeightDp * size.scale).toInt()

/**
 * The size the host view is actually laid out at (and told). NATIVE lays out at the footprint
 * so the widget picks the matching variant; FIT lays out at the shape's base size and the hub
 * scales that down to the footprint, so it can't clip.
 */
fun HostedWidget.layoutWidthDp(): Int =
    if (fit == WidgetFit.NATIVE) tileWidthDp() else shape.baseWidthDp
fun HostedWidget.layoutHeightDp(): Int =
    if (fit == WidgetFit.NATIVE) tileHeightDp() else shape.baseHeightDp

/** Serialises the hosted widgets; kept pure so the round-trip can be unit-tested. */
object WidgetStorage {
    fun encode(widgets: List<HostedWidget>): String =
        widgets.joinToString(SEPARATOR) {
            "${it.id}$FIELD${it.size.name}$FIELD${it.alignment.name}$FIELD${it.shape.name}$FIELD${it.fit.name}"
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
            val fit = fields.getOrNull(4)
                ?.let { name -> WidgetFit.entries.firstOrNull { it.name == name } }
                ?: WidgetFit.NATIVE
            HostedWidget(id, size, alignment, shape, fit)
        }

    private const val SEPARATOR = ","
    private const val FIELD = ":"
}
