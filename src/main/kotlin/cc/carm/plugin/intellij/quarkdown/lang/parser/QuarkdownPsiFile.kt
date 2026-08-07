package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownReference
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownReferenceAnchors
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference

class QuarkdownPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, QuarkdownLanguage.INSTANCE),
    ContributedReferenceHost {

    override fun getFileType(): FileType = QuarkdownFileType.INSTANCE

    override fun toString(): String = "Quarkdown File"

    /**
     * Finds the reference at [offset] by walking the leaf element and its ancestors and
     * asking [com.intellij.psi.PsiReferenceService] for contributed references.
     *
     * The default `SharedPsiElementImplUtil.findReferenceAt` assumes PSI elements report
     * absolute offsets for `getRangeInElement()`; our flat PSI tree makes that math fail
     * for the innermost leaf, so we resolve references with explicit offset handling.
     *
     * When several references cover the offset (e.g. a leaf-local reference and a
     * file-level reference spanning the whole hyphenated id), the one with the widest
     * range is returned so the entire `button-start-action` is treated as one unit.
     */
    override fun findReferenceAt(offset: Int): PsiReference? {
        var element = findElementAt(offset) ?: return null
        val service = com.intellij.psi.PsiReferenceService.getService()
        var best: PsiReference? = null
        var bestLength = -1
        while (element != null) {
            for (ref in service.getReferences(element, com.intellij.psi.PsiReferenceService.Hints.NO_HINTS)) {
                val range = ref.getRangeInElement()
                val local = offset - element.textRange.startOffset
                if (range.contains(local) && range.length > bestLength) {
                    bestLength = range.length
                    best = ref
                }
            }
            if (element is PsiFile) break
            element = element.parent
        }
        return best
    }

    /**
     * File-level references (document-absolute ranges) for all reference types
     * (`.ref`, `{#id}` label, `.var`, file paths, images). Returning them from the
     * file ensures Ctrl+Click on any part of a hyphenated id finds the whole id.
     */
    override fun getReferences(): Array<PsiReference> {
        val fileText = text
        if (fileText.isEmpty()) return PsiReference.EMPTY_ARRAY

        return QuarkdownReferenceAnchors.of(this)
            .map { QuarkdownReference(this, it.referenceText, it.referenceType, TextRange(it.start, it.end)) }
            .toTypedArray()
    }
}
