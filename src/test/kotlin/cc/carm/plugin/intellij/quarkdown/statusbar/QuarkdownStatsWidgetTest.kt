package cc.carm.plugin.intellij.quarkdown.statusbar

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the status-bar word/paragraph counter renders text for Quarkdown documents.
 */
class QuarkdownStatsWidgetTest : BasePlatformTestCase() {

    private lateinit var widget: QuarkdownStatsWidget

    override fun setUp() {
        super.setUp()
        widget = QuarkdownStatsWidget(project)
    }

    override fun tearDown() {
        try {
            widget.dispose()
        } catch (_: Throwable) {
        }
        super.tearDown()
    }

    private fun widgetText(): String {
        val presentation = widget.getPresentation() as StatusBarWidget.TextPresentation
        return presentation.getText()
    }

    fun `test empty text for non-quarkdown file`() {
        myFixture.configureByText("plain.txt", "hello world")
        FileEditorManager.getInstance(project).openFile(myFixture.file.virtualFile, true)
        assertEquals("", widgetText())
    }

    fun `test shows word and paragraph counts for quarkdown file`() {
        myFixture.configureByText("test.qd", "# Title\n\nSome prose here.\n\n.center {}\nmore text")
        FileEditorManager.getInstance(project).openFile(myFixture.file.virtualFile, true)
        val text = widgetText()
        System.out.println("widget text: '$text'")
        assertTrue("widget should show counts, got '$text'", text.contains("words") || text.contains("paragraphs"))
    }

    fun `test empty text when no quarkdown editor selected`() {
        myFixture.configureByText("plain.txt", "hello world")
        val text = widgetText()
        System.out.println("widget text for non-qd: '$text'")
        assertTrue(
            "expected empty or no qd text, got '$text'",
            text.isEmpty() || !text.contains("words")
        )
    }

    fun `test dispose does not throw for unattached widget`() {
        // A freshly created widget with no editor/install must dispose cleanly.
        widget.dispose()
    }
}
