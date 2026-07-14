package crazyboyfeng.justTvLauncher.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update from GitHub Releases. All builds share one signing key (see the
 * committed debug keystore) so Android accepts the update, and the release
 * versionCode is derived from the tag the same way the CI computes it.
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
            val name = json.getString("tag_name").removePrefix("v")
            Release(name, versionCodeFromName(name), apkUrl)
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

    /** Hands the APK to the system package installer (requires user confirmation). */
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

    /** Mirrors the CI formula: 2026.7.14 -> 2026*10000 + 7*100 + 14 = 20260714. */
    private fun versionCodeFromName(name: String): Long {
        val parts = name.split(".")
        val major = parts.getOrNull(0)?.toLongOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toLongOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toLongOrNull() ?: 0
        return major * 10000 + minor * 100 + patch
    }

    companion object {
        private const val TAG = "UpdateManager"
        private const val REPO = "alexcmb/MyTVLauncher"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPO/releases/latest"
        private const val TIMEOUT_MS = 15000
        private const val DOWNLOAD_TIMEOUT_MS = 60000
    }
}
