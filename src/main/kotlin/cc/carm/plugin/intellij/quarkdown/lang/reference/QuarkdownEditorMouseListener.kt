package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

/**
 * Ctrl+Click behaviour for Quarkdown declarations (`{#id}` label or `.var { name }`).
 *
 * The platform's `GotoDeclaration` action is suppressed by
 * [QuarkdownCtrlClickInterceptor] (registered on the event queue, which runs BEFORE
 * the keymap mouse dispatcher), so it never shows a "Choose Declaration" or
 * "Cannot find declaration to go to" popup. This listener then synchronously counts
 * the usages of the clicked declaration and acts based on the count:
 *
 * - **>1 usage**: shows a usages popup anchored at the declaration. The popup is built
 *   from the synchronously computed usages and NEVER moves the editor caret, so it opens
 *   right where the `{#id}` sits (unlike `ShowUsagesAction`, which pings the editor to the
 *   first usage and makes the caret jump).
 * - **=1 usage**: navigates directly to that single usage.
 * - **0 usages**: shows an information hint "No references found".
 */
class QuarkdownEditorMouseListener : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) = Unit

    override fun mouseReleased(event: EditorMouseEvent) {
        val mouseEvent = event.mouseEvent
        if (!mouseEvent.isControlDown) return
        if (mouseEvent.clickCount != 1) return

        val editor = event.editor
        val project = editor.project ?: return
        if (project.isDisposed) return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        if (file.fileType != QuarkdownFileType.INSTANCE) return

        val offset = event.offset
        if (offset < 0 || offset > editor.document.textLength) return
        // Only declarations (`{#id}` label / `.var { name }`) are handled here.
        if (declarationElementAt(file, offset) == null) return

        // Consume so other editor listeners / handlers do not also act on this click.
        event.consume()

        // Synchronously collect usages of this declaration.
        val usages = ApplicationManager.getApplication().runReadAction<List<PsiElement>> {
            val ref: PsiReference? = file.findReferenceAt(offset)
            (ref as? QuarkdownReference)?.multiResolve(false)?.mapNotNull { it.element }
                ?: emptyList()
        }

        when {
            usages.size > 1 -> showUsagesPopup(editor, usages)
            usages.size == 1 -> (usages.first() as? Navigatable)?.navigate(true)
            else -> HintManager.getInstance().showInformationHint(
                editor, QuarkdownBundle.message("quarkdown.ref.noReferences")
            )
        }
    }

    /**
     * Shows a popup listing [usages] anchored at the current caret (the declaration), without
     * moving the caret. Selecting an entry (Enter / double-click) navigates to that usage.
     */
    private fun showUsagesPopup(editor: Editor, usages: List<PsiElement>) {
        val list = JBList(usages.map { describeUsage(it) })
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.border = JBUI.Borders.empty(4, 6)
        list.cellRenderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                ) as javax.swing.JLabel
                c.text = value?.toString() ?: ""
                c.icon = null
                return c
            }
        }

        val popup: JBPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(list, list)
            .setTitle(QuarkdownBundle.message("quarkdown.ref.references"))
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelOnOtherWindowOpen(true)
            .createPopup()

        fun navigateSelected() {
            val index = list.selectedIndex
            if (index in usages.indices) {
                popup.closeOk(null)
                (usages[index] as? Navigatable)?.navigate(true)
            }
        }

        // Double-click navigates.
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount >= 2) navigateSelected()
            }
        })

        // Enter navigates.
        list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "quarkdown.navigate")
        list.actionMap.put("quarkdown.navigate", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) = navigateSelected()
        })

        // Anchor the popup at the current caret so it opens right at the declaration.
        popup.showInBestPositionFor(editor)
    }

    /** Builds a one-line description for [usage] (file name, line number and line text). */
    private fun describeUsage(usage: PsiElement): String {
        val usageFile = usage.containingFile ?: return usage.text
        val project = usageFile.project
        val document = PsiDocumentManager.getInstance(project).getDocument(usageFile)
        val line = if (document != null) document.getLineNumber(usage.textOffset) + 1 else 0
        val lineText = if (document != null && line > 0) {
            val start = document.getLineStartOffset(line - 1)
            val end = document.getLineEndOffset(line - 1)
            document.getText(TextRange(start, end)).trim().take(120)
        } else {
            usage.text.take(120)
        }
        return "${usageFile.name}:$line  $lineText"
    }

    /** Returns the declaration leaf at [offset] (label / var-decl), or null. */
    internal fun declarationElementAt(file: PsiFile, offset: Int): PsiElement? {
        val anchor = QuarkdownReferenceAnchors.of(file).firstOrNull {
            TextRange(it.start, it.end).contains(offset)
        } ?: return null
        if (anchor.referenceType != "label" && anchor.referenceType != "var-decl") return null
        return file.findElementAt(anchor.start)
    }

    override fun mousePressed(event: EditorMouseEvent) = Unit
    override fun mouseEntered(event: EditorMouseEvent) = Unit
    override fun mouseExited(event: EditorMouseEvent) = Unit
}
