package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost

/**
 * Composite PSI element. Implements [ContributedReferenceHost] so the platform consults our
 * [com.intellij.psi.PsiReferenceContributor] (required for Ctrl+Click / Find Usages).
 */
class QuarkdownPsiElement(node: ASTNode) : ASTWrapperPsiElement(node), ContributedReferenceHost
