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
 *
 * [getDescriptiveName] / [getNodeText] return the bare id (`foo-bar`), not the token
 * text (`{#foo-bar}`), so the Find Usages tool window shows `foo-bar` as the search key.
 */
class QuarkdownFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (file.fileType != QuarkdownFileType.INSTANCE) return false
        // Only elements inside a reference anchor are findable targets.
        val anchors = QuarkdownReferenceAnchors.of(file)
        val range = element.textRange
        return anchors.any { it.overlaps(range.startOffset, range.endOffset) }
    }

    override fun getHelpId(element: PsiElement): String? = null

    /**
     * The type label shown in the Show Usages window title. It is rendered grey before the
     * (white) [getDescriptiveName], e.g. `References plc-symbol-output`.
     */
    override fun getType(element: PsiElement): String = "References"

    override fun getDescriptiveName(element: PsiElement): String = bareName(element) ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = bareName(element) ?: ""

    override fun getWordsScanner(): WordsScanner? = null

    /** Returns the bare id inside `{#...}` / `{...}`, or the element text as-is. */
    private fun bareName(element: PsiElement): String? {
        val t = element.text ?: return null
        return when {
            t.startsWith("{#") && t.endsWith("}") -> t.substring(2, t.length - 1)
            t.startsWith("{") && t.endsWith("}") -> t.substring(1, t.length - 1)
            else -> t
        }
    }
}
