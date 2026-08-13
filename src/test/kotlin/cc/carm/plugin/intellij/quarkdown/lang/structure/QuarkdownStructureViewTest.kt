package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the Structure View: `{#id}` stripped from heading text and shown as the gray
 * location string, heading nodes use the gutter icon, content blocks (image/table/equation/
 * code) appear under their heading, and nodes can be navigated to by double-click.
 */
class QuarkdownStructureViewTest : BasePlatformTestCase() {

    private fun headingTreeElements(): List<QuarkdownStructureTreeElement> {
        val model = QuarkdownStructureViewModel(myFixture.file, myFixture.editor)
        return model.root.children
            .filterIsInstance<QuarkdownStructureTreeElement>()
            .toList()
    }

    fun `test heading with id shows text and id separately`() {
        myFixture.configureByText("test.qd", "# Example {#first}\n")
        val heading = headingTreeElements().single()

        assertEquals("Example", heading.presentation.presentableText)
        assertEquals("first", heading.presentation.locationString)
    }

    fun `test heading without id has empty location`() {
        myFixture.configureByText("test.qd", "# Example\n")
        val heading = headingTreeElements().single()

        assertEquals("Example", heading.presentation.presentableText)
        assertNull("no id means no location string", heading.presentation.locationString)
    }

    fun `test heading icon is the gutter marker icon`() {
        myFixture.configureByText("test.qd", "# Example {#first}\n")
        val heading = headingTreeElements().single()

        assertSame("heading should use the gutter marker icon", QuarkdownIcons.HEADING_MARKER, heading.presentation.getIcon(false))
    }

    fun `test nested heading keeps hierarchy`() {
        myFixture.configureByText("test.qd", "# Chapter {#ch}\n\n## Section {#sec}\n")
        val chapter = headingTreeElements().single()
        assertEquals("Chapter", chapter.presentation.presentableText)
        assertEquals("ch", chapter.presentation.locationString)

        val section = chapter.children
            .filterIsInstance<QuarkdownStructureTreeElement>()
            .single()
        assertEquals("Section", section.presentation.presentableText)
        assertEquals("sec", section.presentation.locationString)
    }

    fun `test heading id is exposed on the psi element`() {
        myFixture.configureByText("test.qd", "# Example {#first}\n")
        val heading = PsiTreeUtil.findChildOfType(myFixture.file, QuarkdownHeading::class.java)!!
        assertEquals("first", heading.id)
        assertEquals("Example", heading.headingText)
    }

    fun `test heading can be navigated to`() {
        myFixture.configureByText("test.qd", "# Example\nsome prose\n")
        val heading = headingTreeElements().single()

        assertTrue("heading must be navigable", heading.canNavigate())
        assertTrue(heading.canNavigateToSource())
    }

    fun `test image appears as a child of its heading`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n![alt](images/logo.png)\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.IMAGE, content.block.kind)
        assertEquals("logo.png", content.presentation.presentableText)
        assertNull("no id means no location string", content.presentation.locationString)
        assertSame(QuarkdownIcons.IMAGE_MARKER, content.presentation.getIcon(false))
        assertTrue("content block must be navigable", content.canNavigate())
    }

    fun `test image with title and id shows title and gray id`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n!(105%)[img](img.png \"Test Image\") {#img-1}\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.IMAGE, content.block.kind)
        assertEquals("title should be the display label", "Test Image", content.presentation.presentableText)
        assertEquals("id must be shown in gray", "img-1", content.presentation.locationString)
    }

    fun `test image with only an id shows the path and gray id`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n![alt](images/logo.png){#img-logo}\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals("logo.png", content.presentation.presentableText)
        assertEquals("img-logo", content.presentation.locationString)
    }

    fun `test table appears as a child of its heading`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n| A | B |\n|---|---|\n| 1 | 2 |\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.TABLE, content.block.kind)
        assertEquals("A | B", content.presentation.presentableText)
        assertNull("no label line means no location", content.presentation.locationString)
        assertSame(QuarkdownIcons.TABLE_MARKER, content.presentation.getIcon(false))
    }

    fun `test table with caption label shows caption and gray id`() {
        myFixture.configureByText(
            "test.qd",
            "# Chapter\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Some Values\" {#table}\n"
        )
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals("Some Values", content.presentation.presentableText)
        assertEquals("table", content.presentation.locationString)
    }

    fun `test heading id does not leak from a later table label`() {
        // The `{#table}` belongs to the table's label line, NOT to the heading above it.
        myFixture.configureByText(
            "test.qd",
            "### Another thing\n\nQwq\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Some Values\" {#table}\n"
        )
        val heading = headingTreeElements().single()
        assertEquals("Another thing", heading.presentation.presentableText)
        assertNull("heading must not inherit the table's id", heading.presentation.locationString)
    }

    fun `test fenced code block appears under its heading`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n```python\nprint(1)\n```\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.CODE_BLOCK, content.block.kind)
        assertEquals("python", content.presentation.presentableText)
        assertNull("no caption/id means no location", content.presentation.locationString)
        assertSame(QuarkdownIcons.CODE_MARKER, content.presentation.getIcon(false))
    }

    fun `test fenced code block with caption and id shows caption and gray id`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n```java \"Super Code\" {#code-1}\nvoid main() {}\n```\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.CODE_BLOCK, content.block.kind)
        assertEquals("caption should be the display label", "Super Code", content.presentation.presentableText)
        assertEquals("id must be shown in gray", "code-1", content.presentation.locationString)
        assertSame(QuarkdownIcons.CODE_MARKER, content.presentation.getIcon(false))
    }

    fun `test fenced code block with id but no caption shows language and gray id`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n```java {#code-1}\nvoid main() {}\n```\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals("java", content.presentation.presentableText)
        assertEquals("code-1", content.presentation.locationString)
    }

    fun `test code function block shows caption and gray id`() {
        myFixture.configureByText(
            "test.qd",
            "# Chapter\n\n.code lang:{java} caption:{Super Code} ref:{code-1}\n    void main() {}\n"
        )
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.CODE_BLOCK, content.block.kind)
        assertEquals("Super Code", content.presentation.presentableText)
        assertEquals("code-1", content.presentation.locationString)
    }

    fun `test equation appears under its heading`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n$$$\nE = mc^2\n$$$\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals(QuarkdownStructureContentElement.Kind.EQUATION, content.block.kind)
        assertEquals("Equation", content.presentation.presentableText)
        assertNull("no id means no location", content.presentation.locationString)
        assertSame(QuarkdownIcons.EQUATION_MARKER, content.presentation.getIcon(false))
    }

    fun `test equation with id shows the id in gray`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n$$$ {#eq-energy}\nE = mc^2\n$$$\n")
        val heading = headingTreeElements().single()

        val content = heading.children
            .filterIsInstance<QuarkdownStructureContentElement>()
            .single()
        assertEquals("Equation", content.presentation.presentableText)
        assertEquals("eq-energy", content.presentation.locationString)
    }

    fun `test content navigation targets the block offset`() {
        myFixture.configureByText("test.qd", "# Chapter\n\n![alt](images/logo.png)\n")
        val heading = headingTreeElements().single()
        val content = heading.children.filterIsInstance<QuarkdownStructureContentElement>().single()

        // The fake PSI element must report the block's real offset so navigation
        // does not jump to the top of the file (FakePsiElement.getTextOffset() defaults to 0).
        assertEquals(content.block.offset, content.value.textOffset)
    }

    fun `test content stays under its own heading not the parent`() {
        myFixture.configureByText(
            "test.qd",
            "# Parent\n\n![img](a.png)\n\n## Child\n\n| X | Y |\n|---|---|\n| 1 | 2 |\n"
        )
        val parent = headingTreeElements().single()
        val child = parent.children.filterIsInstance<QuarkdownStructureTreeElement>().single()

        val parentContent = parent.children.filterIsInstance<QuarkdownStructureContentElement>()
        assertEquals("parent keeps its own image", setOf("a.png"), parentContent.map { it.presentation.presentableText }.toSet())

        val childContent = child.children.filterIsInstance<QuarkdownStructureContentElement>()
        assertEquals("child keeps its own table", setOf("X | Y"), childContent.map { it.presentation.presentableText }.toSet())
    }

    fun `test image inside nested heading is not duplicated in parent`() {
        myFixture.configureByText(
            "test.qd",
            "# Parent\n\n## Child\n\n![img](a.png)\n"
        )
        val parent = headingTreeElements().single()
        assertEquals("parent must not show child's content", 0, parent.children.filterIsInstance<QuarkdownStructureContentElement>().size)
        val child = parent.children.filterIsInstance<QuarkdownStructureTreeElement>().single()
        assertEquals("child must show its image", 1, child.children.filterIsInstance<QuarkdownStructureContentElement>().size)
    }

    fun `test fenced code id is a navigable declaration`() {
        myFixture.configureByText("test.qd", "```java \"Super Code\" {#code-1}\nvoid main() {}\n```\n")
        // The id must be a PsiNamedElement leaf (like image/table ids) so Ctrl+Click works.
        val codeId = myFixture.file.findElementAt(myFixture.file.text.indexOf("code-1") + 1)
        assertNotNull("must find a leaf at the code id", codeId)
        assertTrue(
            "code id leaf must be a named element",
            codeId is com.intellij.psi.PsiNamedElement
        )
        assertEquals("code-1", (codeId as com.intellij.psi.PsiNamedElement).name)
        // The id must carry a reference so it is underlined and clickable.
        val ref = myFixture.file.findReferenceAt(myFixture.file.text.indexOf("code-1") + 3)
        assertNotNull("code id must be clickable", ref)
    }

    fun `test fenced code caption token is produced`() {
        myFixture.configureByText("test.qd", "```java \"Super Code\" {#code-1}\nvoid main() {}\n```\n")
        val caption = myFixture.file.findElementAt(myFixture.file.text.indexOf("Super Code") + 1)
        assertNotNull("must find a leaf at the caption", caption)
        assertEquals(
            "caption must be a CAPTION token",
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.CAPTION,
            caption!!.node?.elementType
        )
    }
}
