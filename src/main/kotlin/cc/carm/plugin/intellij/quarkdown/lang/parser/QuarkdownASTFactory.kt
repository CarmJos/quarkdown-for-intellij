package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.lang.ASTFactory
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType

/**
 * AST factory for the Quarkdown language.
 *
 * Leaf tokens are created as [QuarkdownLeafPsiElement] (a `ContributedReferenceHost`), which is
 * required for the platform's `PsiReferenceService` to consult our `PsiReferenceContributor`
 * (otherwise Ctrl+Click / Go-to-declaration / Find Usages cannot see the references).
 *
 * Id-bearing tokens (`{#id}` = ID_TAG, and `{id}` inside `.ref`/`.var` = FUNCTION_PARAMS)
 * are created as [QuarkdownIdLeafPsiElement], which implements `PsiNamedElement`. The platform
 * Symbol model then treats them as declarations, so Ctrl+Click shows the Java-style Show Usages
 * popup listing every usage.
 */
class QuarkdownASTFactory : ASTFactory() {

    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
        if (type.language != QuarkdownLanguage.INSTANCE) {
            return super.createLeaf(type, text)
        }
        return if (type == QuarkdownTokenTypes.ID_TAG || type == QuarkdownTokenTypes.FUNCTION_PARAMS) {
            QuarkdownIdLeafPsiElement(type, text)
        } else {
            QuarkdownLeafPsiElement(type, text)
        }
    }
}
