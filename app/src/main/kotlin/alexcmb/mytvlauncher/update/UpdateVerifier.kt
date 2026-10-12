package alexcmb.mytvlauncher.update

/** What identifies an APK for the update check: who it is, which version, who signed it. */
data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 (hex) of each signing certificate. */
    val signers: Set<String>,
)

/** Outcome of checking a downloaded APK against the installed app. */
enum class UpdateVerdict { OK, UNREADABLE, WRONG_PACKAGE, NOT_NEWER, WRONG_SIGNER }

/**
 * Decides whether a downloaded APK may be handed to the installer. Android itself refuses an
 * update signed with another key, but it would happily install an APK with a *different*
 * package — a whole other app — so that, the version and the signer are all checked here
 * before the user is ever asked. Pure, so it's unit-tested.
 */
object UpdateVerifier {

    /** Only assets of this repository's releases are ever downloaded. */
    fun isTrustedApkUrl(url: String, repo: String): Boolean =
        url.startsWith("https://github.com/$repo/releases/download/")

    fun verify(installed: ApkIdentity, candidate: ApkIdentity?): UpdateVerdict = when {
        candidate == null || candidate.signers.isEmpty() -> UpdateVerdict.UNREADABLE
        candidate.packageName != installed.packageName -> UpdateVerdict.WRONG_PACKAGE
        candidate.versionCode <= installed.versionCode -> UpdateVerdict.NOT_NEWER
        // Any certificate in common: allows for a key rotation's lineage, not for a stranger.
        candidate.signers.none { it in installed.signers } -> UpdateVerdict.WRONG_SIGNER
        else -> UpdateVerdict.OK
    }
}
