package cc.carm.plugin.intellij.quarkdown.settings

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
        "Default",
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

    override fun getDisplayName(): @Nls String = "Editor Appearance"

    override fun createComponent(): JComponent {
        return panel {
            group("Font Settings") {
                row("Font family:") {
                    comboBox(fontFamilyOptions)
                        .bindItem(
                            { selectedFontFamily },
                            { selectedFontFamily = it ?: "Default" }
                        )
                        .align(AlignX.FILL)
                }
                row("Font size:") {
                    intTextField(8..72, 1)
                        .bindIntText(
                            { fontSize },
                            { fontSize = it }
                        )
                        .applyToComponent { columns = 6 }
                }
                row("Line height:") {
                    intTextField(10..300, 5)
                        .bindIntText(
                            { lineHeight },
                            { lineHeight = it }
                        )
                        .applyToComponent { columns = 6 }
                    label("(percentage, 100 = normal)")
                }
            }

            group("Editor Behavior") {
                row {
                    checkBox("Enable line wrap (soft wrap)")
                        .bindSelected(
                            { enableLineWrap },
                            { enableLineWrap = it }
                        )
                }
                row {
                    checkBox("Show line numbers")
                        .bindSelected(
                            { showLineNumbers },
                            { showLineNumbers = it }
                        )
                }
            }

            group("Per-Component Styling") {
                row("Headings:") {}
                indent {
                    row {
                        checkBox("Bold headings")
                            .bindSelected(
                                { headingBold },
                                { headingBold = it }
                            )
                    }
                    row {
                        checkBox("Italic headings")
                            .bindSelected(
                                { headingItalic },
                                { headingItalic = it }
                            )
                    }
                }

                row("Code Blocks:") {}
                indent {
                    row {
                        checkBox("Show background color")
                            .bindSelected(
                                { codeBlockBackground },
                                { codeBlockBackground = it }
                            )
                    }
                    row {
                        checkBox("Show border")
                            .bindSelected(
                                { codeBlockBorder },
                                { codeBlockBorder = it }
                            )
                    }
                }

                row("Tables:") {}
                indent {
                    row {
                        checkBox("Show border")
                            .bindSelected(
                                { tableBorder },
                                { tableBorder = it }
                            )
                    }
                    row {
                        checkBox("Striped rows")
                            .bindSelected(
                                { tableStriped },
                                { tableStriped = it }
                            )
                    }
                }
            }

            group("Note") {
                row {
                    label("These settings are for preview purposes. Some settings may require IDE restart to take effect.")
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
