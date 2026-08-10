package cc.carm.plugin.intellij.quarkdown.lang.function

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.io.File
import java.security.MessageDigest
import javax.swing.SwingUtilities

/**
 * Builds and serves the Quarkdown function index for the project.
 *
 * The cache follows the same ideas IntelliJ uses for Java code caches:
 *
 *  - **In-memory layer** backed by [CachedValue] (see [CachedValuesManager]). The value is
 *    automatically invalidated whenever the Quarkdown installation path changes or a force
 *    refresh is requested (mirrors PSI-dependent cached computations).
 *  - **Persistent layer** stored inside the IDE's system cache directory
 *    ([PathManager.getSystemPath()]/quarkdown), keyed by the Quarkdown home and a fingerprint
 *    of the stdlib JARs, so the cache is automatically invalidated when Quarkdown is updated
 *    (mirrors how the IDE invalidates its caches when libraries change).
 *
 * Function signatures, parameter metadata and enum value options are reflected from the
 * Quarkdown standard library JARs; human-readable documentation is parsed from the shipped
 * Dokka documentation and merged into the metadata, preserving its structure.
 */
@Service(Service.Level.PROJECT)
class FunctionRegistry(private val project: Project) {

    private val logger = Logger.getInstance(FunctionRegistry::class.java)
    private val gson = Gson()

    @Volatile
    private var forceNextRefresh = false

    @Volatile
    private var forceRefreshVersion = 0L

    /** Home explicitly supplied to [refresh], used before the persisted settings value. */
    @Volatile
    private var pendingHome: String? = null

    /** Modification count changes whenever the Quarkdown path or a force-refresh changes. */
    private val cacheTracker = ModificationTracker {
        val path = pendingHome ?: QuarkdownSettings.getInstance(project).state.quarkdownPath.orEmpty()
        path.hashCode().toLong() * 31 + forceRefreshVersion
    }

    private val cachedFunctions: CachedValue<List<FunctionMetadata>> =
        CachedValuesManager.getManager(project).createCachedValue(
            CachedValueProvider { CachedValueProvider.Result.create(computeFunctions(), cacheTracker) },
            false
        )

    fun getFunctions(): List<FunctionMetadata> = cachedFunctions.value

    /** Resolves a function by exact name, or by unique prefix (used by documentation). */
    fun getFunction(name: String): FunctionMetadata? {
        val lower = name.trim().lowercase()
        if (lower.isEmpty()) return null
        val functions = getFunctions()
        functions.firstOrNull { it.name == lower }?.let { return it }
        val matching = functions.filter { it.name.startsWith(lower) }
        return if (matching.size == 1) matching.first() else null
    }

    fun refresh(homePath: String, force: Boolean = false) {
        logger.info("Refreshing function registry from: $homePath (force=$force)")
        if (homePath.isEmpty()) {
            clear()
            return
        }
        pendingHome = homePath
        if (force) {
            forceNextRefresh = true
            forceRefreshVersion++
        }
        // Trigger (re)computation of the cached value if it was invalidated.
        cachedFunctions.value
    }

    fun refreshAsync(homePath: String, force: Boolean = false, onDone: (success: Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                refresh(homePath, force)
                SwingUtilities.invokeLater { onDone(true) }
            } catch (e: Exception) {
                logger.warn("Refresh failed", e)
                SwingUtilities.invokeLater { onDone(false) }
            }
        }
    }

    /** Clears both the in-memory value and the persistent cache file. */
    fun clear() {
        forceRefreshVersion++
        pendingHome = null
        try {
            val file = cacheFile()
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }

    fun getCacheInfo(): String {
        val functions = getFunctions()
        if (functions.isEmpty()) return "No cached data"
        val paramCount = functions.sumOf { f -> f.parameters.count { !it.isInjected } }
        val documented = functions.count { it.description.isNotEmpty() || it.signature.isNotEmpty() }
        return "${functions.size} functions, $paramCount parameters, $documented documented"
    }

    // ----------------------------------------------------------------------------------
    // Cache computation
    // ----------------------------------------------------------------------------------

    private fun computeFunctions(): List<FunctionMetadata> {
        val home = pendingHome ?: QuarkdownSettings.getInstance(project).state.quarkdownPath.orEmpty()
        pendingHome = null
        if (home.isEmpty()) return emptyList()

        val force = forceNextRefresh
        forceNextRefresh = false

        if (!force) {
            loadFromDiskCache(home)?.let { cached ->
                logger.info("Loaded ${cached.size} functions from IDE cache")
                return cached
            }
        }

        return buildAndSave(home)
    }

    private fun buildAndSave(home: String): List<FunctionMetadata> {
        val functions = reflectFromJars(home)
        if (functions.isNotEmpty()) {
            val docs = QuarkdownDocParser.parseDocs(home)
            val enriched = functions.map { fn -> enrichWithDocs(fn, docs) }
            saveToDiskCache(home, enriched)
            return enriched
        }
        return functions
    }

    private fun enrichWithDocs(
        fn: FunctionMetadata,
        docs: Map<String, QuarkdownDocParser.FunctionDoc>
    ): FunctionMetadata {
        val doc = docs[fn.name.lowercase()] ?: return fn
        return fn.copy(
            description = doc.description.ifEmpty { fn.description },
            returnDescription = doc.returnDescription,
            signature = doc.signature.ifEmpty { fn.signature },
            module = doc.module,
            samples = doc.samples,
            docUrl = doc.docUrl,
            isLikelyChained = fn.isLikelyChained || doc.isChained,
            parameters = fn.parameters.map { p ->
                p.copy(description = doc.parameterDescriptions[p.name] ?: p.description)
            }
        )
    }

    // ----------------------------------------------------------------------------------
    // Persistent cache in the IDE system directory
    // ----------------------------------------------------------------------------------

    internal data class DiskCache(
        /**
         * Format version; bump when the cache layout or the reflected data shape changes.
         *
         * Defaults to a sentinel (`-1`) rather than the current version so that old cache
         * files written without this field (or by an older plugin version) are detected as
         * incompatible and rebuilt. Otherwise Gson keeps the field default and a stale
         * snake_case enum cache would survive the version check.
         */
        val cacheVersion: Int = CACHE_FORMAT_VERSION_SENTINEL,
        val quarkdownHome: String = "",
        val jarsFingerprint: String = "",
        val generatedAt: Long = 0L,
        val functions: List<FunctionMetadata> = emptyList()
    ) {
        companion object {
            /** Current cache format version; bump on every incompatible layout change. */
            const val CACHE_FORMAT_VERSION = 2

            /** Value used when the JSON has no `cacheVersion` field (older cache). */
            const val CACHE_FORMAT_VERSION_SENTINEL = -1
        }
    }

    private fun cacheFile(): File {
        val dir = File(PathManager.getSystemPath(), "quarkdown")
        if (!dir.exists()) dir.mkdirs()
        // Versioned file name so stale caches from older formats are never read.
        return File(dir, "function-cache-v2.json")
    }

    private fun loadFromDiskCache(home: String): List<FunctionMetadata>? {
        val file = cacheFile()
        if (!file.exists()) return null
        return try {
            val disk = gson.fromJson(file.readText(), DiskCache::class.java)
            if (disk.cacheVersion != DiskCache.CACHE_FORMAT_VERSION) {
                logger.info("Quarkdown cache format changed, invalidating IDE cache")
                return null
            }
            if (disk.quarkdownHome != home) return null
            if (disk.jarsFingerprint != jarsFingerprint(home)) {
                logger.info("Quarkdown stdlib changed, invalidating IDE cache")
                return null
            }
            disk.functions.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.warn("Failed to load IDE cache: ${e.message}")
            null
        }
    }

    private fun saveToDiskCache(home: String, funcs: List<FunctionMetadata>) {
        try {
            val disk = DiskCache(
                cacheVersion = DiskCache.CACHE_FORMAT_VERSION,
                quarkdownHome = home,
                jarsFingerprint = jarsFingerprint(home),
                generatedAt = System.currentTimeMillis(),
                functions = funcs
            )
            cacheFile().writeText(gson.toJson(disk))
            logger.info("Saved ${funcs.size} functions to IDE cache")
        } catch (e: Exception) {
            logger.warn("Failed to save IDE cache: ${e.message}")
        }
    }

    /**
     * Fingerprint of the Quarkdown standard library used to invalidate the cache.
     *
     * Only `quarkdown-*.jar` files are hashed (they define the function set), and their
     * **content** is hashed rather than just the name + mtime — rebuilds that preserve
     * timestamps (or jars copied with identical mtimes) are still detected, so a stale
     * cache can never silently hide new/renamed/removed functions.
     */
    private fun jarsFingerprint(home: String): String {
        val libDir = File(home, "lib")
        if (!libDir.isDirectory) return ""
        val jars = libDir.listFiles { f ->
            f.name.startsWith("quarkdown-") && f.name.endsWith(".jar")
        }
            ?.sortedBy { it.name }
            ?: return ""
        if (jars.isEmpty()) return ""
        val md5 = MessageDigest.getInstance("MD5")
        for (jar in jars) {
            md5.update(jar.name.toByteArray())
            md5.update(jar.length().toString().toByteArray())
            try {
                jar.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        md5.update(buffer, 0, read)
                    }
                }
            } catch (_: Exception) {
                // Fall back to the file name only if the jar can't be read.
            }
        }
        return md5.digest().joinToString("") { "%02x".format(it) }
    }

    // ----------------------------------------------------------------------------------
    // Reflection from the Quarkdown stdlib JARs
    // ----------------------------------------------------------------------------------

    private fun reflectFromJars(homePath: String): List<FunctionMetadata> {
        val classLoader = try {
            val jars = QuarkdownSdkClassLoader.jars(homePath)
            logger.info("Found ${jars.size} JAR files in $homePath/lib")
            if (jars.isEmpty()) return emptyList()
            QuarkdownSdkClassLoader(homePath, javaClass.classLoader)
        } catch (e: Exception) {
            logger.warn("Failed to create classloader: ${e.message}")
            return emptyList()
        }

        return try {
            val stdlibClass = classLoader.loadClass("com.quarkdown.stdlib.Stdlib")
            val instanceField = stdlibClass.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            val instance = instanceField.get(null)
                ?: run { logger.warn("Stdlib.INSTANCE is null"); return emptyList() }

            val library = instance.javaClass.getMethod("getLibrary").invoke(instance)
                ?: run { logger.warn("getLibrary() returned null"); return emptyList() }

            val rawFunctions = library.javaClass.getMethod("getFunctions").invoke(library)
                ?: run { logger.warn("getFunctions() returned null"); return emptyList() }

            val functionsList = when {
                rawFunctions is List<*> -> rawFunctions
                rawFunctions is Collection<*> -> rawFunctions.toList()
                rawFunctions is Iterable<*> -> rawFunctions.toList()
                else -> {
                    logger.warn("getFunctions() returned type ${rawFunctions.javaClass.name}, cannot convert to list")
                    return emptyList()
                }
            }
            logger.info("Found ${functionsList.size} functions in stdlib")
            functionsList.mapNotNull { f -> f?.let { reflectFunction(it) } }
        } catch (e: Exception) {
            logger.warn("Failed to reflect stdlib: ${e.message}", e)
            emptyList()
        } finally {
            try {
                classLoader.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun reflectFunction(f: Any): FunctionMetadata? {
        return try {
            val cls = f.javaClass

            val name = try {
                cls.getMethod("getName").invoke(f) as? String
            } catch (e: Exception) {
                logger.warn("Failed get name: ${e.message}")
                null
            } ?: return null

            val description = try {
                cls.getMethod("getDescription").invoke(f) as? String ?: ""
            } catch (_: Exception) {
                ""
            }

            val isChained = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyChained") == true }
            } catch (_: Exception) {
                false
            }

            val params = try {
                (cls.getMethod("getParameters").invoke(f) as? List<*>) ?: emptyList<Any>()
            } catch (_: Exception) {
                emptyList<Any>()
            }

            val paramMetas = params.mapNotNull { p -> p?.let { reflectParameter(it) } }

            FunctionMetadata(
                name = name.lowercase(),
                parameters = paramMetas,
                description = description,
                isLikelyChained = isChained
            )
        } catch (e: Exception) {
            logger.warn("Failed to reflect function: ${e.message}")
            null
        }
    }

    private fun reflectParameter(p: Any): ParameterMetadata? {
        return try {
            val cls = p.javaClass

            val pName = try {
                cls.getMethod("getName").invoke(p) as? String
            } catch (_: Exception) {
                null
            } ?: return null

            val typeName = try {
                when (val type = cls.getMethod("getType").invoke(p)) {
                    is kotlin.reflect.KClass<*> -> type.simpleName ?: type.qualifiedName ?: "Any"
                    is Class<*> -> type.simpleName
                    else -> type.toString().substringAfterLast(".")
                }
            } catch (_: Exception) {
                "Any"
            }

            val index = try {
                cls.getMethod("getIndex").invoke(p) as? Int ?: 0
            } catch (_: Exception) {
                0
            }
            val isOptional = try {
                cls.getMethod("isOptional").invoke(p) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }
            val isInjected = try {
                cls.getMethod("isInjected").invoke(p) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }
            val isNullable = try {
                cls.getMethod("isNullable").invoke(p) as? Boolean ?: false
            } catch (_: Exception) {
                false
            }

            val isLikelyNamed = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyNamed") == true }
            } catch (_: Exception) {
                false
            }

            val isLikelyBody = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyBody") == true }
            } catch (_: Exception) {
                false
            }

            val allowedValues: List<String>? = try {
                val javaClass = when (val type = cls.getMethod("getType").invoke(p)) {
                    is kotlin.reflect.KClass<*> -> type.java
                    is Class<*> -> type
                    else -> null
                }
                if (javaClass?.isEnum == true) {
                    // Quarkdown reads enum values with its own naming convention:
                    // lowercase, underscores removed. `BOTTOM_CENTER` → `bottomcenter`.
                    javaClass.enumConstants?.map { QuarkdownNaming.enumValueName((it as Enum<*>).name) }
                } else null
            } catch (_: Exception) {
                null
            }

            ParameterMetadata(
                name = pName.lowercase(), type = typeName.lowercase(), index = index,
                isOptional = isOptional, isInjected = isInjected, isNullable = isNullable,
                isLikelyNamed = isLikelyNamed, isLikelyBody = isLikelyBody, allowedValues = allowedValues
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun getInstance(project: Project): FunctionRegistry = project.service()
    }
}
