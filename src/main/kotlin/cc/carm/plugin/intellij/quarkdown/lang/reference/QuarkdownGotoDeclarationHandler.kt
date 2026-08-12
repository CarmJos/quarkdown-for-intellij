package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Ctrl+Click navigation for Quarkdown references.
 *
 * **Declarations** (`{#id}` label, `.var { name }`):
 *  - with exactly ONE usage: return that usage so the platform navigates directly (no
 *    usages window, no flash);
 *  - with several usages: return **no targets** so the platform falls back to the Symbol
 *    model and shows the native Show Usages window listing every `.ref { id }` usage,
 *    anchored at the declaration (no caret jump);
 *  - with no usages: return **no targets**; the Symbol model then shows "No usages found".
 *
 * **Usages** (`.ref { id }`, `.name`): return the single declaration so Ctrl+Click navigates
 * directly.
 *
 * **File paths** (`.include` / `.read` and image paths): return the
 * target file.
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
            // Declarations (`{#id}` label / `.var {name}`):
            //  - with exactly ONE usage: return it so the platform navigates directly (GTD),
            //    no usages window and no flash.
            //  - with several or no usages: return EMPTY so the platform falls back to the
            //    Symbol model and shows the native Show Usages window (which lists the real
            //    `.ref {id}` usages, anchored at the declaration).
            "label", "var-decl" -> declarationTargets(project, psiFile, anchor)
            // Usages: navigate directly to the declaration.
            "ref" -> findLabelDeclaration(project, psiFile, id)
            "var" -> findVarDeclaration(project, psiFile, id)
            // File paths: navigate to the target file.
            "read", "include", "image" -> resolveFileTarget(psiFile, anchor.referenceText)
            "image-dir" -> resolveFileTarget(psiFile, anchor.referenceText)
            else -> PsiElement.EMPTY_ARRAY
        }
    }

    /**
     * Navigation targets for a declaration anchor (`label` / `var-decl`).
     *
     * Exactly ONE usage → return that usage so Ctrl+Click navigates directly. Otherwise
     * return EMPTY so the platform produces the native "Show Usages" outcome (listing every
     * usage), which is exactly what a multi-usage declaration needs — and a zero-usage
     * declaration simply shows "No usages found".
     */
    private fun declarationTargets(project: Project, sourceFile: PsiFile, anchor: QuarkdownReferenceParser.Anchor): Array<PsiElement> {
        val id = anchor.referenceText.trim()
        if (id.isEmpty()) return PsiElement.EMPTY_ARRAY
        val usages = when (anchor.referenceType) {
            "label" -> collectRaw(
                project, sourceFile,
                Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE), id
            ) { match -> match.groupValues[1].trim().equals(id, ignoreCase = true) }

            "var-decl" -> collectRaw(
                project, sourceFile,
                Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b"""), id
            ) { match -> match.groupValues[1].equals(id, ignoreCase = true) }

            else -> emptyList()
        }
        return if (usages.size == 1) arrayOf(usages.first()) else PsiElement.EMPTY_ARRAY
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

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
    private fun findLabelDeclaration(project: Project, sourceFile: PsiFile, id: String): Array<PsiElement> {
        val escaped = Regex.escape(id)
        val pattern = Regex("""\{#\s*$escaped\s*}""", RegexOption.IGNORE_CASE)

        // Prefer a declaration in the source file, then any other file.
        val all = collectRaw(project, sourceFile, pattern, id) { true }
        if (all.isEmpty()) {
            // Heading whose slug matches the id (no explicit {#id}).
            val slugTarget = id.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
            val headingPattern = Regex("""#{1,6}\s+(.+?)(?:\s*#+\s*)?$""", RegexOption.MULTILINE)
            val headings = collectRaw(project, sourceFile, headingPattern, id) { hMatch ->
                val text = hMatch.groupValues[1].trim()
                text.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-') == slugTarget
            }
            if (headings.isNotEmpty()) return arrayOf(headings.first())
        }
        if (all.isEmpty()) return PsiElement.EMPTY_ARRAY
        return arrayOf(all.first())
    }

    /**
     * Collects raw leaves (without wrapping) for declaration navigation. The file that
     * contains the reference being resolved is always scanned first — even when it is a
     * brand-new (unsaved) file not yet visible through [FileTypeIndex] — so `.ref {id}`
     * can jump back to `{#id}` in the same buffer.
     */
    private fun collectRaw(
        project: Project,
        sourceFile: PsiFile,
        pattern: Regex,
        id: String,
        predicate: (MatchResult) -> Boolean
    ): List<PsiElement> {
        val files = QuarkdownReferenceFiles.collect(project, sourceFile)
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
    private fun findVarDeclaration(project: Project, sourceFile: PsiFile, name: String): Array<PsiElement> {
        val escaped = Regex.escape(name)
        val pattern = Regex("""\.var\s*\{\s*$escaped\s*\}""", RegexOption.IGNORE_CASE)
        val all = collectRaw(project, sourceFile, pattern, name) { true }
        return if (all.isEmpty()) PsiElement.EMPTY_ARRAY else arrayOf(all.first())
    }
}