package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * A PSI reference that resolves to a target element.
 *
 * Supported types:
 *   "ref"        → .label { id } or heading anchor
 *   "read/include/css/code" → file path resolved relative to source file
 *   "image"      → image filename segment (resolves to PsiFile)
 *   "image-dir"  → image directory segment (resolves to PsiDirectory)
 */
class QuarkdownReference(
    anchorElement: PsiElement,
    private val referenceText: String,
    private val referenceType: String,
    private val rangeInElement: TextRange
) : PsiReferenceBase<PsiElement>(anchorElement, rangeInElement) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val virtualFile = element.containingFile?.virtualFile ?: return null
        return when (referenceType) {
            "ref" -> resolveRef(project, virtualFile)
            "read", "include", "css", "code", "image" -> resolveFile(project, virtualFile)
            "image-dir" -> resolveDirectory(project, virtualFile)
            else -> null
        }
    }

    override fun handleElementRename(newElementName: String): PsiElement = element
    override fun bindToElement(element: PsiElement): PsiElement = element
    override fun isReferenceTo(element: PsiElement): Boolean = resolve() == element
    override fun getVariants(): Array<Any> = emptyArray()

    // ---- .ref { id } → .label { id } or heading anchor ----
    private fun resolveRef(project: Project, sourceFile: VirtualFile): PsiElement? {
        val id = referenceText.trim()
        if (id.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        val fileType = cc.carm.plugin.intellij.quarkdown.QuarkdownFileType.INSTANCE
        val qdVFiles = FileTypeIndex.getFiles(fileType, scope)
        val qdFiles = qdVFiles.mapNotNull { psiManager.findFile(it) }

        val escapedId = Regex.escape(id)

        // 1) Look for .label { id } definition (NOT .ref — avoid self-resolution)
        val labelPattern = Regex("""\.label\s*\{\s*$escapedId\s*\}""", RegexOption.IGNORE_CASE)
        for (psiFile in qdFiles) {
            val match = labelPattern.find(psiFile.text) ?: continue
            // Return the PSI element at the label/content position inside braces
            val labelContent = psiFile.findElementAt(
                match.range.first + match.value.indexOf('{') + 1
            )
            return labelContent ?: psiFile.findElementAt(match.range.first) ?: psiFile
        }

        // 2) Look for heading that matches the id
        val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
        for (psiFile in qdFiles) {
            for (hMatch in headingPattern.findAll(psiFile.text)) {
                val headingText = hMatch.groupValues[1].trim()
                val slug = headingText.lowercase()
                    .replace(Regex("""[^a-z0-9]+"""), "-")
                    .trim('-')
                if (slug == id.lowercase()
                        .replace(Regex("""[^a-z0-9]+"""), "-")
                        .trim('-')
                ) {
                    val hElement = psiFile.findElementAt(hMatch.range.first)
                    return hElement ?: psiFile
                }
            }
        }

        return null
    }

    // ---- Resolve file path ----
    private fun resolveFile(project: Project, sourceFile: VirtualFile): PsiElement? {
        val vf = resolveToVirtualFile(sourceFile, project) ?: return null
        if (vf.isDirectory) return null
        return PsiManager.getInstance(project).findFile(vf) ?: element
    }

    // ---- Resolve directory path (for image path folder segments) ----
    private fun resolveDirectory(project: Project, sourceFile: VirtualFile): PsiElement? {
        val vf = resolveToVirtualFile(sourceFile, project) ?: return null
        val pm = PsiManager.getInstance(project)
        return if (vf.isDirectory) pm.findDirectory(vf) else pm.findFile(vf)
    }

    /**
     * Resolves a relative path against the source file's directory,
     * using both VFS and java.io.File for reliable ".." handling.
     */
    private fun resolveToVirtualFile(sourceFile: VirtualFile, project: Project): VirtualFile? {
        val path = referenceText.trim().removeSurrounding("\"")
        if (path.isEmpty()) return null

        // 1) Try VFS-relative resolution (non-deprecated API)
        val sourceDir = sourceFile.parent
        if (sourceDir != null) {
            val resolved = VfsUtilCore.findRelativeFile(path, sourceDir)
            if (resolved != null && resolved.exists()) return resolved
        }

        // 2) Try java.io.File canonical resolution (handles ".." robustly)
        val sourceFileOnDisk = File(sourceFile.path).parentFile
        if (sourceFileOnDisk != null) {
            try {
                val absolute = File(sourceFileOnDisk, path).canonicalFile
                if (absolute.exists()) {
                    val vf = findOrRefreshVirtualFile(absolute)
                    if (vf != null) return vf
                }
            } catch (_: Exception) {
                // Invalid path, continue to next strategy
            }
        }

        // 3) Try relative to project base
        val projectDir = project.basePath
        if (projectDir != null) {
            try {
                val absolute = File(projectDir, path).canonicalFile
                if (absolute.exists()) {
                    val vf = findOrRefreshVirtualFile(absolute)
                    if (vf != null) return vf
                }
            } catch (_: Exception) {
                // Invalid path, continue
            }
        }

        return null
    }

    /**
     * Finds a VirtualFile for a java.io.File, refreshing the VFS if necessary.
     * This ensures files not yet indexed by the IDE can still be resolved.
     */
    private fun findOrRefreshVirtualFile(file: File): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        // First try a quick lookup
        lfs.findFileByIoFile(file)?.let { return it }
        // If not found, refresh the parent directory and try again
        val parent = file.parentFile ?: return null
        lfs.refreshAndFindFileByPath(parent.canonicalPath) ?: lfs.refreshAndFindFileByIoFile(parent)
        return lfs.findFileByIoFile(file)
    }
}
