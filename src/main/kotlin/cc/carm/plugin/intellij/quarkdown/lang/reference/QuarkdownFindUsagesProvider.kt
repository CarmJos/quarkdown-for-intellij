package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement

/**
 * Find Usages support for Quarkdown documents.
 *
 * Plain text leaves (reference ids, variable names) are treated as findable targets:
 * Alt+F7 on a `{#id}` label or `.var` name will search for all references pointing at it.
 */
class QuarkdownFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        return file.fileType == QuarkdownFileType.INSTANCE
    }

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = "Quarkdown element"

    override fun getDescriptiveName(element: PsiElement): String = element.text ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = element.text ?: ""

    override fun getWordsScanner(): WordsScanner? = null
}
