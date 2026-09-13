package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.google.gson.Gson

/**
 * Pure (no IntelliJ dependencies) builder of the page that typesets LaTeX with the KaTeX copy
 * bundled in the Quarkdown installation.
 *
 * The page is loaded once into the embedded browser; equations are then handed to it with
 * [renderCall], which is a single JavaScript call. That makes a preview take milliseconds instead
 * of the seconds a CLI compile needs, and nothing has to be spawned or bundled.
 *
 * Two details make the result match the final document:
 *
 *  - **the same KaTeX build** — the script and stylesheet are the exact files Quarkdown copies into
 *    every compiled document (see [QuarkdownLatexWebAssets]), so the glyphs and metrics agree;
 *  - **the document's macros** — `.texmacro` declarations are read out of the document and passed
 *    to KaTeX's `macros` option ([macros]), so custom commands resolve. Parameterised macros
 *    (`.texmacro {\sumlim} {\sum_{#1}^{#2}}`) work too, because KaTeX implements `#1` natively.
 *
 * Unlike `.math`, KaTeX does **not** evaluate Quarkdown function calls: nested calls inside a
 * `.math` body are shown as their raw text, which is the honest thing to show when the values are
 * only known to the compiler.
 */
object QuarkdownLatexPreviewSource {

    private val gson = Gson()

    /** Marker the page exposes; the browser view calls into it (see [renderCall]). */
    const val API_NAME = "quarkdownMath"

    /**
     * The document/page HTML. [scriptUrl] and [stylesheetUrl] are resolved by the browser, so callers pass
     * paths relative to the served asset root (see `QuarkdownLatexAssetServer`); the stylesheet's
     * own relative font references then keep resolving next to it.
     */
    fun pageHtml(scriptUrl: String, stylesheetUrl: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <link rel="stylesheet" href="${escapeAttribute(stylesheetUrl)}">
          <script src="${escapeAttribute(scriptUrl)}"></script>
          <style>
            html, body { margin: 0; padding: 0; background: transparent; }
            body {
              /* The preview floats above the editor input, so the formula is centred and the
                 placeholder keeps the pane from collapsing when nothing is rendered yet. */
              display: flex; align-items: center; justify-content: center;
              min-height: 100vh; overflow: auto;
              font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
            }
            #output { padding: 8px 12px; }
            #output.display { width: 100%; text-align: center; }
            .placeholder { color: #9aa0a6; font-size: 12px; }
            .error {
              color: #d93025; font-family: monospace; font-size: 12px;
              white-space: pre-wrap; text-align: left;
            }
            .katex { font-size: 1.35em; }
          </style>
        </head>
        <body>
          <div id="output"></div>
          <script>
            window.$API_NAME = {
              /**
               * Typesets [tex] into the page. [macros] is a JSON object of macro name to
               * replacement, [displayMode] selects block vs inline layout.
               */
              render: function (tex, macros, displayMode) {
                var output = document.getElementById('output');
                output.className = displayMode ? 'display' : '';
                if (!tex) {
                  output.innerHTML = '<span class="placeholder">${PLACEHOLDER_TEXT}</span>';
                  return;
                }
                if (typeof katex === 'undefined') {
                  output.className = 'error';
                  output.textContent = '${KATEX_UNAVAILABLE_TEXT}';
                  return;
                }
                try {
                  // throwOnError stays false: an unfinished formula is the normal state while
                  // typing, so a partial expression is shown in red rather than throwing.
                  katex.render(tex, output, {
                    displayMode: !!displayMode,
                    throwOnError: false,
                    strict: false,
                    macros: macros || {}
                  });
                } catch (e) {
                  output.className = 'error';
                  output.textContent = (e && e.message) ? e.message : String(e);
                }
              }
            };
          </script>
        </body>
        </html>
    """.trimIndent()

    /**
     * The JavaScript call that typesets [tex] with [macros]. Values are JSON-encoded, so quotes,
     * backslashes and newlines in a formula cannot break out of the call.
     */
    fun renderCall(tex: String, macros: Map<String, String>, displayMode: Boolean): String =
        "$API_NAME.render(${gson.toJson(tex)}, ${gson.toJson(macros)}, $displayMode);"


    /**
     * Every `.texmacro {name} {body}` declaration of [documentText], as the macro map KaTeX takes.
     *
     * Quarkdown emits exactly this into the generated page as `window.texMacros`, so the keys keep
     * their leading backslash (`{"\gradient": "\nabla"}`). A macro may declare its body either as a
     * braced argument or as an indented block body, so the regions from
     * [QuarkdownEquationRegions] are reused: they already cover both forms. Declarations missing a
     * name or a body are skipped, and a later re-declaration of a name wins, matching the compiler.
     */
    fun macros(documentText: CharSequence): Map<String, String> {
        val source = documentText.toString()
        val macroRegions = QuarkdownEquationRegions.find(source).regions
            .filter { it.kind == QuarkdownEquationRegions.Kind.TEX_MACRO }
        if (macroRegions.isEmpty()) return emptyMap()

        val callStarts = QuarkdownCallParser.findAllCallStarts(source)
        val macros = LinkedHashMap<String, String>()
        for ((index, start) in callStarts.withIndex()) {
            val call = QuarkdownCallParser.parseCall(source, start) ?: continue
            if (call.name != "texmacro") continue
            // The name region comes first, the body second; both lie before the next call.
            val limit = callStarts.getOrElse(index + 1) { source.length }
            val own = macroRegions.filter { it.contentStart >= start && it.contentStart < limit }
            val name = own.getOrNull(0)?.let { source.substring(it.contentStart, it.contentEnd) }?.trim()
            val body = own.getOrNull(1)?.let { source.substring(it.contentStart, it.contentEnd) }?.trim()
            if (name.isNullOrEmpty() || body.isNullOrEmpty()) continue
            macros[name] = body
        }
        return macros
    }

    /**
     * Whether a formula should be typeset in display (block) mode: a `$$$` block always is, and so
     * is any multi-line expression, since inline layout cannot break lines.
     */
    fun displayMode(content: String, fenced: Boolean): Boolean =
        fenced || content.contains('\n')


    private fun escapeAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")

    /** Page texts are inlined into the script, so they are escaped for a single-quoted literal. */
    private const val PLACEHOLDER_TEXT = "No formula yet"

    private const val KATEX_UNAVAILABLE_TEXT =
        "KaTeX is unavailable: check the Quarkdown home in Settings."
}

