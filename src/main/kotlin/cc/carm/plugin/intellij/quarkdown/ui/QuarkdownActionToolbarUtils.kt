package cc.carm.plugin.intellij.quarkdown.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

/**
 * Public-API replacement for the internal `ToolbarUtils.createImmediatelyUpdatedToolbar`.
 *
 * An `ActionToolbar` only creates its buttons once it is attached to a parent container AND
 * is actually "showing" (see `ActionToolbarImpl.updateActionsAsync` and the `ToolbarUpdater`
 * update runnable). Toolbars used inside floating hints are not shown yet when they are
 * built, so the official internal helper temporarily marked the component as showing via
 * `ComponentUtil.markAsShowing` and then ran the forced update.
 *
 * Here the same effect is achieved with public APIs only: the toolbar's container is attached
 * to a temporary off-screen [JWindow] that is shown for the duration of the update, which
 * always makes it "showing". Because the actions used by floating toolbars typically declare
 * `ActionUpdateThread.BGT`, the platform's update itself runs asynchronously (it is dispatched
 * to a background thread and the toolbar is populated when it completes). Both
 * [populateImmediately] variants therefore wait for the update to finish before invoking the
 * caller — this is what guarantees the hint is shown with an already-populated toolbar
 * (identical to the platform's own `FloatingToolbar.createHint`, which suspends until the
 * toolbar has been updated).
 *
 * Either way the container is detached again before anything is painted, so no flicker is
 * visible.
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
     * Public-API replacement for the internal `ToolbarUtils.createTargetComponent`: a component
     * that provides [provider]'s data to the toolbar's actions while also exposing the editor's
     * own data context (`EDITOR`, `PSI_FILE`, …). It does so by reporting the editor's content
     * component as its parent — the exact same trick the platform's `ToolbarUtils$MyComponent`
     * uses, so [com.intellij.ide.DataManager.getDataContext] walks up the editor component tree
     * when building the toolbar's data context.
     */
    fun createTargetComponent(editor: Editor, provider: UiDataProvider): JComponent =
        object : JComponent(), UiDataProvider {
            override fun getParent(): Container = editor.contentComponent
            override fun isShowing(): Boolean = true
            override fun uiDataSnapshot(sink: DataSink) {
                // `UiDataProvider.uiDataSnapshot` is @ApiStatus.OverrideOnly, so it must not be
                // invoked directly; use the companion utility instead (exactly like the
                // platform's own ToolbarUtils.MyComponent does).
                DataSink.uiDataSnapshot(sink, provider)
            }
        }

    /**
     * Populates [toolbar] and invokes [onReady] once the toolbar has finished its (possibly
     * asynchronous) update. [anchor] must be a component of the window that will eventually
     * display the toolbar (normally the editor content component).
     */
    fun populateImmediately(toolbar: ActionToolbar, anchor: JComponent, onReady: (ActionToolbar) -> Unit) {
        if (GraphicsEnvironment.isHeadless()) {
            // No display is available (e.g. CI test runner), so no window can be created.
            // The platform's update runs synchronously in unit-test mode; for robustness wait
            // for the returned future otherwise.
            val future = toolbar.updateActionsAsync()
            if (toolbar.hasVisibleActions()) {
                onReady(toolbar)
            } else {
                onFutureDone(future) { SwingUtilities.invokeLater { onReady(toolbar) } }
            }
            return
        }
        populateViaWindow(toolbar, anchor, onReady)
    }

    /**
     * Suspending variant of [populateImmediately]: returns once the toolbar has finished its
     * update. Safe to call on the EDT; cancellation (e.g. from a `collectLatest` collector)
     * disposes the temporary window and aborts the wait.
     */
    suspend fun populateImmediately(toolbar: ActionToolbar, anchor: JComponent) {
        if (GraphicsEnvironment.isHeadless()) {
            val future = toolbar.updateActionsAsync()
            if (!toolbar.hasVisibleActions()) awaitFuture(future)
            return
        }
        val (window, holder) = attachToShowingWindow(toolbar, anchor)
        try {
            val future = toolbar.updateActionsAsync()
            if (!toolbar.hasVisibleActions()) awaitFuture(future)
        } finally {
            detach(window, holder)
        }
    }

    /** Attaches the toolbar's container to a visible off-screen window so it is "showing". */
    private fun attachToShowingWindow(toolbar: ActionToolbar, anchor: JComponent): Pair<JWindow, Container> {
        val holder = toolbar.component.parent ?: toolbar.component
        val owner = SwingUtilities.getWindowAncestor(anchor)
        val window = JWindow(owner)
        window.contentPane.add(holder)
        window.pack()
        window.location = Point(-10000, -10000)
        window.isVisible = true
        return window to holder
    }

    private fun detach(window: JWindow, holder: Container) {
        window.contentPane.remove(holder)
        window.isVisible = false
        window.dispose()
    }

    private fun populateViaWindow(toolbar: ActionToolbar, anchor: JComponent, onReady: (ActionToolbar) -> Unit) {
        val (window, holder) = attachToShowingWindow(toolbar, anchor)
        val future = toolbar.updateActionsAsync()
        if (toolbar.hasVisibleActions()) {
            detach(window, holder)
            onReady(toolbar)
            return
        }
        onFutureDone(future) {
            SwingUtilities.invokeLater {
                detach(window, holder)
                onReady(toolbar)
            }
        }
    }

    /**
     * Invokes [onDone] once [future] completes. The platform's `ActionToolbar.updateActionsAsync`
     * always returns a `CompletableFuture`; the fallback invokes the callback eagerly.
     */
    private fun onFutureDone(future: Future<*>, onDone: () -> Unit) {
        val completable = future as? CompletableFuture<*>
        if (completable != null) {
            completable.whenComplete { _, _ -> onDone() }
        } else {
            onDone()
        }
    }

    private suspend fun awaitFuture(future: Future<*>) {
        if (future.isDone) return
        val completable = future as? CompletableFuture<*> ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            completable.whenComplete { _, _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { }
        }
    }
}
