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
 * **Declarations** (`{#id}` label, `.var { name }`): return **no targets** so the platform
 * falls back to the Symbol model. The Symbol model recognises the [PsiNamedElement] +
 * [PsiNameIdentifierOwner] on [QuarkdownIdLeafPsiElement] and produces a Show Usages (SU)
 * result — the official Show Usages popup with no "Choose Declaration" and no "Cannot find
 * declaration to go to". The [PsiNameIdentifierOwner] also ensures the Ctrl+hover underline
 * covers only the id (not the whole `{#id}` token).
 *
 * **Usages** (`.ref { id }`, `.name`): return the single declaration so Ctrl+Click navigates
 * directly.
 *
 * **File paths** (`.include` / `.read` / `.css` / `.code` and image paths): return the
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
            // Declarations: return no targets so the platform falls back to the Symbol model,
            // which produces a Show Usages (SU) result via ShowUsagesGTDUActionData.
            // The PsiNameIdentifierOwner on QuarkdownIdLeafPsiElement ensures the underline
            // covers only the id.
            "label", "var-decl" -> PsiElement.EMPTY_ARRAY
            // Usages: navigate directly to the declaration.
            "ref" -> findLabelDeclaration(project, psiFile, id)
            "var" -> findVarDeclaration(project, psiFile, id)
            // File paths: navigate to the target file.
            "read", "include", "css", "code", "image" -> resolveFileTarget(psiFile, anchor.referenceText)
            "image-dir" -> resolveFileTarget(psiFile, anchor.referenceText)
            else -> PsiElement.EMPTY_ARRAY
        }
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