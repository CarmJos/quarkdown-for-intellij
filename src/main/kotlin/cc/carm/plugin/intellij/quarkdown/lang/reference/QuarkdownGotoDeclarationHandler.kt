package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Ctrl+Click navigation for Quarkdown references.
 *
 * `{#id}` label and `.var { name }` declarations deliberately return NO target here: the
 * platform then falls back to the Symbol model (`TargetsKt.declaredReferencedData`), where
 * our id leaves implement `PsiNamedElement` and become declared symbols. That routes to the
 * Java-style Show Usages popup listing every usage with file, line and context.
 *
 * `.ref { id }` usages return the single `{#id}` declaration (or heading) so they navigate
 * directly; `.include` / `.read` / `.css` / `.code` and image paths return the target file.
 */
class QuarkdownGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement> {
        if (sourceElement == null || editor == null) return PsiElement.EMPTY_ARRAY
        return ApplicationManager.getApplication().runReadAction<Array<PsiElement>> {
            val psiFile = sourceElement.containingFile ?: return@runReadAction PsiElement.EMPTY_ARRAY
            if (psiFile.fileType != QuarkdownFileType.INSTANCE) return@runReadAction PsiElement.EMPTY_ARRAY
            computeTargets(sourceElement, offset, editor, psiFile)
        }
    }

    private fun computeTargets(
        sourceElement: PsiElement,
        offset: Int,
        editor: Editor,
        psiFile: PsiFile
    ): Array<PsiElement> {
        val anchors = QuarkdownReferenceAnchors.of(psiFile)
        val anchor = anchors.firstOrNull {
            TextRange(it.start, it.end).contains(offset)
        } ?: return PsiElement.EMPTY_ARRAY

        val id = anchor.referenceText.trim()
        if (id.isEmpty()) return PsiElement.EMPTY_ARRAY

        val project: Project = psiFile.project
        return when (anchor.referenceType) {
            // Declarations: return every usage so the platform underlines the whole id
            // on hover (multipleTargetsCtrlMouseData uses reference ranges). The click is
            // intercepted by QuarkdownEditorMouseListener, which shows the official
            // Show Usages popup and consumes the event.
            "label" -> showUsagesFor(project, id)
            "var-decl" -> showVarUsagesFor(project, id)
            // Usages: navigate directly to the declaration.
            "ref" -> findLabelDeclaration(project, id)
            "var" -> findVarDeclaration(project, id)
            // File paths: navigate to the target file.
            "read", "include", "css", "code", "image" -> resolveFileTarget(psiFile, anchor.referenceText)
            "image-dir" -> resolveFileTarget(psiFile, anchor.referenceText)
            else -> PsiElement.EMPTY_ARRAY
        }
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

    /**
     * For a `{#id}` label: returns every `.ref { id }` usage (as NavigationItems) so the
     * platform draws the underline over the whole id on Ctrl+hover. The click itself is
     * handled by [QuarkdownEditorMouseListener].
     */
    private fun showUsagesFor(project: Project, id: String): Array<PsiElement> =
        collectElements(project, Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE), id) { match ->
            match.groupValues[1].trim().lowercase() == id.lowercase()
        }

    /** Same as [showUsagesFor] but for `.var { name }` declarations (`.name` usages). */
    private fun showVarUsagesFor(project: Project, name: String): Array<PsiElement> =
        collectElements(project, Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b"""), name) { match ->
            match.groupValues[1].lowercase() == name.lowercase()
        }

    /** Resolves a file-path reference to its target [PsiFile] (or directory). */
    private fun resolveFileTarget(sourceFile: PsiFile, referenceText: String): Array<PsiElement> {
        val vf = QuarkdownPathUtil.resolveToVirtualFile(
            sourceFile.project, sourceFile.virtualFile ?: return PsiElement.EMPTY_ARRAY, referenceText.trim()
        ) ?: return PsiElement.EMPTY_ARRAY
        val psi = if (vf.isDirectory) {
            sourceFile.manager.findDirectory(vf)
        } else {
            sourceFile.manager.findFile(vf)
        } ?: return PsiElement.EMPTY_ARRAY
        return arrayOf(psi)
    }

    /**
     * The `{#id}` declaration for a `.ref { id }` usage (or heading anchor fallback).
     * Returns at most ONE element so `.ref` clicks navigate directly instead of showing
     * a "Choose Declaration" dialog. The first declaration in the source file wins,
     * falling back to the first project-wide match.
     */
    private fun findLabelDeclaration(project: Project, id: String): Array<PsiElement> {
        val escaped = Regex.escape(id)
        val pattern = Regex("""\{#\s*$escaped\s*}""", RegexOption.IGNORE_CASE)

        // Prefer a declaration in the source file, then any other file.
        val all = collectRaw(project, pattern, id) { true }
        if (all.isEmpty()) {
            // Heading whose slug matches the id (no explicit {#id}).
            val slugTarget = id.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
            val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
            val headings = collectRaw(project, headingPattern, id) { hMatch ->
                val text = hMatch.groupValues[1].trim()
                text.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-') == slugTarget
            }
            if (headings.isNotEmpty()) return arrayOf(headings.first())
        }
        if (all.isEmpty()) return PsiElement.EMPTY_ARRAY
        return arrayOf(all.first())
    }

    /** Collects raw leaves (without wrapping) for declaration navigation. */
    private fun collectRaw(
        project: Project,
        pattern: Regex,
        id: String,
        predicate: (MatchResult) -> Boolean
    ): List<PsiElement> {
        val psiManager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .mapNotNull { psiManager.findFile(it) }
        val result = mutableListOf<PsiElement>()
        for (f in files) {
            for (match in pattern.findAll(f.text)) {
                if (!predicate(match)) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                val leaf = f.findElementAt(contentStart) ?: f.findElementAt(match.range.first)
                if (leaf != null) {
                    result.add(leaf)
                }
            }
        }
        return result
    }

    /** The `.var { name }` declaration for a `.name` usage. */
    private fun findVarDeclaration(project: Project, name: String): Array<PsiElement> {
        val escaped = Regex.escape(name)
        val pattern = Regex("""\.var\s*\{\s*$escaped\s*\}""", RegexOption.IGNORE_CASE)
        return collectElements(project, pattern, name) { true }
    }

    /**
     * Scans all Quarkdown files, finds matching anchor starts and returns the leaf at each
     * position. Used as hover targets so the platform underlines the whole id.
     */
    private fun collectElements(
        project: Project,
        pattern: Regex,
        id: String,
        predicate: (MatchResult) -> Boolean
    ): Array<PsiElement> {
        val psiManager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .mapNotNull { psiManager.findFile(it) }
        val result = mutableListOf<PsiElement>()
        for (f in files) {
            for (match in pattern.findAll(f.text)) {
                if (!predicate(match)) continue
                val contentStart = match.range.first + match.value.indexOf('{') + 1
                val leaf = f.findElementAt(contentStart) ?: f.findElementAt(match.range.first)
                if (leaf != null) {
                    result.add(leaf)
                }
            }
        }
        return result.toTypedArray()
    }
}