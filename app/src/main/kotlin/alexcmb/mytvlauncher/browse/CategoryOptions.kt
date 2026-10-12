package alexcmb.mytvlauncher.browse

/** Builds the category choices offered when moving a shortcut out of its current row. */
object CategoryOptions {
    /**
     * Categories already in use first (most relevant), then the built-in defaults, then the
     * presets; de-duplicated, with the shortcut's current category left out since moving it
     * there would be a no-op.
     */
    fun candidates(
        current: String,
        existing: List<String>,
        defaults: List<String>,
        presets: List<String>,
    ): List<String> = (existing + defaults + presets).distinct().filter { it != current }

    /**
     * A typed category name made usable, or null if there's nothing to use: spaces trimmed
     * and collapsed, and no leading '#', which marks the built-in tabs' keys (HomeTabs).
     */
    fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ")
            .takeIf { it.isNotEmpty() && !it.startsWith("#") }
}
