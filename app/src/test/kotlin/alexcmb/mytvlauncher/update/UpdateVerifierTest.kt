package alexcmb.mytvlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVerifierTest {

    private val installed = ApkIdentity("alexcmb.mytvlauncher", 400, setOf("aaa"))

    private fun candidate(
        packageName: String = "alexcmb.mytvlauncher",
        versionCode: Long = 500,
        signers: Set<String> = setOf("aaa"),
    ) = ApkIdentity(packageName, versionCode, signers)

    @Test
    fun `accepts a newer build of the same app with the same key`() {
        assertEquals(UpdateVerdict.OK, UpdateVerifier.verify(installed, candidate()))
    }

    @Test
    fun `rejects an apk that could not be read or carries no signature`() {
        assertEquals(UpdateVerdict.UNREADABLE, UpdateVerifier.verify(installed, null))
        assertEquals(UpdateVerdict.UNREADABLE, UpdateVerifier.verify(installed, candidate(signers = emptySet())))
    }

    @Test
    fun `rejects another package, which would install a separate app`() {
        assertEquals(UpdateVerdict.WRONG_PACKAGE, UpdateVerifier.verify(installed, candidate(packageName = "evil.app")))
    }

    @Test
    fun `rejects the same or an older version`() {
        assertEquals(UpdateVerdict.NOT_NEWER, UpdateVerifier.verify(installed, candidate(versionCode = 400)))
        assertEquals(UpdateVerdict.NOT_NEWER, UpdateVerifier.verify(installed, candidate(versionCode = 300)))
    }

    @Test
    fun `rejects a build signed with another key, but accepts a rotated lineage`() {
        assertEquals(UpdateVerdict.WRONG_SIGNER, UpdateVerifier.verify(installed, candidate(signers = setOf("bbb"))))
        assertEquals(UpdateVerdict.OK, UpdateVerifier.verify(installed, candidate(signers = setOf("aaa", "ccc"))))
    }

    @Test
    fun `only trusts release assets of the repository`() {
        val repo = "alexcmb/MyTVLauncher"
        assertTrue(UpdateVerifier.isTrustedApkUrl(
            "https://github.com/alexcmb/MyTVLauncher/releases/download/v0.5/MyTvLauncher-v0.5.apk", repo))
        assertFalse(UpdateVerifier.isTrustedApkUrl(
            "https://github.com/someone/else/releases/download/v1/app.apk", repo))
        assertFalse(UpdateVerifier.isTrustedApkUrl(
            "http://github.com/alexcmb/MyTVLauncher/releases/download/v0.5/x.apk", repo))
        assertFalse(UpdateVerifier.isTrustedApkUrl(
            "https://github.com.evil.io/alexcmb/MyTVLauncher/releases/download/v0.5/x.apk", repo))
    }
}
