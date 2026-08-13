package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Downloads and installs the official Quarkdown CLI distribution.
 *
 * The distribution zip is fetched from the GitHub releases of
 * [iamgio/quarkdown](https://github.com/iamgio/quarkdown). The asset name depends on
 * the current platform (Windows / macOS / Linux × x64 / aarch64).
 *
 * Version lookup uses the GitHub API (`releases/latest`). The result is cached in
 * memory and re-validated after a configurable timeout, so repeated lookups don't
 * hammer the API.
 */
object QuarkdownInstaller {

    private val logger = Logger.getInstance(QuarkdownInstaller::class.java)

    private const val GITHUB_API_LATEST = "https://api.github.com/repos/iamgio/quarkdown/releases/latest"
    private const val DOWNLOAD_BASE = "https://github.com/iamgio/quarkdown/releases/download"

    /** How long (ms) a fetched "latest version" stays valid before a re-fetch. */
    private const val VERSION_CACHE_TTL_MS = 10 * 60 * 1000L

    private data class VersionCache(val version: String, val fetchedAt: Long)

    @Volatile
    private var versionCache: VersionCache? = null

    /** The cached latest version, or `null` when it was never fetched (or expired). */
    fun cachedLatestVersion(): String? = versionCache?.version

    /**
     * Returns the latest Quarkdown version string, or `null` when the request failed.
     * The result is cached in memory; expired entries are re-fetched.
     */
    fun fetchLatestVersion(timeoutMs: Int = 15_000): String? {
        versionCache?.let { cached ->
            if (System.currentTimeMillis() - cached.fetchedAt < VERSION_CACHE_TTL_MS) {
                return cached.version
            }
        }
        return try {
            val connection = openConnection(GITHUB_API_LATEST, timeoutMs)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "quarkdown-for-intellij")
            connection.connect()
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()
            if (code !in 200..299) {
                logger.warn("Failed to fetch latest Quarkdown version: HTTP $code")
                return null
            }
            val version = parseTagName(body) ?: return null
            versionCache = VersionCache(version, System.currentTimeMillis())
            version
        } catch (e: Exception) {
            logger.warn("Failed to fetch latest Quarkdown version", e)
            null
        }
    }

    /**
     * Downloads and extracts the Quarkdown distribution of [version] into [targetDir].
     *
     * @return the installation home directory (containing `bin/` and `lib/`), or
     *         `null` when the download or extraction failed.
     */
    fun downloadAndInstall(version: String, targetDir: File, timeoutMs: Int = 60_000): File? {
        val asset = platformAssetName()
        val url = "$DOWNLOAD_BASE/$version/$asset"
        logger.info("Downloading Quarkdown $version from $url")
        return try {
            val connection = openConnection(url, timeoutMs)
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "quarkdown-for-intellij")
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                logger.warn("Failed to download Quarkdown: HTTP $code")
                connection.disconnect()
                return null
            }
            connection.inputStream.use { input ->
                extract(input, targetDir)
            }
            connection.disconnect()
            targetDir
        } catch (e: Exception) {
            logger.warn("Failed to download/install Quarkdown", e)
            null
        }
    }

    /** Extracts the downloaded zip stream into [targetDir], stripping the top-level folder. */
    private fun extract(input: java.io.InputStream, targetDir: File) {
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // The archive contains a single top-level folder (`quarkdown/` or
                    // `quarkdown-<version>/`). Strip it so the content lands directly in
                    // targetDir, mirroring what build.gradle.kts does for the test SDK.
                    val relative = entry.name.substringAfter('/', entry.name)
                    val target = File(targetDir, relative)
                    target.parentFile?.mkdirs()
                    zis.copyTo(target.outputStream())
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Asset name of the Quarkdown release zip for the current platform. */
    fun platformAssetName(): String = when {
        SystemInfo.isWindows -> "quarkdown-windows-x64.zip"
        SystemInfo.isMac -> {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("aarch64") || arch.contains("arm")) "quarkdown-macos-aarch64.zip" else "quarkdown-macos-x64.zip"
        }
        else -> "quarkdown-linux-x64.zip"
    }

    private fun openConnection(url: String, timeoutMs: Int): HttpURLConnection =
        URI(url).toURL().openConnection() as HttpURLConnection

    /** Extracts the `tag_name` field from the GitHub API response (JSON). */
    internal fun parseTagName(json: String): String? {
        val regex = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)?.trimStart('v')?.takeIf { it.isNotBlank() }
    }
}
