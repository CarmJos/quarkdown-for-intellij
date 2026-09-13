package cc.carm.plugin.intellij.quarkdown.lang.fold

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownReferenceLabelResolver
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownReferenceLabelResolver.Kind
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.LightweightHint
import java.awt.Cursor
import javax.swing.Icon

/**
 * Keeps the icon-chip preview of collapsed `.ref {id}` folds in sync with the folding model.
 *
 * Fold placeholders only support plain text, so [QuarkdownFoldingBuilder] collapses resolved
 * `.ref` usages to an *empty* placeholder and this synchronizer paints the actual preview —
 * the target-kind icon (figure / table / …) plus its caption — as an inline inlay chip
 * ([QuarkdownRefFoldChipRenderer]) placed at the fold start:
 *
 *  - a chip is added whenever its fold region is collapsed, and removed when expanded;
 *  - hovering the chip shows the raw `.ref {id}` source (as the plain fold tooltip did);
 *  - clicking the chip expands the fold, revealing the raw reference again.
 *
 * Installed per editor by [installOnEditors], which is called while fold regions are built for a
 * Quarkdown document (see `QuarkdownFoldingBuilder.buildFoldRegions`) — that guarantees a
 * synchronizer is in place before collapsed `.ref` previews become visible. All listeners are
 * registered against the synchronizer instance and disappear with the editor.
 */
class QuarkdownRefFoldPreviewSynchronizer private constructor(
    private val editor: Editor,
) : FoldingListener, Disposable {

    private var hovered: Inlay<*>? = null
    private var tooltip: LightweightHint? = null
    private var syncing = false

    // ------------------------------------------------------------------
    // FoldingListener — react to expand/collapse and to folding rebuilds
    // ------------------------------------------------------------------

    override fun onFoldRegionStateChange(region: FoldRegion) = sync()

    override fun onFoldProcessingEnd() = sync()

    // ------------------------------------------------------------------
    // Sync: chips ⇄ collapsed ref folds
    // ------------------------------------------------------------------

    /** Reconciles the chip inlays with the currently collapsed `.ref` fold regions. */
    fun sync() {
        if (syncing || editor.isDisposed) return
        syncing = true
        try {
            reconcile()
        } finally {
            syncing = false
        }
    }

    private fun reconcile() {
        val document = editor.document
        val text = document.text
        val inlayModel = editor.inlayModel

        val existing = inlayModel.getInlineElementsInRange(0, text.length, QuarkdownRefFoldChipRenderer::class.java)
        val byOffset = mutableMapOf<Int, MutableList<Inlay<out QuarkdownRefFoldChipRenderer>>>()
        for (inlay in existing) byOffset.getOrPut(inlay.offset) { mutableListOf() }.add(inlay)

        val keptOffsets = mutableSetOf<Int>()
        for (region in editor.foldingModel.allFoldRegions) {
            if (!region.isValid || region.isExpanded) continue
            if (region.placeholderText.isNotEmpty()) continue
            val target = refTargetOf(region, text) ?: continue
            keptOffsets.add(region.startOffset)

            val candidates = byOffset[region.startOffset].orEmpty()
            val signature = "${target.kind.name} ${target.caption}"
            val matching = candidates.firstOrNull { it.renderer.signature == signature }
            if (matching != null) {
                // Drop stale duplicates at the same offset, keep the up-to-date chip.
                candidates.filter { it !== matching }.forEach { removeChip(it) }
                continue
            }
            candidates.forEach { removeChip(it) }
            inlayModel.addInlineElement(
                region.startOffset,
                InlayProperties().relatesToPrecedingText(true).showWhenFolded(true),
                QuarkdownRefFoldChipRenderer(editor, iconFor(target.kind), target.caption, signature)
            )
        }

        for ((offset, inlays) in byOffset) {
            if (offset !in keptOffsets) inlays.forEach { removeChip(it) }
        }
    }

    /** Drops a chip inlay, also clearing the hover state it may currently own. */
    private fun removeChip(inlay: Inlay<out QuarkdownRefFoldChipRenderer>) {
        if (inlay === hovered) clearHover()
        if (inlay.isValid) inlay.dispose()
    }

    /** Resolves the fold region to its reference target, or `null` when it is not a resolved `.ref` fold. */
    private fun refTargetOf(region: FoldRegion, text: String): QuarkdownReferenceLabelResolver.Target? {
        val rangeText = text.substring(region.startOffset, region.endOffset)
        val match = REF_PATTERN.matchEntire(rangeText) ?: return null
        return QuarkdownReferenceLabelResolver.resolve(text, match.groupValues[1].trim())
    }

    // ------------------------------------------------------------------
    // Chip interaction: hover tooltip (raw source) + click to expand
    // ------------------------------------------------------------------

    private val mouseListener = object : EditorMouseListener {
        override fun mouseClicked(event: EditorMouseEvent) {
            val inlay = chipAt(event) ?: return
            val region = regionOf(inlay) ?: return
            clearHover()
            editor.foldingModel.runBatchFoldingOperation { region.isExpanded = true }
        }

        override fun mouseExited(event: EditorMouseEvent) = clearHover()
    }

    private val motionListener = object : EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            val inlay = chipAt(event)
            if (inlay === hovered) return
            clearHover()
            hovered = inlay ?: return
            editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            val region = regionOf(inlay) ?: return
            val raw = editor.document.text.substring(region.startOffset, region.endOffset)
            tooltip = PresentationFactory(editor).showTooltip(event.mouseEvent, raw)
        }
    }

    private fun chipAt(event: EditorMouseEvent): Inlay<out QuarkdownRefFoldChipRenderer>? =
        editor.inlayModel.getElementAt(event.mouseEvent.point, QuarkdownRefFoldChipRenderer::class.java)

    /** The collapsed `.ref` fold region a chip is attached to. */
    private fun regionOf(inlay: Inlay<out QuarkdownRefFoldChipRenderer>): FoldRegion? =
        editor.foldingModel.allFoldRegions.firstOrNull {
            it.isValid && it.startOffset == inlay.offset && it.placeholderText.isEmpty()
        }

    private fun clearHover() {
        hovered = null
        tooltip?.hide()
        tooltip = null
        if (!editor.isDisposed) editor.contentComponent.cursor = Cursor.getDefaultCursor()
    }

    override fun dispose() = clearHover()

    // ------------------------------------------------------------------
    // Installation
    // ------------------------------------------------------------------

    companion object {
        /** Per-editor handle: marks an editor as already instrumented and keeps the instance alive. */
        val KEY = Key.create<QuarkdownRefFoldPreviewSynchronizer>("quarkdown.ref.fold.preview")

        /**
         * Matches a *whole* `.ref {id}` usage; group 1 is the reference id.
         *
         * This must mirror the pattern [QuarkdownFoldingBuilder] uses to create the `.ref`
         * fold regions, because [refTargetOf] matches the fold's range text entirely: if the
         * two patterns diverge, folded references would stop getting their preview chip.
         */
        private val REF_PATTERN = Regex("""\.ref\s*\{\s*([^}]+?)\s*}""", RegexOption.IGNORE_CASE)

        /**
         * Instruments [editor] if it is not instrumented yet, returning the synchronizer in
         * charge of it. The synchronizer lives as long as the editor: its listeners are
         * registered against it, so they disappear together with the editor's own listeners.
         */
        fun install(editor: Editor): QuarkdownRefFoldPreviewSynchronizer? {
            if (editor.isDisposed) return null
            editor.getUserData(KEY)?.let { return it }
            val synchronizer = QuarkdownRefFoldPreviewSynchronizer(editor)
            editor.putUserData(KEY, synchronizer)
            (editor.foldingModel as? FoldingModelEx)?.addListener(synchronizer, synchronizer)
            editor.addEditorMouseListener(synchronizer.mouseListener, synchronizer)
            editor.addEditorMouseMotionListener(synchronizer.motionListener, synchronizer)
            // Installation may happen in the middle of a fold update, so the first
            // reconciliation is deferred (the fold-processing-end callback gets there first).
            ApplicationManager.getApplication().invokeLater(synchronizer::sync)
            return synchronizer
        }

        /**
         * Instruments every editor showing [document]. Called while fold regions are built for
         * a Quarkdown document, which guarantees the chip synchronizer is in place before the
         * collapsed `.ref` previews become visible.
         */
        fun installOnEditors(project: Project, document: Document) {
            val application = ApplicationManager.getApplication()
            if (!application.isDispatchThread) {
                application.invokeLater { installOnEditors(project, document) }
                return
            }
            if (project.isDisposed) return
            for (editor in EditorFactory.getInstance().getEditors(document, project)) install(editor)
        }

        /** Maps a resolved reference kind to the icon shown inside its preview chip. */
        fun iconFor(kind: Kind): Icon = when (kind) {
            Kind.SECTION -> QuarkdownIcons.HEADING_MARKER
            Kind.FIGURE -> QuarkdownIcons.IMAGE_MARKER
            Kind.TABLE -> QuarkdownIcons.TABLE_MARKER
            Kind.CODE -> QuarkdownIcons.CODE_MARKER
            Kind.EQUATION -> QuarkdownIcons.EQUATION_MARKER
        }
    }
}
