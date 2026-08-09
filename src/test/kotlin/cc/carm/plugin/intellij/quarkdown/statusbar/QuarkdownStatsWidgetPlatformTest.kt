package cc.carm.plugin.intellij.quarkdown.statusbar

import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the Quarkdown word & paragraph status-bar widget is registered on the
 * platform and renders correct counts for the active editor.
 */
class QuarkdownStatsWidgetPlatformTest : BasePlatformTestCase() {

    fun `test widget factory is registered in the extension point`() {
        val factories = StatusBarWidgetFactory.EP_NAME.extensions
        assertTrue(
            "QuarkdownStatsWidgetFactory should be registered, got: ${factories.map { it.id }}",
            factories.any { it is QuarkdownStatsWidgetFactory }
        )
    }

    fun `test factory creates a widget`() {
        val factory = QuarkdownStatsWidgetFactory()
        val widget = factory.createWidget(project)
        try {
            assertEquals(QuarkdownStatsWidgetFactory.ID, widget.ID())
            assertTrue(widget is QuarkdownStatsWidget)
        } finally {
            factory.disposeWidget(widget)
        }
    }

    fun `test widget shows counts for a quarkdown document`() {
        myFixture.configureByText("test.qd", ".var {version} {v1}\n\nHello world paragraph")
        val factory = QuarkdownStatsWidgetFactory()
        val widget = factory.createWidget(project)
        try {
            val presentation = widget.getPresentation() as StatusBarWidget.TextPresentation
            val text = presentation.getText()
            System.out.println("widget text for .qd: '$text'")
            assertTrue("should include word count, got '$text'", text.contains("3 words"))
            assertTrue("should include paragraph count, got '$text'", text.contains("1 paragraphs"))
        } finally {
            factory.disposeWidget(widget)
        }
    }

    fun `test widget empty for non quarkdown file`() {
        myFixture.configureByText("plain.txt", "just some words here")
        val factory = QuarkdownStatsWidgetFactory()
        val widget = factory.createWidget(project)
        try {
            val presentation = widget.getPresentation() as StatusBarWidget.TextPresentation
            val text = presentation.getText()
            System.out.println("widget text for .txt: '$text'")
            assertEquals("", text)
        } finally {
            factory.disposeWidget(widget)
        }
    }

    fun `test widget updates after document change`() {
        myFixture.configureByText("test.qd", "one two three")
        val factory = QuarkdownStatsWidgetFactory()
        val widget = factory.createWidget(project)
        try {
            val presentation = widget.getPresentation() as StatusBarWidget.TextPresentation
            assertTrue("initial count should be 3 words", presentation.getText().contains("3 words"))

            // Modify the document (as the user typing).
            val document = myFixture.editor.document
            val writeAction = com.intellij.openapi.command.WriteCommandAction.writeCommandAction(project)
            writeAction.run<Throwable> {
                document.insertString(document.textLength, "\n\nnew paragraph here")
            }

            // Create a fresh widget which reads the current (updated) editor.
            val widget2 = factory.createWidget(project)
            try {
                val text2 = (widget2.getPresentation() as StatusBarWidget.TextPresentation).getText()
                System.out.println("widget text after edit: '$text2'")
                assertTrue("should show updated counts, got '$text2'", text2.contains("6 words"))
            } finally {
                factory.disposeWidget(widget2)
            }
        } finally {
            factory.disposeWidget(widget)
        }
    }
}
