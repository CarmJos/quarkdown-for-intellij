package cc.carm.plugin.intellij.quarkdown.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * Public-API replacement for the internal `ToolbarUtils.createImmediatelyUpdatedToolbar`.
 *
 * An `ActionToolbar` only creates its buttons once it is attached to a parent container AND
 * is actually "showing" (see `ActionToolbarImpl.updateActionsAsync` and the `ToolbarUpdater`
 * update runnable). Toolbars used inside floating hints are not shown yet when they are
 * built, so the official internal helper temporarily marked the component as showing via
 * `ComponentUtil.markAsShowing` and then ran the forced update.
 *
 * Here the same effect is achieved with public APIs only:
 *  - the toolbar's container is briefly attached to the [JLayeredPane] of the window that
 *    [anchor] belongs to (making it displayable / showing while the window is visible), and
 *    the public [ActionToolbar.updateActionsAsync] is called, which updates the actions
 *    synchronously whenever the component is showing;
 *  - if that does not produce visible actions (e.g. the window is not currently visible), the
 *    container is instead attached to a temporary off-screen [JWindow] that is shown for the
 *    duration of the update, which always makes it "showing".
 *
 * Either way the container is detached again before anything is painted, so no flicker is
 * visible and the hint is built with an already-populated toolbar.
 */
object QuarkdownActionToolbarUtils {

    fun createToolbar(
        place: String,
        group: ActionGroup,
        horizontal: Boolean,
        targetComponent: JComponent?,
    ): ActionToolbar {
        val toolbar = ActionManager.getInstance().createActionToolbar(place, group, horizontal)
        toolbar.setReservePlaceAutoPopupIcon(false)
        if (targetComponent != null) toolbar.targetComponent = targetComponent
        return toolbar
    }

    /**
     * Populates [toolbar] immediately even though it is not displayed yet.
     *
     * [anchor] must be a component of the window that will eventually display the toolbar
     * (normally the editor content component).
     */
    fun populateImmediately(toolbar: ActionToolbar, anchor: JComponent) {
        val holder = toolbar.component.parent
        val layeredPane = anchor.rootPane?.layeredPane
        if (layeredPane != null && holder != null) {
            layeredPane.add(holder, JLayeredPane.DEFAULT_LAYER)
            // Give the toolbar a real area while it is attached so layout runs normally.
            holder.setBounds(0, 0, layeredPane.width, layeredPane.height)
            try {
                toolbar.updateActionsAsync()
            } finally {
                layeredPane.remove(holder)
                layeredPane.revalidate()
                layeredPane.repaint()
            }
            if (toolbar.hasVisibleActions()) return
        }
        populateViaWindow(toolbar, anchor)
    }

    /** Guaranteed "showing" fallback: attach to an off-screen visible window and update. */
    private fun populateViaWindow(toolbar: ActionToolbar, anchor: JComponent) {
        val holder = toolbar.component.parent ?: toolbar.component
        val owner = SwingUtilities.getWindowAncestor(anchor)
        val window = JWindow(owner)
        try {
            window.contentPane.add(holder)
            window.pack()
            window.location = Point(-10000, -10000)
            window.isVisible = true
            toolbar.updateActionsAsync()
        } finally {
            window.isVisible = false
            window.dispose()
        }
    }
}
