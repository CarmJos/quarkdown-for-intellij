package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import java.io.File

object QuarkdownPathDetector {

    fun detect(): String? =
        System.getenv("QUARKDOWN_HOME")?.takeIf { File(it).exists() }
            ?: detectFromDefaultInstallations()
            ?: detectFromKnownLaunchers()
            ?: detectFromPath()

    fun isValidQuarkdownHome(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val home = File(path)
        if (!home.exists() || !home.isDirectory) return false

        val libDir = File(home, "lib")
        return libDir.isDirectory && libDir.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true || hasQuarkdownBinary(home)
    }

    private fun hasQuarkdownBinary(dir: File): Boolean {
        val binDir = File(dir, "bin")
        return QuarkdownCli.LAUNCHER_NAMES.any { File(dir, it).exists() } ||
                (binDir.isDirectory && QuarkdownCli.LAUNCHER_NAMES.any { File(binDir, it).exists() })
    }

    private fun detectFromDefaultInstallations(): String? {
        val userHome = System.getProperty("user.home")
        val defaultPaths = listOf(
            "$userHome/AppData/Local/Quarkdown/",
            "$userHome/scoop/apps/quarkdown/current",
            "C:/Program Files/Quarkdown",
            // Homebrew (macOS): Apple Silicon (/opt/homebrew) and Intel (/usr/local).
            // `libexec` holds the extracted bin/ + lib/ tree, i.e. the Quarkdown home.
            "/opt/homebrew/Cellar/quarkdown/current/libexec",
            "/usr/local/Cellar/quarkdown/current/libexec",
            "/opt/quarkdown",
            "/usr/local/share/quarkdown"
        )
        for (path in defaultPaths) {
            val dir = File(path)
            if (dir.exists()) return dir.absolutePath
        }
        return null
    }

    /**
     * Looks for the `quarkdown` launcher in well-known locations.
     *
     * On macOS, applications launched from the Dock/Finder do **not** inherit the user's
     * shell `PATH`, so [detectFromPath] misses Homebrew binaries (`/opt/homebrew/bin`,
     * `/usr/local/bin`). The discovered launcher is resolved back to its installation
     * home (the directory whose `lib` folder holds the `*.jar` stdlib files) so the
     * function registry can also load the standard library.
     */
    private fun detectFromKnownLaunchers(): String? {
        val launchers = listOf(
            "/opt/homebrew/bin/quarkdown",
            "/usr/local/bin/quarkdown"
        )
        for (launcher in launchers) {
            val file = File(launcher)
            if (!file.isFile) continue
            resolveHomeFromLauncher(file)?.let { return it.absolutePath }
            return file.absolutePath
        }
        return null
    }

    private fun detectFromPath(): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            val launcher = QuarkdownCli.LAUNCHER_NAMES
                .firstNotNullOfOrNull { name -> File(dir, name).takeIf { it.isFile } }
                ?: continue
            // Prefer the installation home (with lib/*.jar) over the bare bin directory,
            // so the function registry can load the stdlib instead of returning nothing.
            resolveHomeFromLauncher(launcher)?.let { return it.absolutePath }
            return File(dir).absolutePath
        }
        return null
    }

    /**
     * Resolves a `quarkdown` launcher back to the Quarkdown installation home.
     *
     * The launcher is frequently a symlink into the real install - e.g. Homebrew's
     * `/opt/homebrew/bin/quarkdown` -> `<keg>/bin/quarkdown`, which delegates to
     * `<keg>/libexec/bin/quarkdown`. The home is the closest ancestor (or a sibling
     * `libexec` directory) that actually contains the stdlib `*.jar` files under `lib`.
     */
    private fun resolveHomeFromLauncher(launcher: File): File? {
        val parent = try {
            launcher.canonicalFile.parentFile
        } catch (_: Exception) {
            return null
        }
        var dir: File? = parent
        var depth = 0
        while (dir != null && depth < MAX_HOME_LOOKUP_DEPTH) {
            if (hasStdlibJars(dir)) return dir
            // Homebrew layout: <keg>/bin/quarkdown -> sibling <keg>/libexec/lib
            File(dir, "libexec").takeIf { hasStdlibJars(it) }?.let { return it }
            dir = dir.parentFile
            depth++
        }
        return null
    }

    private fun hasStdlibJars(dir: File): Boolean {
        val libDir = File(dir, "lib")
        if (!libDir.isDirectory) return false
        return libDir.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true
    }

    private const val MAX_HOME_LOOKUP_DEPTH = 6
}
