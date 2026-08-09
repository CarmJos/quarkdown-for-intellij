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
}
