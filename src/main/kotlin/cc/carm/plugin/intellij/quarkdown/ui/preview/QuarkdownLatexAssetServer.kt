package cc.carm.plugin.intellij.quarkdown.ui.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Serves the preview page plus the LaTeX renderer assets over loopback HTTP for the embedded
 * browser.
 *
 * An in-process server is used instead of `file:` URLs because Chromium (and therefore CEF)
 * restricts a `file:` page from loading other `file:` resources: scripts, stylesheets and — most
 * visibly — the KaTeX web fonts would be blocked, which shows up as an unstyled or font-less
 * formula. Serving everything from a single `http://127.0.0.1:<port>/` origin sidesteps those
 * restrictions completely, and costs nothing beyond a socket: no external process, no CLI, and the
 * assets are still the ones bundled in the user's Quarkdown installation.
 *
 * Requests are restricted to [assetRoot]; anything else (and every path-traversal attempt) is
 * rejected. The socket is bound to the loopback interface on an ephemeral port, so it is not
 * reachable from outside the machine.
 */
class QuarkdownLatexAssetServer(
    private val assetRoot: File,
    private val pageHtml: String,
) : Disposable {

    private val logger = Logger.getInstance(QuarkdownLatexAssetServer::class.java)

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    /** Base URL of the served page, e.g. `http://127.0.0.1:54321/`. */
    val baseUrl: String

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.executor = null // the default (calling) executor is enough for a handful of files
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/"
        logger.info("Formula preview assets served from $baseUrl (root: ${assetRoot.absolutePath})")
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path.orEmpty()
            if (path == "/" || path == "/index.html") {
                respond(exchange, pageHtml.toByteArray(Charsets.UTF_8), "text/html; charset=utf-8")
                return
            }

            val file = resolveAsset(path)
            if (file == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            respond(exchange, file.readBytes(), contentType(file.name))
        } catch (e: Exception) {
            logger.warn("Failed to serve a formula preview asset: ${exchange.requestURI}", e)
            try {
                exchange.sendResponseHeaders(500, -1)
            } catch (_: Exception) {
                // The client may already be gone; nothing useful left to do.
            }
        } finally {
            exchange.close()
        }
    }

    /**
     * Maps a request path to a file under [assetRoot], or `null` when it escapes the root or does
     * not exist. The canonical-path check is what blocks `../` traversal.
     */
    private fun resolveAsset(path: String): File? {
        val relative = path.removePrefix("/")
        if (relative.isBlank()) return null
        val candidate = File(assetRoot, relative)
        val root = try {
            assetRoot.canonicalFile
        } catch (_: Exception) {
            return null
        }
        val canonical = try {
            candidate.canonicalFile
        } catch (_: Exception) {
            return null
        }
        if (!canonical.path.startsWith(root.path + File.separator)) return null
        return canonical.takeIf { it.isFile }
    }

    private fun respond(exchange: HttpExchange, body: ByteArray, contentType: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    private fun contentType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "html" -> "text/html; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    override fun dispose() {
        try {
            server.stop(0)
        } catch (e: Exception) {
            logger.warn("Failed to stop the formula preview asset server", e)
        }
    }
}

