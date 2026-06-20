package cc.carm.plugin.intellij.quarkdown.settings

import java.io.File

object QuarkdownPathDetector {

    fun detect(): String? {
        System.getenv("QUARKDOWN_HOME")?.let { dir ->
            if (File(dir).exists()) return dir
        }

        detectDefaultInstallations()?.let { return it }

        return detectFromPath()
    }

    fun isValidQuarkdownHome(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val home = File(path)
        if (!home.exists() || !home.isDirectory) return false
        return hasQuarkdownBinary(home)
    }

    private fun hasQuarkdownBinary(dir: File): Boolean {
        if (File(dir, "quarkdown").exists()) return true
        if (File(dir, "quarkdown.bat").exists()) return true
        if (File(dir, "quarkdown.cmd").exists()) return true

        val binDir = File(dir, "bin")
        if (binDir.isDirectory) {
            if (File(binDir, "quarkdown").exists()) return true
            if (File(binDir, "quarkdown.bat").exists()) return true
            if (File(binDir, "quarkdown.cmd").exists()) return true
        }
        return false
    }

    private fun detectDefaultInstallations(): String? {
        val userHome = System.getProperty("user.home")
        val defaultPaths = listOf(
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
            if (File(dir, "quarkdown").exists()
                || File(dir, "quarkdown.bat").exists()
                || File(dir, "quarkdown.cmd").exists()
            ) {
                return File(dir).absolutePath
            }
        }
        return null
    }
}
