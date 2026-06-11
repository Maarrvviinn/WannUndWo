package de.marvin.wannundwo.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val apkUrl: String,
    val assetName: String
)

object GitHubUpdateManager {
    private const val PLACEHOLDER = "REPLACE_ME"

    suspend fun checkForUpdate(context: Context, currentVersionCode: Int): ReleaseInfo? = withContext(Dispatchers.IO) {
        val owner = context.getString(de.marvin.wannundwo.R.string.github_owner).trim()
        val repo = context.getString(de.marvin.wannundwo.R.string.github_repo).trim()
        if (owner.isBlank() || repo.isBlank() || owner == PLACEHOLDER || repo == PLACEHOLDER) return@withContext null

        val request = URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            request.connectTimeout = 10_000
            request.readTimeout = 10_000
            request.setRequestProperty("Accept", "application/vnd.github+json")
            request.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")

            if (request.responseCode !in 200..299) return@withContext null

            val body = request.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name")
            val versionCode = tagName.filter(Char::isDigit).toIntOrNull() ?: return@withContext null
            if (versionCode <= currentVersionCode) return@withContext null

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl = ""
            var assetName = ""
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    assetName = name
                    break
                }
            }
            if (apkUrl.isBlank()) return@withContext null

            ReleaseInfo(
                versionCode = versionCode,
                versionName = json.optString("name", tagName),
                tagName = tagName,
                apkUrl = apkUrl,
                assetName = assetName.ifBlank { "WannUndWo-v$versionCode.apk" }
            )
        } finally {
            request.disconnect()
        }
    }

    suspend fun downloadApk(context: Context, releaseInfo: ReleaseInfo): File? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(cacheDir, releaseInfo.assetName)
        val connection = URL(releaseInfo.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            if (connection.responseCode !in 200..299) return@withContext null

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } finally {
            connection.disconnect()
        }
    }
}