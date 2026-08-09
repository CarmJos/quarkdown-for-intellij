@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

private val QUARKDOWN_TABLE_EDITOR_KEY = SettingsKey<NoSettings>("quarkdown.table.editor")

/**
 * Inlay-hint based floating table editor for Quarkdown documents (mirrors the IntelliJ
 * Markdown plugin):
 *  - a horizontal bar above the header row: one clickable segment per column separator,
 *  - a vertical bar at the left edge of every row.
 *
 * Clicking a bar opens a floating toolbar of row/column operations (insert, remove,
 * move, select, alignment).
 */
class QuarkdownTableEditorProvider : InlayHintsProvider<NoSettings> {
    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        return if (file.fileType is QuarkdownFileType) Collector(editor) else null
    }

    override fun createSettings(): NoSettings = NoSettings()
    override val key: SettingsKey<NoSettings> = QUARKDOWN_TABLE_EDITOR_KEY
    override val name: String = "Quarkdown table editor"
    override val previewText: String =
        "| Header 1 | Header 2 |\n|:---------|---------:|\n| Cell 1   | Cell 2   |"

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    internal class CollectorForTest(private val editor: Editor) : Collector(editor)

    internal open class Collector(private val editor: Editor) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (!element.isValid) return true
            // The framework visits the PsiFile element once per collect cycle; process the
            // whole file here. There is intentionally no cache (unlike the previous
            // `processedFile` guard): after a document change the same PsiFile instance may
            // be reused, and a guard would skip re-collection so all bars disappear.
            if (element is PsiFile && element.fileType is QuarkdownFileType) {
                processFile(element, sink)
            }
            return true
        }

        internal fun processFile(file: PsiFile, sink: InlayHintsSink) {
            val text = file.text
            for (block in QuarkdownTableModificationUtils.findTableBlocks(text)) {
                val table = QuarkdownTableParser.parse(block.lines) ?: continue
                // Horizontal bar above the header row (column operations).
                val horizontal = QuarkdownHorizontalBarPresentation.create(factory, editor, block)
                sink.addBlockElement(block.startOffset, false, true, -1, horizontal)
                // Vertical bar at the left edge of every row (row operations).
                for (rowIndex in block.lines.indices) {
                    val vertical = QuarkdownVerticalBarPresentation.create(factory, editor, block, rowIndex)
                    sink.addInlineElement(block.lineStarts[rowIndex], false, vertical, false)
                }
            }
        }
    }
}
