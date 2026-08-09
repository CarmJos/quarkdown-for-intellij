package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Leaf PSI element for ordinary plain-text tokens. Implements [ContributedReferenceHost]
 * so the platform consults our [com.intellij.psi.PsiReferenceContributor] (required for
 * Ctrl+Click / Find Usages).
 *
 * NOTE: this class deliberately does NOT implement [com.intellij.psi.PsiNamedElement].
 * Doing so would make every plain-text leaf a "name target" for Ctrl+Click, which
 * underlines ALL text in the editor. Only id-bearing tokens (`{#id}` / `{id}`) use
 * [QuarkdownIdLeafPsiElement], which implements `PsiNamedElement` for the Symbol model.
 */
class QuarkdownLeafPsiElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), ContributedReferenceHost
