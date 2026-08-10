import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.io.File
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.zip.ZipInputStream

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

// ── Quarkdown installation path detection ──────────────────────────
// Resolves a local Quarkdown installation home. A home is only considered valid when it
// contains `lib/quarkdown-stdlib.jar`. Candidates are checked in order:
//   1. the QUARKDOWN_HOME environment variable,
//   2. well-known install locations (scoop, Program Files, brew, /opt, …),
//   3. a `quarkdown` launcher found on PATH (its home is resolved by walking up the
//      directory chain until `lib/` is found — this handles both `<home>/bin` and
//      scoop-style shims correctly).

val quarkdownHome: String? = resolveQuarkdownHome()?.absolutePath

fun resolveQuarkdownHome(): File? =
    validQuarkdownHome(System.getenv("QUARKDOWN_HOME"))
        ?: findDefaultInstallations()
        ?: findQuarkdownFromPath()

fun findQuarkdownFromPath(): File? {
    val path = System.getenv("PATH") ?: return null
    val pathDirs = path.split(File.pathSeparator)
    for (dir in pathDirs) {
        if (dir.isBlank()) continue
        val hasLauncher = File(dir, "quarkdown").exists() ||
            File(dir, "quarkdown.bat").exists() ||
            File(dir, "quarkdown.cmd").exists()
        if (!hasLauncher) continue
        // The launcher is usually in `<home>/bin` or in a shim directory (e.g. scoop's
        // `shims/`). Walk up the directory chain and return the first ancestor that is a
        // valid Quarkdown home (contains `lib/quarkdown-stdlib.jar`).
        var candidate = File(dir).absoluteFile
        while (candidate != null) {
            val home = validQuarkdownHome(candidate.absolutePath)
            if (home != null) return home
            candidate = candidate.parentFile
        }
    }
    return null
}

fun findDefaultInstallations(): File? {
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
        validQuarkdownHome(path)?.let { return it }
    }
    return null
}

/** Returns [path] as a File when it is a valid Quarkdown home, or `null`. */
fun validQuarkdownHome(path: String?): File? {
    if (path.isNullOrBlank()) return null
    val home = File(path.trim())
    return home.takeIf { File(home, "lib/quarkdown-stdlib.jar").exists() }
}

// ── Quarkdown SDK auto-download for tests ──────────────────────────
// The function-registry & completion tests reflect against a real Quarkdown standard
// library (com.quarkdown.stdlib.Stdlib). Instead of relying on a local installation
// (which CI does not have) or a hand-written fake, the official Quarkdown CLI
// distribution is downloaded from the GitHub releases and extracted into the project's
// `build/` directory. A locally installed Quarkdown (QUARKDOWN_HOME or a default install
// location) is preferred when present to avoid re-downloading.

/** Directory under `build/` where the downloaded/extracted Quarkdown SDK is stored. */
val quarkdownSdkCacheDir: File = layout.buildDirectory.dir("quarkdown-sdk").get().asFile

/** Asset name of the Quarkdown release zip for the current platform. */
fun quarkdownSdkPlatformAsset(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("win") -> "quarkdown-windows-x64.zip"
        os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm")) "quarkdown-macos-aarch64.zip" else "quarkdown-macos-x64.zip"
        os.contains("linux") -> "quarkdown-linux-x64.zip"
        else -> error("Unsupported platform: os=$os arch=$arch")
    }
}

/**
 * Downloads and extracts the Quarkdown CLI distribution (only `lib/` and `docs/` are
 * kept; the bundled runtime JRE is not needed by the tests). The result is cached under
 * `build/quarkdown-sdk` so subsequent builds don't re-download it.
 */
abstract class DownloadQuarkdownSdkTask : DefaultTask() {

    @get:Input
    abstract val assetName: Property<String>

    @get:Input
    abstract val forceRefresh: Property<Boolean>

    @get:Input
    abstract val skipDownload: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        if (skipDownload.get()) {
            logger.lifecycle("Skipping Quarkdown SDK download (local installation available)")
            return
        }
        val home = outputDir.get().asFile
        val stdlib = File(home, "lib/quarkdown-stdlib.jar")
        if (stdlib.exists() && !forceRefresh.get()) {
            logger.lifecycle("Quarkdown SDK already present at {}", home)
            return
        }
        if (stdlib.exists()) home.deleteRecursively()

        val asset = assetName.get()
        val url = "https://github.com/iamgio/quarkdown/releases/download/latest/$asset"
        val uri = URI(url)
        val zip = File(outputDir.get().asFile.parentFile, asset)
        logger.lifecycle("Downloading {} ...", url)
        zip.parentFile.mkdirs()
        try {
            // Use the system proxy configuration (ProxySelector.getDefault()) so the
            // download works in proxied environments without hard-coding a proxy.
            val connection: java.net.HttpURLConnection =
                ProxySelector.getDefault()
                    ?.select(uri)
                    ?.firstOrNull { it.type() != Proxy.Type.DIRECT }
                    ?.let { proxy ->
                        logger.lifecycle("Using system proxy {}", proxy.address())
                        uri.toURL().openConnection(proxy) as java.net.HttpURLConnection
                    }
                    ?: uri.toURL().openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 60_000
            connection.readTimeout = 600_000
            connection.setRequestProperty("User-Agent", "quarkdown-for-intellij-gradle")
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("Failed to download Quarkdown SDK: HTTP $responseCode for $url")
            }
            connection.inputStream.use { input ->
                zip.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } catch (e: Exception) {
            zip.delete()
            throw e
        }

        logger.lifecycle("Extracting {} ...", asset)
        home.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix("quarkdown/")
                if (!entry.isDirectory && (name.startsWith("lib/") || name.startsWith("docs/"))) {
                    val target = File(home, name)
                    target.parentFile.mkdirs()
                    zis.copyTo(target.outputStream())
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        zip.delete()
        logger.lifecycle("Quarkdown SDK ready at {}", home)
    }
}

// Prefer a locally installed Quarkdown; otherwise fall back to the downloaded SDK.
val localQuarkdownHome: File? = resolveQuarkdownHome()

fun quarkdownTestHome(): File = localQuarkdownHome ?: quarkdownSdkCacheDir

// `-Pquarkdown.sdk.force=true` forces a fresh download even when a local install exists.
val quarkdownSdkForce: Boolean =
    providers.gradleProperty("quarkdown.sdk.force").map { it.toBoolean() }.orElse(false).get()

val downloadQuarkdownSdk by tasks.registering(DownloadQuarkdownSdkTask::class) {
    group = "quarkdown"
    description = "Downloads and extracts the Quarkdown SDK used by the tests"
    assetName.set(quarkdownSdkPlatformAsset())
    forceRefresh.set(quarkdownSdkForce)
    skipDownload.set(!quarkdownSdkForce && localQuarkdownHome != null)
    outputDir.set(quarkdownSdkCacheDir)
}

tasks.named<Test>("test") {
    dependsOn(downloadQuarkdownSdk)
    systemProperty("quarkdown.test.home", quarkdownTestHome().absolutePath)
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

    // The Quarkdown standard-library classes are never referenced at compile time:
    // FunctionRegistry loads them reflectively at runtime through QuarkdownSdkClassLoader
    // (a URLClassLoader over the detected installation's lib/*.jar). Bundling them here
    // would also break compilation because the SDK ships Kotlin 2.3 metadata while this
    // project compiles with Kotlin 2.1. So no quarkdown jars are declared as dependencies.
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

// ── Plugin metadata & JetBrains Marketplace configuration ────────────
// Values declared here are patched into META-INF/plugin.xml at build time
// by the IntelliJ Platform Gradle Plugin (see the `patchPluginXml` task).
intellijPlatform {
    pluginConfiguration {
        vendor {
            name = "CarmJos"
            url = "https://github.com/CarmJos/quarkdown-for-intellij"
        }
    }

    // Publish to JetBrains Marketplace via `./gradlew publishPlugin`.
    // Requires the PUBLISH_TOKEN environment variable (set in CI secrets).
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }

    // Plugin signing (required by JetBrains Marketplace since 2021).
    // Credentials are read from environment variables (set in CI secrets).
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}
