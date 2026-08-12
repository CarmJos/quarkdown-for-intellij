package cc.carm.plugin.intellij.quarkdown.action.image

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

/**
 * Handles drag-and-drop of image files onto a Quarkdown editor.
 * When an image file (.png, .jpg, .svg, .webp, etc.) is dropped,
 * opens the ImageDialog with the path pre-filled.
 *
 * Implements BOTH [EditorDropHandler] (EP: com.intellij.editorDropHandler, order="first")
 * and [FileDropHandler] (EP: com.intellij.fileDropHandler, order="first") to intercept
 * the drop at the lowest AWT level before [com.intellij.openapi.fileEditor.impl.FileEditorDropHandler]
 * opens the image in a new editor tab.
 */
class ImageDropHandler : FileDropHandler, EditorDropHandler {

    companion object {
        val IMAGE_EXTENSIONS = setOf(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".webp", ".ico", ".tiff", ".tif"
        )

        fun isImageFile(file: File): Boolean {
            val name = file.name.lowercase()
            return IMAGE_EXTENSIONS.any { name.endsWith(it) }
        }

        fun isImageFile(file: VirtualFile): Boolean {
            val name = file.name.lowercase()
            return IMAGE_EXTENSIONS.any { name.endsWith(it) }
        }
    }

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

        // FileDropManager invokes this suspend handler on a background coroutine
        // thread, but ImageDialog (a DialogWrapper) must be created and shown on
        // the EDT. Switch to the EDT before showing the dialog, otherwise the
        // constructor throws "Access is allowed from Event Dispatch Thread (EDT)
        // only" and the drop falls through to the default handler (which would
        // open the image in a new editor tab).
        return withContext(Dispatchers.EDT) {
            showDialogAndInsert(e.project, editor, docFile, imageFiles.first())
        }
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
        editor: Editor,
        docFile: VirtualFile,
        imageFile: File,
    ): Boolean {
        val dialog = ImageDialog(project, ImageDialog.Mode.INSERT)
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
}
