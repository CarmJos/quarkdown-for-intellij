package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import com.intellij.openapi.util.SystemInfo
import java.io.File

object QuarkdownPathDetector {

    fun detect(): String? =
        System.getenv("QUARKDOWN_HOME")?.takeIf { File(it).exists() }
            ?: detectFromDefaultInstallations()
            ?: detectFromKnownLaunchers()
            ?: detectFromPath()

    /**
     * Resolves a configured path — a home directory, a `bin/` folder, or a launcher file
     * itself — to the actual Quarkdown installation home: the directory whose `lib`
     * folder holds the `*.jar` files required to launch the LSP server.
     *
     * This handles the common mistake of pointing the setting at the *launcher*
     * location (e.g. `/opt/homebrew/bin`, where the `quarkdown` symlink lives) instead
     * of the installation home. Without this the LSP server would be launched with a
     * broken classpath (`<launcher-dir>/lib/asterisk`) and die with
     * `ClassNotFoundException: com.quarkdown.cli.QuarkdownCliKt`.
     *
     * @return the resolved installation home, or `null` when [configuredPath] is blank
     * or does not point at anything that resembles a Quarkdown installation.
     */
    fun resolveHome(configuredPath: String?): String? {
        if (configuredPath.isNullOrBlank()) return null
        val file = File(configuredPath.trim())
        if (!file.exists()) return null
        return when {
            // The configured path is the launcher file itself (e.g. .../bin/quarkdown).
            file.isFile -> resolveHomeFromLauncher(file)?.absolutePath ?: file.parentFile?.absolutePath

            // Already a proper home (`lib/*.jar` present).
            hasStdlibJars(file) -> file.absolutePath

            else -> {
                // A `bin/` folder (or a plain directory) that contains the launcher.
                val launcher = findLauncherIn(file)
                if (launcher != null) {
                    resolveHomeFromLauncher(launcher)?.absolutePath ?: file.absolutePath
                } else {
                    null
                }
            }
        }
    }

    /** Finds the platform-specific `quarkdown` launcher inside [dir] (or its `bin/` sub-directory). */
    private fun findLauncherIn(dir: File): File? = QuarkdownCli.findLauncherIn(dir)

    /**
     * Well-known installation directories for the current OS.
     *
     *  - **Windows**: per-user `%LOCALAPPDATA%\Quarkdown`, Scoop, Program Files.
     *  - **macOS**: Homebrew (Apple Silicon `/opt/homebrew` and Intel `/usr/local`). The
     *    keg layout is `<keg>/bin` (wrapper) + `<keg>/libexec/{bin,lib}`; `current` is the
     *    version symlink, but a versioned keg (e.g. `2.5.0`) is also accepted as a fallback.
     *  - **Linux**: `/opt` and `/usr/local/share` (generic unpack), plus Homebrew-Linux
     *    (`/home/linuxbrew/.linuxbrew`). `/usr/local/Cellar` is covered as the Intel/brew
     *    prefix used on macOS and Linux Homebrew.
     */
    private fun detectFromDefaultInstallations(): String? {
        val userHome = System.getProperty("user.home")
        val candidates = when {
            SystemInfo.isWindows -> listOf(
                // Per-user install directory (official installer).
                "${System.getenv("LOCALAPPDATA") ?: "$userHome/AppData/Local"}/Quarkdown",
                "$userHome/scoop/apps/quarkdown/current",
                "C:/Program Files/Quarkdown",
            )

            SystemInfo.isMac -> listOf(
                "/opt/homebrew/Cellar/quarkdown/current/libexec",
                "/usr/local/Cellar/quarkdown/current/libexec",
                // Versioned kegs when the `current` symlink is absent.
                "/opt/homebrew/Cellar/quarkdown/2.5.0/libexec",
                "/usr/local/Cellar/quarkdown/2.5.0/libexec",
                "/opt/quarkdown",
                "/usr/local/share/quarkdown",
            )

            else -> listOf(
                // Linux Homebrew and generic unpack locations.
                "/home/linuxbrew/.linuxbrew/Cellar/quarkdown/current/libexec",
                "/home/linuxbrew/.linuxbrew/Cellar/quarkdown/2.5.0/libexec",
                "/opt/quarkdown",
                "/usr/local/share/quarkdown",
                "/usr/local/lib/quarkdown",
            )
        }
        for (path in candidates) {
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
        val launchers = when {
            SystemInfo.isMac -> listOf(
                "/opt/homebrew/bin/quarkdown",
                "/usr/local/bin/quarkdown",
            )

            SystemInfo.isLinux -> listOf(
                "/usr/local/bin/quarkdown",
                "/usr/bin/quarkdown",
                "/home/linuxbrew/.linuxbrew/bin/quarkdown",
            )

            else -> emptyList()
        }
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

    /**
     * True when [dir] is a directory whose `lib` folder contains at least one `.jar`
     * (the Quarkdown stdlib / LSP classpath).
     */
    fun hasStdlibJars(dir: File): Boolean {
        val libDir = File(dir, "lib")
        if (!libDir.isDirectory) return false
        return libDir.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true
    }

    private const val MAX_HOME_LOOKUP_DEPTH = 6
}
