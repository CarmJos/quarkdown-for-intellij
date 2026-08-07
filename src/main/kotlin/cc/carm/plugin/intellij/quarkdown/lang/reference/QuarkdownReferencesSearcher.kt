package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Find Usages support for Quarkdown documents.
 *
 * The platform's default `CachesBasedRefSearcher` only performs a word-index search when
 * the search target is a [PsiNamedElement], [PsiFileSystemItem] or [PsiMetaOwner]. Our
 * reference targets are plain text leaves (e.g. the `mybutton` inside `{#mybutton}`), so
 * the default searcher extracts an empty word and does nothing.
 *
 * This executor extracts the target leaf's own text as the search word and registers it
 * with the search request collector. Word occurrences then resolve to references, which
 * are filtered with `isReferenceTo`, so all `.ref {id}` usages of a `{#id}` label are
 * found (and vice versa).
 */
class QuarkdownReferencesSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>
    ) {
        val element = parameters.elementToSearch
        val file = element.containingFile ?: return
        if (file.fileType != QuarkdownFileType.INSTANCE) return

        // Elements that already expose a name are handled by the default searcher.
        if (element is PsiNamedElement || element is PsiFileSystemItem) return
        if (element is PsiMetaOwner && element.metaData is PsiMetaData) return

        // Hyphenated ids (e.g. `button-start-action`) are tokenized into separate leaves
        // by the lexer, so the word index stores each token. Searching the leaf's own
        // text finds all files containing that token; isReferenceTo then filters to the
        // exact id, so every `.ref {button-start-action}` usage is matched.
        val word = element.text?.trim()
        if (word.isNullOrEmpty()) return

        parameters.optimizer.searchWord(
            word,
            parameters.effectiveSearchScope,
            true,
            element
        )
    }
}
