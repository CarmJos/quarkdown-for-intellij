package cc.carm.plugin.intellij.quarkdown.lang.table

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Verifies every floating-toolbar action has a tooltip text set in plugin.xml, so
 * hovering the icon shows its function.
 */
class QuarkdownTableTooltipTest : BasePlatformTestCase() {

    private fun actionText(actionId: String): String {
        val action = ActionManager.getInstance().getAction(actionId)
        assertTrue("action $actionId should exist", action != null)
        return action!!.templatePresentation.text ?: ""
    }

    fun `test row actions have tooltips`() {
        assertEquals("Format Table", actionText("Quarkdown.Table.FormatTable"))
        assertEquals("Move Row Up", actionText("Quarkdown.Table.SwapRows.SwapWithAbove"))
        assertEquals("Move Row Down", actionText("Quarkdown.Table.SwapRows.SwapWithBelow"))
        assertEquals("Insert Row Above", actionText("Quarkdown.Table.InsertRow.InsertAbove"))
        assertEquals("Insert Row Below", actionText("Quarkdown.Table.InsertRow.InsertBelow"))
        assertEquals("Select Row", actionText("Quarkdown.Table.SelectRow"))
        assertEquals("Remove Row", actionText("Quarkdown.Table.RemoveCurrentRow"))
    }

    fun `test column actions have tooltips`() {
        assertEquals("Format Table", actionText("Quarkdown.Table.FormatTable"))
        assertEquals("Move Column Left", actionText("Quarkdown.Table.SwapColumns.SwapWithLeftColumn"))
        assertEquals("Move Column Right", actionText("Quarkdown.Table.SwapColumns.SwapWithRightColumn"))
        assertEquals("Insert Column Left", actionText("Quarkdown.Table.InsertTableColumn.InsertBefore"))
        assertEquals("Insert Column Right", actionText("Quarkdown.Table.InsertTableColumn.InsertAfter"))
        assertEquals("Select Column", actionText("Quarkdown.Table.SelectCurrentColumn"))
    }

    fun `test alignment actions have tooltips`() {
        assertEquals("Align Column Left", actionText("Quarkdown.Table.SetColumnAlignment.Left"))
        assertEquals("Align Column Center", actionText("Quarkdown.Table.SetColumnAlignment.Center"))
        assertEquals("Align Column Right", actionText("Quarkdown.Table.SetColumnAlignment.Right"))
    }

    fun `test every table action has non-empty tooltip`() {
        val ids = listOf(
            "Quarkdown.Table.FormatTable",
            "Quarkdown.Table.SwapRows.SwapWithAbove",
            "Quarkdown.Table.SwapRows.SwapWithBelow",
            "Quarkdown.Table.InsertRow.InsertAbove",
            "Quarkdown.Table.InsertRow.InsertBelow",
            "Quarkdown.Table.SelectRow",
            "Quarkdown.Table.RemoveCurrentRow",
            "Quarkdown.Table.SwapColumns.SwapWithLeftColumn",
            "Quarkdown.Table.SwapColumns.SwapWithRightColumn",
            "Quarkdown.Table.InsertTableColumn.InsertBefore",
            "Quarkdown.Table.InsertTableColumn.InsertAfter",
            "Quarkdown.Table.SelectCurrentColumn",
            "Quarkdown.Table.SetColumnAlignment.Left",
            "Quarkdown.Table.SetColumnAlignment.Center",
            "Quarkdown.Table.SetColumnAlignment.Right"
        )
        for (id in ids) {
            val text = actionText(id)
            assertFalse("$id should have a tooltip text", text.isBlank())
        }
    }
}
