package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.ConcurrentHashMap
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
 * (e.g. the compiler-provided `context`) are not rendered — which is exactly what the
 * parameter-name inlay hints need to map positional arguments. This replaces the legacy
 * reflective stdlib introspection (FunctionRegistry) as the metadata source.
 */
@Service(Service.Level.PROJECT)
class QuarkdownLspFunctionSignatureCache(private val project: Project) {

    private val logger = Logger.getInstance(QuarkdownLspFunctionSignatureCache::class.java)

    /** lowercase function name → ordered user-facing parameter names. */
    private val signatures = ConcurrentHashMap<String, List<String>>()

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

    /** Returns the ordered user-facing parameter names for [functionName], or `null` if unknown/not cached. */
    fun getParameterNames(functionName: String): List<String>? =
        signatures[functionName.trim().lowercase()]

    /** Test-only: seeds a signature without contacting the LSP server. */
    internal fun seedSignature(functionName: String, parameterNames: List<String>) {
        signatures[functionName.trim().lowercase()] = parameterNames
        versionCounter.incrementAndGet()
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

        val server = QuarkdownLspServerDescriptor.currentServer(project) ?: return
        val fileUri = try { server.getDocumentIdentifier(virtualFile) } catch (e: Exception) { null }
        if (fileUri == null) return

        val text = file.text.takeIf { it.isNotEmpty() } ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            var updated = false
            for (name in missing) {
                if (!inFlight.add(name)) continue
                try {
                    fetchSignature(server, fileUri, text, name)?.let { params ->
                        if (params.isNotEmpty()) {
                            signatures[name] = params
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

    private fun fetchSignature(
        server: com.intellij.platform.lsp.api.LspServer,
        fileUri: TextDocumentIdentifier,
        text: String,
        name: String
    ): List<String>? {
        // Hover at the first occurrence of the function call name in the document.
        val offset = findNameOffset(text, name) ?: return null
        val position = offsetToPosition(text, offset)
        val hover = server.sendRequestSync(com.intellij.platform.lsp.api.LspServer.DEFAULT_REQUEST_TIMEOUT_MS) { ls ->
            ls.textDocumentService.hover(HoverParams(fileUri, position))
        } ?: return null

        val contents = hover.contents
        // quarkdown-lsp always returns MarkupContent ({kind: "markdown", value: ...}).
        val markdown = contents?.right?.value ?: return null
        return parseParameterNames(markdown)
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
    internal fun parseParameterNames(markdown: String): List<String>? = parseFunctionSignature(markdown)

    companion object {
        fun getInstance(project: Project): QuarkdownLspFunctionSignatureCache = project.service()
    }
}

/**
 * Extracts the ordered user-facing parameter names from the hover signature markdown.
 *
 * The signature is the first ```lang-kotlin / ```block block in the hover markdown.
 * Parameter names are the identifiers immediately followed by `:{` (possibly after
 * whitespace, possibly across `\` line-continuations for long signatures).
 */
internal fun parseFunctionSignature(markdown: String): List<String>? {
    // First fenced code block — the signature.
    val fence = Regex("```[^\n]*\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val signatureBlock = fence.find(markdown)?.groupValues?.get(1) ?: return null

    // Parameter names are `identifier : {` — the signature renders them as
    // `name:{Type}` (optionally preceded by spaces on continuation lines).
    val paramRegex = Regex("""([a-zA-Z][a-zA-Z0-9]*)\s*:\s*\{""")
    return paramRegex.findAll(signatureBlock)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
        .takeIf { it.isNotEmpty() }
}
