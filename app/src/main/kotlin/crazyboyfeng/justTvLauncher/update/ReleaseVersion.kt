package crazyboyfeng.justTvLauncher.update

/**
 * Maps a release version name to a versionCode, mirroring the formula the CI applies
 * to the git tag. Both sides must agree or the updater can't tell newer from older.
 */
object ReleaseVersion {
    /** "v2026.7.14" or "2026.7.14" -> 2026*10000 + 7*100 + 14 = 20260714. */
    fun codeFromName(name: String): Long {
        val parts = name.removePrefix("v").split(".")
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toLongOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }
}
