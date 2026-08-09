package cc.carm.plugin.intellij.quarkdown.lang.function

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FunctionRegistryDiskCacheTest {

    private val gson = Gson()

    @Test
    fun `saved cache carries the current format version`() {
        val disk = FunctionRegistry.DiskCache(
            cacheVersion = FunctionRegistry.DiskCache.CACHE_FORMAT_VERSION,
            quarkdownHome = "/home",
            functions = emptyList()
        )
        val json = gson.toJson(disk)
        // The version is serialized explicitly so new caches are valid.
        assertEquals(
            FunctionRegistry.DiskCache.CACHE_FORMAT_VERSION,
            gson.fromJson(json, FunctionRegistry.DiskCache::class.java).cacheVersion
        )
    }

    @Test
    fun `old cache without version field is detected as stale`() {
        // JSON written by an older plugin version (no cacheVersion key).
        val oldJson = """{"quarkdownHome":"/home","jarsFingerprint":"abc","generatedAt":0,"functions":[]}"""
        val disk = gson.fromJson(oldJson, FunctionRegistry.DiskCache::class.java)
        // The sentinel default makes the version mismatch → cache is invalidated and rebuilt.
        assertEquals(FunctionRegistry.DiskCache.CACHE_FORMAT_VERSION_SENTINEL, disk.cacheVersion)
        assertNotEquals(FunctionRegistry.DiskCache.CACHE_FORMAT_VERSION, disk.cacheVersion)
    }

    @Test
    fun `new cache round-trips the enum naming`() {
        val disk = FunctionRegistry.DiskCache(
            cacheVersion = FunctionRegistry.DiskCache.CACHE_FORMAT_VERSION,
            quarkdownHome = "/home",
            functions = listOf(
                FunctionMetadata(
                    name = "pagemargin",
                    parameters = listOf(
                        ParameterMetadata(
                            "position",
                            "pagemarginposition",
                            0,
                            allowedValues = listOf("bottomcenter", "topleftcorner")
                        )
                    )
                )
            )
        )
        val restored = gson.fromJson(gson.toJson(disk), FunctionRegistry.DiskCache::class.java)
        val position = restored.functions.first().parameters.first()
        // Quarkdown naming (lowercase + strip underscores) is what the validator expects.
        assertEquals(listOf("bottomcenter", "topleftcorner"), position.allowedValues)
    }
}
