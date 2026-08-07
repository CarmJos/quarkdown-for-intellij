package cc.carm.plugin.intellij.quarkdown.statusbar

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownStatsParser
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownStatsParser.QuarkdownStats
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
import com.intellij.util.messages.MessageBusConnection
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

    override fun getDisplayName(): String = "Quarkdown Word & Paragraph Count"

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
    private var cachedStats = QuarkdownStats(0, 0)

    private var listenedEditor: Editor? = null

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
            return "${cachedStats.wordCount} words \u00b7 ${cachedStats.paragraphCount} paragraphs"
        }

        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

        override fun getTooltipText(): String =
            "Word & paragraph count of the current Quarkdown document (function calls excluded)"
    }

    override fun ID(): String = QuarkdownStatsWidgetFactory.ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = presentation

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        // Attach the document listener immediately so typing updates the counts even
        // before the first editor-selection change.
        reattachDocumentListener()
    }

    @Suppress("DEPRECATION")
    override fun registerCustomListeners(connection: MessageBusConnection) {
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    reattachDocumentListener()
                    cachedModCount = -1L
                    myStatusBar?.updateWidget(ID())
                }
            }
        )
    }

    private fun reattachDocumentListener() {
        val editor = getEditor()
        if (editor === listenedEditor) return
        listenedEditor?.document?.removeDocumentListener(documentListener)
        listenedEditor = editor
        editor?.document?.addDocumentListener(documentListener)
    }

    override fun dispose() {
        listenedEditor?.document?.removeDocumentListener(documentListener)
        listenedEditor = null
        super.dispose()
    }
}
