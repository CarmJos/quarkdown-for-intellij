package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Configurable for Quarkdown editor appearance settings.
 * Provides settings for font family, size, line height, line wrap,
 * and per-component styling (headings, code blocks, tables, etc.).
 */
class QuarkdownEditorAppearanceConfigurable :
    SearchableConfigurable,
    Configurable.NoScroll {

    companion object {
        const val ID = "QuarkdownEditorAppearance"
    }

    private val settings = QuarkdownEditorAppearanceSettings.getInstance()

    // Font settings
    private val fontFamilyOptions = listOf(
        QuarkdownBundle.message("quarkdown.settings.editor.appearance.font.default"),
        "JetBrains Mono",
        "Consolas",
        "Courier New",
        "Monospaced",
        "Source Code Pro",
        "Fira Code"
    )

    private var selectedFontFamily = settings.state.fontFamily
    private var fontSize = settings.state.fontSize
    private var lineHeight = settings.state.lineHeight
    private var enableLineWrap = settings.state.enableLineWrap
    private var showLineNumbers = settings.state.showLineNumbers

    // Per-component settings
    private var headingBold = settings.state.headingBold
    private var headingItalic = settings.state.headingItalic
    private var codeBlockBackground = settings.state.codeBlockBackground
    private var codeBlockBorder = settings.state.codeBlockBorder
    private var tableBorder = settings.state.tableBorder
    private var tableStriped = settings.state.tableStriped

    override fun getId(): String = ID

    override fun getDisplayName(): @Nls String =
        QuarkdownBundle.message("quarkdown.settings.editor.appearance.name")

    override fun createComponent(): JComponent {
        return panel {
            group(QuarkdownBundle.message("quarkdown.settings.editor.appearance.font.settings")) {
                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.font.family")) {
                    comboBox(fontFamilyOptions)
                        .bindItem(
                            { selectedFontFamily },
                            { selectedFontFamily = it ?: QuarkdownBundle.message("quarkdown.settings.editor.appearance.font.default") }
                        )
                        .align(AlignX.FILL)
                }
                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.font.size")) {
                    intTextField(8..72, 1)
                        .bindIntText(
                            { fontSize },
                            { fontSize = it }
                        )
                        .applyToComponent { columns = 6 }
                }
                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.line.height")) {
                    intTextField(10..300, 5)
                        .bindIntText(
                            { lineHeight },
                            { lineHeight = it }
                        )
                        .applyToComponent { columns = 6 }
                    label(QuarkdownBundle.message("quarkdown.settings.editor.appearance.line.height.hint"))
                }
            }

            group(QuarkdownBundle.message("quarkdown.settings.editor.appearance.behavior")) {
                row {
                    checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.line.wrap"))
                        .bindSelected(
                            { enableLineWrap },
                            { enableLineWrap = it }
                        )
                }
                row {
                    checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.line.numbers"))
                        .bindSelected(
                            { showLineNumbers },
                            { showLineNumbers = it }
                        )
                }
            }

            group(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling")) {
                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.headings")) {}
                indent {
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.headings.bold"))
                            .bindSelected(
                                { headingBold },
                                { headingBold = it }
                            )
                    }
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.headings.italic"))
                            .bindSelected(
                                { headingItalic },
                                { headingItalic = it }
                            )
                    }
                }

                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.codeblocks")) {}
                indent {
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.codeblocks.background"))
                            .bindSelected(
                                { codeBlockBackground },
                                { codeBlockBackground = it }
                            )
                    }
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.codeblocks.border"))
                            .bindSelected(
                                { codeBlockBorder },
                                { codeBlockBorder = it }
                            )
                    }
                }

                row(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.tables")) {}
                indent {
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.tables.border"))
                            .bindSelected(
                                { tableBorder },
                                { tableBorder = it }
                            )
                    }
                    row {
                        checkBox(QuarkdownBundle.message("quarkdown.settings.editor.appearance.styling.tables.striped"))
                            .bindSelected(
                                { tableStriped },
                                { tableStriped = it }
                            )
                    }
                }
            }

            group(QuarkdownBundle.message("quarkdown.settings.editor.appearance.note")) {
                row {
                    label(QuarkdownBundle.message("quarkdown.settings.editor.appearance.note.text"))
                        .applyToComponent {
                            foreground = JBColor.GRAY
                        }
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return selectedFontFamily != settings.state.fontFamily ||
                fontSize != settings.state.fontSize ||
                lineHeight != settings.state.lineHeight ||
                enableLineWrap != settings.state.enableLineWrap ||
                showLineNumbers != settings.state.showLineNumbers ||
                headingBold != settings.state.headingBold ||
                headingItalic != settings.state.headingItalic ||
                codeBlockBackground != settings.state.codeBlockBackground ||
                codeBlockBorder != settings.state.codeBlockBorder ||
                tableBorder != settings.state.tableBorder ||
                tableStriped != settings.state.tableStriped
    }

    override fun apply() {
        settings.state.fontFamily = selectedFontFamily
        settings.state.fontSize = fontSize
        settings.state.lineHeight = lineHeight
        settings.state.enableLineWrap = enableLineWrap
        settings.state.showLineNumbers = showLineNumbers
        settings.state.headingBold = headingBold
        settings.state.headingItalic = headingItalic
        settings.state.codeBlockBackground = codeBlockBackground
        settings.state.codeBlockBorder = codeBlockBorder
        settings.state.tableBorder = tableBorder
        settings.state.tableStriped = tableStriped
    }

    override fun reset() {
        selectedFontFamily = settings.state.fontFamily
        fontSize = settings.state.fontSize
        lineHeight = settings.state.lineHeight
        enableLineWrap = settings.state.enableLineWrap
        showLineNumbers = settings.state.showLineNumbers
        headingBold = settings.state.headingBold
        headingItalic = settings.state.headingItalic
        codeBlockBackground = settings.state.codeBlockBackground
        codeBlockBorder = settings.state.codeBlockBorder
        tableBorder = settings.state.tableBorder
        tableStriped = settings.state.tableStriped
    }
}
