package cc.carm.plugin.intellij.quarkdown.lang.function

import java.io.File
import java.net.URL
import java.net.URLClassLoader

class QuarkdownSdkClassLoader(homePath: String, parent: ClassLoader) :
    URLClassLoader(jars(homePath), parent) {

    companion object {
        fun jars(home: String): Array<URL> {
            val libDir = File(home, "lib")
            if (!libDir.isDirectory) return emptyArray()
            return libDir.listFiles { f -> f.name.endsWith(".jar") }
                ?.map { it.toURI().toURL() }
                ?.toTypedArray()
                ?: emptyArray()
        }
    }
}
