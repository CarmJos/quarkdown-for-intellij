package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.google.gson.Gson
import java.awt.Color

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
 *
 * The page is also where the preview's two interactive behaviours live, because the embedded
 * browser forwards input to the page rather than to the Swing component around it:
 *
 *  - **theme** — the text and background colors are CSS variables the host sets with [themeCall];
 *    without them the formula would keep the page's default black, which is unreadable on the dark
 *    theme;
 *  - **zoom & pan** — the wheel zooms around the pointer and dragging with the left button pans, so
 *    a formula can be inspected without leaving the dialog.
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
            /* The colours come from the IDE theme (see setTheme): a fixed black would be unreadable
               on the dark theme, and a fixed white on the light one. */
            html, body {
              margin: 0; padding: 0;
              background: var(--quarkdown-background, transparent);
              color: var(--quarkdown-foreground, #000000);
              font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
            }
            body { overflow: hidden; }
            /* The whole pane is the drag surface; the formula floats inside it. */
            #viewport {
              position: absolute; inset: 0; overflow: hidden;
              cursor: grab; touch-action: none;
            }
            #viewport.dragging { cursor: grabbing; }
            #output {
              position: absolute; left: 50%; top: 50%;
              transform: translate(-50%, -50%);
              transform-origin: center center;
              padding: 8px 12px;
              will-change: transform;
            }
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
          <div id="viewport"><div id="output"></div></div>
          <script>
            // Zoom & pan. The view is a single CSS transform on the formula, so it needs no layout
            // pass and stays smooth; the wheel zooms around the pointer and the left button pans.
            (function () {
              var viewport = document.getElementById('viewport');
              var output = document.getElementById('output');
              var MIN_ZOOM = 0.2, MAX_ZOOM = 8, ZOOM_STEP = 1.1;
              var zoom = 1, panX = 0, panY = 0;
              var dragging = false, lastX = 0, lastY = 0;

              function applyView() {
                output.style.transform =
                  'translate(-50%, -50%) translate(' + panX + 'px, ' + panY + 'px) scale(' + zoom + ')';
              }

              viewport.addEventListener('wheel', function (event) {
                event.preventDefault();
                var next = zoom * (event.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
                next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, next));
                // Keep whatever sits under the pointer in place while the scale changes.
                var centreX = window.innerWidth / 2, centreY = window.innerHeight / 2;
                var ratio = next / zoom;
                panX = (event.clientX - centreX) - ((event.clientX - centreX) - panX) * ratio;
                panY = (event.clientY - centreY) - ((event.clientY - centreY) - panY) * ratio;
                zoom = next;
                applyView();
              }, { passive: false });

              viewport.addEventListener('mousedown', function (event) {
                if (event.button !== 0) return;
                dragging = true;
                lastX = event.clientX; lastY = event.clientY;
                viewport.classList.add('dragging');
                event.preventDefault();
              });

              window.addEventListener('mousemove', function (event) {
                if (!dragging) return;
                panX += event.clientX - lastX;
                panY += event.clientY - lastY;
                lastX = event.clientX; lastY = event.clientY;
                applyView();
              });

              window.addEventListener('mouseup', function () {
                dragging = false;
                viewport.classList.remove('dragging');
              });

              applyView();
            })();

            window.$API_NAME = {
              /**
               * Applies the IDE colours, so the formula follows the current theme. Both arguments
               * are CSS colors taken from the running theme by the host.
               */
              setTheme: function (foreground, background) {
                document.documentElement.style.setProperty('--quarkdown-foreground', foreground);
                document.documentElement.style.setProperty('--quarkdown-background', background);
              },
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
     * The JavaScript call that switches the page to [foreground] / [background] (CSS colors).
     *
     * The page cannot know the IDE theme, and a formula typeset for the wrong one is barely
     * readable — this is how the host keeps the text in the theme's own color. Values are
     * JSON-encoded, so they cannot break out of the call either.
     */
    fun themeCall(foreground: String, background: String): String =
        "$API_NAME.setTheme(${gson.toJson(foreground)}, ${gson.toJson(background)});"

    /** [color] as the CSS `rgb(…)` literal the page's theme variables expect. */
    fun cssColor(color: Color): String = "rgb(${color.red}, ${color.green}, ${color.blue})"

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

