import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import java.time.Instant

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// Quarkdown installation path detection
val quarkdownHome: String? = System.getenv("QUARKDOWN_HOME")
    ?: findQuarkdownFromPath()
    ?: findDefaultInstallations()

fun findQuarkdownFromPath(): String? {
    val path = System.getenv("PATH") ?: return null
    val pathDirs = path.split(File.pathSeparator)
    for (dir in pathDirs) {
        val quarkdownBin = File(dir, "quarkdown")
        if (quarkdownBin.exists() || File(dir, "quarkdown.bat").exists() || File(dir, "quarkdown.cmd").exists()) {
            return File(dir).parent
        }
    }
    return null
}

fun findDefaultInstallations(): String? {
    val defaultPaths = listOf(
        // Windows scoop
        "${System.getProperty("user.home")}/scoop/apps/quarkdown/current",
        // Windows default
        "C:/Program Files/Quarkdown",
        // macOS brew
        "/usr/local/Cellar/quarkdown/current",
        // Linux
        "/opt/quarkdown",
        "/usr/local/share/quarkdown"
    )
    
    for (path in defaultPaths) {
        if (File(path).exists()) {
            return path
        }
    }
    return null
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)

        // Plugin dependencies - bundled plugins
        bundledPlugin("com.intellij.platform.images")
    }

    // Quarkdown dependencies - add Quarkdown jars if available
    if (quarkdownHome != null) {
        val libDir = File("$quarkdownHome/lib")
        if (libDir.exists()) {
            val quarkdownJars = libDir.listFiles { file -> 
                file.name.startsWith("quarkdown") && file.name.endsWith(".jar") 
            } ?: emptyArray()
            
            for (jar in quarkdownJars) {
                implementation(files(jar.absolutePath))
            }
            
            // Also add required dependencies
            val additionalJars = libDir.listFiles { file -> 
                (file.name.startsWith("flexmark") || 
                 file.name.startsWith("antlr4-runtime") ||
                 file.name.startsWith("kotlin-stdlib") ||
                 file.name.startsWith("gson") ||
                 file.name.startsWith("commons-") ||
                 file.name.startsWith("kotlinx-") ||
                 file.name.startsWith("ktor-") ||
                 file.name.startsWith("slf4j") ||
                 file.name.startsWith("log4j")) && 
                file.name.endsWith(".jar")
            } ?: emptyArray()
            
            for (jar in additionalJars) {
                implementation(files(jar.absolutePath))
            }
        }
    }
}

// Project metadata and developer information
allprojects {
    apply(plugin = "java")

    java {
        withSourcesJar()
        withJavadocJar()
    }

    // Configure JAR manifest with project metadata
    tasks.withType<Jar> {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "CarmJos",
                "Built-By" to "CarmJos",
                "Build-Jdk" to System.getProperty("java.version"),
                "Build-Timestamp" to Instant.now().toString(),
                "Quarkdown-Home" to (quarkdownHome ?: "not-found")
            )
        }
    }
}

// Print Quarkdown detection result
println("Quarkdown home: ${quarkdownHome ?: "Not found - Quarkdown API will not be available"}")
