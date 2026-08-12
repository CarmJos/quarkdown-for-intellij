package cc.carm.plugin.intellij.quarkdown.lang.lsp

import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Integration test for the official `quarkdown language-server`.
 *
 * These tests launch a real `quarkdown language-server` JVM process (using the same
 * JVM command the plugin's [QuarkdownLspServerDescriptor] builds — `java` with the
 * `<home>/lib/asterisk` wildcard classpath and `com.quarkdown.cli.QuarkdownCliKt
 * language-server`) and drive it over the LSP stdio protocol:
 *
 *  - `initialize` handshake returns the expected capabilities;
 *  - hover over a known function returns documentation;
 *  - completion after a function prefix offers the matching function;
 *  - diagnostics flag an invalid enum value;
 *  - semantic tokens are reported for a function call.
 *
 * When no Quarkdown home is configured (CI without a distribution), the tests are
 * skipped rather than failed.
 */
class QuarkdownLspServerIntegrationTest {

    private var quarkdownHome: String? = null

    @Before
    fun resolveHome() {
        val configured = System.getProperty("quarkdown.test.home")
            ?: System.getenv("QUARKDOWN_HOME")
            ?: System.getenv("QUARKDOWN_LSP_HOME")
        if (configured != null && File(configured, "lib/quarkdown-lsp.jar").exists()) {
            quarkdownHome = configured
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun javaExecutable(home: String): File? {
        val bundled = File(File(home, "runtime"), "bin/java${if (isWindows()) ".exe" else ""}")
        if (bundled.isFile) return bundled
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            File(dir, if (isWindows()) "java.exe" else "java").takeIf { it.isFile }?.let { return it }
        }
        return null
    }

    private fun withServer(block: (LspClient, Map<*, *>) -> Unit) {
        val home = quarkdownHome
        if (home == null) {
            Assume.assumeTrue("quarkdown home not configured, skipping LSP integration test", false)
            return
        }
        val java = javaExecutable(home)
            ?: run {
                Assume.assumeTrue("no java executable found, skipping LSP integration test", false)
                return
            }

        val proc = ProcessBuilder(
            java.absolutePath,
            "-classpath", File(home, "lib").absolutePath + File.separator + "*",
            "com.quarkdown.cli.QuarkdownCliKt",
            "language-server"
        ).start()

        // Drain stderr so the LSP stdout framing stays clean (JVM warnings / log
        // messages must never corrupt the stdio channel).
        val stderrDrain = Thread {
            try {
                proc.errorStream.use { it.readBytes() }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val client = LspClient(proc)
        try {
            val result = client.request(1, "initialize", mapOf(
                "processId" to null,
                "rootUri" to null,
                "capabilities" to emptyMap<String, Any>()
            ))
            assertNotNull("initialize must return a result", result["result"])
            client.notify("initialized", emptyMap<String, Any>())
            block(client, result["result"] as Map<*, *>)
        } catch (e: Throwable) {
            val readerErr = client.lastReaderError?.toString() ?: "none"
            throw AssertionError(
                "${e.message}\n--- reader error ---\n$readerErr\n--- server stdout ---\n${client.dumpRaw()}",
                e
            )
        } finally {
            proc.destroyForcibly()
            proc.waitFor(10, TimeUnit.SECONDS)
            stderrDrain.interrupt()
        }
    }

    @Test
    fun `initialize reports the expected LSP capabilities`() {
        withServer { _, result ->
            val capabilities = result["capabilities"] as Map<*, *>
            assertEquals(true, capabilities["hoverProvider"])
            assertTrue("completion provider expected", capabilities.containsKey("completionProvider"))
            assertTrue("semantic tokens provider expected", capabilities.containsKey("semanticTokensProvider"))
            assertTrue("on-type formatting expected", capabilities.containsKey("documentOnTypeFormattingProvider"))
        }
    }

    @Test
    fun `hover over a known function returns documentation`() {
        withServer { client, _ ->
            openDocument(client, "Hello\n\n.doctype { paged }\n\nBye")
            val result = client.request(2, "textDocument/hover", mapOf(
                "textDocument" to mapOf("uri" to URI),
                "position" to mapOf("line" to 2, "character" to 5)
            ))
            val contents = (result["result"] as? Map<*, *>)?.get("contents")
            assertNotNull("hover should return content for .doctype", contents)
        }
    }

    @Test
    fun `completion after a function prefix offers the function`() {
        withServer { client, _ ->
            openDocument(client, "Hello\n\n.doc {  }\n\nBye")
            val result = client.request(3, "textDocument/completion", mapOf(
                "textDocument" to mapOf("uri" to URI),
                "position" to mapOf("line" to 2, "character" to 4),
                "context" to mapOf("triggerKind" to 2, "triggerCharacter" to ".")
            ))
            val payload = result["result"]
            val items = when (payload) {
                is List<*> -> payload
                is Map<*, *> -> payload["items"] as? List<*> ?: emptyList<Any>()
                else -> emptyList<Any>()
            }
            val labels = items.mapNotNull { (it as? Map<*, *>)?.get("label")?.toString() }
            assertTrue("completion should offer doctype, got: $labels", labels.contains("doctype"))
        }
    }

    @Test
    fun `diagnostics flag an invalid enum value`() {
        withServer { client, _ ->
            openDocument(client, "Hello\n\n.doctype type:{invalid}\n\nBye")
            val diagnostic = client.awaitDiagnostics()
            assertNotNull("publishDiagnostics expected for invalid enum value", diagnostic)
            val params = diagnostic?.get("params") as? Map<*, *>
            val diagnostics = params?.get("diagnostics") as? List<*>
            assertNotNull("diagnostics list expected", diagnostics)
            assertTrue("invalid value should be reported", diagnostics!!.isNotEmpty())
        }
    }

    @Test
    fun `semantic tokens are reported for function calls`() {
        withServer { client, _ ->
            openDocument(client, ".doctype { paged }\n")
            val result = client.request(4, "textDocument/semanticTokens/full", mapOf(
                "textDocument" to mapOf("uri" to URI)
            ))
            val data = (result["result"] as? Map<*, *>)?.get("data") as? List<*>
            assertNotNull("semantic tokens expected", data)
            assertTrue("semantic tokens should contain data", data!!.isNotEmpty())
        }
    }

    private fun openDocument(client: LspClient, text: String) {
        client.notify("textDocument/didOpen", mapOf(
            "textDocument" to mapOf(
                "uri" to URI,
                "languageId" to "quarkdown",
                "version" to 1,
                "text" to text
            )
        ))
        Thread.sleep(200)
    }

    companion object {
        private const val URI = "file:///C:/test/integration.qd"
    }

    /** Minimal LSP client speaking the stdio framed protocol (Content-Length headers). */
    private inner class LspClient(private val proc: Process) {

        private val input: OutputStream = proc.outputStream
        private val frames = java.util.concurrent.ArrayBlockingQueue<Map<*, *>>(64)
        private val rawBytes = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
        private val readerThread = Thread {
            try {
                while (true) {
                    val header = StringBuilder()
                    while (!header.toString().endsWith("\r\n\r\n")) {
                        val c = proc.inputStream.read()
                        if (c < 0) return@Thread
                        header.append(c.toChar())
                    }
                    val length = Regex("(?i)Content-Length:\\s*(\\d+)")
                        .find(header.toString())
                        ?.groupValues
                        ?.get(1)
                        ?.toInt()
                        ?: continue
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = proc.inputStream.read(body, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    rawBytes.add(body)
                    frames.put(Json.decode(String(body, StandardCharsets.UTF_8)))
                }
            } catch (e: Exception) {
                lastReaderError = e
            }
        }.apply {
            isDaemon = true
            start()
        }

        @Volatile
        var lastReaderError: Exception? = null

        fun notify(method: String, params: Any) {
            send(mapOf("jsonrpc" to "2.0", "method" to method, "params" to params))
        }

        fun request(id: Int, method: String, params: Any): Map<*, *> {
            send(mapOf("jsonrpc" to "2.0", "id" to id, "method" to method, "params" to params))
            return readUntil(30_000) { msg -> msg["id"].asNumeric() == id.toLong() }
                ?: run {
                    fail("Timed out waiting for LSP response (id=$id)")
                    throw IllegalStateException("unreachable")
                }
        }

        /** Reads frames until a `publishDiagnostics` notification arrives (with timeout). */
        fun awaitDiagnostics(): Map<*, *>? {
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                val msg = frames.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (msg["method"] == "textDocument/publishDiagnostics") return msg
            }
            return null
        }

        private fun readUntil(timeoutMs: Long, predicate: (Map<*, *>) -> Boolean): Map<*, *>? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val msg = frames.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                if (predicate(msg)) return msg
            }
            return null
        }

        private fun send(message: Map<*, *>) {
            val json = Json.encode(message)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
            synchronized(input) {
                input.write(header)
                input.write(bytes)
                input.flush()
            }
        }

        fun dumpRaw(): String {
            val sb = StringBuilder()
            rawBytes.forEach { b -> sb.appendLine(String(b, StandardCharsets.UTF_8).take(2000)) }
            return sb.toString()
        }
    }

    /** Converts a JSON number to its `Long` value, tolerating `Int`/`Double` decoding. */
    private fun Any?.asNumeric(): Long? = when (this) {
        is Int -> toLong()
        is Long -> this
        is Double -> toLong()
        is Number -> toLong()
        else -> null
    }

    /** Minimal JSON support (no external dependency in tests). */    private object Json {
        fun encode(value: Any?): String = when (value) {
            null -> "null"
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":${encode(v)}" }
            is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
            is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> "\"$value\""
        }

        fun decode(json: String): Map<*, *> {
            val parser = Parser(json)
            return parser.parseValue() as Map<*, *>
        }

        private class Parser(private val s: String) {
            private var i = 0

            fun parseValue(): Any? {
                skipWs()
                return when (s[i]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't', 'f' -> parseBoolean()
                    'n' -> { i += 4; null }
                    else -> parseNumber()
                }
            }

            private fun skipWs() {
                while (i < s.length && s[i].isWhitespace()) i++
            }

            private fun parseObject(): Map<String, Any?> {
                val map = LinkedHashMap<String, Any?>()
                i++ // {
                skipWs()
                if (i < s.length && s[i] == '}') { i++; return map }
                while (true) {
                    skipWs()
                    val key = parseString()
                    skipWs()
                    i++ // :
                    map[key] = parseValue()
                    skipWs()
                    when (s[i]) {
                        ',' -> { i++; continue }
                        '}' -> { i++; return map }
                        else -> error("invalid object at $i: ${s[i]}")
                    }
                }
            }

            private fun parseArray(): List<Any?> {
                val list = mutableListOf<Any?>()
                i++ // [
                skipWs()
                if (i < s.length && s[i] == ']') { i++; return list }
                while (true) {
                    list.add(parseValue())
                    skipWs()
                    when (s[i]) {
                        ',' -> { i++; continue }
                        ']' -> { i++; return list }
                        else -> error("invalid array at $i: ${s[i]}")
                    }
                }
            }

            private fun parseString(): String {
                i++ // "
                val sb = StringBuilder()
                while (i < s.length) {
                    val c = s[i++]
                    when {
                        c == '"' -> return sb.toString()
                        c == '\\' -> {
                            val esc = s[i++]
                            sb.append(
                                when (esc) {
                                    'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000C'
                                    'u' -> s.substring(i, i + 4).toInt(16).toChar().also { i += 4 }
                                    else -> esc
                                }
                            )
                        }
                        else -> sb.append(c)
                    }
                }
                return sb.toString()
            }

            private fun parseNumber(): Any {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
                val text = s.substring(start, i)
                return if ('.' in text || 'e' in text || 'E' in text) {
                    text.toDouble()
                } else {
                    text.toLong()
                }
            }

            private fun parseBoolean(): Boolean {
                if (s[i] == 't') { i += 4; return true }
                i += 5
                return false
            }
        }
    }
}
