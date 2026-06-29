package cc.carm.plugin.intellij.quarkdown.action

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for choosing where to save a pasted image file.
 * Shows a directory chooser and a filename field.
 * Returns the target [File] where the image should be saved.
 */
class QuarkdownImageSaveDialog(
    project: Project?,
    private val defaultFileName: String,
    private val defaultDir: VirtualFile?,
    private val sourceImageFile: File? = null,
    private val sourceBufferedImage: BufferedImage? = null,
) : DialogWrapper(project) {

    private var dirField: TextFieldWithBrowseButton? = null
    private var nameField: JBTextField? = null
    private val myProject: Project? = project

    /** The resulting target file after OK. */
    var targetFile: File? = null
        private set

    init {
        title = "Save Image To..."
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(2, 2, JBUI.insets(10), -1, -1))

        val dirLabel = JBLabel("Save to:")
        val df = TextFieldWithBrowseButton()
        dirField = df
        val dirDescriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Select Image Save Directory")
            .withDescription("Choose a directory to save the image file")
        df.addBrowseFolderListener(TextBrowseFolderListener(dirDescriptor, myProject))
        if (defaultDir != null) {
            df.text = defaultDir.path
        }
        panel.add(dirLabel, GridConstraints(0, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(df, GridConstraints(0, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        val nameLabel = JBLabel("File name:")
        val nf = JBTextField(defaultFileName)
        nameField = nf
        panel.add(nameLabel, GridConstraints(1, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(nf, GridConstraints(1, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val dirPath = dirField?.text?.trim().orEmpty()
        if (dirPath.isEmpty()) {
            return ValidationInfo("Please select a save directory.", dirField)
        }
        val dir = File(dirPath)
        if (!dir.isDirectory) {
            return ValidationInfo("Directory does not exist: $dirPath", dirField)
        }
        val fileName = nameField?.text?.trim().orEmpty()
        if (fileName.isEmpty()) {
            return ValidationInfo("Please enter a file name.", nameField)
        }
        return null
    }

    override fun doOKAction() {
        val dirPath = dirField?.text?.trim() ?: return
        val fileName = nameField?.text?.trim() ?: return
        targetFile = File(dirPath, fileName)
        super.doOKAction()
    }

    /**
     * Copies the source image to the target file chosen by the user.
     * Returns true if the copy was successful.
     */
    fun copyImageToTarget(): Boolean {
        val target = targetFile ?: return false
        return try {
            target.parentFile?.mkdirs()
            when {
                sourceImageFile != null -> {
                    sourceImageFile.copyTo(target, overwrite = true)
                }
                sourceBufferedImage != null -> {
                    val ext = target.extension.lowercase()
                    val formatName = when (ext) {
                        "jpg", "jpeg" -> "jpg"
                        "gif" -> "gif"
                        "bmp" -> "bmp"
                        else -> "png"
                    }
                    ImageIO.write(sourceBufferedImage, formatName, target)
                }
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
