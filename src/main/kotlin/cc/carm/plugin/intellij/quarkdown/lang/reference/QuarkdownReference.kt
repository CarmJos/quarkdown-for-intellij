package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

/**
 * A PSI reference that resolves to a target element.
 *
 * Supported types:
 *   "ref"        → `{#id}` label declaration or heading anchor
 *   "label"      → the declaration itself (resolve); `multiResolve` returns every `.ref { id }`
 *   "var"        → .var { name } declaration
 *   "read/include" → file path resolved relative to source file
 *   "image"      → image filename segment (resolves to PsiFile)
 *   "image-dir"  → image directory segment (resolves to PsiDirectory)
 */
class QuarkdownReference(
    anchorElement: PsiElement,
    private val referenceText: String,
    private val referenceType: String,
    rangeInElement: TextRange
) : PsiPolyVariantReferenceBase<PsiElement>(anchorElement, rangeInElement) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val virtualFile = element.containingFile?.virtualFile ?: return null
        return when (referenceType) {
            "ref" -> resolveRef(project, virtualFile)
            // Declarations (`{#id}` / `.var {name}`) resolve to THEMSELVES, not to their
            // first usage. Resolving to a usage makes the platform's GotoDeclaration
            // outcome GTD and Ctrl+Click would jump to the first usage instead of showing
            // the usages popup at the declaration.
            "label", "var-decl" -> resolveDeclarationLeaf()
            "var" -> resolveVar(project, virtualFile)
            "read", "include", "image" -> resolveFile(project, virtualFile)
            "image-dir" -> resolveDirectory(project, virtualFile)
            else -> null
        }
    }

    /**
     * Returns the declaration leaf this reference sits on. The reference may be attached to
     * the file (file-level, absolute range) or to the leaf itself (leaf-local range), so the
     * absolute offset is recomputed from [rangeInElement] and [element.textRange].
     */
    private fun resolveDeclarationLeaf(): PsiElement? {
        val file = element.containingFile ?: return element
        if (!file.isValid) return element
        val absStart = element.textRange.startOffset + rangeInElement.startOffset
        if (absStart < 0 || absStart >= file.textLength) return element
        return file.findElementAt(absStart) ?: element
    }

    /**
     * Resolves to ALL possible targets. For a `{#id}` label declaration this returns
     * every `.ref { id }` usage, so Ctrl+Click pops up the list of usages (like Java).
     * For `.ref { id }` usages it returns the (single) label declaration.
     */
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val project = element.project
        val virtualFile = element.containingFile?.virtualFile ?: return emptyArray()
        return when (referenceType) {
            "label" -> findAllRefUsages(project, virtualFile)
                .map { PsiElementResolveResult(it) }
                .toTypedArray()

            "ref" -> resolveRef(project, virtualFile)
                ?.let { arrayOf(PsiElementResolveResult(it)) }
                ?: emptyArray()

            "var" -> resolveVar(project, virtualFile)
                ?.let { arrayOf(PsiElementResolveResult(it)) }
                ?: emptyArray()

            "var-decl" -> findAllVarUsages(project, virtualFile)
                .map { PsiElementResolveResult(it) }
                .toTypedArray()

            else -> resolve()
                ?.let { arrayOf(PsiElementResolveResult(it)) }
                ?: emptyArray()
        }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val targetFile = element.containingFile ?: return false
        if (targetFile.fileType != QuarkdownFileType.INSTANCE) return false

        val id = referenceText.trim()
        if (id.isEmpty()) return false

        // Find the anchor overlapping the candidate element and compare ids.
        // This works regardless of how the lexer splits a hyphenated id.
        val anchors = QuarkdownReferenceAnchors.of(targetFile)
        val targetRange = element.textRange
        return anchors.any { anchor ->
            anchor.referenceText.trim().equals(id, ignoreCase = true) &&
                    TextRange(anchor.start, anchor.end).intersects(targetRange)
        }
    }

    /**
     * Finds the document range of the declared id ([referenceText]) inside [file],
     * according to this reference's type. This lets Find Usages match any leaf of a
     * multi-leaf (hyphenated) id.
     */
    private fun declarationIdRange(file: PsiFile, id: String): TextRange? {
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

    // ---- `{#id}` label → every `.ref { id }` usage ----
    private fun findAllRefUsages(project: Project, sourceFile: VirtualFile): List<PsiElement> {
        val id = referenceText.trim().lowercase()
        if (id.isEmpty()) return emptyList()
        val pattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)
        val result = mutableListOf<PsiElement>()
        val qdFiles = QuarkdownReferenceFiles.collect(project, element.containingFile)
        for (psiFile in qdFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (match.groupValues[1].trim().lowercase() != id) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                val leaf = psiFile.findElementAt(contentStart)
                result.add(leaf ?: psiFile)
            }
        }
        return result
    }

    // ---- `{#id}` label → first `.ref { id }` usage ----
    private fun resolveFirstUsage(project: Project, sourceFile: VirtualFile): PsiElement? {
        val id = referenceText.trim().lowercase()
        if (id.isEmpty()) return null
        val pattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)
        return findElementInQdFiles(project, pattern) { match ->
            match.groupValues[1].trim().lowercase() == id
        }
    }

    // ---- `.var {name}` declaration → first `.name` usage ----
    private fun resolveFirstVarUsage(project: Project, sourceFile: VirtualFile): PsiElement? {
        val name = referenceText.trim().lowercase()
        if (name.isEmpty()) return null
        val pattern = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b""")
        return findElementInQdFiles(project, pattern) { match ->
            match.groupValues[1].lowercase() == name
        }
    }

    // ---- `.var {name}` declaration → every `.name` usage ----
    private fun findAllVarUsages(project: Project, sourceFile: VirtualFile): List<PsiElement> {
        val name = referenceText.trim().lowercase()
        if (name.isEmpty()) return emptyList()
        val pattern = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b""")
        val result = mutableListOf<PsiElement>()
        val qdFiles = QuarkdownReferenceFiles.collect(project, element.containingFile)
        for (psiFile in qdFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (match.groupValues[1].lowercase() != name) continue
                val leaf = psiFile.findElementAt(match.groups[1]!!.range.first)
                result.add(leaf ?: psiFile)
            }
        }
        return result
    }

    // ---- .name → .var { name } declaration (document scoped) ----
    private fun resolveVar(project: Project, sourceFile: VirtualFile): PsiElement? {
        val name = referenceText.trim().lowercase()
        if (name.isEmpty()) return null
        val pattern = Regex("""\.var\s*\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}""", RegexOption.IGNORE_CASE)
        return findElementInQdFiles(project, pattern) { match ->
            match.groupValues[1].lowercase() == name
        }
    }

    /**
     * Scans all Quarkdown files in the project for [pattern]; for the first match that
     * satisfies [predicate], returns the PSI element at the captured group's position.
     *
     * The file that contains this reference's element is always scanned first — even if
     * it is a brand-new (unsaved) file not yet visible through [FileTypeIndex] — so
     * `.ref {id}` / `{#id}` declared in the same buffer resolve correctly.
     */
    private fun findElementInQdFiles(
        project: Project,
        pattern: Regex,
        predicate: (MatchResult) -> Boolean
    ): PsiElement? {
        val orderedFiles = QuarkdownReferenceFiles.collect(project, element.containingFile)

        for (psiFile in orderedFiles) {
            for (match in pattern.findAll(psiFile.text)) {
                if (!predicate(match)) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                return psiFile.findElementAt(contentStart) ?: psiFile.findElementAt(match.range.first) ?: psiFile
            }
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // Replace the reference text inside the current element with the new name.
        val file = element.containingFile ?: return element
        val document = file.viewProvider.document ?: return element
        if (!element.isValid) return element

        val absoluteStart = element.textRange.startOffset + rangeInElement.startOffset
        val absoluteEnd = element.textRange.startOffset + rangeInElement.endOffset
        if (absoluteEnd <= absoluteStart || absoluteEnd > document.textLength) return element

        // Skip if the range already holds the new name (e.g. the declaration was already
        // renamed via setName and this usage overlaps the same position).
        val currentText = document.getText(TextRange(absoluteStart, absoluteEnd))
        if (currentText == newElementName) return element

        WriteCommandAction.runWriteCommandAction(element.project) {
            document.replaceString(absoluteStart, absoluteEnd, newElementName)
            PsiDocumentManager.getInstance(element.project).commitDocument(document)
        }
        return element
    }

    override fun bindToElement(targetElement: PsiElement): PsiElement {
        // Text-based references cannot be rebound to an arbitrary element.
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    /** Creates a reference for the given anchor (used by Find Usages / rename). */
    fun withAnchor(anchorElement: PsiElement, range: TextRange): QuarkdownReference =
        QuarkdownReference(anchorElement, referenceText, referenceType, range)

    // ---- .ref { id } → `{#id}` label declaration or heading anchor ----
    private fun resolveRef(project: Project, sourceFile: VirtualFile): PsiElement? {
        val id = referenceText.trim().lowercase()
        if (id.isEmpty()) return null
        val escapedId = Regex.escape(id)

        // 1) Look for `{#id}` label declaration (case-insensitive id).
        val labelPattern = Regex("""\{#\s*$escapedId\s*}""", RegexOption.IGNORE_CASE)
        findElementInQdFiles(project, labelPattern) { true }
            ?.let { return it }

        // 2) Look for a heading whose text or trailing `{#id}` matches the id.
        val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
        val slugTarget = id.replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
        return findElementInQdFiles(project, headingPattern) { hMatch ->
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
