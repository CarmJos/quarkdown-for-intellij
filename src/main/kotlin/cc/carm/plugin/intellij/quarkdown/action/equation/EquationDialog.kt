package cc.carm.plugin.intellij.quarkdown.action.equation

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationEdit
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownFormulaPreviewSource
import cc.carm.plugin.intellij.quarkdown.ui.preview.QuarkdownFormulaPreviewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Editor for a single Quarkdown equation, used both when editing an existing one (from the
 * gutter) and when inserting a new one.
 *
 * ```
 * ┌ Form:  [ $ … $ ▾ ]──────────────────────┐
 * │ ┌ preview (live, debounced) ──────────┐ │
 * │ └─────────────────────────────────────┘ │
 * │ ┌ TeX content ────────────────────────┐ │
 * │ │ \begin{aligned} …                   │ │
 * │ └─────────────────────────────────────┘ │
 * │ ID: [ … ]                               │
 * └─────────────────────────────────────────┘
 * ```
 *
 * Behaviour worth noting:
 *
 *  - the **content** is pre-filled from the equation (it used to come up empty) and is a
 *    multi-line area, because TeX expressions are routinely several lines long;
 *  - the **preview** re-renders as you type, debounced by the pane so the CLI is not started on
 *    every keystroke;
 *  - the **form** can be switched between `$ … $` and `.math`, which is the conversion needed
 *    when one syntax has to be used instead of the other;
 *  - the **id** field is offered where an id makes sense: always for `.math` (written as
 *    `ref:{…}`) and for `$ … $` when inserting or when the equation already has one, so an
 *    existing id is never silently dropped.
 */
class EquationDialog(
    private val project: Project,
    /** Whole document text: the preview reuses its `.texmacro` / `.var` declarations. */
    private val documentText: String,
    /** The equation being edited, or `null` when inserting a new one. */
    initial: QuarkdownEquationEdit.Occurrence?,
) : DialogWrapper(project) {

    private val inserting: Boolean = initial == null

    /** What the dialog currently edits; the insert case starts from a blank `$ … $`. */
    private val original: QuarkdownEquationEdit.Occurrence = initial ?: QuarkdownEquationEdit.Occurrence(
        form = QuarkdownEquationEdit.Form.DOLLAR,
        content = "",
        id = "",
        fence = false,
        indent = "",
        standalone = true,
    )

    /** Whether the dollar form keeps using the `$$$` fence. */
    private var fence: Boolean = original.fence

    private val contentArea = JBTextArea(original.content, CONTENT_ROWS, CONTENT_COLUMNS).apply {
        lineWrap = true
        wrapStyleWord = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(FONT_SIZE))
    }

    private val idField = JBTextField(original.id, 24)

    private val formSelector = JComboBox(
        arrayOf(FormChoice.DOLLAR, FormChoice.MATH)
    ).apply {
        selectedItem = if (original.form == QuarkdownEquationEdit.Form.MATH) FormChoice.MATH else FormChoice.DOLLAR
    }

    private val idRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.equation.id")))
        add(idField)
    }

    private val previewPane = QuarkdownFormulaPreviewPane(project)

    private val rootPanel = JPanel(BorderLayout())

    init {
        title = QuarkdownBundle.message(
            if (inserting) "quarkdown.dialog.equation.insert.title" else "quarkdown.dialog.equation.title"
        )
        init()
        installListeners()
        refreshIdVisibility()
        updatePreview()
    }

    private fun installListeners() {
        contentArea.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updatePreview()
        })
        idField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updatePreview()
        })
        formSelector.addActionListener {
            // Converting keeps a `$$$` block fenced: only that syntax can hold several lines.
            fence = QuarkdownEquationEdit.fenceAfterConversion(original, currentForm(), contentArea.text)
            refreshIdVisibility()
            updatePreview()
        }
    }

    private fun currentForm(): QuarkdownEquationEdit.Form =
        if (formSelector.selectedItem == FormChoice.MATH) {
            QuarkdownEquationEdit.Form.MATH
        } else {
            QuarkdownEquationEdit.Form.DOLLAR
        }

    /**
     * The id is only meaningful for `.math` (`ref:{…}`) and for `$ … $` equations that either
     * already carry one or are being created.
     */
    private fun refreshIdVisibility() {
        idRow.isVisible = currentForm() == QuarkdownEquationEdit.Form.MATH ||
                inserting ||
                original.id.isNotEmpty()
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    /** Builds the occurrence text for the current fields. */
    fun buildText(): String = QuarkdownEquationEdit.render(
        form = currentForm(),
        content = contentArea.text,
        id = idField.text,
        fence = fence,
        indent = original.indent,
        standalone = if (inserting) true else original.standalone,
    )

    private fun updatePreview() {
        val formula = buildText()
        previewPane.render(if (formula.isBlank()) "" else QuarkdownFormulaPreviewSource.wrap(documentText, formula))
    }

    override fun createCenterPanel(): JComponent {
        rootPanel.preferredSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)

        val formRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false
            add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.equation.form")))
            add(formSelector)
        }

        val contentPanel = JPanel(BorderLayout()).apply {
            add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.equation.content")).apply {
                border = JBUI.Borders.empty(2, 0)
            }, BorderLayout.NORTH)
            add(JBScrollPane(contentArea).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }

        // The preview sits above the input so it stays visible while typing.
        val upper = JPanel(BorderLayout()).apply {
            add(previewPane.component, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }

        rootPanel.add(formRow, BorderLayout.NORTH)
        rootPanel.add(upper, BorderLayout.CENTER)
        rootPanel.add(idRow, BorderLayout.SOUTH)
        return rootPanel
    }

    override fun dispose() {
        previewPane.dispose()
        super.dispose()
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    /** The current id, for tests. */
    internal fun getIdForTest(): String = idField.text.trim()

    /** The current content, for tests. */
    internal fun getContentForTest(): String = contentArea.text

    /** Which syntax family the form selector points at, for tests. */
    internal fun getFormForTest(): QuarkdownEquationEdit.Form = currentForm()

    /** Whether the id field is offered, for tests. */
    internal fun isIdVisibleForTest(): Boolean = idRow.isVisible

    /** Renders the dialog body once, for tests that cannot show a dialog. */
    internal fun buildPanelForTest(): JComponent = createCenterPanel()

    private enum class FormChoice(private val labelKey: String) {
        DOLLAR("quarkdown.dialog.equation.form.dollar"),
        MATH("quarkdown.dialog.equation.form.math");

        override fun toString(): String = QuarkdownBundle.message(labelKey)
    }

    private companion object {
        const val CONTENT_ROWS = 10
        const val CONTENT_COLUMNS = 64
        const val FONT_SIZE = 13
        const val DIALOG_WIDTH = 700
        const val DIALOG_HEIGHT = 540
    }
}

