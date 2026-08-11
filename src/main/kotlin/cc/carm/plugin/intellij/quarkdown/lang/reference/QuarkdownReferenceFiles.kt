package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Collects the set of Quarkdown files that participate in reference resolution.
 *
 * The platform's [FileTypeIndex] only knows files that have been saved to disk and
 * indexed. A brand-new (unsaved) file backed by a `LightVirtualFile` is invisible to
 * it, which is why `.ref {id}` / `{#id}` navigation and Find Usages silently reported
 * "no references" for the currently-open file after the reference rework.
 *
 * Every resolution path therefore starts from the file that contains the element being
 * resolved — even when it is not (yet) indexed — and then adds the indexed Quarkdown
 * files. Results are deduplicated and ordered so the current file wins ties.
 */
object QuarkdownReferenceFiles {

    /**
     * All Quarkdown files to scan for reference resolution.
     *
     * @param currentFile the file containing the element being resolved (may be null),
     *                    always included first even if it is not in [FileTypeIndex].
     * @param scope       optional scope used to enumerate indexed files; defaults to the
     *                    whole project scope.
     */
    fun collect(
        project: Project,
        currentFile: PsiFile?,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)
    ): List<PsiFile> {
        val result = LinkedHashSet<PsiFile>()
        if (currentFile != null && currentFile.isValid && currentFile.fileType == QuarkdownFileType.INSTANCE) {
            result.add(currentFile)
        }
        val psiManager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, scope)
            .mapNotNull { psiManager.findFile(it) }
            .forEach { result.add(it) }
        return result.toList()
    }
}
