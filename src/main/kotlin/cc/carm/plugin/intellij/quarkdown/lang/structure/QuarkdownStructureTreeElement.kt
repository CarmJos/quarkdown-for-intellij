package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Tree element for a Quarkdown heading inside the Structure View.
 *
 * Children are gathered by filtering the PSI node's children for
 * [QuarkdownHeading] instances — the parser already nests lower-level
 * headings as children of higher-level ones, so this naturally produces
 * the hierarchical outline:
 *
 * ```
 * 📄 example.qd
 *   H1  Chapter 1
 *     H2  Section 1.1
 *       H3  Subsection
 *     H2  Section 1.2
 *   H1  Chapter 2
 * ```
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
        // The parser nests child headings as PSI children of the parent heading.
        // We filter for QuarkdownHeading instances to build the outline tree.
        return element.children
            .filterIsInstance<QuarkdownHeading>()
            .map { QuarkdownStructureTreeElement(it) }
            .toTypedArray()
    }

    /**
     * Whether this element has any nested [QuarkdownHeading] children.
     * Used by the ViewModel to show "+" expand icons vs leaf markers.
     */
    fun hasNestedHeadings(): Boolean {
        return element.children.any { it is QuarkdownHeading }
    }

    /**
     * Presentation for a heading node — shows only the heading text.
     */
    private class HeadingPresentation(private val heading: QuarkdownHeading) : ItemPresentation {
        override fun getPresentableText(): String {
            return heading.headingText.ifEmpty { "(empty heading)" }
        }

        override fun getLocationString(): String? = null

        override fun getIcon(unused: Boolean): Icon = QuarkdownIcons.FILE
    }
}
