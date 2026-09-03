package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.action.table.RemoveCurrentColumnAction
import cc.carm.plugin.intellij.quarkdown.action.table.TableActionKeys
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import cc.carm.plugin.intellij.quarkdown.ui.QuarkdownActionToolbarUtils
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Verifies the floating toolbars are populated before the hint is shown, mirroring the
 * platform's internal `ToolbarUtils.createImmediatelyUpdatedToolbar` behaviour: the toolbar
 * must have visible actions by the time the `populateImmediately` callback fires, even though
 * the toolbar update runs asynchronously on a background thread for `ActionUpdateThread.BGT`
 * actions.
 */
class QuarkdownToolbarPopulationTest : BasePlatformTestCase() {

    fun `test formatting toolbar is populated when callback fires`() {
        myFixture.configureByText("populate.qd", "Some selected text.")

        val group = ActionManager.getInstance().getAction("Quarkdown.Toolbar.Floating") as ActionGroup
        val toolbar = QuarkdownActionToolbarUtils.createToolbar(
            ActionPlaces.EDITOR_FLOATING_TOOLBAR, group, true, myFixture.editor.contentComponent
        )
        com.intellij.util.ui.components.BorderLayoutPanel().apply {
            addToCenter(toolbar.component)
        }

        val ready = awaitPopulate(toolbar)
        assertTrue("formatting toolbar should have visible actions", ready.hasVisibleActions())
        assertTrue(
            "formatting toolbar should contain buttons, children=${ready.component.componentCount}",
            ready.component.componentCount > 0
        )
    }

    fun `test table row toolbar population completes`() {
        myFixture.configureByText("table.qd", "| A | B |\n|---|---|\n| a | b |\n")

        val block = QuarkdownTableModificationUtils.findTableBlocks(myFixture.file.text).first()
        val targetComponent = QuarkdownActionToolbarUtils.createTargetComponent(myFixture.editor) { sink ->
            TableActionKeys.putRowSnapshot(sink, block, 0)
        }
        val group = ActionManager.getInstance().getAction("Quarkdown.TableRowActions") as ActionGroup
        val toolbar = QuarkdownActionToolbarUtils.createToolbar(
            "QuarkdownTableInlayToolbar", group, true, targetComponent
        )
        com.intellij.util.ui.components.BorderLayoutPanel().apply {
            addToCenter(toolbar.component)
        }

        // The population callback must fire (the update must not hang); whether the actions
        // are visible depends on the full editor data context, which is not available for the
        // fixture editor (it is not displayed), so we only assert completion here.
        val ready = awaitPopulate(toolbar)
        assertNotNull("population callback must fire for the table row toolbar", ready)
    }

    fun `test table column toolbar population completes`() {
        myFixture.configureByText("table.qd", "| A | B |\n|---|---|\n| a | b |\n")

        val block = QuarkdownTableModificationUtils.findTableBlocks(myFixture.file.text).first()
        val targetComponent = QuarkdownActionToolbarUtils.createTargetComponent(myFixture.editor) { sink ->
            TableActionKeys.putColumnSnapshot(sink, block, 0)
        }
        val group = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions") as ActionGroup
        val toolbar = QuarkdownActionToolbarUtils.createToolbar(
            "QuarkdownTableInlayToolbar", group, true, targetComponent
        )
        com.intellij.util.ui.components.BorderLayoutPanel().apply {
            addToCenter(toolbar.component)
        }

        val ready = awaitPopulate(toolbar)
        assertNotNull("population callback must fire for the table column toolbar", ready)
    }

    fun `test remove column action is registered in the column toolbar`() {
        val action = ActionManager.getInstance().getAction("Quarkdown.Table.RemoveCurrentColumn")
        assertNotNull("Quarkdown.Table.RemoveCurrentColumn must be registered", action)
        assertTrue(
            "the registered action must be RemoveCurrentColumnAction",
            action is RemoveCurrentColumnAction
        )

        val group = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions") as ActionGroup
        val children = group.getChildren(null)
        assertTrue(
            "the column toolbar group must include the remove-column action",
            children.any { it is RemoveCurrentColumnAction }
        )
    }

    fun `test delete column removes the column from the document`() {
        // In-memory document: avoids the OS file-indexer/antivirus temp-dir lock that a
        // file-backed fixture write can trigger on teardown (same rationale as the
        // table-editor live-write tests).
        val document = com.intellij.openapi.editor.impl.DocumentImpl("| A | B | C |\n|---|---|---|\n| 1 | 2 | 3 |\n")
        val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence).first()

        QuarkdownTableModificationUtils.deleteColumn(project, document, block, 1)

        val text = document.text
        assertFalse("column B must be gone, got: $text", text.contains("B"))
        assertFalse("column B's cell must be gone, got: $text", text.contains("2"))
        assertTrue("column A must remain, got: $text", text.contains("A"))
        assertTrue("column C must remain, got: $text", text.contains("C"))
        assertTrue("cell 1 must remain, got: $text", text.contains("1"))
        assertTrue("cell 3 must remain, got: $text", text.contains("3"))
    }

    fun `test target component exposes editor data`() {
        myFixture.configureByText("table.qd", "| A | B |\n|---|---|\n| a | b |\n")

        val block = QuarkdownTableModificationUtils.findTableBlocks(myFixture.file.text).first()
        val targetComponent = QuarkdownActionToolbarUtils.createTargetComponent(myFixture.editor) { sink ->
            TableActionKeys.putRowSnapshot(sink, block, 0)
        }
        val dataContext = DataManager.getInstance().getDataContext(targetComponent)

        // The target component must expose the editor's own data context (EDITOR/PSI_FILE),
        // otherwise the table actions cannot resolve the clicked row/column.
        assertNotNull("EDITOR must be available through the target component", dataContext.getData(CommonDataKeys.EDITOR))
        assertNotNull("PSI_FILE must be available through the target component", dataContext.getData(CommonDataKeys.PSI_FILE))
    }

    /**
     * Runs [QuarkdownActionToolbarUtils.populateImmediately] and returns the populated toolbar.
     *
     * The platform's toolbar update runs on a background thread and resumes on the EDT, so the
     * test must keep pumping the EDT while waiting instead of blocking it with a latch.
     */
    private fun awaitPopulate(toolbar: ActionToolbar): ActionToolbar {
        val result = CompletableFuture<ActionToolbar>()
        QuarkdownActionToolbarUtils.populateImmediately(toolbar, myFixture.editor.contentComponent) { ready ->
            result.complete(ready)
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && !result.isDone) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(50)
        }
        assertTrue("populateImmediately callback never fired", result.isDone)
        return result.get(5, TimeUnit.SECONDS)
    }
}
