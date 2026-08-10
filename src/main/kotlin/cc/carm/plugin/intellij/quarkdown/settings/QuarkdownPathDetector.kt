package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import java.io.File

object QuarkdownPathDetector {

    fun detect(): String? =
        System.getenv("QUARKDOWN_HOME")?.takeIf { File(it).exists() }
            ?: detectDefaultInstallations()
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

    private fun detectDefaultInstallations(): String? {
        val userHome = System.getProperty("user.home")
        val defaultPaths = listOf(
            "$userHome/AppData/Local/Quarkdown/",
            "$userHome/scoop/apps/quarkdown/current",
            "C:/Program Files/Quarkdown",
            "/usr/local/Cellar/quarkdown/current",
            "/opt/quarkdown",
            "/usr/local/share/quarkdown"
        )
        for (path in defaultPaths) {
            val dir = File(path)
            if (dir.exists()) return dir.absolutePath
        }
        return null
    }

    private fun detectFromPath(): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            if (QuarkdownCli.LAUNCHER_NAMES.any { File(dir, it).exists() }) {
                return File(dir).absolutePath
            }
        }
        return null
    }
}
