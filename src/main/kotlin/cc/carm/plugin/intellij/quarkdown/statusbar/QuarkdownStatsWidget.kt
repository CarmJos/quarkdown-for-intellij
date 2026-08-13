package cc.carm.plugin.intellij.quarkdown.statusbar

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.statusbar.QuarkdownStatsParser.Stats
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import java.awt.Component

/**
 * Status-bar factory registering the Quarkdown word & paragraph counter.
 *
 * The widget is shown for every Quarkdown editor and reports the document's word and
 * paragraph counts, excluding Quarkdown function calls (`.var`, `.read`, `.center`,
 * `.container`, …), their arguments and indented bodies, and fenced code blocks.
 */
class QuarkdownStatsWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ID

    override fun getDisplayName(): String =
        QuarkdownBundle.message("quarkdown.statusbar.stats.display.name")

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = QuarkdownStatsWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    companion object {
        const val ID = "QuarkdownStats"
    }
}

/**
 * Renders `N words · M paragraphs` in the status bar for the currently focused Quarkdown
 * editor. The text is recomputed lazily whenever the active document changes.
 */
@Suppress("DEPRECATION")
class QuarkdownStatsWidget(project: Project) : EditorBasedWidget(project) {

    private var cachedModCount = -1L
    private var cachedStats = Stats(0, 0)

    private var listenedEditor: Editor? = null

    // Documents the [documentListener] is currently attached to. The listener lifecycle is
    // managed manually (plain addDocumentListener, no parent disposable) so switching editors
    // or disposing the widget never removes the listener twice - a double removal makes
    // DocumentImpl log "Can't remove document listener".
    private val attachedDocuments = mutableSetOf<Document>()

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            // Invalidate the cache; the next getText() recomputes the counts.
            cachedModCount = -1L
            myStatusBar?.updateWidget(ID())
        }
    }

    private val presentation = object : StatusBarWidget.TextPresentation {
        override fun getText(): String {
            val editor = getEditor() ?: return ""
            if (editor.isDisposed) return ""
            val virtualFile = editor.virtualFile ?: return ""
            if (virtualFile.fileType != QuarkdownFileType.INSTANCE) return ""

            val document = editor.document
            if (document.modificationStamp != cachedModCount) {
                cachedStats = QuarkdownStatsParser.computeStats(document.text)
                cachedModCount = document.modificationStamp
            }
            return QuarkdownBundle.message(
                "quarkdown.statusbar.stats.text",
                cachedStats.wordCount,
                cachedStats.paragraphCount,
                cachedStats.cjkCharCount
            )
        }

        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

        override fun getTooltipText(): String =
            QuarkdownBundle.message("quarkdown.statusbar.stats.tooltip")
    }

    override fun ID(): String = QuarkdownStatsWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = presentation

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        // Subscribe to editor-selection changes so the counts follow the active editor.
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    reattachDocumentListener()
                    cachedModCount = -1L
                    myStatusBar?.updateWidget(ID())
                }
            }
        )
        // Attach the document listener immediately so typing updates the counts even
        // before the first editor-selection change.
        reattachDocumentListener()
        // The 2025.2+ status bar only re-renders widget text when updateWidget() is
        // invoked. Without an explicit refresh here, the initial value stays empty until
        // the next selection/document change - which may never arrive when a .qd file is
        // already the active editor at startup, hiding the counts until the status bar is
        // toggled. Request the first render explicitly.
        cachedModCount = -1L
        myStatusBar?.updateWidget(ID())
    }

    private fun reattachDocumentListener() {
        val editor = getEditor()
        if (editor === listenedEditor) return
        removeListenerFromAllDocuments()
        listenedEditor = editor
        editor?.takeIf { !it.isDisposed }?.let { e ->
            e.document.addDocumentListener(documentListener)
            attachedDocuments.add(e.document)
        }
    }

    private fun removeListenerFromAllDocuments() {
        for (document in attachedDocuments) {
            try {
                document.removeDocumentListener(documentListener)
            } catch (_: Throwable) {
                // The document was disposed in the meantime, which already dropped its listeners.
            }
        }
        attachedDocuments.clear()
    }

    override fun dispose() {
        removeListenerFromAllDocuments()
        listenedEditor = null
        super.dispose()
    }
}
