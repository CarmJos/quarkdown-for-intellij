package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Project-level cache of Quarkdown function signatures, populated from the official
 * `quarkdown language-server`.
 *
 * The LSP hover on a function-call name returns the rendered signature (the first
 * ```lang-kotlin block), e.g.:
 *
 * ```
 * .multiply a:{Number} by:{Number} -> Number
 * .pageformat side:{PageSide? = null} \
 *          pages:{Range? = null} \
 * ...
 * ```
 *
 * The signature lists every **user-facing** parameter in order — injected parameters
 * (e.g. the compiler-provided `context`) are not rendered. This drives both the
 * parameter-name inlay hints and the Ctrl+P parameter-info popup, replacing the legacy
 * reflective stdlib introspection (FunctionRegistry).
 */
@Service(Service.Level.PROJECT)
class QuarkdownLspFunctionSignatureCache(private val project: Project) {

    private val logger = Logger.getInstance(QuarkdownLspFunctionSignatureCache::class.java)

    /** lowercase function name → parsed signature (text + ordered user-facing parameter names). */
    private val signatures = ConcurrentHashMap<String, QuarkdownFunctionSignature>()

    /** Names currently being fetched, to avoid duplicate in-flight requests. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val versionCounter = AtomicLong()

    /** Bumped whenever new signatures are cached; inlay providers can watch it. */
    val modificationTracker: ModificationTracker = ModificationTracker { versionCounter.get() }

    /**
     * Optional listener invoked (on the EDT) after new signatures are cached, so the
     * inlay hints pass can be re-run to show hints that were waiting on the fetch.
     */
    @Volatile
    var onSignaturesUpdated: (() -> Unit)? = null

    /** Returns the cached signature for [functionName], or `null` if unknown/not cached. */
    fun getSignature(functionName: String): QuarkdownFunctionSignature? =
        signatures[functionName.trim().lowercase()]

    /** Returns the ordered user-facing parameter names for [functionName], or `null` if unknown/not cached. */
    fun getParameterNames(functionName: String): List<String>? =
        getSignature(functionName)?.parameterNames

    /** Test-only: seeds a signature without contacting the LSP server. */
    internal fun seedSignature(functionName: String, parameterNames: List<String>) {
        val name = functionName.trim().lowercase()
        val text = "." + name + " " + parameterNames.joinToString(" ") { "$it:{?}" }
        signatures[name] = QuarkdownFunctionSignature(name, text, parameterNames)
        versionCounter.incrementAndGet()
    }

    /**
     * Returns the signature for [functionName], fetching it from the LSP server when not
     * yet cached. [onReady] is invoked on the EDT with the signature (or `null` when the
     * fetch fails / the function has no user-facing parameters).
     *
     * This is used by the parameter-info (Ctrl+P) popup so a signature that was not
     * pre-fetched by the inlay pass is still shown.
     */
    fun requestSignature(
        functionName: String,
        file: com.intellij.psi.PsiFile,
        onReady: (QuarkdownFunctionSignature?) -> Unit,
    ) {
        val name = functionName.trim().lowercase()
        if (name.isEmpty()) return
        signatures[name]?.let {
            onReady(it)
            return
        }

        val virtualFile = file.virtualFile
        if (virtualFile == null || !virtualFile.isValid) return
        val server = currentServer() ?: return

        val fileUri = try { TextDocumentIdentifier(LSPIJUtils.toUriAsString(virtualFile)) } catch (e: Exception) { null }
        if (fileUri == null) return
        val text = file.text.takeIf { it.isNotEmpty() } ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            if (!inFlight.add(name)) {
                // Already being fetched by requestSignatures; wait for the modification
                // counter to bump, then serve from the cache.
                val before = versionCounter.get()
                val deadline = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < deadline && versionCounter.get() == before) {
                    Thread.sleep(20)
                }
                val sig = signatures[name]
                ApplicationManager.getApplication().invokeLater { onReady(sig) }
                return@executeOnPooledThread
            }
            try {
                val sig = try {
                    fetchSignature(server, fileUri, text, name)
                } catch (e: Exception) {
                    logger.debug("Failed to fetch signature for '$name': ${e.message}")
                    null
                }
                if (sig != null && sig.parameterNames.isNotEmpty()) {
                    signatures[name] = sig
                    versionCounter.incrementAndGet()
                }
                ApplicationManager.getApplication().invokeLater { onReady(sig) }
            } finally {
                inFlight.remove(name)
            }
        }
    }

    /**
     * Requests signatures for the given function names via the LSP server's hover, on a
     * pooled thread. Only names not yet cached are fetched. When new data arrives, the
     * modification counter is bumped so inlay hints re-collect.
     *
     * @param names function names to resolve (lowercase-safe)
     * @param file  the open document context used to issue the hover request (its
     *              in-memory text is what the LSP server has synced)
     */
    fun requestSignatures(names: Collection<String>, file: com.intellij.psi.PsiFile) {
        val virtualFile = file.virtualFile
        if (names.isEmpty() || virtualFile == null || !virtualFile.isValid) return
        val missing = names.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !signatures.containsKey(it) }
        if (missing.isEmpty()) return

        val server = currentServer() ?: return
        val fileUri = try { TextDocumentIdentifier(LSPIJUtils.toUriAsString(virtualFile)) } catch (e: Exception) { null }
        if (fileUri == null) return

        val text = file.text.takeIf { it.isNotEmpty() } ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            var updated = false
            for (name in missing) {
                if (!inFlight.add(name)) continue
                try {
                    fetchSignature(server, fileUri, text, name)?.let { sig ->
                        if (sig.parameterNames.isNotEmpty()) {
                            signatures[name] = sig
                            versionCounter.incrementAndGet()
                            updated = true
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to fetch signature for '$name': ${e.message}")
                } finally {
                    inFlight.remove(name)
                }
            }
            if (updated) {
                val listener = onSignaturesUpdated
                if (listener != null) {
                    ApplicationManager.getApplication().invokeLater(listener)
                }
            }
        }
    }

    /**
     * Returns the currently running Quarkdown LSP4J server, or `null` when it is not
     * initialized. Uses LSP4IJ's [LanguageServerManager] to look up the server by id.
     */
    private fun currentServer(): org.eclipse.lsp4j.services.LanguageServer? =
        try {
            LanguageServerManager.getInstance(project)
                .getLanguageServer(SERVER_ID)
                .get(3, TimeUnit.SECONDS)
                ?.server
        } catch (e: Exception) {
            null
        }

    private fun fetchSignature(
        server: org.eclipse.lsp4j.services.LanguageServer,
        fileUri: TextDocumentIdentifier,
        text: String,
        name: String
    ): QuarkdownFunctionSignature? {
        // Hover at the first occurrence of the function call name in the document.
        val offset = findNameOffset(text, name) ?: return null
        val position = offsetToPosition(text, offset)
        val hover = try {
            server.textDocumentService.hover(HoverParams(fileUri, position))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.debug("Hover request failed for '$name': ${e.message}")
            null
        } ?: return null

        val contents = hover.contents
        // quarkdown-lsp always returns MarkupContent ({kind: "markdown", value: ...}).
        val markdown = contents?.right?.value ?: return null
        return parseFunctionSignature(markdown)
    }

    private fun findNameOffset(text: String, name: String): Int? {
        val lowered = name.lowercase()
        // Only consider actual function-call start positions (`.name`), so a `.name`
        // appearing in prose doesn't produce a spurious hover position.
        for (dotStart in QuarkdownCallParser.findAllCallStarts(text)) {
            val call = QuarkdownCallParser.parseCall(text, dotStart) ?: continue
            if (call.name == lowered) return call.nameStart
        }
        return null
    }

    private fun offsetToPosition(text: String, offset: Int): Position {
        val upTo = text.take(offset)
        val line = upTo.count { it == '\n' }
        val lastNl = upTo.lastIndexOf('\n')
        val character = if (lastNl < 0) upTo.length else upTo.length - lastNl - 1
        return Position(line, character)
    }

    /**
     * Extracts the ordered user-facing parameter names from the hover signature.
     *
     * The signature is the first ```lang-kotlin / ```block block in the hover markdown.
     * Parameter names are the identifiers immediately followed by `:{` (possibly after
     * whitespace, possibly across `\` line-continuations for long signatures).
     */
    internal fun parseParameterNames(markdown: String): List<String>? =
        parseFunctionSignature(markdown)?.parameterNames

    companion object {
        private const val SERVER_ID = "quarkdownLspServer"
        private const val REQUEST_TIMEOUT_SECONDS = 5L

        fun getInstance(project: Project): QuarkdownLspFunctionSignatureCache = project.service()
    }
}

/**
 * A parsed Quarkdown function signature: the raw signature block rendered by the LSP
 * hover, plus the ordered user-facing parameter names.
 *
 * @param name lowercase function name
 * @param signatureText the raw signature (may span lines with `\` continuations),
 *                      e.g. `.multiply a:{Number} by:{Number} -> Number`
 * @param parameterNames ordered user-facing parameter names (injected params excluded)
 */
data class QuarkdownFunctionSignature(
    val name: String,
    val signatureText: String,
    val parameterNames: List<String>
)

/**
 * Extracts the ordered user-facing parameter names from the hover signature markdown.
 *
 * The signature is the first ```lang-kotlin / ```block block in the hover markdown.
 * Parameter names are the identifiers immediately followed by `:{` (possibly after
 * whitespace, possibly across `\` line-continuations for long signatures).
 */
internal fun parseFunctionSignature(markdown: String): QuarkdownFunctionSignature? {
    // First fenced code block — the signature.
    val fence = Regex("```[^\n]*\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val signatureBlock = fence.find(markdown)?.groupValues?.get(1)?.trim() ?: return null

    // Parameter names are `identifier : {` — the signature renders them as
    // `name:{Type}` (optionally preceded by spaces on continuation lines).
    val paramRegex = Regex("""([a-zA-Z][a-zA-Z0-9]*)\s*:\s*\{""")
    val paramNames = paramRegex.findAll(signatureBlock)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
    if (paramNames.isEmpty()) return null

    // The function name is the first dotted identifier in the signature block.
    val name = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b""").find(signatureBlock)?.groupValues?.get(1)?.lowercase()
        ?: paramNames.first()

    return QuarkdownFunctionSignature(name = name, signatureText = signatureBlock, parameterNames = paramNames)
}
