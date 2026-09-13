package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.equation.EquationDialog
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownFormulaPreview
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownIdRenameUtils
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent

/**
 * Shows an equation icon in the gutter on every piece of LaTeX content:
 *
 *  - `$ ... $ {#id}` equations — on the equation line;
 *  - `$$$ {#id}` fenced equations — on the opening `$$$` line;
 *  - `.math` and `.texmacro` content — on the first content line (for an indented block body)
 *    or on the call line (for a brace argument).
 *
 * The icon offers, through its popup menu:
 *
 *  - **Preview Formula** — renders the formula with the Quarkdown CLI (see
 *    [QuarkdownFormulaPreview]) and shows the result in an embedded browser;
 *  - **Edit Equation ID…** — the [EquationDialog], available only where an equation really has
 *    an editable id (a `$` equation, not `.math` / `.texmacro`).
 *
 * A left click keeps the historical behaviour where it applies — opening the id dialog for
 * `$` equations — and previews the formula everywhere else.
 *
 * The regions come from [QuarkdownEquationRegions], the same single source of truth used by
 * the LaTeX annotator, so the gutter can never disagree with the highlighting about what
 * counts as TeX. That is also why [collectSlowLineMarkers] is used: locating the regions (and
 * pairing `$$$` fences) needs one scan of the document, which a per-element callback cannot do.
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

        // One gutter icon per line: group the regions by the line that should carry the icon.
        // `.texmacro {\a} {\b}` yields two regions on one line, `$ x $ {#a} $ y $ {#b}` too.
        val regionsByLine = LinkedHashMap<Int, MutableList<QuarkdownEquationRegions.Region>>()
        for (region in QuarkdownEquationRegions.find(text).regions) {
            if (region.isEmpty) continue
            regionsByLine.getOrPut(markerLineStart(text, region)) { mutableListOf() }.add(region)
        }
        if (regionsByLine.isEmpty()) return

        for (element in elements) {
            if (element.firstChild != null) continue
            val elementOffset = element.textRange.startOffset
            // Anchor the marker at the leaf that starts the line: the first non-whitespace
            // character of a line belongs to the leaf whose range begins at the line start.
            if (elementOffset != findLineStart(text, elementOffset)) continue
            val regions = regionsByLine.remove(elementOffset) ?: continue
            // The regions themselves are not kept: the preview re-resolves them from the
            // current text, so stale offsets can never be used.
            if (regions.isEmpty()) continue
            result.add(createMarker(file, element, elementOffset))
        }
    }

    private fun createMarker(
        file: PsiFile,
        element: PsiElement,
        lineStart: Int,
    ): LineMarkerInfo<*> {
        val handler = EquationGutterHandler(file, lineStart)
        return object : LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            QuarkdownIcons.EQUATION_MARKER,
            { QuarkdownBundle.message("quarkdown.marker.equation.tooltip") },
            handler,
            GutterIconRenderer.Alignment.RIGHT,
            { QuarkdownBundle.message("quarkdown.marker.equation.tooltip") },
        ) {
            // The default renderer has no popup menu; this one adds the formula preview (and the
            // id dialog) to the gutter's menu.
            override fun createGutterRenderer(): GutterIconRenderer =
                FormulaGutterRenderer(this, file, lineStart)
        }
    }

    /**
     * The line that should carry the gutter icon for [region]. Fenced blocks are marked on
     * their opening `$$$` line (the line before the content), everything else on the line the
     * content starts on.
     */
    private fun markerLineStart(text: CharSequence, region: QuarkdownEquationRegions.Region): Int =
        if (region.kind == QuarkdownEquationRegions.Kind.MULTILINE) {
            findLineStart(text, (region.contentStart - 1).coerceAtLeast(0))
        } else {
            findLineStart(text, region.contentStart)
        }

    /** Left click: the id dialog where an equation has an id, the formula preview otherwise. */
    private inner class EquationGutterHandler(
        private val file: PsiFile,
        private val lineStart: Int,
    ) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            if (editableEquationKind(elt) != null) editEquationId(elt) else previewFormula(file, lineStart)
        }
    }

    /** Gutter icon adding the formula preview (and the id dialog) to the popup menu. */
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
            // Only `$ ... $` / `$$$` equations have an editable id; `.math` and `.texmacro`
            // do not, so the entry is not offered there (and is decided when the menu opens).
            if (currentElement()?.let { editableEquationKind(it) } != null) {
                group.addSeparator()
                group.add(object : AnAction(QuarkdownBundle.message("quarkdown.dialog.equation.title")) {
                    override fun actionPerformed(e: AnActionEvent) {
                        currentElement()?.let { editEquationId(it) }
                    }
                })
            }
            return group
        }

        /** The element at the marker's line, re-resolved because PSI may have been rebuilt. */
        private fun currentElement(): PsiElement? {
            val text = file.text
            return file.findElementAt(lineStart.coerceIn(0, text.length))
        }
    }

    /**
     * Renders the formula of the gutter icon on [lineStart].
     *
     * The regions are recomputed from the current document text instead of reusing the ones the
     * markers were built from: the document may have been edited in the meantime, and a formula
     * is short enough for the re-scan to be cheap. When several regions share the line — a
     * `.texmacro` name and its body, or two equations — the largest is previewed, which selects
     * the macro body rather than its name.
     */
    private fun previewFormula(file: PsiFile, lineStart: Int) {
        if (file.project.isDisposed) return
        val text = file.text
        val region = QuarkdownEquationRegions.find(text).regions
            .filter { !it.isEmpty && markerLineStart(text, it) == lineStart }
            .maxByOrNull { it.contentEnd - it.contentStart }
            ?: return
        QuarkdownFormulaPreview.getInstance(file.project).preview(text, region)
    }


    /** Opens the [EquationDialog] for the equation starting on [element]'s line. */
    private fun editEquationId(elt: PsiElement) {
        val project = elt.project
        val file = elt.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val kind = editableEquationKind(elt) ?: return
        val text = document.immutableCharSequence
        val start = findLineStart(text, elt.textRange.startOffset)
        val end = findLineEnd(text, start)
        val line = text.subSequence(start, end).toString()

        val dialog = EquationDialog(project, kind)
        val oldId: String
        when (kind) {
            QuarkdownEquationSyntax.Kind.INLINE -> {
                val info = QuarkdownEquationSyntax.parseInlineEquationLine(line) ?: return
                dialog.parseInline(info)
                oldId = info.id
            }

            QuarkdownEquationSyntax.Kind.FENCED -> {
                val info = QuarkdownEquationSyntax.parseFenceEquationLine(line) ?: return
                dialog.parseFence(info)
                oldId = info.id
            }
        }

        if (dialog.showAndGet()) {
            val newId = dialog.getIdForTest()
            WriteCommandAction.runWriteCommandAction(project) {
                val replacement = dialog.buildLine()
                document.replaceString(start, end, replacement)
            }
            if (oldId != newId) {
                QuarkdownIdRenameUtils.renameRefUsagesAndNotify(project, file, oldId, newId)
            }
        }
    }

    /**
     * The equation kind whose id is editable from the line [element] belongs to, or `null`
     * when the line is not an `$ ... $` / `$$$` equation (`.math` and `.texmacro` carry no
     * editable id).
     */
    private fun editableEquationKind(element: PsiElement): QuarkdownEquationSyntax.Kind? {
        val file = element.containingFile ?: return null
        val text = file.text
        val start = findLineStart(text, element.textRange.startOffset)
        val line = text.subSequence(start, findLineEnd(text, start)).toString()

        if (QuarkdownEquationSyntax.parseFenceEquationLine(line) != null) {
            // Only the opening fence is editable — a closing `$$$` must not open the dialog.
            return if (start in QuarkdownEquationSyntax.findEquationFenceOpenOffsets(text)) {
                QuarkdownEquationSyntax.Kind.FENCED
            } else {
                null
            }
        }
        return if (QuarkdownEquationSyntax.parseInlineEquationLine(line) != null) {
            QuarkdownEquationSyntax.Kind.INLINE
        } else {
            null
        }
    }

    private fun findLineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun findLineEnd(text: CharSequence, offset: Int): Int {
        var i = offset
        while (i < text.length && text[i] != '\n') i++
        return i
    }
}
