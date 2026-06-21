package cc.carm.plugin.intellij.quarkdown.lang.function

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class FunctionRegistry(val project: Project) {

    private val logger = Logger.getInstance(FunctionRegistry::class.java)
    private val gson = Gson()
    private var functions: List<FunctionMetadata> = emptyList()
    private var loadedHome: String = ""

    fun getFunctions(): List<FunctionMetadata> {
        if (!isLoaded()) {
            val home = QuarkdownSettings.getInstance(project).state.quarkdownPath.orEmpty()
            if (home.isNotEmpty()) {
                refresh(home)
            }
        }
        return functions
    }

    fun isLoaded(): Boolean = functions.isNotEmpty()

    fun refresh(homePath: String, force: Boolean = false) {
        logger.info("Refreshing function registry from: $homePath (force=$force)")
        if (homePath.isEmpty()) {
            functions = emptyList()
            loadedHome = ""
            return
        }

        if (!force) {
            val cached = loadFromCache(homePath)
            if (cached != null) {
                if (cached.isNotEmpty()) {
                    logger.info("Loaded ${cached.size} functions from cache")
                    functions = cached
                    loadedHome = homePath
                    return
                } else {
                    logger.info("Cache exists but empty, will reflect from JARs")
                }
            } else {
                logger.info("No cache found, reflecting from JARs...")
            }
        } else {
            logger.info("Force refresh requested, bypassing cache")
        }
        val funcs = reflectFromJars(homePath)
        logger.info("Reflected ${funcs.size} functions from JARs")
        if (funcs.isNotEmpty()) {
            saveToCache(homePath, funcs)
        }
        functions = funcs
        loadedHome = homePath
    }

    fun refreshAsync(homePath: String, force: Boolean = false, onDone: (success: Boolean) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                refresh(homePath, force)
                javax.swing.SwingUtilities.invokeLater { onDone(true) }
            } catch (e: Exception) {
                logger.warn("Refresh failed", e)
                javax.swing.SwingUtilities.invokeLater { onDone(false) }
            }
        }
    }

    fun clear() {
        functions = emptyList()
        loadedHome = ""
    }

    fun getCacheInfo(): String {
        if (functions.isEmpty()) return "No cached data"
        val paramCount = functions.sumOf { f -> f.parameters.count { !it.isInjected } }
        return "${functions.size} functions, $paramCount parameters"
    }

    private fun cacheFile(home: String): File {
        val dir = File(project.basePath ?: System.getProperty("user.home"), ".quarkdown")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "function-cache.json")
    }

    private fun loadFromCache(home: String): List<FunctionMetadata>? {
        val file = cacheFile(home)
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<FunctionMetadata>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            logger.warn("Failed to load cache: ${e.message}")
            null
        }
    }

    private fun saveToCache(home: String, funcs: List<FunctionMetadata>) {
        try {
            val file = cacheFile(home)
            file.writeText(gson.toJson(funcs))
            logger.info("Saved ${funcs.size} functions to cache")
        } catch (e: Exception) {
            logger.warn("Failed to save cache: ${e.message}")
        }
    }

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

            logger.info("Library type: ${library.javaClass.name}")

            val rawFunctions = try {
                library.javaClass.getMethod("getFunctions").invoke(library)
            } catch (_: NoSuchMethodException) {
                try {
                    library.javaClass.getMethod("functions").invoke(library)
                } catch (_: NoSuchMethodException) {
                    logger.warn("No getFunctions() found")
                    null
                }
            }

            logger.info("getFunctions() returned type: ${rawFunctions?.javaClass?.name ?: "null"}")
            if (rawFunctions != null) {
                logger.info("  is Collection: ${rawFunctions is Collection<*>}")
                logger.info("  is Iterable: ${rawFunctions is Iterable<*>}")
            }

            val functionsList = when {
                rawFunctions is List<*> -> rawFunctions
                rawFunctions is Collection<*> -> rawFunctions.toList()
                rawFunctions is Iterable<*> -> rawFunctions.toList()
                else -> {
                    logger.warn("getFunctions() returned type ${rawFunctions?.javaClass?.name}, cannot convert to list")
                    return emptyList()
                }
            }
            logger.info("Found ${functionsList.size} functions in stdlib")

            functionsList.mapNotNull { f -> f?.let { reflectFunction(it) } }
        } catch (e: Exception) {
            logger.warn("Failed to reflect stdlib: ${e.message}", e)
            emptyList()
        } finally {
            try { classLoader.close() } catch (_: Exception) {}
        }
    }

    private fun reflectFunction(f: Any): FunctionMetadata? {
        return try {
            val cls = f.javaClass

            val name = try {
                val nameField = cls.getMethod("getName").invoke(f) as? String
                nameField?.lowercase()
            } catch (e: Exception) { logger.warn("Failed get name: ${e.message}"); null } ?: return null

            val description = try {
                cls.getMethod("getDescription").invoke(f) as? String ?: ""
            } catch (_: Exception) { "" }

            val isChained = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyChained") == true }
            } catch (_: Exception) { false }

            val params = try {
                (cls.getMethod("getParameters").invoke(f) as? List<*>) ?: emptyList<Any>()
            } catch (_: Exception) { emptyList<Any>() }

            val paramMetas = params.mapNotNull { p -> p?.let { reflectParameter(it) } }

            FunctionMetadata(
                name = name,
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
            } catch (_: Exception) { null } ?: return null

            val typeName = try {
                val type = cls.getMethod("getType").invoke(p)
                (type as? Class<*>)?.simpleName ?: type.toString().substringAfterLast(".")
            } catch (_: Exception) { "Any" }

            val index = try { cls.getMethod("getIndex").invoke(p) as? Int ?: 0 } catch (_: Exception) { 0 }
            val isOptional = try { cls.getMethod("isOptional").invoke(p) as? Boolean ?: false } catch (_: Exception) { false }
            val isInjected = try { cls.getMethod("isInjected").invoke(p) as? Boolean ?: false } catch (_: Exception) { false }
            val isNullable = try { cls.getMethod("isNullable").invoke(p) as? Boolean ?: false } catch (_: Exception) { false }

            val isLikelyNamed = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyNamed") == true }
            } catch (_: Exception) { false }

            val isLikelyBody = try {
                cls.annotations.any { it.annotationClass.qualifiedName?.contains("LikelyBody") == true }
            } catch (_: Exception) { false }

            val allowedValues: List<String>? = try {
                val typeClass = cls.getMethod("getType").invoke(p) as? Class<*>
                if (typeClass?.isEnum == true) {
                    typeClass.enumConstants?.map { (it as Enum<*>).name.lowercase() }
                } else null
            } catch (_: Exception) { null }

            ParameterMetadata(
                name = pName.lowercase(), type = typeName.lowercase(), index = index,
                isOptional = isOptional, isInjected = isInjected, isNullable = isNullable,
                isLikelyNamed = isLikelyNamed, isLikelyBody = isLikelyBody, allowedValues = allowedValues
            )
        } catch (_: Exception) { null }
    }

    companion object {
        fun getInstance(project: Project): FunctionRegistry = project.service()
    }
}
