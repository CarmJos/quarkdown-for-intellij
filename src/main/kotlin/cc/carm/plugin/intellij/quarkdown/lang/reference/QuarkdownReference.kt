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
 *   "ref"        → `{#id}` label declaration or heading anchor
 *   "label"      → first `.ref { id }` usage of the label (go-to-usage)
 *   "var"        → .var { name } declaration
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
            "label" -> resolveFirstUsage(project, virtualFile)
            "var" -> resolveVar(project, virtualFile)
            "read", "include", "css", "code", "image" -> resolveFile(project, virtualFile)
            "image-dir" -> resolveDirectory(project, virtualFile)
            else -> null
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolved = resolve() ?: return false
        if (resolved == element || resolved.isEquivalentTo(element)) return true

        // Hyphenated ids (e.g. `button-start-action`) are split across several leaf
        // elements by the lexer, so resolve() returns the first leaf. For Find Usages,
        // any element inside the resolved declaration's id range is a valid target.
        val resolvedFile = resolved.containingFile ?: return false
        if (resolvedFile != element.containingFile) return false
        val idRange = declarationIdRange(resolvedFile, referenceText.trim().lowercase()) ?: return false
        return element.textRange.intersects(idRange)
    }

    /**
     * Finds the document range of the declared id ([referenceText]) inside [file],
     * according to this reference's type. This lets Find Usages match any leaf of a
     * multi-leaf (hyphenated) id.
     */
    private fun declarationIdRange(file: com.intellij.psi.PsiFile, id: String): TextRange? {
        if (id.isEmpty()) return null
        val escaped = Regex.escape(id)
        val pattern = when (referenceType) {
            "ref" -> Regex("""\{#\s*($escaped)\s*}""", RegexOption.IGNORE_CASE)
            "label" -> Regex("""\.ref\s*\{\s*($escaped)\s*\}""", RegexOption.IGNORE_CASE)
            "var" -> Regex("""\.var\s*\{\s*($escaped)\s*\}""", RegexOption.IGNORE_CASE)
            else -> return null
        }
        val match = pattern.find(file.text) ?: return null
        val g = match.groups[1] ?: return null
        return TextRange(g.range.first, g.range.last + 1)
    }

    // ---- `{#id}` label → first `.ref { id }` usage ----
    private fun resolveFirstUsage(project: Project, sourceFile: VirtualFile): PsiElement? {
        val id = referenceText.trim().lowercase()
        if (id.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val fileType = cc.carm.plugin.intellij.quarkdown.QuarkdownFileType.INSTANCE
        val qdFiles = FileTypeIndex.getFiles(fileType, scope)
            .mapNotNull { psiManager.findFile(it) }

        // Iterate ALL `.ref` occurrences in each file — the first one may not be this id.
        val pattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)
        for (psiFile in qdFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (match.groupValues[1].trim().lowercase() != id) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                return psiFile.findElementAt(contentStart) ?: psiFile.findElementAt(match.range.first) ?: psiFile
            }
        }
        return null
    }

    // ---- .name → .var { name } declaration (document scoped) ----
    private fun resolveVar(project: Project, sourceFile: VirtualFile): PsiElement? {
        val name = referenceText.trim().lowercase()
        if (name.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val fileType = cc.carm.plugin.intellij.quarkdown.QuarkdownFileType.INSTANCE
        val qdFiles = FileTypeIndex.getFiles(fileType, scope)
            .mapNotNull { psiManager.findFile(it) }

        // Variables are document-scoped; the source file is checked first.
        val orderedFiles = listOfNotNull(psiManager.findFile(sourceFile)) + qdFiles
        val pattern = Regex("""\.var\s*\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}""", RegexOption.IGNORE_CASE)
        for (psiFile in orderedFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (match.groupValues[1].lowercase() != name) continue
                // Return the element at the variable name inside the braces.
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                return psiFile.findElementAt(contentStart) ?: psiFile
            }
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement = element
    override fun bindToElement(element: PsiElement): PsiElement = element
    override fun getVariants(): Array<Any> = emptyArray()

    // ---- .ref { id } → `{#id}` label declaration or heading anchor ----
    private fun resolveRef(project: Project, sourceFile: VirtualFile): PsiElement? {
        val id = referenceText.trim().lowercase()
        if (id.isEmpty()) return null

        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        val fileType = cc.carm.plugin.intellij.quarkdown.QuarkdownFileType.INSTANCE
        val qdVFiles = FileTypeIndex.getFiles(fileType, scope)
        val qdFiles = qdVFiles.mapNotNull { psiManager.findFile(it) }

        val escapedId = Regex.escape(id)

        // 1) Look for `{#id}` label declaration (case-insensitive id).
        // Iterate ALL occurrences — the first `{#id}` in a file may not be this one.
        val labelPattern = Regex("""\{#\s*$escapedId\s*}""", RegexOption.IGNORE_CASE)
        for (psiFile in qdFiles) {
            for (match in labelPattern.findAll(psiFile.text)) {
                val labelContent = psiFile.findElementAt(match.range.first + 2) // skip `{#`
                return labelContent ?: psiFile.findElementAt(match.range.first) ?: psiFile
            }
        }

        // 2) Look for a heading whose text or trailing `{#id}` matches the id.
        val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
        for (psiFile in qdFiles) {
            for (hMatch in headingPattern.findAll(psiFile.text)) {
                val headingText = hMatch.groupValues[1].trim()

                // explicit label on the heading: `# Heading {#id}`
                val explicit = Regex("""\{#\s*$escapedId\s*}""", RegexOption.IGNORE_CASE).find(headingText)
                if (explicit != null) {
                    return psiFile.findElementAt(hMatch.range.first) ?: psiFile
                }

                // fall back to slug matching
                val slug = headingText.lowercase()
                    .replace(Regex("""[^a-z0-9]+"""), "-")
                    .trim('-')
                if (slug == id.replace(Regex("""[^a-z0-9]+"""), "-").trim('-')) {
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
