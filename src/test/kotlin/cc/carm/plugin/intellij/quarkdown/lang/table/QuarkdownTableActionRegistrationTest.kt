package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.action.table.FormatTableAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    fun `test format table is the first button in row actions`() {
        val group = ActionManager.getInstance().getAction("Quarkdown.TableRowActions") as ActionGroup
        val first = group.getChildren(null).firstNotNullOfOrNull { it }
        assertTrue(
            "row group should start with FormatTableAction, got: $first",
            first is FormatTableAction
        )
    }

    fun `test format table is the first button in column actions`() {
        val group = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions") as ActionGroup
        val first = group.getChildren(null).firstNotNullOfOrNull { it }
        assertTrue(
            "column group should start with FormatTableAction, got: $first",
            first is FormatTableAction
        )
    }
}
