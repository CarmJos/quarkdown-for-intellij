package cc.carm.plugin.intellij.quarkdown.action.image

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtilCore
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
import java.nio.file.Paths
import javax.swing.*

/**
 * Dialog for inserting or editing an image in Quarkdown syntax:
 *   ![(size)][(id)]((path) "(label)") {#id}
 */
class ImageDialog(
    private val project: Project?,
    private val mode: Mode = Mode.INSERT
) : DialogWrapper(project) {

    enum class Mode {
        /** Creating a new image */
        INSERT,

        /** Editing an existing image line */
        EDIT
    }

    /** The original full line text (for EDIT mode, to compute indent) */
    private var originalLine: String = ""

    private var pathField: TextFieldWithBrowseButton? = null
    private var percentRadio: JBRadioButton? = null
    private var fixedSizeRadio: JBRadioButton? = null
    private var percentSlider: JSlider? = null
    private var percentInput: JBTextField? = null
    private var widthField: JBTextField? = null
    private var heightField: JBTextField? = null
    private var unitCombo: ComboBox<String>? = null
    private var labelField: JBTextField? = null
    private var altField: JBTextField? = null
    private var anchorIdField: JBTextField? = null

    private var percentPanel: JPanel? = null
    private var fixedSizePanel: JPanel? = null

    private var currentFileDir: VirtualFile? = null

    init {
        title = if (mode == Mode.EDIT) {
            QuarkdownBundle.message("quarkdown.dialog.image.title.edit")
        } else {
            QuarkdownBundle.message("quarkdown.dialog.image.title.insert")
        }
        init()
    }

    fun setCurrentFileDir(dir: VirtualFile?) {
        currentFileDir = dir
    }

    /** Pre-fill the image path field (used by drag & drop). */
    fun setImagePath(path: String) {
        pathField?.text = path
        onPathChanged()
    }

    /** Pre-populate from an existing image syntax line. Preserves indent. */
    fun parseExistingLine(line: String) {
        originalLine = line
        val indentLen = line.indexOfFirst { it != ' ' }
        val prefix = if (indentLen > 0) line.substring(0, indentLen) else ""
        val content = line.trim()

        // ![(100%)][id](path "label") or !(100%)[id](path "label")
        val imageRegex = Regex(
            """^!\s*(?:\(([^)]*)\))?\s*(?:\[([^\]]*)\])?\s*\(([^)]+)\)(?:\s*\{#([^}]+)})?\s*$"""
        )
        val match = imageRegex.find(content)
        if (match != null) {
            val size = match.groupValues[1].trim()
            val imgId = match.groupValues[2].trim()
            val pathEtc = match.groupValues[3].trim()
            val anchorId = match.groupValues[4].trim()

            // Parse path and optional label: path "label"
            val labelMatch = Regex("""^(.+?)\s*"([^"]*)"$""").find(pathEtc)
            val rawPath: String
            val label: String
            if (labelMatch != null) {
                rawPath = labelMatch.groupValues[1].trim()
                label = labelMatch.groupValues[2].trim()
            } else {
                rawPath = pathEtc.trim()
                label = ""
            }

            pathField?.text = rawPath
            labelField?.text = label
            altField?.text = imgId
            anchorIdField?.text = anchorId

            // Parse size
            if (size.isNotEmpty()) {
                if (size.endsWith("%")) {
                    percentRadio?.isSelected = true
                    val pct = size.removeSuffix("%").trim().toIntOrNull() ?: 100
                    percentSlider?.value = pct.coerceIn(0, 150)
                    percentInput?.text = pct.toString()
                } else {
                    // Fixed size like "100px 200px"
                    fixedSizeRadio?.isSelected = true
                    val parts = size.split(Regex("""\s+"""))
                    if (parts.size >= 2) {
                        val wPair = parseSizeUnit(parts[0])
                        val hPair = parseSizeUnit(parts[1])
                        widthField?.text = wPair.first ?: ""
                        heightField?.text = hPair.first ?: ""
                        val unit = (wPair.second ?: hPair.second) ?: "px"
                        unitCombo?.selectedItem = unit
                    }
                }
                toggleSizePanels()
            }
        }
    }

    private fun parseSizeUnit(s: String): Pair<String?, String?> {
        val num = s.takeWhile { it.isDigit() || it == '.' }
        val unit = s.removePrefix(num)
        return if (num.isEmpty()) null to null else num to unit.ifEmpty { "px" }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(9, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val pathLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.path"))
        val pf = TextFieldWithBrowseButton()
        pathField = pf
        val imageDescriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle(QuarkdownBundle.message("quarkdown.dialog.image.chooser.title"))
            .withDescription(QuarkdownBundle.message("quarkdown.dialog.image.chooser.description"))
        val listener = TextBrowseFolderListener(imageDescriptor, project)
        pf.addBrowseFolderListener(listener)
        pf.addPropertyChangeListener("text") { onPathChanged() }
        panel.add(
            pathLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            pf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val modeLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.size.mode"))
        val modePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val pr = JBRadioButton(QuarkdownBundle.message("quarkdown.dialog.image.size.percentage"), true)
        percentRadio = pr
        val fsr = JBRadioButton(QuarkdownBundle.message("quarkdown.dialog.image.size.fixed"), false)
        fixedSizeRadio = fsr
        val modeGroup = ButtonGroup()
        modeGroup.add(pr)
        modeGroup.add(fsr)
        modePanel.add(pr)
        modePanel.add(Box.createHorizontalStrut(16))
        modePanel.add(fsr)
        panel.add(
            modeLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            modePanel, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        percentPanel = buildPercentPanel()
        fixedSizePanel = buildFixedSizePanel()
        fixedSizePanel?.isVisible = false

        panel.add(
            percentPanel, GridConstraints(
                row, 0, 1, 2,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            fixedSizePanel, GridConstraints(
                row, 0, 1, 2,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        pr.addActionListener { toggleSizePanels() }
        fsr.addActionListener { toggleSizePanels() }

        val labelLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.label"))
        val lf = JBTextField()
        labelField = lf
        panel.add(
            labelLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            lf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val altLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.alt"))
        val altf = JBTextField()
        altField = altf
        panel.add(
            altLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            altf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val anchorIdLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.anchor"))
        val aidf = JBTextField()
        anchorIdField = aidf
        panel.add(
            anchorIdLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            aidf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        return panel
    }

    private fun buildPercentPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0))
        val label = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.scale"))
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
            val v = input.text.replace("%", "").trim().toIntOrNull()
            if (v != null) slider.value = v.coerceIn(0, 150)
        }
        return panel
    }

    private fun buildFixedSizePanel(): JPanel {
        val panel = JPanel(GridLayoutManager(2, 4, JBUI.emptyInsets(), -1, -1))
        val widthLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.width"))
        val wf = JBTextField(6)
        widthField = wf
        val heightLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.height"))
        val hf = JBTextField(6)
        heightField = hf
        val combo = ComboBox(arrayOf("px", "cm", "in"))
        combo.selectedIndex = 0
        unitCombo = combo

        panel.add(
            widthLabel, GridConstraints(
                0, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            wf, GridConstraints(
                0, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            JPanel(), GridConstraints(
                0, 2, 1, 1,
                GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            combo, GridConstraints(
                0, 3, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        panel.add(
            heightLabel, GridConstraints(
                1, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            hf, GridConstraints(
                1, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        val optionalLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.image.optional"))
        optionalLabel.foreground = UIManager.getColor("Label.disabledForeground")
        panel.add(
            optionalLabel, GridConstraints(
                1, 3, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        return panel
    }

    private fun toggleSizePanels() {
        val isPercent = percentRadio!!.isSelected
        percentPanel?.isVisible = isPercent
        fixedSizePanel?.isVisible = !isPercent
        SwingUtilities.getWindowAncestor(percentPanel)?.pack()
    }

    private fun onPathChanged() {
        if (altField!!.text.isEmpty()) {
            val path = pathField!!.text.trim()
            if (path.isNotEmpty()) {
                val fileName = File(path).nameWithoutExtension
                altField?.text = fileName
            }
        }
    }

    override fun doOKAction() {
        if (pathField!!.text.trim().isEmpty()) return
        super.doOKAction()
    }

    /** Build image syntax. In EDIT mode, preserves the original line's indent. */
    fun buildImageSyntax(): String {
        val rawPath = pathField!!.text.trim()
        val path = toRelativePath(rawPath)
        val size = buildSizeString()
        val label = labelField!!.text.trim()
        val alt = altField!!.text.trim()
        val anchorId = anchorIdField!!.text.trim()

        val sb = StringBuilder()
        sb.append("!(").append(size).append(")")
        sb.append("[").append(alt).append("]")
        sb.append("(").append(path)
        if (label.isNotEmpty()) sb.append(" \"").append(label).append("\"")
        sb.append(")")
        if (anchorId.isNotEmpty()) sb.append(" {#").append(anchorId).append("}")

        if (mode == Mode.EDIT) {
            val indent = computeIndent()
            if (indent.isNotEmpty()) sb.insert(0, indent)
        }
        return sb.toString()
    }

    /** Recover leading whitespace from the original line. */
    fun computeIndent(): String {
        val idx = originalLine.indexOfFirst { it != ' ' && it != '\t' }
        return if (idx > 0) originalLine.substring(0, idx) else ""
    }

    private fun toRelativePath(rawPath: String): String {
        val dir = currentFileDir ?: return rawPath
        val directoryPath = VfsUtilCore.virtualToIoFile(dir).absolutePath
        return try {
            val imagePath = Paths.get(rawPath)
            if (!imagePath.isAbsolute) return rawPath
            val baseDir = Paths.get(directoryPath)
            baseDir.relativize(imagePath).toString().replace('\\', '/')
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
