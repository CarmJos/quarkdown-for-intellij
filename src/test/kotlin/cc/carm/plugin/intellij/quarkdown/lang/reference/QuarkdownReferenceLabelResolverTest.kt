package cc.carm.plugin.intellij.quarkdown.lang.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies [QuarkdownReferenceLabelResolver] maps `.ref {id}` targets to their element
 * type and caption for the documented cross-reference syntaxes.
 */
class QuarkdownReferenceLabelResolverTest {

    private fun resolve(text: String, id: String) = QuarkdownReferenceLabelResolver.resolve(text, id)

    @Test
    fun `resolves a section (heading) reference`() {
        val text = "Check out .ref {getting-started} for a quick guide.\n\n## Getting started {#getting-started}\n"
        val target = resolve(text, "getting-started")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.SECTION, target.kind)
        assertEquals("Getting started", target.caption)
    }

    @Test
    fun `resolves a figure (image) reference`() {
        val text = "The Quarkdown logo is shown in .ref {logo}.\n\n![Logo](icon.svg \"The Quarkdown icon\") {#logo}\n"
        val target = resolve(text, "logo")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.FIGURE, target.kind)
        assertEquals("The Quarkdown icon", target.caption)
    }

    @Test
    fun `resolves a figure reference falling back to alt text`() {
        val text = "See .ref {logo}.\n\n![Quarkdown icon](icon.svg) {#logo}\n"
        val target = resolve(text, "logo")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.FIGURE, target.kind)
        assertEquals("Quarkdown icon", target.caption)
    }

    @Test
    fun `resolves a table reference with a caption`() {
        val text = "As shown in .ref {data}.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Beverage preferences\" {#data}\n"
        val target = resolve(text, "data")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.TABLE, target.kind)
        assertEquals("Beverage preferences", target.caption)
    }

    @Test
    fun `resolves a table reference without a caption`() {
        val text = "As shown in .ref {data}.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n{#data}\n"
        val target = resolve(text, "data")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.TABLE, target.kind)
        assertEquals("", target.caption)
    }

    @Test
    fun `resolves a fenced code block reference`() {
        val text = "See the main function in .ref {main}.\n\n```kotlin \"Hello World\" {#main}\nfun main() {}\n```\n"
        val target = resolve(text, "main")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.CODE, target.kind)
        assertEquals("Hello World", target.caption)
    }

    @Test
    fun `resolves a code function reference`() {
        val text = "See .ref {main}.\n\n.code lang:{kotlin} caption:{Hello World} ref:{main}\n    fun main() {}\n"
        val target = resolve(text, "main")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.CODE, target.kind)
        assertEquals("Hello World", target.caption)
    }

    @Test
    fun `resolves an inline equation reference`() {
        val text = "Einstein's famous equation is shown in .ref {energy}.\n\n$ E = mc^2 $ {#energy}\n"
        val target = resolve(text, "energy")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.EQUATION, target.kind)
        assertEquals("", target.caption)
    }

    @Test
    fun `resolves a heading slug fallback without an explicit label`() {
        val text = "See .ref {getting-started}.\n\n## Getting started\n"
        val target = resolve(text, "getting-started")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.SECTION, target.kind)
        assertEquals("Getting started", target.caption)
    }

    @Test
    fun `resolves case-insensitively`() {
        val text = "See .ref {Data}.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Preferences\" {#data}\n"
        val target = resolve(text, "Data")!!
        assertEquals(QuarkdownReferenceLabelResolver.Kind.TABLE, target.kind)
        assertEquals("Preferences", target.caption)
    }

    @Test
    fun `returns null for an unresolvable reference`() {
        assertNull(resolve("See .ref {unknown}.", "unknown"))
    }
}
