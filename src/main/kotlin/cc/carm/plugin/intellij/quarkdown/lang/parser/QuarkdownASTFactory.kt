package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType

/**
 * AST factory for the Quarkdown language.
 *
 * Leaf tokens are created as [QuarkdownLeafPsiElement] (a `ContributedReferenceHost`), which is
 * required for the platform's `PsiReferenceService` to consult our `PsiReferenceContributor`
 * (otherwise Ctrl+Click / Go-to-declaration / Find Usages cannot see the references).
 */
class QuarkdownASTFactory : ASTFactory() {

    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
        return if (type.language == QuarkdownLanguage.INSTANCE) {
            QuarkdownLeafPsiElement(type, text)
        } else {
            super.createLeaf(type, text)
        }
    }
}
