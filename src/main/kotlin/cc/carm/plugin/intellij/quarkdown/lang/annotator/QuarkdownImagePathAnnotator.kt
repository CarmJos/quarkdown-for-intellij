package cc.carm.plugin.intellij.quarkdown.lang.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.io.File

/**
 * Annotates non-existent image paths in Quarkdown files.
 *
 * Scans the file text for image syntax `!(size)[alt](path)` and checks
 * whether the referenced file actually exists on disk. If the file is
 * not found, a WARNING annotation is created.
 *
 * Skips external URLs (http://, https://) and data URIs.
 */
class QuarkdownImagePathAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val text = element.text
        if (text.isEmpty()) return

        val project = element.project
        val virtualFile = element.virtualFile ?: return
        val sourceDir = virtualFile.parent ?: return

        for (match in IMG_PATH_PATTERN.findAll(text)) {
            val path = match.groupValues[1].trim().removeSurrounding("\"")
            if (path.isEmpty()) continue
            if (isNonFileReference(path)) continue

            val pathStart = match.groups[1]!!.range.first
            val pathEnd = match.groups[1]!!.range.last + 1

            // Try to resolve the path
            val resolved = resolveImagePath(project, virtualFile, sourceDir, path)
            if (resolved == null || !resolved.exists()) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Image file not found: '$path'"
                ).range(TextRange(pathStart, pathEnd)).create()
            }
        }
    }

    /**
     * Returns true for external URLs, data URIs, or other non-file references
     * that should not be validated against the local filesystem.
     */
    private fun isNonFileReference(path: String): Boolean {
        val lower = path.lowercase()
        return lower.startsWith("http://") ||
                lower.startsWith("https://") ||
                lower.startsWith("data:")
    }

    /**
     * Resolves a relative/absolute path to a [VirtualFile], trying multiple strategies:
     * 1. VFS-relative resolution (handles ".." via VfsUtilCore)
     * 2. java.io.File canonical resolution (handles ".." robustly)
     * 3. Project-base-relative resolution
     */
    private fun resolveImagePath(
        project: Project,
        sourceFile: VirtualFile,
        sourceDir: VirtualFile,
        path: String
    ): VirtualFile? {
        // 1) Try VFS-relative resolution (non-deprecated API)
        val vfsResolved = VfsUtilCore.findRelativeFile(path, sourceDir)
        if (vfsResolved != null && vfsResolved.exists()) return vfsResolved

        // 2) Try java.io.File canonical resolution (handles ".." robustly)
        val sourceFileOnDisk = File(sourceFile.path).parentFile
        if (sourceFileOnDisk != null) {
            try {
                val absolute = File(sourceFileOnDisk, path).canonicalFile
                if (absolute.exists()) {
                    val vf = LocalFileSystem.getInstance().findFileByIoFile(absolute)
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
                    val vf = LocalFileSystem.getInstance().findFileByIoFile(absolute)
                    if (vf != null) return vf
                }
            } catch (_: Exception) {
                // Invalid path, continue
            }
        }

        return null
    }

    companion object {
        /**
         * Pattern matching Quarkdown image syntax:
         *   `![alt](path)`
         *   `!(100%][alt](path)`
         *   `! [alt](path "title")`
         *
         * Group 1 captures the path value (before any space/title/close-paren).
         */
        private val IMG_PATH_PATTERN = Regex("""!\s*(?:\([^)]*\)\s*)?\[[^\]]*\]\s*\(\s*([^)\s]+)""")
    }
}
