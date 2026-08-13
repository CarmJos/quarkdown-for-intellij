package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Tree element for a Quarkdown heading (or the root file) inside the Structure View.
 *
 * Children are gathered from the heading's own PSI children: nested [QuarkdownHeading]s
 * build the hierarchical outline, while the heading's section content is scanned for
 * image / table / equation / code-block blocks and appended after the nested headings:
 *
 * ```
 * 📄 example.qd
 *   H1  Chapter 1
 *     🖼 image.png
 *     H2  Section 1.1
 *       🔢 Equation
 *     H2  Section 1.2
 *   H1  Chapter 2
 * ```
 *
 * Navigation is delegated to the wrapped PSI element (or content block), so double-clicking
 * a node opens the editor at that position.
 */
class QuarkdownStructureTreeElement(private val element: PsiElement) : StructureViewTreeElement {

    override fun getValue(): PsiElement = element

    override fun getPresentation(): ItemPresentation {
        if (element is QuarkdownHeading) {
            return HeadingPresentation(element)
        }
        // Root file node — show filename with file icon
        if (element is PsiFile) {
            return object : ItemPresentation {
                override fun getPresentableText(): String = element.name
                override fun getLocationString(): String? = null
                override fun getIcon(unused: Boolean): Icon = QuarkdownIcons.FILE
            }
        }
        return object : ItemPresentation {
            override fun getPresentableText(): String = element.text.take(48)
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon? = null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return when (element) {
            is QuarkdownHeading -> headingChildren(element)
            is PsiFile -> fileChildren(element)
            else -> TreeElement.EMPTY_ARRAY
        }
    }

    /**
     * Whether this element has any nested [QuarkdownHeading] children.
     * Used by the ViewModel to show "+" expand icons vs leaf markers.
     */
    private fun hasNestedHeadings(): Boolean {
        return element.children.any { it is QuarkdownHeading }
    }

    /**
     * Whether this element has any child tree elements (nested headings or content blocks).
     * Used by the ViewModel to show "+" expand icons vs leaf markers.
     */
    fun hasChildren(): Boolean {
        return when (element) {
            is QuarkdownHeading -> hasNestedHeadings() || contentBlocksOf(element).isNotEmpty()
            is PsiFile -> element.children.isNotEmpty()
            else -> false
        }
    }

    /**
     * Whether this tree element can be navigated (double-clicked) to its source location.
     * The [Navigatable] base default returns `false`, so it must be overridden explicitly.
     */
    override fun canNavigate(): Boolean = when (element) {
        is QuarkdownHeading -> element.isValid && element.containingFile != null
        is PsiFile -> element.isValid
        else -> false
    }

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        if (!canNavigate()) return
        val descriptor = PsiNavigationSupport.getInstance().getDescriptor(element) ?: return
        descriptor.navigate(requestFocus)
    }

    /** Children of a heading: nested headings plus the content blocks in its section. */
    private fun headingChildren(heading: QuarkdownHeading): Array<TreeElement> {
        val result = mutableListOf<TreeElement>()
        result += contentBlocksOf(heading)
        result += heading.children
            .filterIsInstance<QuarkdownHeading>()
            .map { QuarkdownStructureTreeElement(it) }
        return result.toTypedArray()
    }

    /** Children of the root file: the top-level headings (content blocks live under headings). */
    private fun fileChildren(file: PsiFile): Array<TreeElement> {
        return file.children
            .filterIsInstance<QuarkdownHeading>()
            .map { QuarkdownStructureTreeElement(it) }
            .toTypedArray()
    }

    /** Content blocks (images, tables, equations, code blocks) directly inside [heading]'s section. */
    private fun contentBlocksOf(heading: QuarkdownHeading): List<QuarkdownStructureContentElement> {
        val range = headingSectionRange(heading) ?: return emptyList()
        return QuarkdownStructureContentScanner.scan(heading.containingFile ?: return emptyList(), range)
    }

    /**
     * The text range of [heading]'s *direct* content: from the end of its heading line up to
     * the start of the first nested heading (or the end of the heading node when there is none).
     *
     * The parser nests every token after a heading under its PSI node until the next heading
     * of equal/higher level, so the heading node's own range already covers exactly this
     * section. Content belonging to nested headings must be excluded (it is shown under those
     * headings instead), so the range stops at the first [QuarkdownHeading] child.
     */
    private fun headingSectionRange(heading: QuarkdownHeading): IntRange? {
        val node = heading.node
        val nodeStart = node.startOffset
        val nodeEnd = nodeStart + node.textLength
        // Find the end of the heading line: the first NEWLINE child.
        var headingLineEnd = nodeEnd
        for (child in node.getChildren(null)) {
            if (child.text.contains('\n')) {
                headingLineEnd = child.startOffset + child.textLength
                break
            }
        }
        // The direct section ends where the first nested heading starts.
        val sectionEnd = heading.children
            .filterIsInstance<QuarkdownHeading>()
            .minOfOrNull { it.node.startOffset }
            ?: nodeEnd
        if (headingLineEnd >= sectionEnd) return null
        return headingLineEnd until sectionEnd
    }

    /**
     * Presentation for a heading node — shows the heading text (without the `{#id}`)
     * and renders the cross-reference id in gray via [getLocationString].
     */
    private class HeadingPresentation(private val heading: QuarkdownHeading) : ItemPresentation {
        override fun getPresentableText(): String {
            return heading.headingText.ifEmpty { QuarkdownBundle.message("quarkdown.heading.empty") }
        }

        override fun getLocationString(): String? = heading.id.takeIf { it.isNotEmpty() }

        override fun getIcon(unused: Boolean): Icon = QuarkdownIcons.HEADING_MARKER
    }
}
