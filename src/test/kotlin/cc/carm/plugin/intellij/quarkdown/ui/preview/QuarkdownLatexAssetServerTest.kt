package cc.carm.plugin.intellij.quarkdown.ui.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

/**
 * Verifies [QuarkdownLatexAssetServer]: the page and the renderer assets are served, and requests
 * cannot escape the asset root. The traversal guard matters because the server exposes the
 * installation's asset directory over a loopback port.
 */
class QuarkdownLatexAssetServerTest : BasePlatformTestCase() {

    private lateinit var root: File
    private var server: QuarkdownLatexAssetServer? = null

    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("qd-assets").toFile()
        val katex = File(root, "lib/katex")
        assertTrue(katex.mkdirs())
        File(katex, "katex.min.js").writeText("// stub renderer")
        // A file just outside the root, which traversal must never reach.
        File(root.parentFile, "outside-${root.name}.txt").writeText("secret")
    }

    override fun tearDown() {
        try {
            server?.dispose()
        } finally {
            super.tearDown()
        }
    }

    private fun start(): String {
        val started = QuarkdownLatexAssetServer(root, "<html>preview page</html>")
        server = started
        return started.baseUrl
    }

    private fun get(baseUrl: String, path: String): Pair<Int, String> {
        val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            connection.disconnect()
        }
    }

    fun `test the preview page is served at the root`() {
        val (code, body) = get(start(), "")
        assertEquals(200, code)
        assertTrue("the page must be served, got: $body", body.contains("preview page"))
    }

    fun `test a renderer asset is served with a script content type`() {
        val baseUrl = start()
        val connection = URI(baseUrl + "lib/katex/katex.min.js").toURL().openConnection() as HttpURLConnection
        try {
            assertEquals(200, connection.responseCode)
            assertTrue(
                "expected a script content type, got ${connection.contentType}",
                connection.contentType.orEmpty().startsWith("text/javascript"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun `test a missing asset is reported as not found`() {
        val (code, _) = get(start(), "lib/katex/missing.js")
        assertEquals(404, code)
    }

    fun `test a path traversal attempt is refused`() {
        // The traversal is percent-encoded so it reaches the handler instead of being normalised by
        // the client; the server must still refuse to leave the asset root.
        val (code, body) = get(start(), "%2e%2e/outside-${root.name}.txt")
        assertEquals("traversal must not be served", 404, code)
        assertFalse("the secret must not leak", body.contains("secret"))
    }
}

