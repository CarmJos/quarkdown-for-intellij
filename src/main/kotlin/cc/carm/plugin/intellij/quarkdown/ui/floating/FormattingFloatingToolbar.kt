package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.ui.QuarkdownActionToolbarUtils
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AWTEvent
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

/**
 * Floating formatting toolbar for Quarkdown documents, shown when the user selects text.
 *
 * This is a self-contained re-implementation of the platform's internal
 * `com.intellij.openapi.actionSystem.impl.FloatingToolbar` using **public** APIs only:
 *  - [QuarkdownActionToolbarUtils] builds and populates the toolbar the same way the official
 *    `ToolbarUtils.createImmediatelyUpdatedToolbar` does (it waits for the asynchronous
 *    update of `ActionUpdateThread.BGT` actions before returning);
 *  - [HintManagerImpl.showEditorHint] / [getHintPosition] position it above the selection;
 *  - editor listeners (selection / mouse / document) drive show & hide.
 *
 * Behaviour mirrors the original (and the IntelliJ Markdown plugin's MarkdownFloatingToolbar):
 * the toolbar appears above the selection and hides when the selection is cleared, the caret
 * leaves the selection, a document change happens, or Escape is pressed.
 */
class FormattingFloatingToolbar(
    private val editor: Editor,
    private val coroutineScope: CoroutineScope
) : Disposable {

    private var hint: LightweightHint? = null
    private var buttonSize = 0
    private var lastSelection: String? = null
    private var preventHintFromShowing = false

    private enum class HintRequest { SHOW, HIDE }

    private val hintRequests =
        MutableSharedFlow<HintRequest>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val hintCollectorJob: Job = coroutineScope.launch {
        hintRequests.debounce(50.milliseconds).collectLatest { request ->
            withContext(Dispatchers.Main) {
                when (request) {
                    HintRequest.SHOW -> showIfHidden()
                    HintRequest.HIDE -> hide()
                }
            }
        }
    }

    init {
        editor.addEditorMouseListener(MouseListener(), this)
        editor.addEditorMouseMotionListener(MouseMotionListener(), this)
        editor.contentComponent.addKeyListener(KeyboardListener(), this)
        editor.selectionModel.addSelectionListener(EditorSelectionListener(), this)
        editor.document.addDocumentListener(DocumentChangeListener(), this)
    }

    fun createActionGroup(): ActionGroup? {
        return CustomActionsSchema.getInstance().getCorrectedAction("Quarkdown.Toolbar.Floating") as? ActionGroup
    }

    /**
     * True when the selection is inside content that must not get inline formatting:
     *  - fenced code blocks / inline code spans,
     *  - image paths `![alt](path)` or link destinations,
     *  - Quarkdown function-call arguments (e.g. `.fullwidth { … }`), and
     *  - front matter / HTML blocks.
     */
    fun hasIgnoredParent(element: PsiElement): Boolean {
        val file = element.containingFile ?: return true
        if (file.fileType !is QuarkdownFileType) return true

        val type = element.node?.elementType
        if (type in IGNORED_TYPES) return true

        // Quarkdown's PSI is flat, so additionally scan the document around the
        // element for non-prose context (function calls, image/link destinations…).
        if (editor.isDisposed) return true
        val offset = element.textRange.startOffset
        return isNonProseContext(editor.document.immutableCharSequence, offset)
    }

    fun isEnabled(): Boolean = true

    fun isShown(): Boolean = hint?.isVisible == true

    fun scheduleShow() {
        if (isEnabled() && !preventHintFromShowing) {
            check(hintRequests.tryEmit(HintRequest.SHOW))
        }
    }

    fun scheduleHide() {
        check(hintRequests.tryEmit(HintRequest.HIDE))
    }

    fun show(callback: Runnable) {
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                showIfHidden()
                callback.run()
            }
        }
    }

    private fun hide() {
        hint?.hide()
        hint = null
    }

    private suspend fun showIfHidden() {
        preventHintFromShowing = true
        if (isShown() || !isEnabled()) return
        // Mirrors the official FloatingToolbar: PSI access is done under a read action.
        val canBeShown = com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
            canBeShownAtCurrentSelection()
        }
        if (!canBeShown) return
        val newHint = createHint() ?: return
        showHint(newHint)
        newHint.addHintListener {
            hint = null
        }
        hint = newHint
    }

    /**
     * Builds and populates the toolbar, suspending until the (possibly asynchronous) update
     * finished — mirroring the official `FloatingToolbar.createHint`, which only returns a
     * hint once the toolbar has visible actions. Returns `null` when the toolbar ended up
     * empty (e.g. all actions are invisible).
     */
    private suspend fun createHint(): LightweightHint? {
        val component = BorderLayoutPanel()
        val toolbar = createUpdatedActionToolbar(editor.contentComponent, component)
        if (!toolbar.hasVisibleActions()) return null
        return LightweightHint(component).apply {
            setForceShowAsPopup(true)
        }
    }

    private suspend fun createUpdatedActionToolbar(
        targetComponent: JComponent,
        parent: BorderLayoutPanel
    ): ActionToolbar {
        val group = createActionGroup() ?: DefaultActionGroup()
        // Mirrors the official ToolbarUtils.createImmediatelyUpdatedToolbar (public APIs):
        // the toolbar is populated before the hint is built, identical to the platform's own
        // FloatingToolbar behaviour. populateImmediately waits for the toolbar update to
        // complete (see QuarkdownActionToolbarUtils), so the buttons exist before the hint is
        // shown.
        val toolbar = QuarkdownActionToolbarUtils.createToolbar(
            ActionPlaces.EDITOR_FLOATING_TOOLBAR, group, true, targetComponent
        )
        parent.addToCenter(toolbar.component)
        QuarkdownActionToolbarUtils.populateImmediately(toolbar, editor.contentComponent)
        buttonSize = toolbar.maxButtonHeight
        return toolbar
    }

    private fun showHint(newHint: LightweightHint) {
        HintManagerImpl.getInstanceImpl().showEditorHint(
            newHint,
            editor,
            getHintPosition(newHint),
            HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
            0,
            true
        )
    }

    private fun getHintPosition(newHint: LightweightHint): Point {
        val hintPos = HintManagerImpl.getInstanceImpl().getHintPosition(newHint, editor, HintManager.DEFAULT)
        // because of `hint.setForceShowAsPopup(true)`, HintManager.ABOVE does not place the hint above
        // the hint remains on the line, so we need to move it up ourselves
        val verticalGap = 2
        val dy = -(newHint.component.preferredSize.height + verticalGap)
        val dx = buttonSize * -2
        hintPos.translate(dx, dy)
        return hintPos
    }

    private fun canBeShownAtCurrentSelection(): Boolean {
        if (!isEnabled()) return false
        val project = editor.project ?: return false
        val document = editor.document
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return false
        if (!PsiDocumentManager.getInstance(project).isCommitted(document)) return false
        val selectionModel = editor.selectionModel
        val elementAtStart = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionStart)
        val elementAtEnd = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionEnd)
        return !(hasIgnoredParent(elementAtStart) || hasIgnoredParent(elementAtEnd))
    }

    private fun updateLocationIfShown() {
        hint?.let(::showHint)
    }

    private fun updateOnProbablyChangedSelection(onSelectionChanged: (String) -> Unit) {
        val newSelection = editor.selectionModel.selectedText
        when (newSelection) {
            null -> scheduleHide()
            lastSelection -> Unit
            else -> onSelectionChanged(newSelection)
        }
        lastSelection = newSelection
    }

    override fun dispose() {
        hintCollectorJob.cancel()
        hide()
        coroutineScope.cancel()
    }

    private inner class MouseListener : EditorMouseListener {
        override fun mouseReleased(event: EditorMouseEvent) {
            updateOnProbablyChangedSelection {
                if (isShown()) {
                    updateLocationIfShown()
                } else {
                    scheduleShow()
                }
            }
        }
    }

    private inner class KeyboardListener : KeyAdapter() {
        override fun keyReleased(event: KeyEvent) {
            super.keyReleased(event)
            if (event.source != editor.contentComponent) return
            updateOnProbablyChangedSelection {
                scheduleHide()
            }
        }
    }

    private inner class MouseMotionListener : EditorMouseMotionListener {
        override fun mouseMoved(event: EditorMouseEvent) {
            val visualPosition = event.visualPosition
            val hoverSelected = editor.caretModel.allCarets.any { caret ->
                val beforeSelectionEnd = caret.selectionEndPosition.after(visualPosition)
                val afterSelectionStart = visualPosition.after(caret.selectionStartPosition)
                beforeSelectionEnd && afterSelectionStart
            }
            if (hoverSelected) {
                scheduleShow()
            } else if (!isShown()) {
                preventHintFromShowing = false
            }
        }
    }

    private inner class EditorSelectionListener : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            preventHintFromShowing = false
            if (isIgnoredEvent(IdeEventQueue.getInstance().trueCurrentEvent)) {
                preventHintFromShowing = true
            }
        }

        private fun isIgnoredEvent(event: AWTEvent): Boolean {
            return (event as? MouseEvent)?.clickCount == 2
        }
    }

    private inner class DocumentChangeListener : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            preventHintFromShowing = false
            scheduleHide()
        }
    }

    private fun JComponent.addKeyListener(listener: KeyAdapter, parentDisposable: Disposable) {
        addKeyListener(listener)
        Disposer.register(parentDisposable) {
            removeKeyListener(listener)
        }
    }

    companion object {
        /** Token types whose content must never get inline styling from the toolbar. */
        private val IGNORED_TYPES = setOf(
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_START,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_END,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_LANGUAGE,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.CODE_MARKER,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.CODE_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.IMAGE_PREFIX,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.LINK_URL,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.LINK_TITLE,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_DOT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_NAME,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_PARAMS,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FRONT_MATTER_DELIMITER,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FRONT_MATTER_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_TAG,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_COMMENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_COMMENT_CONTENT
        )

        /**
         * Scans the line around [offset] to decide whether the caret/selection sits in
         * non-prose content: a Quarkdown function call (including its name, positional
         * and named arguments such as `margin:{0}`), an image path `![alt](path)`, a
         * link destination `[…] (…)` or an inline code span.
         */
        internal fun isNonProseContext(text: CharSequence, offset: Int): Boolean {
            val lineStart = text.lastIndexOf('\n', offset - 1) + 1
            val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            val line = text.subSequence(lineStart, lineEnd).toString()

            // Inline code span backticks.
            if (line.contains('`')) return true

            // Inside a Quarkdown function call (name, positional/named arguments).
            if (isInFunctionCall(text, offset)) return true

            // Image syntax `![alt](path)` or link `[text](url)`: caret inside `(…)`.
            if (line.contains('!') && line.contains("](")) return true
            if (line.contains("](")) {
                val parenOpen = line.indexOf("](")
                val parenClose = line.indexOf(')', parenOpen + 2)
                val caretInLine = offset - lineStart
                if (parenOpen >= 0 && parenClose < 0) return true          // unfinished destination
                if (parenOpen >= 0 && caretInLine > parenOpen + 1 && (parenClose < 0 || caretInLine < parenClose)) return true
            }

            return false
        }

        /**
         * True when [offset] lies inside a Quarkdown function call: from its `.name`
         * through all arguments (positional `{…}` and named `name:{…}`). Uses the real
         * [QuarkdownCallParser] so every call form is covered (e.g. `.resetpagenumber`,
         * `.pageformat pages:{..1} margin:{0}`).
         */
        private fun isInFunctionCall(text: CharSequence, offset: Int): Boolean {
            val source = text.toString()
            val start = QuarkdownCallParser.findCallStart(source, offset)
            if (start < 0) return false
            val call = QuarkdownCallParser.parseCall(source, start) ?: return false
            return offset >= call.start && offset <= call.end
        }
    }
}
