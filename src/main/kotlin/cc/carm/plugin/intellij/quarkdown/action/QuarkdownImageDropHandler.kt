package cc.carm.plugin.intellij.quarkdown.action

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

/**
 * Handles drag-and-drop of image files onto a Quarkdown editor.
 * When an image file (.png, .jpg, .svg, .webp, etc.) is dropped,
 * opens the QuarkdownImageDialog with the path pre-filled.
 *
 * Implements BOTH [EditorDropHandler] (EP: com.intellij.editorDropHandler, order="first")
 * and [FileDropHandler] (EP: com.intellij.fileDropHandler, order="first") to intercept
 * the drop at the lowest AWT level before [com.intellij.openapi.fileEditor.impl.FileEditorDropHandler]
 * opens the image in a new editor tab.
 */
class QuarkdownImageDropHandler : FileDropHandler, EditorDropHandler {

    // ========================
    // EditorDropHandler — intercepts AWT drops on the editor component
    // ========================

    override fun canHandleDrop(transferFlavors: Array<out DataFlavor>): Boolean {
        return transferFlavors.any { it == DataFlavor.javaFileListFlavor }
    }

    override fun handleDrop(
        t: Transferable,
        project: Project?,
        editorWindow: EditorWindow?,
    ) {
        // Get the FileEditor from the editor window, then extract the text Editor
        val fileEditor = editorWindow?.selectedComposite?.selectedEditor ?: return
        val editor = (fileEditor as? TextEditor)?.editor ?: return
        if (editor.isViewer) return

        // Only handle drops in Quarkdown (.qd) files
        val docFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (docFile.fileType !is QuarkdownFileType) return

        // Extract image files from the transferable
        val imageFiles = extractImageFiles(t)
        if (imageFiles.isEmpty()) return

        // Open dialog with first image file path pre-filled
        showDialogAndInsert(project ?: editor.project ?: return, editor, docFile, imageFiles.first())
    }

    // ========================
    // FileDropHandler — intercepts higher-level drop events
    // ========================

    override suspend fun handleDrop(e: FileDropEvent): Boolean {
        val editor = e.editor ?: return false
        if (editor.isViewer) return false

        // Only handle drops in Quarkdown (.qd) files
        val docFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        if (docFile.fileType !is QuarkdownFileType) return false

        // Check if any dropped file is an image
        val imageFiles = e.files.filter { isImageFile(it) }
        if (imageFiles.isEmpty()) return false

        // Open dialog with first image file path pre-filled
        return showDialogAndInsert(e.project, editor, docFile, imageFiles.first())
    }

    // ========================
    // Shared logic
    // ========================

    private fun extractImageFiles(t: Transferable): List<File> {
        if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        val list = try {
            t.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        return list.filterIsInstance<File>().filter { isImageFile(it) }
    }

    private fun showDialogAndInsert(
        project: Project,
        editor: com.intellij.openapi.editor.Editor,
        docFile: com.intellij.openapi.vfs.VirtualFile,
        imageFile: File,
    ): Boolean {
        val dialog = QuarkdownImageDialog(project, QuarkdownImageDialog.Mode.INSERT)
        dialog.setCurrentFileDir(docFile.parent)
        dialog.setImagePath(imageFile.absolutePath)

        if (!dialog.showAndGet()) return true // Drop consumed (user cancelled)

        val syntax = dialog.buildImageSyntax()
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = editor.caretModel.offset
            editor.document.insertString(offset, syntax)
        }
        return true
    }

    private fun isImageFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".svg")
                || name.endsWith(".webp")
    }
}
