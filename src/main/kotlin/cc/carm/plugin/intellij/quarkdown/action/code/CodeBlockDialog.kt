package cc.carm.plugin.intellij.quarkdown.action.code

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.CodeBlockLanguageProvider
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/**
 * Dialog for editing a Quarkdown code block's language, caption and cross-reference id.
 *
 * Depending on [kind] the changes are written back either to a fenced code block opening
 * line or to a `.code` function header line:
 * ```
 * ```python "Fibonacci function" {#fibonacci}
 * .code lang:{python} caption:{Fibonacci function} ref:{example}
 * ```
 */
class CodeBlockDialog(
    private val project: Project?,
    private val kind: QuarkdownCodeBlockSyntax.Kind
) : DialogWrapper(project) {

    /** The original header line (EDIT mode, to preserve indentation/fence). */
    private var originalLine: String = ""

    /** Searchable dropdown listing every known code block language (names + aliases). */
    private var languageCombo: ComboBox<String>? = null
    private var captionField: JBTextField? = null
    private var idField: JBTextField? = null

    /** Guards programmatic model/editor updates from re-triggering [filterLanguages]. */
    private var updating = false

    /** Last values pushed into the language model, to skip redundant updates. */
    private var lastFiltered: List<String>? = null
    private var lastSelected: String? = null

    init {
        title = QuarkdownBundle.message("quarkdown.dialog.codeblock.title")
        init()
    }

    /** Pre-populate from an existing fenced code block opening line. */
    fun parseFence(info: QuarkdownCodeBlockSyntax.FenceInfo) {
        originalLine = info.line
        setLanguage(info.language)
        captionField?.text = info.caption
        idField?.text = info.id
    }

    /** Pre-populate from an existing `.code` function header line. */
    fun parseCodeFunction(info: QuarkdownCodeBlockSyntax.CodeFunctionInfo) {
        originalLine = info.line
        setLanguage(info.language)
        captionField?.text = info.caption
        idField?.text = info.id
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(3, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val languageLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.codeblock.language"))
        val combo = ComboBox(CollectionComboBoxModel(ALL_LANGUAGES, null))
        combo.isEditable = true
        languageCombo = combo
        (combo.editor.editorComponent as? JTextComponent)?.document?.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = filterLanguages()
                override fun removeUpdate(e: DocumentEvent) = filterLanguages()
                override fun changedUpdate(e: DocumentEvent) = filterLanguages()
            }
        )
        panel.add(
            languageLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            combo, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val captionLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.codeblock.caption"))
        val cf = JBTextField()
        captionField = cf
        panel.add(
            captionLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            cf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val idLabel = JBLabel(
            if (kind == QuarkdownCodeBlockSyntax.Kind.CODE_FUNCTION) {
                QuarkdownBundle.message("quarkdown.dialog.codeblock.id.ref")
            } else {
                QuarkdownBundle.message("quarkdown.dialog.codeblock.id")
            }
        )
        val idf = JBTextField()
        idField = idf
        panel.add(
            idLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            idf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Make the dialog a bit wider so the searchable language dropdown is comfortable.
        val natural = panel.preferredSize
        panel.preferredSize = Dimension(maxOf(natural.width, 460), natural.height)

        return panel
    }

    /** Builds the replacement header line, preserving indentation and fence style. */
    fun buildLine(): String {
        val language = (languageCombo?.editor?.item as? String)?.trim().orEmpty()
        val caption = captionField?.text?.trim().orEmpty()
        val id = idField?.text?.trim().orEmpty()
        return when (kind) {
            QuarkdownCodeBlockSyntax.Kind.FENCED ->
                QuarkdownCodeBlockSyntax.buildFenceLine(originalLine, language, caption, id)

            QuarkdownCodeBlockSyntax.Kind.CODE_FUNCTION ->
                QuarkdownCodeBlockSyntax.buildCodeFunctionLine(originalLine, language, caption, id)
        }
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    fun setLanguageForTest(language: String) {
        setLanguage(language)
    }

    fun setCaptionForTest(caption: String) {
        captionField?.text = caption
    }

    fun setIdForTest(id: String) {
        idField?.text = id
    }

    /** Test helper: exposes the language combo to verify live-filtering behavior. */
    internal fun languageComboForTest(): ComboBox<String>? = languageCombo

    /** Test helper: returns the current language text shown in the combo editor. */
    internal fun getLanguageForTest(): String =
        (languageCombo?.editor?.item as? String)?.trim().orEmpty()

    /**
     * Live-filters the language dropdown as the user types in the editor, narrowing the
     * items to languages starting with the typed prefix. The combo stays editable so
     * custom/unknown languages can still be entered freely.
     */
    private fun filterLanguages() {
        if (updating) return
        val combo = languageCombo ?: return
        val editor = combo.editor.editorComponent as? JTextComponent ?: return

        // Defer the model swap out of the editor's document notification: replacing the
        // model makes Swing re-configure the editor (writing to the same document that is
        // currently being notified), which throws "Attempt to mutate in notification".
        SwingUtilities.invokeLater {
            updateLanguageModel(combo, editor)
        }
    }

    /**
     * Replaces the combo model with the languages matching the current editor text.
     * Runs outside the document notification (see [filterLanguages]); re-reads the text
     * so a newer keystroke is never reverted by an older queued update.
     */
    private fun updateLanguageModel(combo: ComboBox<String>, editor: JTextComponent) {
        val typed = editor.text
        val caret = editor.caretPosition
        val prefix = typed.trim().lowercase()
        val filtered = ALL_LANGUAGES.filter { it.lowercase().startsWith(prefix) }
        val changed = lastFiltered != filtered || lastSelected != typed

        updating = true
        try {
            if (changed) {
                lastFiltered = filtered
                lastSelected = typed
                // Use the typed text as the selection: IntelliJ's ComboBox re-configures
                // the editor to the selected item whenever the model changes or the popup
                // opens. Keeping them equal preserves the typed text instead of clearing
                // it, and prevents the single-match auto-selection from overwriting input.
                combo.model = CollectionComboBoxModel(filtered, typed)
            }
            ensurePopup(combo, editor, typed, filtered)
            // configureEditor (from the model change / popup open) selects the whole
            // text; restore the caret to the position it was before.
            editor.caretPosition = caret.coerceIn(0, typed.length)
        } finally {
            updating = false
        }
    }

    /**
     * Keeps the dropdown open while the user is typing, and hides it when nothing
     * matches. Re-opening after the model swap also covers combos whose popup is closed
     * by the model change itself.
     */
    private fun ensurePopup(
        combo: ComboBox<String>,
        editor: JTextComponent,
        typed: String,
        filtered: List<String>
    ) {
        if (!editor.isFocusOwner || !combo.isShowing) return
        if (filtered.isEmpty()) {
            if (combo.isPopupVisible) combo.isPopupVisible = false
            return
        }
        if (!combo.isPopupVisible) {
            // Don't re-open right after the user picked an exact item from the list.
            val exact = ALL_LANGUAGES.any { it.equals(typed.trim(), ignoreCase = true) }
            if (!exact) combo.showPopup()
        }
    }

    /** Programmatically sets the language text without triggering live filtering. */
    private fun setLanguage(text: String) {
        val combo = languageCombo ?: return
        updating = true
        try {
            combo.editor.item = text
            // Keep the model selection in sync so opening the popup (which reconfigures
            // the editor to the selected item) doesn't wipe out the pre-filled text.
            if (combo.selectedItem != text) combo.selectedItem = text
        } finally {
            updating = false
        }
        lastFiltered = null
        lastSelected = null
    }

    companion object {
        /** Every known code block language identifier (canonical names + aliases), sorted. */
        private val ALL_LANGUAGES: List<String> = CodeBlockLanguageProvider.languages
            .flatMap { it.allIdentifiers }
            .distinct()
            .sorted()
    }
}
