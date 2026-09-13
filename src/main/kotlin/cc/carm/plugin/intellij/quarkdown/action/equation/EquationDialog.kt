package cc.carm.plugin.intellij.quarkdown.action.equation

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationEdit
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexFileType
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexPreviewSource
import cc.carm.plugin.intellij.quarkdown.ui.preview.QuarkdownLatexPreviewView
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent as EditorDocumentEvent
import com.intellij.openapi.editor.event.DocumentListener as EditorDocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
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
 * │ ┌ preview (typeset as you type) ────────┐ │
 * │ └───────────────────────────────────────┘ │
 * │ ┌ TeX content ──────────────────────────┐ │
 * │ │ \begin{aligned} …                     │ │
 * │ └───────────────────────────────────────┘ │
 * │ ID: [ … ]                                 │
 * └───────────────────────────────────────────┘
 * ```
 *
 * Behaviour worth noting:
 *
 *  - the **content** is pre-filled from the equation (it used to come up empty) and is edited in a
 *    real editor — line numbers, soft wraps, the IDE's editor font and TeX coloring — because TeX
 *    expressions are routinely several lines long;
 *  - the **preview** typesets the content with the KaTeX build from the Quarkdown installation, so
 *    it updates as you type without any compile step; it follows the IDE theme, and the wheel zooms
 *    it while dragging with the left button pans it (see `QuarkdownLatexPreviewSource`);
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

    /**
     * The TeX input's document.
     *
     * The input is an [EditorTextField] rather than a text area so it is a real editor: line
     * numbers, soft wraps, the IDE's editor font, and — through [QuarkdownLatexFileType] — the same
     * TeX colors the document shows for the same formula.
     */
    private val contentDocument: Document = EditorFactory.getInstance()
        .createDocument(StringUtil.convertLineSeparators(original.content))

    private val contentField = EditorTextField(
        contentDocument,
        project,
        QuarkdownLatexFileType.INSTANCE,
        false,
        false,
    ).apply {
        addSettingsProvider { editor ->
            editor.settings.isLineNumbersShown = true
            editor.settings.isUseSoftWraps = true
            editor.settings.isLineMarkerAreaShown = false
            editor.settings.isFoldingOutlineShown = false
            editor.settings.isIndentGuidesShown = false
            editor.setVerticalScrollbarVisible(true)
        }
        border = JBUI.Borders.customLine(JBColor.border(), 1)
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

    private val previewView = QuarkdownLatexPreviewView(project, disposable)

    /** The document's `.texmacro` declarations, used to resolve custom commands in the preview. */
    private val macros: Map<String, String> by lazy { QuarkdownLatexPreviewSource.macros(documentText) }

    private val rootPanel = JPanel(BorderLayout())

    /**
     * The centre panel, built **once**.
     *
     * Building it per call is not safe: [DialogWrapper] may ask for the panel more than once, and
     * every rebuild re-adds the same children (the content area, the preview component, the id row)
     * to the same container. That orphans the previous layout and leaves degenerate bounds — the
     * content area ended up **negative** in height, which is how the input field disappeared.
     */
    private val centerPanel: JComponent by lazy { buildCenterPanel() }

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
        // The input is an editor document, so it reports changes through the editor's listener; the
        // id field is a plain text field and keeps the Swing one.
        contentDocument.addDocumentListener(object : EditorDocumentListener {
            override fun documentChanged(event: EditorDocumentEvent) = updatePreview()
        })
        idField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updatePreview()
        })
        formSelector.addActionListener {
            // Converting keeps a `$$$` block fenced: only that syntax can hold several lines.
            fence = QuarkdownEquationEdit.fenceAfterConversion(original, currentForm(), contentField.text)
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
        content = contentField.text,
        id = idField.text,
        fence = fence,
        indent = original.indent,
        standalone = if (inserting) true else original.standalone,
    )

    private fun updatePreview() {
        // The preview typesets the TeX content directly (no `.math` / `$` wrapper): KaTeX is the
        // renderer Quarkdown itself uses, and the wrapper carries no TeX meaning of its own.
        val content = contentField.text.trim()
        previewView.render(
            tex = content,
            macros = macros,
            displayMode = QuarkdownLatexPreviewSource.displayMode(content, fence),
        )
    }

    override fun createCenterPanel(): JComponent {
        return centerPanel
    }

    /** Builds the dialog body: form selector, preview, TeX input and the id row. */
    private fun buildCenterPanel(): JComponent {
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
            add(
                contentField.apply {
                    // The input must never be squeezed out of view by the preview above it.
                    minimumSize = JBUI.size(0, JBUI.scale(CONTENT_MIN_HEIGHT))
                },
                BorderLayout.CENTER,
            )
        }

        // The preview sits above the input so it stays visible while typing, but it needs a
        // *bounded* height: an embedded browser component asks for a lot of space, and in
        // BorderLayout.NORTH it would otherwise take the whole dialog and push the content area
        // out of view. The dialog can still be enlarged: extra space goes to the content area.
        val previewPanel = JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(PREVIEW_WIDTH, PREVIEW_HEIGHT)
            minimumSize = JBUI.size(0, JBUI.scale(PREVIEW_MIN_HEIGHT))
            border = JBUI.Borders.customLine(JBColor.border(), 1)
            add(previewView.component, BorderLayout.CENTER)
        }

        val upper = JPanel(BorderLayout()).apply {
            add(previewPanel, BorderLayout.NORTH)
            add(contentPanel, BorderLayout.CENTER)
        }

        rootPanel.add(formRow, BorderLayout.NORTH)
        rootPanel.add(upper, BorderLayout.CENTER)
        rootPanel.add(idRow, BorderLayout.SOUTH)
        return rootPanel
    }

    override fun dispose() {
        // The view is a child of the dialog's disposable, so the platform disposes it (and the
        // embedded browser it owns) first; this covers the paths that dispose the dialog directly.
        Disposer.dispose(previewView)
        super.dispose()
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    /** The current id, for tests. */
    internal fun getIdForTest(): String = idField.text.trim()

    /** The current content, for tests. */
    internal fun getContentForTest(): String = contentField.text

    /** Which syntax family the form selector points at, for tests. */
    internal fun getFormForTest(): QuarkdownEquationEdit.Form = currentForm()

    /** Whether the id field is offered, for tests. */
    internal fun isIdVisibleForTest(): Boolean = idRow.isVisible

    /** Renders the dialog body once, for tests that cannot show a dialog. */
    internal fun buildPanelForTest(): JComponent = centerPanel

    /**
     * The component that hosts the typeset preview, for tests.
     *
     * It is exposed so a test can emulate the embedded browser's appetite (JCEF asks for 800×600)
     * and assert that the TeX input still keeps a usable height.
     */
    internal fun previewComponentForTest(): JComponent = previewView.component

    /**
     * The view that owns the embedded browser and the asset server, for tests.
     *
     * Exposed so a test can assert it is disposed together with the dialog — it is registered as a
     * child of the dialog's disposable, and a view left undisposed is reported as a memory leak.
     */
    internal fun previewViewForTest(): QuarkdownLatexPreviewView = previewView

    /** Releases the dialog's resources without showing it, for tests. */
    internal fun disposeForTest() = dispose()

    private enum class FormChoice(private val labelKey: String) {
        DOLLAR("quarkdown.dialog.equation.form.dollar"),
        MATH("quarkdown.dialog.equation.form.math");

        override fun toString(): String = QuarkdownBundle.message(labelKey)
    }

    private companion object {
        const val DIALOG_WIDTH = 700
        const val DIALOG_HEIGHT = 540
        const val CONTENT_MIN_HEIGHT = 100
        const val PREVIEW_WIDTH = 300
        const val PREVIEW_HEIGHT = 200
        const val PREVIEW_MIN_HEIGHT = 100
    }
}
