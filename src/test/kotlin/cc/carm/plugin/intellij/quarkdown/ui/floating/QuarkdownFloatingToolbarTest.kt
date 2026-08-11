package cc.carm.plugin.intellij.quarkdown.ui.floating

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the floating formatting toolbar group is registered with the ActionManager and
 * contains the expected formatting actions (mirrors the Markdown plugin).
 */
class QuarkdownFloatingToolbarTest : BasePlatformTestCase() {

    fun `test floating toolbar group is registered`() {
        val action = ActionManager.getInstance().getAction("Quarkdown.Toolbar.Floating")
        assertNotNull("Quarkdown.Toolbar.Floating must be registered", action)
        assertTrue(action is ActionGroup)
    }

    fun `test floating toolbar contains formatting actions`() {
        val group = ActionManager.getInstance().getAction("Quarkdown.Toolbar.Floating") as ActionGroup
        val children = group.getChildren(null)
        val ids = children.mapNotNull { ActionManager.getInstance().getId(it) }
        assertTrue(
            "should contain Bold, got: $ids",
            ids.any { it == "Quarkdown.Floating.Bold" }
        )
        assertTrue(
            "should contain Italic, got: $ids",
            ids.any { it == "Quarkdown.Floating.Italic" }
        )
        assertTrue(
            "should contain Strikethrough, got: $ids",
            ids.any { it == "Quarkdown.Floating.Strikethrough" }
        )
        assertTrue(
            "should contain Inline Code, got: $ids",
            ids.any { it == "Quarkdown.Floating.Code" }
        )
        assertTrue(
            "should contain Link, got: $ids",
            ids.any { it == "Quarkdown.Floating.Link" }
        )
    }

    fun `test toolbar has icons`() {
        val group = ActionManager.getInstance().getAction("Quarkdown.Toolbar.Floating") as ActionGroup
        val children = group.getChildren(null).filter { it !is com.intellij.openapi.actionSystem.Separator }
        val withIcons = children.filter { it.templatePresentation.icon != null }
        assertTrue(
            "all toolbar buttons should have icons, got ${withIcons.size}/${children.size}",
            withIcons.size == children.size
        )
    }

    fun `test toolbar populates visible actions when not yet shown`() {
        myFixture.configureByText("populate.qd", "Some selected text.")

        val group = ActionManager.getInstance().getAction("Quarkdown.Toolbar.Floating") as ActionGroup
        val toolbar = cc.carm.plugin.intellij.quarkdown.ui.QuarkdownActionToolbarUtils.createToolbar(
            com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_FLOATING_TOOLBAR,
            group,
            true,
            myFixture.editor.contentComponent
        )
        toolbar.setReservePlaceAutoPopupIcon(false)

        // Mirror the real usage: attach the toolbar to a container that becomes the hint
        // content, then populate it before the container is displayed anywhere.
        val panel = com.intellij.util.ui.components.BorderLayoutPanel().apply {
            addToCenter(toolbar.component)
        }
        cc.carm.plugin.intellij.quarkdown.ui.QuarkdownActionToolbarUtils.populateImmediately(
            toolbar, myFixture.editor.contentComponent
        )

        assertTrue(
            "toolbar should have visible actions after populateImmediately, " +
                    "component children=${toolbar.component.componentCount}",
            toolbar.hasVisibleActions()
        )
        assertTrue(
            "toolbar component should contain action buttons, children=${toolbar.component.componentCount}",
            toolbar.component.componentCount > 0
        )
        assertEquals(
            "hint content should be attached to the toolbar",
            toolbar.component.parent,
            panel
        )
    }
}
