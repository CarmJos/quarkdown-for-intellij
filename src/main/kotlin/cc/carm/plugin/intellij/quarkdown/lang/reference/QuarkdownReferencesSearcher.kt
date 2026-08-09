package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
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
 * The query runs on a background thread, so all PSI access is wrapped in [ReadAction].
 */
class QuarkdownReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val target = parameters.elementToSearch
        ReadAction.run<RuntimeException> {
            val targetFile = target.containingFile ?: return@run
            if (targetFile.fileType != QuarkdownFileType.INSTANCE) return@run

            // The target must sit inside a reference anchor (otherwise it has no references).
            val targetAnchors = QuarkdownReferenceAnchors.of(targetFile)
            val targetRange = target.textRange
            val targetId = targetAnchors
                .firstOrNull { TextRange(it.start, it.end).intersects(targetRange) }
                ?.referenceText
                ?.trim()
                ?: return@run
            if (targetId.isEmpty()) return@run

            val psiManager = PsiManager.getInstance(target.project)
            val searchScope = com.intellij.psi.search.GlobalSearchScopeUtil.toGlobalSearchScope(
                parameters.effectiveSearchScope, target.project
            )
            val files = FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, searchScope)
                .mapNotNull { psiManager.findFile(it) }

            for (psiFile in files) {
                for (anchor in QuarkdownReferenceAnchors.of(psiFile)) {
                    // Only id-based references participate in label/ref cross-referencing.
                    if (anchor.referenceType != "ref" && anchor.referenceType != "label" &&
                        anchor.referenceType != "var" && anchor.referenceType != "var-decl"
                    ) continue
                    if (!anchor.referenceText.trim().equals(targetId, ignoreCase = true)) continue

                    // Attach the reference to the FILE with document-absolute ranges so that
                    // handleElementRename can replace the whole (possibly hyphenated) id at once.
                    val ref = QuarkdownReference(
                        psiFile, anchor.referenceText, anchor.referenceType,
                        TextRange(anchor.start, anchor.end)
                    )
                    if (ref.isReferenceTo(target)) {
                        if (!consumer.process(ref)) return@run
                    }
                }
            }
        }
    }
}
