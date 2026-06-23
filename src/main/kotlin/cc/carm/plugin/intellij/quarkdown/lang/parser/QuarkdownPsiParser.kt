package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes as TT

/**
 * Minimal parser that builds a flat PSI tree at the file level.
 * For a custom language, a "dumb" parser that just wraps all tokens under
 * the file node is sufficient for basic syntax highlighting and reference
 * support to work. More sophisticated parsing (structure view, folding, etc.)
 * can be added incrementally.
 */
class QuarkdownPsiParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        builder.setDebugMode(false)

        val marker = builder.mark()

        // Consume all tokens as children of the file node
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType != null) {
                builder.advanceLexer()
            } else {
                // Shouldn't happen, but guard against null
                builder.advanceLexer()
            }
        }

        marker.done(root)

        return builder.treeBuilt
    }
}
