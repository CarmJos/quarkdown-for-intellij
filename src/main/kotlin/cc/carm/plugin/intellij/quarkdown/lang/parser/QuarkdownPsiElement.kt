package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost

/**
 * Base PSI element for Quarkdown syntax trees.
 *
 * Implements [ContributedReferenceHost] so that the platform's [com.intellij.psi.PsiReferenceService]
 * consults [com.intellij.psi.PsiReferenceContributor]s for this element. Without this marker,
 * `PsiReferenceServiceImpl` only calls `element.getReferences()` and reference-contributor
 * references (our `.ref` / `.var` / path references) are invisible to Ctrl+Click, Go-to-declaration
 * and Find Usages.
 */
class QuarkdownPsiElement(node: ASTNode) : ASTWrapperPsiElement(node), ContributedReferenceHost
