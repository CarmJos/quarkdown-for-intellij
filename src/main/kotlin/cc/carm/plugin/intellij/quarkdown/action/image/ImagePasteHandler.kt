package cc.carm.plugin.intellij.quarkdown.action.image

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File

/**
 * Handles paste (Ctrl+V) in Quarkdown (.qd) editors.
 * When the clipboard contains an image (file or in-memory), this handler:
 *  - If the image file is already inside the current project → opens [ImageDialog] directly.
 *  - If the image file is outside the project → opens [ImageSaveDialog] first to let the
 *    user choose where to copy the file, then opens [ImageDialog].
 *  - If the clipboard holds an in-memory [BufferedImage] (e.g. screenshot) → opens
 *    [ImageSaveDialog] first, saves the image, then opens [ImageDialog].
 *
 * When the clipboard does NOT contain an image, or the current file is not a `.qd` file,
 * the paste is delegated to the original handler.
 */
class ImagePasteHandler(
    private val originalHandler: EditorActionHandler,
) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val project = editor.project
        val docFile = FileDocumentManager.getInstance().getFile(editor.document)

        // Only intercept paste in Quarkdown (.qd) files
        if (project == null || docFile == null || docFile.fileType !is QuarkdownFileType) {
            originalHandler.execute(editor, caret, dataContext)
            return
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        // 1. Check for image files in clipboard (e.g. copied from file explorer)
        val imageFiles = getImageFilesFromClipboard(clipboard)
        if (imageFiles.isNotEmpty()) {
            handleImageFilePaste(project, editor, docFile, imageFiles.first())
            return
        }

        // 2. Check for in-memory image data (e.g. screenshot)
        val bufferedImage = getBufferedImageFromClipboard(clipboard)
        if (bufferedImage != null) {
            handleBufferedImagePaste(project, editor, docFile, bufferedImage)
            return
        }

        // 3. No image in clipboard — delegate to original paste handler
        originalHandler.execute(editor, caret, dataContext)
    }

    // ========================
    // Clipboard helpers
    // ========================

    private fun getImageFilesFromClipboard(
        clipboard: Clipboard,
    ): List<File> {
        if (!clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return emptyList()
        val list = try {
            clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        return list.filterIsInstance<File>().filter { ImageDropHandler.isImageFile(it) }
    }

    private fun getBufferedImageFromClipboard(
        clipboard: Clipboard,
    ): BufferedImage? {
        if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
        return try {
            when (val data = clipboard.getData(DataFlavor.imageFlavor)) {
                is BufferedImage -> data
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ========================
    // Paste handling
    // ========================

    /**
     * Handle paste of an image file.
     * If the file is already inside the current project, open the image dialog directly.
     * Otherwise, show the save dialog first to copy the file into the project.
     */
    private fun handleImageFilePaste(
        project: Project,
        editor: Editor,
        docFile: VirtualFile,
        imageFile: File,
    ) {
        // Check if the image is already in the current project
        val virtualImageFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(imageFile)
        val isInProject = virtualImageFile != null && isInProjectContent(project, virtualImageFile)

        if (isInProject) {
            // Image is already in the project — show image dialog directly
            showImageInsertDialog(project, editor, docFile, imageFile.absolutePath)
        } else {
            // Image is outside the project — show save dialog first
            val saveDialog = ImageSaveDialog(
                project = project,
                defaultFileName = imageFile.name,
                defaultDir = docFile.parent,
                sourceImageFile = imageFile,
            )
            if (!saveDialog.showAndGet()) return
            if (!saveDialog.copyImageToTarget()) return
            showImageInsertDialog(project, editor, docFile, saveDialog.targetFile!!.absolutePath)
        }
    }

    /**
     * Handle paste of an in-memory [BufferedImage] (e.g. from screenshot).
     * Always shows the save dialog first, then the image insert dialog.
     */
    private fun handleBufferedImagePaste(
        project: Project,
        editor: Editor,
        docFile: VirtualFile,
        image: BufferedImage,
    ) {
        val saveDialog = ImageSaveDialog(
            project = project,
            defaultFileName = "pasted-image.png",
            defaultDir = docFile.parent,
            sourceBufferedImage = image,
        )
        if (!saveDialog.showAndGet()) return
        if (!saveDialog.copyImageToTarget()) return
        showImageInsertDialog(project, editor, docFile, saveDialog.targetFile!!.absolutePath)
    }

    // ========================
    // Image insert dialog
    // ========================

    private fun showImageInsertDialog(
        project: Project,
        editor: Editor,
        docFile: VirtualFile,
        imagePath: String,
    ) {
        val dialog = ImageDialog(project, ImageDialog.Mode.INSERT)
        dialog.setCurrentFileDir(docFile.parent)
        dialog.setImagePath(imagePath)

        if (!dialog.showAndGet()) return

        val syntax = dialog.buildImageSyntax()
        WriteCommandAction.runWriteCommandAction(project) {
            val primaryCaret = editor.caretModel.primaryCaret
            if (primaryCaret.hasSelection()) {
                val start = primaryCaret.selectionStart
                val end = primaryCaret.selectionEnd
                editor.document.replaceString(start, end, syntax)
                primaryCaret.moveToOffset(start + syntax.length)
            } else {
                editor.document.insertString(editor.caretModel.offset, syntax)
            }
        }
    }

    // ========================
    // Utility
    // ========================

    private fun isInProjectContent(project: Project, virtualFile: VirtualFile): Boolean {
        return ProjectFileIndex.getInstance(project).isInContent(virtualFile)
    }
}
