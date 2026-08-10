package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes as TT

/**
 * Parser that builds a semi-structured PSI tree with heading hierarchy.
 *
 * Heading lines (e.g., `## My Title`) are grouped into [QuarkdownTypes.HEADING]
 * composite nodes. Content between a heading and the next heading of same
 * or higher level is nested under that heading. This enables breadcrumbs
 * navigation, structure view, and code folding.
 *
 * Example tree:
 * ```
 * File
 * ├── HEADING (L1: "Chapter 1")
 * │   ├── HEADING_MARKER
 * │   ├── TEXT ("Chapter 1")
 * │   ├── NEWLINE
 * │   ├── TEXT ("some text")
 * │   ├── NEWLINE
 * │   ├── HEADING (L2: "Section 1.1")
 * │   │   ├── HEADING_MARKER
 * │   │   ├── TEXT ("Section 1.1")
 * │   │   ├── NEWLINE
 * │   │   └── TEXT ("subsection text")
 * │   └── NEWLINE
 * └── NEWLINE
 * ```
 */
class QuarkdownPsiParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        builder.setDebugMode(false)

        val fileMarker = builder.mark()
        // Stack of (marker, level) for open heading nodes — content after a
        // heading is nested inside it until a heading of same or higher level.
        val headingStack = mutableListOf<Pair<PsiBuilder.Marker, Int>>()

        while (!builder.eof()) {
            val tokenType = builder.tokenType

            if (tokenType == TT.HEADING_MARKER) {
                val level = builder.tokenText?.count { it == '#' } ?: 1

                // Close headings at same or higher level
                while (headingStack.isNotEmpty() && headingStack.last().second >= level) {
                    headingStack.removeAt(headingStack.lastIndex).first.done(QuarkdownTypes.HEADING)
                }

                // Open new heading — all subsequent tokens belong to it
                val marker = builder.mark()
                builder.advanceLexer() // consume HEADING_MARKER

                // Consume heading text tokens until NEWLINE / EOF
                while (!builder.eof()) {
                    val tt = builder.tokenType ?: break
                    if (tt == TT.NEWLINE) break
                    builder.advanceLexer()
                }

                headingStack.add(marker to level)
            } else if (tokenType == TT.FENCED_CODE_START) {
                // Group the whole fenced block (opening fence + language + content +
                // closing fence) into one FENCED_CODE_BLOCK node, so the platform can
                // instantiate QuarkdownCodeBlock and inject the language highlighting.
                val marker = builder.mark()
                builder.advanceLexer() // consume FENCED_CODE_START
                while (!builder.eof()) {
                    val tt = builder.tokenType ?: break
                    builder.advanceLexer()
                    if (tt == TT.FENCED_CODE_END) break
                }
                marker.done(QuarkdownTypes.FENCED_CODE_BLOCK)
            } else {
                builder.advanceLexer()
            }
        }

        // Close any remaining open headings in reverse order (deepest first)
        for (i in headingStack.indices.reversed()) {
            headingStack[i].first.done(QuarkdownTypes.HEADING)
        }

        fileMarker.done(root)
        return builder.treeBuilt
    }
}
