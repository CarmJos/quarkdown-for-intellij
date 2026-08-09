package cc.carm.plugin.intellij.quarkdown.lang.documentation

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiElementBase

/**
 * A lightweight, non-physical [PsiElement] that carries a resolved [FunctionMetadata]
 * through the documentation pipeline. It is returned by the documentation provider for
 * both lookup items and editor positions, and consumed by `generateDoc`.
 */
class QuarkdownDocElement(
    private val project: Project,
    val metadata: FunctionMetadata,
    private val contextFile: PsiFile
) : PsiElementBase() {

    override fun getProject(): Project = project

    override fun getManager(): PsiManager = PsiManager.getInstance(project)

    override fun getLanguage(): Language = QuarkdownLanguage.INSTANCE

    override fun getContainingFile(): PsiFile = contextFile

    override fun getChildren(): Array<PsiElement> = EMPTY_ARRAY

    override fun getParent(): PsiElement? = null

    override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

    override fun getStartOffsetInParent(): Int = 0

    override fun getTextLength(): Int = 0

    override fun findElementAt(offset: Int): PsiElement? = null

    override fun getTextOffset(): Int = 0

    override fun getText(): String = metadata.name

    override fun textToCharArray(): CharArray = CharArray(0)

    override fun getNode(): ASTNode? = null

    override fun isValid(): Boolean = true

    override fun isWritable(): Boolean = false

    override fun toString(): String = "QuarkdownDocElement(${metadata.name})"
}
