package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Leaf PSI element for Quarkdown token types.
 *
 * Implements [ContributedReferenceHost] so the platform's [com.intellij.psi.PsiReferenceService]
 * consults [com.intellij.psi.PsiReferenceContributor]s for leaf tokens. Without this marker,
 * references attached through `PsiReferenceContributor` (our `.ref` / `.var` / path references)
 * are invisible to Ctrl+Click, Go-to-declaration and Find Usages.
 */
class QuarkdownLeafPsiElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), ContributedReferenceHost
