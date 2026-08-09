package cc.carm.plugin.intellij.quarkdown.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

/**
 * PSI element representing a Quarkdown heading line (e.g., `## My Title`).
 *
 * Provides convenience methods to access the heading level and text content,
 * used by breadcrumbs navigation and structure view.
 */
class QuarkdownHeading(node: ASTNode) : ASTWrapperPsiElement(node) {

    /** The heading level: 1 for `#`, 2 for `##`, ..., up to 6 for `######`. */
    val level: Int
        get() {
            val marker = node.findChildByType(
                cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HEADING_MARKER
            )
            return marker?.text?.count { it == '#' } ?: 1
        }

    /** The heading text content (without the `#` marker and surrounding whitespace). */
    val headingText: String
        get() {
            val sb = StringBuilder()
            for (child in node.getChildren(null)) {
                val type = child.elementType
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HEADING_MARKER) continue
                // Stop at the first newline — everything after is content text, not heading
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.NEWLINE) break
                // Stop at nested heading nodes
                if (type == QuarkdownTypes.HEADING) break
                sb.append(child.text)
            }
            return sb.toString().trim()
        }

    override fun toString(): String = "QuarkdownHeading(L$level: $headingText)"
}
