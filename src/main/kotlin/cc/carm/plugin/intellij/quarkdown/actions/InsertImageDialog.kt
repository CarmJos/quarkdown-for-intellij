package cc.carm.plugin.intellij.quarkdown.actions

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.UIManager

class InsertImageDialog(private val project: Project?) : DialogWrapper(project) {

    private var pathField: TextFieldWithBrowseButton? = null
    private var percentRadio: JBRadioButton? = null
    private var fixedSizeRadio: JBRadioButton? = null
    private var percentSlider: JSlider? = null
    private var percentInput: JBTextField? = null
    private var widthField: JBTextField? = null
    private var heightField: JBTextField? = null
    private var unitCombo: ComboBox<String>? = null
    private var labelField: JBTextField? = null
    private var idField: JBTextField? = null

    private var percentPanel: JPanel? = null
    private var fixedSizePanel: JPanel? = null

    private var currentFileDir: VirtualFile? = null

    init {
        title = "Insert Image"
        init()
    }

    fun setCurrentFileDir(dir: VirtualFile?) {
        currentFileDir = dir
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(7, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val pathLabel = JBLabel("Image Path:")
        val pf = TextFieldWithBrowseButton()
        pathField = pf
        val imageDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { f ->
                val name = f.name.lowercase()
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".svg")
                        || name.endsWith(".webp")
            }
        pf.addBrowseFolderListener(
            "Select Image", "Select an image file to insert", project, imageDescriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )
        pf.addPropertyChangeListener("text") { onPathChanged() }
        panel.add(pathLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(pf, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        val modeLabel = JBLabel("Size Mode:")
        val modePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val pr = JBRadioButton("Percentage", true)
        percentRadio = pr
        val fsr = JBRadioButton("Fixed Size", false)
        fixedSizeRadio = fsr
        val modeGroup = ButtonGroup()
        modeGroup.add(pr)
        modeGroup.add(fsr)
        modePanel.add(pr)
        modePanel.add(javax.swing.Box.createHorizontalStrut(16))
        modePanel.add(fsr)
        panel.add(modeLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(modePanel, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        percentPanel = buildPercentPanel()
        fixedSizePanel = buildFixedSizePanel()
        fixedSizePanel!!.isVisible = false

        panel.add(percentPanel, GridConstraints(row, 0, 1, 2,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(fixedSizePanel, GridConstraints(row, 0, 1, 2,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        pr.addActionListener { toggleSizePanels() }
        fsr.addActionListener { toggleSizePanels() }

        val labelLabel = JBLabel("Label:")
        val lf = JBTextField()
        labelField = lf
        panel.add(labelLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(lf, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        val idLabel = JBLabel("Reference ID:")
        val idf = JBTextField()
        idField = idf
        panel.add(idLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(idf, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        return panel
    }

    private fun buildPercentPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        val label = JBLabel("Scale:")
        panel.add(label, BorderLayout.WEST)

        val slider = JSlider(0, 150, 100)
        slider.majorTickSpacing = 25
        slider.minorTickSpacing = 5
        slider.paintTicks = true
        slider.paintLabels = false
        percentSlider = slider
        panel.add(slider, BorderLayout.CENTER)

        val input = JBTextField(5)
        input.text = "100"
        input.horizontalAlignment = SwingConstants.RIGHT
        percentInput = input

        val rightPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        rightPanel.add(input)
        rightPanel.add(JBLabel("%"))
        panel.add(rightPanel, BorderLayout.EAST)

        slider.addChangeListener {
            if (!slider.valueIsAdjusting) {
                input.text = slider.value.toString()
            }
        }

        input.addActionListener {
            try {
                val v = input.text.replace("%", "").trim().toInt()
                slider.value = v.coerceIn(0, 150)
            } catch (_: NumberFormatException) {
            }
        }

        return panel
    }

    private fun buildFixedSizePanel(): JPanel {
        val panel = JPanel(GridLayoutManager(2, 4, JBUI.emptyInsets(), -1, -1))

        val widthLabel = JBLabel("Width:")
        val wf = JBTextField(6)
        widthField = wf
        val heightLabel = JBLabel("Height:")
        val hf = JBTextField(6)
        heightField = hf

        val combo = ComboBox(arrayOf("px", "cm", "in"))
        combo.selectedIndex = 0
        unitCombo = combo

        panel.add(widthLabel, GridConstraints(0, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(wf, GridConstraints(0, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(JPanel(), GridConstraints(0, 2, 1, 1,
            GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(combo, GridConstraints(0, 3, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        panel.add(heightLabel, GridConstraints(1, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(hf, GridConstraints(1, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        val optionalLabel = JBLabel("(optional)")
        optionalLabel.foreground = UIManager.getColor("Label.disabledForeground")
        panel.add(optionalLabel, GridConstraints(1, 3, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))

        return panel
    }

    private fun toggleSizePanels() {
        val isPercent = percentRadio!!.isSelected
        percentPanel!!.isVisible = isPercent
        fixedSizePanel!!.isVisible = !isPercent
        SwingUtilities.getWindowAncestor(percentPanel).pack()
    }

    private fun onPathChanged() {
        if (idField!!.text.isEmpty()) {
            val path = pathField!!.text.trim()
            if (path.isNotEmpty()) {
                val fileName = File(path).name
                val dotIdx = fileName.lastIndexOf('.')
                idField!!.text = if (dotIdx > 0) fileName.substring(0, dotIdx) else fileName
            }
        }
    }

    override fun doOKAction() {
        if (pathField!!.text.trim().isEmpty()) return
        super.doOKAction()
    }

    fun buildImageSyntax(): String {
        val rawPath = pathField!!.text.trim()
        val path = toRelativePath(rawPath)
        val size = buildSizeString()
        val label = labelField!!.text.trim()
        val id = idField!!.text.trim()

        val sb = StringBuilder()
        sb.append("!(").append(size).append(")")
        sb.append("[").append(id).append("]")
        sb.append("(").append(path)
        if (label.isNotEmpty()) {
            sb.append(" \"").append(label).append("\"")
        }
        sb.append(")")
        if (id.isNotEmpty()) {
            sb.append(" {#").append(id).append("}")
        }
        return sb.toString()
    }

    private fun toRelativePath(rawPath: String): String {
        val dir = currentFileDir ?: return rawPath
        return try {
            val imagePath = Paths.get(rawPath)
            if (!imagePath.isAbsolute) return rawPath
            val baseDir = Paths.get(dir.path)
            val relative = baseDir.relativize(imagePath)
            relative.toString().replace('\\', '/')
        } catch (_: IllegalArgumentException) {
            rawPath
        }
    }

    private fun buildSizeString(): String {
        if (percentRadio!!.isSelected) {
            val text = percentInput!!.text.replace("%", "").trim()
            return if (text.isEmpty()) "100%" else "$text%"
        }

        val unit = unitCombo!!.selectedItem as String
        val w = widthField!!.text.trim()
        val h = heightField!!.text.trim()

        return when {
            w.isNotEmpty() && h.isNotEmpty() -> "$w$unit $h$unit"
            w.isNotEmpty() -> "$w$unit _"
            h.isNotEmpty() -> "_ $h$unit"
            else -> "100%"
        }
    }
}
