package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.equation.EquationDialog
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationEdit
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexPreviewSource
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownIdRenameUtils
import cc.carm.plugin.intellij.quarkdown.ui.preview.QuarkdownLatexPreviewDialog
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Shows an equation icon in the gutter on every piece of LaTeX content:
 *
 *  - `$ ... $ {#id}` equations — on the equation line;
 *  - `$$$ {#id}` fenced equations — on the opening `$$$` line;
 *  - `.math` and `.texmacro` content — on the first content line (for an indented block body) or
 *    on the call line (for a brace argument).
 *
 * What a click does:
 *
 *  - one editable equation on the line → the [EquationDialog] opens for it;
 *  - **several** equations on one line → a chooser popup lists them, because the icon cannot
 *    know which one was meant;
 *  - nothing editable (e.g. a `.texmacro`, whose name/body are not equations) → the formula is
 *  - nothing editable (e.g. a `.texmacro`, whose name/body are not equations) → the formula is
 *    typeset with the KaTeX build that ships in the Quarkdown installation.
 *
 * The popup menu always offers both actions explicitly.
 *
 * Regions come from [QuarkdownEquationRegions] — the same single source of truth as the LaTeX
 * annotator — so the gutter can never disagree with the highlighting about what counts as TeX.
 * That is also why [collectSlowLineMarkers] is used: locating the regions (and pairing `$$$`
 * fences) needs one scan of the document, which a per-element callback cannot do.
 */
class QuarkdownEquationLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val file = elements.first().containingFile ?: return
        if (file.fileType !is QuarkdownFileType) return
        val text = file.text

        // One gutter icon per line: several regions can share a line (`.texmacro {\a} {\b}`,
        // two `$ … $` equations in one paragraph).
        val linesWithLatex = HashSet<Int>()
        for (region in QuarkdownEquationRegions.find(text).regions) {
            if (region.isEmpty) continue
            linesWithLatex.add(markerLineStart(text, region))
        }
        if (linesWithLatex.isEmpty()) return

        for (element in elements) {
            if (element.firstChild != null) continue
            val elementOffset = element.textRange.startOffset
            // Anchor the marker at the leaf that starts the line: the first non-whitespace
            // character of a line belongs to the leaf whose range begins at the line start.
            if (elementOffset != findLineStart(text, elementOffset)) continue
            if (elementOffset !in linesWithLatex) continue
            result.add(createMarker(file, element, elementOffset))
        }
    }

    private fun createMarker(file: PsiFile, element: PsiElement, lineStart: Int): LineMarkerInfo<*> {
        // The tooltip is kept in a typed local: the LineMarkerInfo overloads accept either a
        // tooltip Function or an accessible-name Supplier, and an inline lambda is ambiguous.
        val tooltip: com.intellij.util.Function<PsiElement, String> =
            object : com.intellij.util.Function<PsiElement, String> {
                override fun `fun`(param: PsiElement): String =
                    QuarkdownBundle.message("quarkdown.marker.equation.tooltip")
            }
        // The accessible name doubles as the tooltip for screen readers.
        val accessibleName = java.util.function.Supplier {
            QuarkdownBundle.message("quarkdown.marker.equation.tooltip")
        }

        return object : LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            QuarkdownIcons.EQUATION_MARKER,
            tooltip,
            EquationGutterHandler(file, lineStart),
            GutterIconRenderer.Alignment.RIGHT,
            accessibleName,
        ) {
            // The default renderer has no popup menu; this one adds both actions.
            override fun createGutterRenderer(): GutterIconRenderer =
                FormulaGutterRenderer(this, file, lineStart)
        }
    }

    /**
     * The line that should carry the gutter icon for [region]: fenced blocks are marked on their
     * opening `$$$` line, everything else on the line the content starts on.
     */
    private fun markerLineStart(text: CharSequence, region: QuarkdownEquationRegions.Region): Int =
        if (region.kind == QuarkdownEquationRegions.Kind.MULTILINE) {
            findLineStart(text, (region.contentStart - 1).coerceAtLeast(0))
        } else {
            findLineStart(text, region.contentStart)
        }

    /** Left click: edit the single equation on the line, choose among several, or preview. */
    private inner class EquationGutterHandler(
        private val file: PsiFile,
        private val lineStart: Int,
    ) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            val equations = editableEquations(file, lineStart)
            when {
                equations.size == 1 -> editEquation(file, equations.first())
                equations.size > 1 -> chooseEquation(file, equations, e.component)
                else -> previewFormula(file, lineStart)
            }
        }
    }

    /** Gutter icon adding the equation actions to the popup menu. */
    private inner class FormulaGutterRenderer(
        info: LineMarkerInfo<PsiElement>,
        private val file: PsiFile,
        private val lineStart: Int,
    ) : LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>(info) {

        override fun getPopupMenuActions(): ActionGroup {
            val group = DefaultActionGroup()
            group.add(object : AnAction(QuarkdownBundle.message("quarkdown.math.preview.action")) {
                override fun actionPerformed(e: AnActionEvent) = previewFormula(file, lineStart)
            })
            group.addSeparator()
            group.add(object : AnAction(QuarkdownBundle.message("quarkdown.dialog.equation.title")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val equations = editableEquations(file, lineStart)
                    when {
                        equations.size == 1 -> editEquation(file, equations.first())
                        equations.size > 1 -> chooseEquation(file, equations, null)
                    }
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = editableEquations(file, lineStart).isNotEmpty()
                }
            })
            return group
        }
    }

    /**
     * The equations on [lineStart] that can be edited — i.e. those with a content/id model.
     * `.texmacro` regions are excluded: a macro declaration is not an equation.
     */
    private fun editableEquations(
        file: PsiFile,
        lineStart: Int,
    ): List<QuarkdownEquationRegions.Region> {
        val text = file.text
        return QuarkdownEquationRegions.find(text).regions.filter { region ->
            !region.isEmpty && markerLineStart(text, region) == lineStart &&
                    QuarkdownEquationEdit.occurrence(text, region) != null
        }
    }

    /**
     * Asks which equation to edit when several share one line. The popup is shown near the
     * click when [component] is known (gutter click) and otherwise over the editor.
     */
    private fun chooseEquation(
        file: PsiFile,
        equations: List<QuarkdownEquationRegions.Region>,
        component: Component?,
    ) {
        val text = file.text
        val choices = equations.mapIndexed { index, region -> Choice(index + 1, snippet(text, region), region) }
        val builder = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(choices)
            .setTitle(QuarkdownBundle.message("quarkdown.dialog.equation.choose"))
            .setItemChosenCallback { editEquation(file, it.region) }
        val popup = builder.createPopup()
        if (component != null) popup.showUnderneathOf(component) else popup.showInFocusCenter()
    }

    /** Opens the equation editor for [region] and writes the result back over its span. */
    private fun editEquation(file: PsiFile, region: QuarkdownEquationRegions.Region) {
        val project = file.project
        if (project.isDisposed) return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val text = document.immutableCharSequence
        val occurrence = QuarkdownEquationEdit.occurrence(text, region) ?: return

        val dialog = EquationDialog(project, text.toString(), occurrence)
        if (!dialog.showAndGet()) return

        // The region may be replaced wholesale: its span covers the delimiters and the id tag.
        val replacement = dialog.buildText()
        val start = region.spanStart
        val end = region.spanEnd
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(start, end, replacement)
        }

        val newId = dialog.getIdForTest()
        if (occurrence.id.isNotEmpty() && occurrence.id != newId) {
            QuarkdownIdRenameUtils.renameRefUsagesAndNotify(project, file, occurrence.id, newId)
        }
    }

    /**
     * Renders the formula of the gutter icon on [lineStart]. When several regions share the line
     * — a `.texmacro` name and its body, or two equations — the largest is previewed, which
     * selects the macro body rather than its name.
     */
    private fun previewFormula(file: PsiFile, lineStart: Int) {
        val project = file.project
        if (project.isDisposed) return
        val text = file.text
        val region = QuarkdownEquationRegions.find(text).regions
            .filter { !it.isEmpty && markerLineStart(text, it) == lineStart }
            .maxByOrNull { it.contentEnd - it.contentStart }
            ?: return
        // Typeset the region's TeX with the installation's KaTeX build: instant, no compile.
        val tex = text.substring(region.contentStart, region.contentEnd).trim()
        val displayMode = QuarkdownLatexPreviewSource.displayMode(
            tex,
            region.kind == QuarkdownEquationRegions.Kind.MULTILINE,
        )
        QuarkdownLatexPreviewDialog.show(project, tex, text, displayMode)
    }

    /** A one-line description of an equation, used as the chooser's entry text. */
    private fun snippet(text: CharSequence, region: QuarkdownEquationRegions.Region): String {
        val content = text.substring(region.contentStart, region.contentEnd)
            .replace(Regex("\\s+"), " ")
            .trim()
        val head = if (content.length > SNIPPET_LENGTH) content.take(SNIPPET_LENGTH) + "…" else content
        val kind = when (region.kind) {
            QuarkdownEquationRegions.Kind.MATH_CALL -> ".math"
            QuarkdownEquationRegions.Kind.MULTILINE -> "$$$"
            else -> "\$"
        }
        return "$kind  $head"
    }

    /** One entry of the multi-equation chooser. */
    private data class Choice(val index: Int, val label: String, val region: QuarkdownEquationRegions.Region) {
        override fun toString(): String = "$index. $label"
    }

    private fun findLineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private companion object {
        /** Characters of the equation shown in the chooser. */
        const val SNIPPET_LENGTH = 60
    }
}
