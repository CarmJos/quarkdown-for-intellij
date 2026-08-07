package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Shared path-resolution helpers for Quarkdown references and annotators.
 */
object QuarkdownPathUtil {

    /** Image syntax pattern; shared with [QuarkdownReferenceParser]. */
    val IMG_PATH_PATTERN = Regex(QuarkdownReferenceParser.IMG_PATH_PATTERN_STRING)

    /** Returns true for external URLs, data URIs, or other non-file references. */
    fun isNonFileReference(path: String): Boolean {
        val lower = path.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")
    }

    /**
     * Resolves a relative/absolute path to a [VirtualFile], trying multiple strategies:
     * 1. VFS-relative resolution (handles ".." via [VfsUtilCore])
     * 2. java.io.File canonical resolution (handles ".." robustly)
     * 3. Project-base-relative resolution
     */
    fun resolveToVirtualFile(
        project: Project,
        sourceFile: VirtualFile,
        path: String
    ): VirtualFile? {
        val trimmed = path.trim().removeSurrounding("\"")
        if (trimmed.isEmpty()) return null

        // 1) Try VFS-relative resolution (non-deprecated API)
        val sourceDir = sourceFile.parent
        if (sourceDir != null) {
            val resolved = VfsUtilCore.findRelativeFile(trimmed, sourceDir)
            if (resolved != null && resolved.exists()) return resolved
        }

        // 2) Try java.io.File canonical resolution (handles ".." robustly)
        val sourceFileOnDisk = File(sourceFile.path).parentFile
        if (sourceFileOnDisk != null) {
            val resolved = resolveViaIo(project, sourceFileOnDisk, trimmed)
            if (resolved != null) return resolved
        }

        // 3) Try relative to project base
        val projectDir = project.basePath
        if (projectDir != null) {
            val resolved = resolveViaIo(project, File(projectDir), trimmed)
            if (resolved != null) return resolved
        }

        return null
    }

    private fun resolveViaIo(project: Project, baseDir: File, path: String): VirtualFile? {
        return try {
            val absolute = File(baseDir, path).canonicalFile
            if (absolute.exists()) findOrRefreshVirtualFile(absolute) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds a [VirtualFile] for a [File], refreshing the VFS if necessary so files not
     * yet indexed by the IDE can still be resolved.
     */
    private fun findOrRefreshVirtualFile(file: File): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        lfs.findFileByIoFile(file)?.let { return it }
        val parent = file.parentFile ?: return null
        lfs.refreshAndFindFileByPath(parent.canonicalPath) ?: lfs.refreshAndFindFileByIoFile(parent)
        return lfs.findFileByIoFile(file)
    }
}
