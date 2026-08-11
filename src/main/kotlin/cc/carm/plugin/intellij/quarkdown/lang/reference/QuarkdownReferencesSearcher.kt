package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Find Usages support for Quarkdown documents.
 *
 * The platform's default searchers rely on a word index or on [com.intellij.psi.PsiNamedElement]
 * names, neither of which works reliably for our text-based `.ref {id}` / `{#id}` references
 * (hyphenated ids are tokenised into separate leaves by the lexer).
 *
 * This executor instead scans every Quarkdown file in the project, recomputes the reference
 * anchors and creates a reference for each one; a reference is reported when its id matches
 * the id of the target element (via [QuarkdownReference.isReferenceTo]). This reliably finds
 * every `.ref {id}` usage of a `{#id}` label and vice versa.
 *
 * The query runs on a background thread, so all PSI access is wrapped in a read action.
 */
class QuarkdownReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = parameters.elementToSearch
        ApplicationManager.getApplication().runReadAction {
            val targetFile = target.containingFile ?: return@runReadAction
            if (targetFile.fileType != QuarkdownFileType.INSTANCE) return@runReadAction

            // The target must sit inside a reference anchor (otherwise it has no references).
            val targetId = resolveTargetId(target, targetFile) ?: return@runReadAction

            val searchScope = com.intellij.psi.search.GlobalSearchScopeUtil.toGlobalSearchScope(
                parameters.effectiveSearchScope, target.project
            )
            // Always scan the file that contains the target first (it may be a brand-new,
            // unsaved file invisible to FileTypeIndex), then the indexed project files.
            val files = QuarkdownReferenceFiles.collect(target.project, targetFile, searchScope)

            for (psiFile in files) {
                if (!processFileReferences(psiFile, targetFile, target, targetId, consumer)) {
                    return@runReadAction
                }
            }
        }
    }

    /** Resolves the id of the reference anchor that contains [target], or `null`. */
    private fun resolveTargetId(target: PsiElement, targetFile: PsiFile): String? {
        val targetAnchors = QuarkdownReferenceAnchors.of(targetFile)
        val targetRange = target.textRange
        return targetAnchors
            .firstOrNull { TextRange(it.start, it.end).intersects(targetRange) }
            ?.referenceText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Reports the references of [target] found in [psiFile]; returns `false` when the
     * consumer is done and the search must stop.
     */
    private fun processFileReferences(
        psiFile: PsiFile,
        targetFile: PsiFile,
        target: PsiElement,
        targetId: String,
        consumer: Processor<in PsiReference>
    ): Boolean {
        val targetRange = target.textRange
        for (anchor in QuarkdownReferenceAnchors.of(psiFile)) {
            // Only id-based references participate in label/ref cross-referencing.
            if (anchor.referenceType != "ref" && anchor.referenceType != "label" &&
                anchor.referenceType != "var" && anchor.referenceType != "var-decl"
            ) continue
            if (!anchor.referenceText.trim().equals(targetId, ignoreCase = true)) continue
            // The declaration itself is not a reference to itself — a `{#id}` with
            // no `.ref` usages must have zero references, not a self-reference.
            if (psiFile === targetFile && TextRange(anchor.start, anchor.end).intersects(targetRange)) continue

            // Attach the reference to the FILE with document-absolute ranges so that
            // handleElementRename can replace the whole (possibly hyphenated) id at once.
            val ref = QuarkdownReference(
                psiFile, anchor.referenceText, anchor.referenceType,
                TextRange(anchor.start, anchor.end)
            )
            if (ref.isReferenceTo(target)) {
                if (!consumer.process(ref)) return false
            }
        }
        return true
    }
}
