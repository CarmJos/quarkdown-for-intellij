package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo

/**
 * Enables the Rename refactoring (Shift+F6) for Quarkdown `.ref {id}` / `{#id}` ids.
 *
 * Without a registered processor, the IDE reports "Caret should be positioned at symbol
 * to be renamed". This processor claims any element inside a Quarkdown file and lets the
 * standard machinery find the element at the caret and its references (backed by
 * [QuarkdownReferencesSearcher]).
 *
 * [renameElement] collects the absolute document ranges of every reference (plus the
 * element's own id) and rewrites them in a single write command, applying replacements
 * from the end of each file so earlier edits never invalidate later offsets. It avoids
 * the platform's default `setName`/`doRename` path (which is unsuitable for ids that are
 * a sub-range of a token such as `{#plc-symbol-output}`).
 *
 * In-place rename is disabled so the (modal) dialog is used: it shows the bare id (via
 * `PsiNamedElement.getName()`) instead of highlighting the whole `{...}` token.
 *
 * NOTE: Quarkdown leaves do NOT implement [PsiNamedElement] (so plain text is not a
 * Ctrl+Click target); the dialog name is provided by [QuarkdownNameSuggestionProvider].
 */
class QuarkdownRenameProcessor : RenamePsiElementProcessor() {

    private data class Replacement(val document: Document, val start: Int, val end: Int)

    override fun canProcessElement(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (file.fileType != QuarkdownFileType.INSTANCE) return false

        // Only elements that sit inside a reference anchor can be renamed.
        val anchors = QuarkdownReferenceAnchors.of(file)
        val range = element.textRange
        return anchors.any { it.overlaps(range.startOffset, range.endOffset) }
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement {
        return element
    }

    override fun isInplaceRenameSupported(): Boolean = false

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        // Resolve the project before touching the (possibly re-parsed) element.
        val project = runCatching { element.project }.getOrNull()
            ?: usages.firstNotNullOfOrNull { it.element?.containingFile?.project }
            ?: return

        val replacements = mutableListOf<Replacement>()
        replacements += usageReplacements(usages)
        collectElementReplacement(element, replacements)

        applyReplacements(project, replacements, newName)
        listener?.elementRenamed(element)
    }

    /**
     * Collects the absolute document range of every usage reference.
     * `usage.reference` reconstructs the reference via a class check and returns null
     * for our references, so use the stored element + rangeInElement instead.
     */
    private fun usageReplacements(usages: Array<UsageInfo>): List<Replacement> {
        val replacements = mutableListOf<Replacement>()
        for (usage in usages) {
            val refElement = usage.element
            if (refElement == null || !refElement.isValid) continue
            val file = refElement.containingFile ?: continue
            val document = file.viewProvider.document ?: continue
            val range = usage.rangeInElement ?: continue
            val start = refElement.textRange.startOffset + range.startOffset
            val end = refElement.textRange.startOffset + range.endOffset
            if (end > start && end <= document.textLength) {
                replacements.add(Replacement(document, start, end))
            }
        }
        return replacements
    }

    /** Adds a replacement for the element's own id range (in case the caret element wasn't covered by a usage). */
    private fun collectElementReplacement(element: PsiElement, replacements: MutableList<Replacement>) {
        val elementFile = element.containingFile
        val elementDocument = elementFile?.viewProvider?.document ?: return
        if (!element.isValid) return
        val leafText = element.text ?: ""
        val (idStart, idEnd) = when {
            leafText.startsWith("{#") && leafText.endsWith("}") ->
                element.textRange.startOffset + 2 to element.textRange.endOffset - 1

            leafText.startsWith("{") && leafText.endsWith("}") ->
                element.textRange.startOffset + 1 to element.textRange.endOffset - 1

            else ->
                element.textRange.startOffset to element.textRange.endOffset
        }
        if (idEnd <= idStart || idEnd > elementDocument.textLength) return
        val alreadyCovered = replacements.any {
            it.document === elementDocument && it.start == idStart && it.end == idEnd
        }
        if (!alreadyCovered) {
            replacements.add(Replacement(elementDocument, idStart, idEnd))
        }
    }

    /** Applies [replacements] per document, from the end, so earlier offsets stay valid. */
    private fun applyReplacements(project: Project, replacements: List<Replacement>, newName: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            for ((document, list) in replacements.groupBy { it.document }) {
                for (r in list.sortedByDescending { it.start }) {
                    val current = document.getText(TextRange(r.start, r.end))
                    if (current != newName) {
                        document.replaceString(r.start, r.end, newName)
                    }
                }
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }
    }
}