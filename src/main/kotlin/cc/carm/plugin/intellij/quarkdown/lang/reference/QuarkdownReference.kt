package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

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
        val pattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)
        return findElementInQdFiles(project, sourceFile, pattern, sourceFileFirst = false) { match ->
            match.groupValues[1].trim().lowercase() == id
        }
    }

    // ---- .name → .var { name } declaration (document scoped) ----
    private fun resolveVar(project: Project, sourceFile: VirtualFile): PsiElement? {
        val name = referenceText.trim().lowercase()
        if (name.isEmpty()) return null
        val pattern = Regex("""\.var\s*\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}""", RegexOption.IGNORE_CASE)
        return findElementInQdFiles(project, sourceFile, pattern, sourceFileFirst = true) { match ->
            match.groupValues[1].lowercase() == name
        }
    }

    /**
     * Scans all Quarkdown files in the project for [pattern]; for the first match that
     * satisfies [predicate], returns the PSI element at the captured group's position.
     * When [sourceFileFirst] is true, the source file is checked before the others.
     */
    private fun findElementInQdFiles(
        project: Project,
        sourceFile: VirtualFile,
        pattern: Regex,
        sourceFileFirst: Boolean,
        predicate: (MatchResult) -> Boolean
    ): PsiElement? {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val fileType = cc.carm.plugin.intellij.quarkdown.QuarkdownFileType.INSTANCE
        val qdFiles = FileTypeIndex.getFiles(fileType, scope).mapNotNull { psiManager.findFile(it) }
        val orderedFiles = if (sourceFileFirst) listOfNotNull(psiManager.findFile(sourceFile)) + qdFiles else qdFiles

        for (psiFile in orderedFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (!predicate(match)) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                return psiFile.findElementAt(contentStart) ?: psiFile.findElementAt(match.range.first) ?: psiFile
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
        val escapedId = Regex.escape(id)

        // 1) Look for `{#id}` label declaration (case-insensitive id).
        val labelPattern = Regex("""\{#\s*$escapedId\s*}""", RegexOption.IGNORE_CASE)
        findElementInQdFiles(project, sourceFile, labelPattern, sourceFileFirst = false) { true }
            ?.let { return it }

        // 2) Look for a heading whose text or trailing `{#id}` matches the id.
        val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
        val slugTarget = id.replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
        return findElementInQdFiles(project, sourceFile, headingPattern, sourceFileFirst = false) { hMatch ->
            val headingText = hMatch.groupValues[1].trim()
            // explicit label on the heading: `# Heading {#id}`, or slug fallback
            Regex("""\{#\s*$escapedId\s*}""", RegexOption.IGNORE_CASE).find(headingText) != null ||
                headingText.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-') == slugTarget
        }
    }

    // ---- Resolve file path ----
    private fun resolveFile(project: Project, sourceFile: VirtualFile): PsiElement? {
        // Resolve variable references in the path (e.g., {.version/file.qd} -> abc/file.qd)
        val resolvedPath = resolvePathVariables(sourceFile, referenceText)
        val vf = QuarkdownPathUtil.resolveToVirtualFile(project, sourceFile, resolvedPath) ?: return null
        if (vf.isDirectory) return null
        return PsiManager.getInstance(project).findFile(vf) ?: element
    }

    /**
     * Resolves variable references in a path string.
     * Variable references are in the form `.varName` and should be replaced with their values.
     * For example, if `.version` is defined as `version = abc`, then `{.version/file.qd}` 
     * should resolve to `abc/file.qd`.
     */
    private fun resolvePathVariables(sourceFile: VirtualFile, path: String): String {
        // Pattern to match variable references: .varName (not preceded by a letter/digit/underscore)
        val varRefPattern = Regex("""(?<!\w)\.([a-zA-Z][a-zA-Z0-9]*)""")
        
        // Find all variable declarations in the source file
        val sourcePsiFile = PsiManager.getInstance(element.project).findFile(sourceFile) ?: return path
        val varDeclarations = QuarkdownCallParser.findVarDeclarations(sourcePsiFile.text)
        
        // Replace variable references with their values
        return varRefPattern.replace(path) { match ->
            val varName = match.groupValues[1].lowercase()
            // Get the variable value from declarations
            if (varName in varDeclarations) {
                // Find the value argument of the .var declaration
                val value = findVarValue(sourcePsiFile.text, varName)
                value ?: match.value  // If not found, keep the original reference
            } else {
                match.value  // Not a declared variable, keep original
            }
        }
    }

    /**
     * Finds the value of a variable declared with `.var {name} {value}`.
     */
    private fun findVarValue(fileText: String, varName: String): String? {
        val varPattern = Regex("""\.var\s*\{\s*$varName\s*\}\s*\{([^}]+)\}""", RegexOption.IGNORE_CASE)
        val match = varPattern.find(fileText) ?: return null
        return match.groupValues[1].trim()
    }

    // ---- Resolve directory path (for image path folder segments) ----
    private fun resolveDirectory(project: Project, sourceFile: VirtualFile): PsiElement? {
        // Resolve variable references in the path
        val resolvedPath = resolvePathVariables(sourceFile, referenceText)
        val vf = QuarkdownPathUtil.resolveToVirtualFile(project, sourceFile, resolvedPath) ?: return null
        val pm = PsiManager.getInstance(project)
        return if (vf.isDirectory) pm.findDirectory(vf) else pm.findFile(vf)
    }
}
