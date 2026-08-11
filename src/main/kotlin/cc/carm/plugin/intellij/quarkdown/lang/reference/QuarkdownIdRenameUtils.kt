package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Propagates an id rename performed through the element edit dialogs (table / equation /
 * image / heading / code) to every `.ref {oldId}` usage elsewhere in the project.
 *
 * This mirrors the refactor rename (Shift+F6) behaviour for ids that were edited in
 * place: when an existing `{#id}` annotation is changed, all cross-references to the old
 * id are rewritten to the new id in a single write command.
 */
object QuarkdownIdRenameUtils {

    /** Matches a `.ref { id }` usage and captures the id content. */
    private val refBlockPattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)

    /**
     * Renames every `.ref {oldId}` usage of [oldId] to [newId] across all Quarkdown files
     * in the project. No-op when [oldId] is blank or equals [newId] (case-insensitively).
     *
     * @param project    the project to search
     * @param sourceFile the file being edited (included first even if unsaved), may be null
     * @param oldId      the id before the rename
     * @param newId      the id after the rename
     * @return the number of usages that were updated
     */
    fun renameRefUsages(project: Project, sourceFile: PsiFile?, oldId: String, newId: String): Int {
        val old = oldId.trim()
        val fresh = newId.trim()
        if (old.isEmpty() || fresh.isEmpty() || old.equals(fresh, ignoreCase = true)) return 0

        data class Replacement(val document: Document, val start: Int, val end: Int)
        val replacements = mutableListOf<Replacement>()

        for (psiFile in QuarkdownReferenceFiles.collect(project, sourceFile)) {
            val document = psiFile.viewProvider.document ?: continue
            val text = document.immutableCharSequence
            for (match in refBlockPattern.findAll(text)) {
                val content = match.groupValues[1]
                if (!content.trim().equals(old, ignoreCase = true)) continue
                val group = match.groups[1] ?: continue
                val groupStart = group.range.first
                val leading = content.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
                val trailing = content.indexOfLast { !it.isWhitespace() }
                val start = groupStart + leading
                val end = groupStart + trailing + 1
                if (end > start && end <= text.length) {
                    replacements.add(Replacement(document, start, end))
                }
            }
        }
        if (replacements.isEmpty()) return 0

        WriteCommandAction.runWriteCommandAction(project) {
            for ((document, list) in replacements.groupBy { it.document }) {
                for (r in list.sortedByDescending { it.start }) {
                    val current = document.getText(TextRange(r.start, r.end))
                    if (current != fresh) {
                        document.replaceString(r.start, r.end, fresh)
                    }
                }
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }
        return replacements.size
    }

    /**
     * Runs [renameRefUsages] and, when at least one usage was updated, shows a notification
     * telling the user that the id rename also updated cross-references elsewhere.
     */
    fun renameRefUsagesAndNotify(project: Project, sourceFile: PsiFile?, oldId: String, newId: String): Int {
        val count = renameRefUsages(project, sourceFile, oldId, newId)
        if (count > 0) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Quarkdown")
                .createNotification(
                    QuarkdownBundle.message("quarkdown.rename.refs.updated", count, oldId, newId),
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
        return count
    }
}
