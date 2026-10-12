package alexcmb.mytvlauncher.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-update from GitHub Releases. Release builds are signed with the release key held in
 * the CI's secrets, and the versionCode is derived from the tag the same way the CI computes
 * it. Nothing reaches the installer unchecked: the APK must come from this repository's
 * releases and be a newer build of this very app, signed with the same key ([verify]).
 */
class UpdateManager(private val context: Context) {

    data class Release(val versionName: String, val versionCode: Long, val apkUrl: String)

    /** Queries the latest GitHub release; returns null on any network/parse failure. */
    suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", context.packageName)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Release check HTTP ${connection.responseCode}")
                return@withContext null
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val apkUrl = firstApkAssetUrl(json) ?: return@withContext null
            if (!UpdateVerifier.isTrustedApkUrl(apkUrl, REPO)) {
                Log.w(TAG, "Ignoring release asset outside $REPO: $apkUrl")
                return@withContext null
            }
            val name = json.getString("tag_name").removePrefix("v")
            Release(name, ReleaseVersion.codeFromName(name), apkUrl)
        } catch (e: Exception) {
            Log.w(TAG, "fetchLatestRelease failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the APK into the app cache and returns the file. */
    suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", context.packageName)
            connectTimeout = TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
        }
        try {
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        file
    }

    /** Checks a downloaded APK against the installed app; only [UpdateVerdict.OK] may install. */
    fun verify(file: File): UpdateVerdict {
        val installed = identityOf(
            context.packageManager.getPackageInfo(context.packageName, SIGNATURE_FLAGS)
        ) ?: return UpdateVerdict.UNREADABLE
        val candidate = identityOf(
            context.packageManager.getPackageArchiveInfo(file.path, SIGNATURE_FLAGS)
        )
        return UpdateVerifier.verify(installed, candidate)
    }

    /**
     * Hands the APK to the system package installer (requires user confirmation). Only for a
     * file that passed [verify].
     */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun currentVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }

    private fun identityOf(info: PackageInfo?): ApkIdentity? {
        info ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        return ApkIdentity(info.packageName, versionCode, signersOf(info))
    }

    private fun signersOf(info: PackageInfo): Set<String> {
        // The lineage where there is one, so a build signed after a key rotation still matches;
        // else the plain signatures (pre-P, and archives on P, which only fill those in).
        val lineage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lineageOf(info) else null
        @Suppress("DEPRECATION")
        val certs = lineage ?: info.signatures ?: return emptySet()
        val sha256 = MessageDigest.getInstance("SHA-256")
        return certs.mapTo(mutableSetOf()) { cert ->
            sha256.digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun lineageOf(info: PackageInfo): Array<Signature>? {
        val signing = info.signingInfo ?: return null
        return if (signing.hasMultipleSigners()) signing.apkContentsSigners
        else signing.signingCertificateHistory
    }

    private fun firstApkAssetUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.getString("browser_download_url")
            }
        }
        return null
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val REPO = "alexcmb/MyTVLauncher"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPO/releases/latest"
        private const val TIMEOUT_MS = 15000
        private const val DOWNLOAD_TIMEOUT_MS = 60000

        // Both: GET_SIGNING_CERTIFICATES for the lineage, and GET_SIGNATURES because Android 9
        // only reads an archive's certificates when that older flag is set.
        @Suppress("DEPRECATION")
        private val SIGNATURE_FLAGS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            } else {
                PackageManager.GET_SIGNATURES
            }
    }
}
