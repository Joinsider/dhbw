package de.fampopprol.dhbwhorb.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val downloadUrl: String,
    val publishedAt: String,
    val isPrerelease: Boolean
)

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String?,
    val release: GitHubRelease?
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val GITHUB_REPO_OWNER = "Joinsider" // Replace with your GitHub username
        private const val GITHUB_REPO_NAME = "dhbw" // Replace with your repository name
        private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"
        private const val PLAY_STORE_PACKAGE = "com.android.vending"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check if the app was installed from Google Play Store
     */
    fun isInstalledFromPlayStore(): Boolean {
        return try {
            val installerPackageName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }

            val isFromPlayStore = installerPackageName == PLAY_STORE_PACKAGE
            Log.d(TAG, "App installer package: $installerPackageName, from Play Store: $isFromPlayStore")
            isFromPlayStore
        } catch (e: Exception) {
            Log.e(TAG, "Error checking installer package", e)
            // Default to false (not from Play Store) if we can't determine
            false
        }
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error getting current version", e)
            "unknown"
        }
    }

    /**
     * Get current app version code
     */
    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Error getting current version code", e)
            0L
        }
    }

    /**
     * Check for updates from GitHub releases
     */
    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()
            Log.d(TAG, "Checking for updates. Current version: $currentVersion")

            // Don't check if installed from Play Store
            if (isInstalledFromPlayStore()) {
                Log.d(TAG, "App installed from Play Store, skipping GitHub update check")
                return@withContext UpdateInfo(
                    isUpdateAvailable = false,
                    currentVersion = currentVersion,
                    latestVersion = null,
                    release = null
                )
            }

            val latestRelease = getLatestGitHubRelease()
            if (latestRelease == null) {
                Log.w(TAG, "Could not fetch latest release from GitHub")
                return@withContext UpdateInfo(
                    isUpdateAvailable = false,
                    currentVersion = currentVersion,
                    latestVersion = null,
                    release = null
                )
            }

            val isUpdateAvailable = isNewerVersion(currentVersion, latestRelease.tagName)
            Log.d(TAG, "Update check result - Current: $currentVersion, Latest: ${latestRelease.tagName}, Update available: $isUpdateAvailable")

            UpdateInfo(
                isUpdateAvailable = isUpdateAvailable,
                currentVersion = currentVersion,
                latestVersion = latestRelease.tagName,
                release = latestRelease
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateInfo(
                isUpdateAvailable = false,
                currentVersion = getCurrentVersion(),
                latestVersion = null,
                release = null
            )
        }
    }

    /**
     * Fetch the latest release from GitHub API
     */
    private suspend fun getLatestGitHubRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "GitHub API request failed with code: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from GitHub API")
                return@withContext null
            }

            val releasesArray = JSONArray(responseBody)
            if (releasesArray.length() == 0) {
                Log.w(TAG, "No releases found in GitHub repository")
                return@withContext null
            }

            // Find the latest non-prerelease version
            for (i in 0 until releasesArray.length()) {
                val releaseJson = releasesArray.getJSONObject(i)
                val isPrerelease = releaseJson.optBoolean("prerelease", false)

                if (!isPrerelease) {
                    val assets = releaseJson.getJSONArray("assets")
                    var downloadUrl = ""

                    // Look for APK file in assets
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    return@withContext GitHubRelease(
                        tagName = releaseJson.getString("tag_name"),
                        name = releaseJson.getString("name"),
                        body = releaseJson.optString("body", ""),
                        downloadUrl = downloadUrl,
                        publishedAt = releaseJson.getString("published_at"),
                        isPrerelease = isPrerelease
                    )
                }
            }

            Log.w(TAG, "No stable releases found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching GitHub releases", e)
            null
        }
    }

    /**
     * Compare version strings to determine if a newer version is available
     * Supports semantic versioning (e.g., "1.0.1", "v1.0.1")
     */
    private fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        try {
            val currentParts = parseVersion(currentVersion)
            val latestParts = parseVersion(latestVersion)

            for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                val current = currentParts.getOrNull(i) ?: 0
                val latest = latestParts.getOrNull(i) ?: 0

                if (latest > current) return true
                if (latest < current) return false
            }

            return false // Versions are equal
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $currentVersion vs $latestVersion", e)
            return false
        }
    }

    /**
     * Parse version string into comparable integer parts
     */
    private fun parseVersion(version: String): List<Int> {
        return version
            .removePrefix("v") // Remove 'v' prefix if present
            .split(".")
            .mapNotNull { part ->
                try {
                    // Extract only the numeric part (e.g., "1.0.1-beta" -> "1", "0", "1")
                    part.takeWhile { it.isDigit() }.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
            }
    }
}
