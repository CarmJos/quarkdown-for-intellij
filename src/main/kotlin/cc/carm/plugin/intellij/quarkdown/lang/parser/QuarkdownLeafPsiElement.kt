package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Leaf PSI element. Implements [ContributedReferenceHost] so the platform consults our
 * [com.intellij.psi.PsiReferenceContributor] (required for Ctrl+Click / Find Usages).
 */
class QuarkdownLeafPsiElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), ContributedReferenceHost
