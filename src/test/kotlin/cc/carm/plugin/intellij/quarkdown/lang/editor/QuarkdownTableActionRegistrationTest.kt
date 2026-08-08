package cc.carm.plugin.intellij.quarkdown.lang.editor

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Verifies the floating-toolbar action groups are actually registered with the
 * ActionManager (they live in the <actions> section of plugin.xml). This is what makes
 * the bar click open a toolbar — a missing group made the old version silently fail.
 */
class QuarkdownTableActionRegistrationTest : BasePlatformTestCase() {

    fun `test row actions group is registered`() {
        val action = ActionManager.getInstance().getAction("Quarkdown.TableRowActions")
        assertNotNull("Quarkdown.TableRowActions must be registered", action)
        assertTrue("row actions should be an ActionGroup", action is ActionGroup)
    }

    fun `test column actions group is registered`() {
        val action = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions")
        assertNotNull("Quarkdown.TableColumnActions must be registered", action)
        assertTrue("column actions should be an ActionGroup", action is ActionGroup)
    }

    fun `test alignment group is registered`() {
        val action = ActionManager.getInstance().getAction("Quarkdown.Table.ColumnAlignmentActions")
        assertNotNull("Quarkdown.Table.ColumnAlignmentActions must be registered", action)
        assertTrue(action is ActionGroup)
    }

    fun `test row actions contain insert above`() {
        val group = ActionManager.getInstance().getAction("Quarkdown.TableRowActions") as ActionGroup
        val children = group.getChildren(null)
        assertTrue(
            "row group should contain insert-above action, got: ${children.map { it.templatePresentation.text }}",
            children.any { it.javaClass.simpleName.contains("InsertAbove") }
        )
    }
}

