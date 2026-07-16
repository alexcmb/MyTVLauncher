package alexcmb.mytvlauncher.widget

/** How much room a hosted widget takes in the band. */
enum class WidgetSize(val widthDp: Int, val heightDp: Int) {
    XSMALL(160, 100),
    SMALL(200, 120),
    MEDIUM(320, 180),
    LARGE(480, 240),
    XLARGE(600, 300),
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
