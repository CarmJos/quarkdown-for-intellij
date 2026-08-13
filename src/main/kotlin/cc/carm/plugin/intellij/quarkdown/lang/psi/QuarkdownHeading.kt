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

    /** The heading text content (without the `#` marker and the `{#id}` tag). */
    val headingText: String
        get() {
            val sb = StringBuilder()
            for (child in node.getChildren(null)) {
                val type = child.elementType
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HEADING_MARKER) continue
                // The `{#id}` tag is always at the end of the line; anything after it
                // is not part of the heading text.
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.ID_TAG_MARKER) break
                // Stop at the first newline — everything after is content text, not heading
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.NEWLINE) break
                // Stop at nested heading nodes
                if (type == QuarkdownTypes.HEADING) break
                sb.append(child.text)
            }
            return sb.toString().trim()
        }

    /**
     * The cross-reference id declared on the heading line as `{#id}`, or empty when none
     * is present.
     *
     * Only an id **on the heading line itself** counts: the parser nests the whole section
     * (including e.g. a table label line `"Some Values" {#table}`) under the heading node,
     * so the search stops at the first NEWLINE child.
     */
    val id: String
        get() {
            for (child in node.getChildren(null)) {
                val type = child.elementType
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.NEWLINE) break
                if (type == cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.ID_TAG) {
                    return child.text.trim()
                }
            }
            return ""
        }

    override fun toString(): String = "QuarkdownHeading(L$level: $headingText)"
}
