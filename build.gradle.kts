import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import java.net.URL
import java.time.Instant

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

// ── Auto-generation of code block languages JSON from highlight.js ──

abstract class GenerateCodeBlockLanguagesTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val markdownContent: String? = try {
            URL("https://raw.githubusercontent.com/highlightjs/highlight.js/main/SUPPORTED_LANGUAGES.md")
                .readText()
        } catch (e: Exception) {
            logger.warn("Failed to download highlight.js language list: ${e.message}. Using fallback.")
            null
        }

        val languages = if (markdownContent != null) {
            parseSupportedLanguages(markdownContent)
        } else {
            emptyList()
        }

        generateJsonFile(languages, outputDir.get().asFile)
    }

    private fun parseSupportedLanguages(markdown: String): List<Pair<String, List<String>>> {
        val result = mutableListOf<Pair<String, List<String>>>()
        val lines = markdown.lines()

        var inTable = false
        for (line in lines) {
            if (line.contains("<!-- LANGLIST -->")) {
                inTable = true
                continue
            }
            if (line.contains("<!-- LANGLIST_END -->")) {
                break
            }
            if (!inTable) continue

            if (line.matches(Regex("^\\|\\s*:?-+.*$"))) continue

            val trimmed = line.trim()
            if (trimmed.startsWith("|")) {
                val content = trimmed.removeSurrounding("|").trim()
                val cells = content.split("|").map { it.trim() }
                if (cells.size >= 2) {
                    val name = sanitizeLanguageName(cells[0])
                    val aliasesStr = cells.getOrElse(1) { "" }

                    if (name.equals("Language", ignoreCase = true)) continue
                    if (name.matches(Regex(":?[-\\s]+"))) continue

                    val aliases = aliasesStr
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
                    if (name.isNotEmpty()) {
                        result.add(name to aliases)
                    }
                }
            }
        }
        return result
    }

    private fun sanitizeLanguageName(name: String): String {
        return name.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            .trim()
    }

    private fun generateJsonFile(languages: List<Pair<String, List<String>>>, outputDir: File) {
        val targetDir = File(outputDir, "cc/carm/plugin/intellij/quarkdown/lang/codeblock")
        targetDir.mkdirs()
        val file = File(targetDir, "code-block-languages.json")

        val langList = if (languages.isEmpty()) {
            FALLBACK_LANGUAGES
        } else {
            languages
        }

        val jsonContent = buildString {
            appendLine("[")
            for ((index, pair) in langList.withIndex()) {
                val (name, aliases) = pair
                val aliasesJson = aliases.joinToString(", ") { "\"${escapeJson(it)}\"" }
                append("  {\"name\": \"${escapeJson(name)}\", \"aliases\": [$aliasesJson]}")
                if (index < langList.size - 1) append(",")
                appendLine()
            }
            appendLine("]")
        }

        file.writeText(jsonContent)
        logger.lifecycle(
            "Generated code block languages JSON: ${file.absolutePath} (${langList.size} languages)"
        )
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private val FALLBACK_LANGUAGES = listOf(
            "plaintext" to listOf("txt", "text"),
            "java" to listOf("jsp"),
            "python" to listOf("py", "gyp"),
            "javascript" to listOf("js", "jsx"),
            "typescript" to listOf("ts", "tsx"),
            "kotlin" to listOf("kt"),
            "cpp" to listOf("hpp", "cc", "hh", "cxx", "hxx"),
            "csharp" to listOf("cs"),
            "go" to listOf("golang"),
            "rust" to listOf("rs"),
            "sql" to emptyList(),
            "html" to listOf("xml", "xhtml", "svg"),
            "css" to emptyList(),
            "bash" to listOf("sh", "zsh"),
        )
    }
}

// ── End of task class definition ────────────────────────────────────

// ── Auto-generate code block languages JSON from highlight.js ───────
val generatedLanguagesDir = layout.buildDirectory.dir("generated/resources/quarkdown-codeblock-languages")

val generateCodeBlockLanguages by tasks.registering(GenerateCodeBlockLanguagesTask::class) {
    description = "Downloads SUPPORTED_LANGUAGES.md from highlight.js and generates CodeBlockLanguage JSON data"
    group = "quarkdown"

    outputDir.set(generatedLanguagesDir)
}

// Add generated resources to main resource set (bundled into JAR)
sourceSets.main.get().resources.srcDir(generatedLanguagesDir)

// Ensure the generation task runs before resources are processed
tasks.named("processResources") {
    dependsOn(generateCodeBlockLanguages)
}

// ── End of auto-generation configuration ────────────────────────────

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

    // IntelliJ Platform Gradle Plugin Dependencies Extension
    // read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)

        // Plugin dependencies - bundled plugins
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.intellij.intelliLang")
        bundledPlugin("com.intellij.platform.images")
        bundledPlugin("org.intellij.plugins.markdown")

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

// Fix searchable options builder locale issue on non-English systems
tasks.named<JavaExec>("buildSearchableOptions") {
    jvmArgs("-Duser.language=en", "-Duser.country=US")
}

// Ensure sourcesJar picks up the generated code block languages
tasks.named("sourcesJar") {
    dependsOn(generateCodeBlockLanguages)
}

// Print Quarkdown detection result
println("Quarkdown home: ${quarkdownHome ?: "Not found - Quarkdown API will not be available"}")
