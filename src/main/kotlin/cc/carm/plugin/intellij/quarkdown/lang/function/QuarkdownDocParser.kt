package cc.carm.plugin.intellij.quarkdown.lang.function

import java.io.File

/**
 * Parses the Quarkdown standard-library documentation (Dokka HTML) that ships with a
 * Quarkdown installation under `<quarkdown-home>/docs/quarkdown-stdlib`.
 *
 * Each function is documented in a dedicated `<module>/<name>.html` page. This parser
 * extracts the description, signature, return documentation, per-parameter descriptions
 * and usage samples while preserving their structure so the plugin can render rich
 * documentation tooltips for completion items without a second lookup.
 *
 * This parser is intentionally free of IntelliJ dependencies so it can be unit-tested
 * with plain JUnit.
 */
object QuarkdownDocParser {

    /** Docs root relative to the Quarkdown installation home. */
    const val DOCS_DIR = "docs"
    const val STDLIB_DOCS_DIR = "$DOCS_DIR/quarkdown-stdlib"

    /** Base URL of the public online docs. */
    const val DOCS_BASE_URL = "https://quarkdown.com/docs/"

    /** Documentation extracted for a single function. */
    data class FunctionDoc(
        val name: String,
        val description: String = "",
        val returnDescription: String = "",
        val signature: String = "",
        val samples: List<String> = emptyList(),
        val parameterDescriptions: Map<String, String> = emptyMap(),
        val module: String = "",
        val docUrl: String? = null,
        val isChained: Boolean = false
    )

    /**
     * Parses all function documentation pages under the given Quarkdown home directory.
     * Returns a map of lowercase function name → parsed docs.
     */
    fun parseDocs(homePath: String): Map<String, FunctionDoc> {
        val docsDir = File(homePath, STDLIB_DOCS_DIR)
        if (!docsDir.isDirectory) return emptyMap()

        val result = LinkedHashMap<String, FunctionDoc>()
        docsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".html") }
            .forEach { file ->
                try {
                    val doc = parsePage(file, docsDir) ?: return@forEach
                    result.putIfAbsent(doc.name.lowercase(), doc)
                } catch (_: Exception) {
                    // skip malformed pages silently
                }
            }
        return result
    }

    private fun parsePage(file: File, docsRoot: File): FunctionDoc? {
        val html = file.readText()
        if (!html.contains("data-page-type=\"member\"")) return null

        val name = extractName(html) ?: return null
        val signature = extractSignature(html)
        val description = extractDescription(html, signature)
        val returnDescription = extractReturn(html)
        val samples = extractSamples(html)
        val params = extractParameters(html)
        val module = file.parentFile?.name?.substringAfterLast('.') ?: ""
        val docUrl = buildDocUrl(file, docsRoot)
        val isChained = html.contains("anchor__likely-chained")

        return FunctionDoc(
            name = name,
            description = description,
            returnDescription = returnDescription,
            signature = signature,
            samples = samples,
            parameterDescriptions = params,
            module = module,
            docUrl = docUrl,
            isChained = isChained
        )
    }

    private fun buildDocUrl(file: File, docsRoot: File): String? {
        return try {
            val relative = file.relativeTo(docsRoot).path.replace('\\', '/')
            DOCS_BASE_URL + STDLIB_DOCS_DIR.removePrefix("$DOCS_DIR/") + "/" + relative
        } catch (_: Exception) {
            null
        }
    }

    private fun extractName(html: String): String? {
        val m = Regex("""<h1 class="cover">\s*<span><span>([^<]+)</span></span>\s*</h1>""").find(html)
        return m?.groupValues?.get(1)?.trim()
    }

    /** First code block is the call signature, e.g. `.multiply a:{Number} by:{Number} -> Number`. */
    private fun extractSignature(html: String): String {
        val m = Regex("""<pre><code class="block lang-kotlin"[^>]*>(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
        return m?.groupValues?.get(1)?.let(::stripAndCollapse) ?: ""
    }

    /** Description paragraphs located between the signature block and the first heading/kdoc section. */
    private fun extractDescription(html: String, signature: String): String {
        if (signature.isEmpty()) return ""
        val end = html.indexOf("</code></pre>")
        if (end < 0) return ""
        var rest = html.substring(end + "</code></pre>".length)

        val cutPositions = listOf("<h4", "<span class=\"kdoc-tag\"", "<h2")
            .map { rest.indexOf(it) }
            .filter { it >= 0 }
        if (cutPositions.isNotEmpty()) rest = rest.substring(0, cutPositions.min())

        return Regex("""<p class="paragraph">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(rest)
            .map { stripAndCollapse(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    private fun extractReturn(html: String): String {
        val m = Regex(
            """<span class="kdoc-tag">\s*<h4[^>]*>Return</h4>\s*(.*?)\s*</span>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)
        return m?.groupValues?.get(1)?.let(::stripAndCollapse) ?: ""
    }

    /** Subsequent code blocks (after the signature) are usage samples. */
    private fun extractSamples(html: String): List<String> {
        return Regex("""<pre><code class="block lang-kotlin"[^>]*>(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .drop(1)
            .map { stripAndCollapse(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /** Parameter table rows: name + description (+ hints like "Likely named"). */
    private fun extractParameters(html: String): Map<String, String> {
        val start = html.indexOf("<h4 class=\"\">Parameters</h4>")
        if (start < 0) return emptyMap()

        var end = html.indexOf("<span class=\"kdoc-tag\"", start)
        val h4After = html.indexOf("<h4", start + 30)
        val h2After = html.indexOf("<h2", start)
        val candidates = listOf(end, h4After, h2After).filter { it > start }
        end = if (candidates.isNotEmpty()) candidates.min() else html.length

        val table = html.substring(start, end)
        val result = LinkedHashMap<String, String>()
        for (row in table.split("<div class=\"table-row\"").drop(1)) {
            val nameM = Regex("""<u>\s*<span><span>([^<]+)</span></span>\s*</u>""").find(row)
                ?: continue
            val paramName = nameM.groupValues[1].trim()
            val descM = Regex("""<p class="paragraph">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                .find(row)
            val desc = descM?.groupValues?.get(1)?.let(::stripAndCollapse) ?: ""
            result[paramName] = desc
        }
        return result
    }

    private fun stripAndCollapse(fragment: String): String {
        val text = fragment.replace(Regex("""<[^>]+>"""), "").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        return text.replace(Regex("""\s+"""), " ").trim()
    }
}
