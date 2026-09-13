package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexPreviewSource
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.io.File
import java.nio.file.Files

/**
 * Verifies the pieces of the KaTeX-based preview that can be checked without a browser:
 * locating the renderer inside a Quarkdown installation, extracting `.texmacro` declarations,
 * and building the page / the JavaScript call that typesets a formula.
 */
class QuarkdownLatexPreviewSourceTest {

    // ------------------------------------------------------------------
    // Macro extraction
    // ------------------------------------------------------------------

    @Test
    fun `reads a braced macro declaration`() {
        val macros = QuarkdownLatexPreviewSource.macros(".texmacro {\\gradient} {\\nabla}\n")
        assertEquals(mapOf("\\gradient" to "\\nabla"), macros)
    }

    @Test
    fun `reads a macro declared with named arguments`() {
        val macros = QuarkdownLatexPreviewSource.macros(".texmacro name:{\\R} macro:{\\mathbb{R}}\n")
        assertEquals(mapOf("\\R" to "\\mathbb{R}"), macros)
    }

    @Test
    fun `reads a macro declared with a block body`() {
        // The body-argument form is the one that is easy to miss: the region logic covers it.
        val macros = QuarkdownLatexPreviewSource.macros(".texmacro {\\sumlim}\n    \\sum_{#1}^{#2}\n")
        assertEquals(mapOf("\\sumlim" to "\\sum_{#1}^{#2}"), macros)
    }

    @Test
    fun `keeps parameterised macro bodies verbatim`() {
        val macros = QuarkdownLatexPreviewSource.macros(".texmacro {\\highlight} {\\colorbox{blue}{#1}}\n")
        assertEquals(mapOf("\\highlight" to "\\colorbox{blue}{#1}"), macros)
    }

    @Test
    fun `a later declaration of the same macro wins`() {
        val text = ".texmacro {\\R} {\\mathbb{R}}\n\n.texmacro {\\R} {\\mathbf{R}}\n"
        assertEquals(mapOf("\\R" to "\\mathbf{R}"), QuarkdownLatexPreviewSource.macros(text))
    }

    @Test
    fun `skips declarations without a body`() {
        assertTrue(QuarkdownLatexPreviewSource.macros(".texmacro {\\onlyname}\n").isEmpty())
    }

    @Test
    fun `a document without macros yields an empty map`() {
        assertTrue(QuarkdownLatexPreviewSource.macros("Just \$ x = 1 \$ prose.\n").isEmpty())
    }

    // ------------------------------------------------------------------
    // Display mode
    // ------------------------------------------------------------------

    @Test
    fun `a fenced formula is typeset in display mode`() {
        assertTrue(QuarkdownLatexPreviewSource.displayMode("x = 1", fenced = true))
    }

    @Test
    fun `a multi-line formula is typeset in display mode`() {
        assertTrue(QuarkdownLatexPreviewSource.displayMode("a = 1\nb = 2", fenced = false))
    }

    @Test
    fun `a single-line inline formula is not in display mode`() {
        assertFalse(QuarkdownLatexPreviewSource.displayMode("x = 1", fenced = false))
    }

    // ------------------------------------------------------------------
    // Page and render call
    // ------------------------------------------------------------------

    @Test
    fun `the page loads the renderer and exposes the render entry point`() {
        val html = QuarkdownLatexPreviewSource.pageHtml("lib/katex/katex.min.js", "lib/katex/katex.min.css")
        assertTrue("the renderer script must be referenced", html.contains("lib/katex/katex.min.js"))
        assertTrue("the stylesheet must be referenced", html.contains("lib/katex/katex.min.css"))
        assertTrue("the API must be exposed", html.contains("window.${QuarkdownLatexPreviewSource.API_NAME}"))
        assertTrue("KaTeX must be called with macros", html.contains("macros: macros"))
    }

    @Test
    fun `the page takes its colors from the theme`() {
        val html = QuarkdownLatexPreviewSource.pageHtml("lib/katex/katex.min.js", "lib/katex/katex.min.css")
        assertTrue("the host must be able to set the theme", html.contains("setTheme: function"))
        assertTrue(
            "the text must use the theme foreground",
            html.contains("var(--quarkdown-foreground"),
        )
        assertTrue(
            "the background must use the theme background",
            html.contains("var(--quarkdown-background"),
        )
    }

    @Test
    fun `the page zooms with the wheel and pans by dragging`() {
        val html = QuarkdownLatexPreviewSource.pageHtml("lib/katex/katex.min.js", "lib/katex/katex.min.css")
        assertTrue("the wheel must zoom", html.contains("addEventListener('wheel'"))
        assertTrue("the left button must start a drag", html.contains("addEventListener('mousedown'"))
        assertTrue("moving the mouse must pan", html.contains("addEventListener('mousemove'"))
        assertTrue("the drag must end on mouse up", html.contains("addEventListener('mouseup'"))
        assertTrue("the view must be a CSS transform", html.contains("output.style.transform"))
    }

    @Test
    fun `the theme call passes both colors`() {
        val call = QuarkdownLatexPreviewSource.themeCall("rgb(1, 2, 3)", "rgb(4, 5, 6)")
        assertTrue(
            "the call must target the page API",
            call.startsWith("${QuarkdownLatexPreviewSource.API_NAME}.setTheme("),
        )
        assertTrue("the foreground must be JSON-encoded", call.contains("\"rgb(1, 2, 3)\""))
        assertTrue("the background must be JSON-encoded", call.contains("\"rgb(4, 5, 6)\""))
    }

    @Test
    fun `a color becomes a CSS rgb literal`() {
        assertEquals("rgb(10, 20, 30)", QuarkdownLatexPreviewSource.cssColor(Color(10, 20, 30)))
    }

    @Test
    fun `the render call passes the formula, macros and display mode`() {
        val call = QuarkdownLatexPreviewSource.renderCall(
            "\\gradient f",
            mapOf("\\gradient" to "\\nabla"),
            displayMode = true,
        )
        assertTrue("the call must target the page API", call.startsWith("${QuarkdownLatexPreviewSource.API_NAME}.render("))
        assertTrue("display mode must be passed", call.endsWith(", true);"))
        assertTrue("the formula must be JSON-encoded", call.contains("\"\\\\gradient f\""))
        assertTrue("the macro must be JSON-encoded", call.contains("\"\\\\gradient\""))
    }

    @Test
    fun `a formula containing quotes and newlines cannot break out of the call`() {
        val call = QuarkdownLatexPreviewSource.renderCall("a \"quoted\" \\ b\nc", emptyMap(), false)
        // Everything after the API prefix must be valid JSON arguments, so parse the payload back.
        val payload = call.removePrefix("${QuarkdownLatexPreviewSource.API_NAME}.render(").removeSuffix(");")
        val parts = payload.split(", ", limit = 3)
        val decoded = JsonParser.parseString(parts[0]).asString
        assertEquals("a \"quoted\" \\ b\nc", decoded)
        assertTrue("the object payload is valid JSON", JsonParser.parseString(parts[1]).isJsonObject)
    }

    // ------------------------------------------------------------------
    // Asset lookup
    // ------------------------------------------------------------------

    @Test
    fun `an installation without the renderer is not an asset root`() {
        val home = Files.createTempDirectory("quarkdown-home").toFile()
        assertTrue(QuarkdownLatexWebAssets.assetRoot(home) == null)
        assertTrue(QuarkdownLatexWebAssets.assetRoot(null) == null)
    }

    @Test
    fun `an installation with the renderer resolves its asset root`() {
        val home = Files.createTempDirectory("quarkdown-home").toFile()
        val katex = File(home, "${QuarkdownLatexWebAssets.ASSET_ROOT}/lib/katex")
        assertTrue("test setup must create the renderer directory", katex.mkdirs())
        File(katex, "katex.min.js").writeText("// stub")

        val root = QuarkdownLatexWebAssets.assetRoot(home)
        assertEquals(File(home, QuarkdownLatexWebAssets.ASSET_ROOT), root)
        assertTrue(File(root!!, QuarkdownLatexWebAssets.SCRIPT_PATH).isFile)
    }
}

