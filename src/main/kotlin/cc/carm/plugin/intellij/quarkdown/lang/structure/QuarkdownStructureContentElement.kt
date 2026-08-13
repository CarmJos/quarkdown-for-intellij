package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils.TableBlock
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.Language
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A lightweight tree element for a non-heading content block (image, table, equation or
 * code block) shown under its containing heading in the Structure View.
 *
 * The block is represented by a [StructureTreeElement] whose [value] is a fake
 * [PsiElement] anchored at the block's start offset, so double-clicking navigates to the
 * content exactly like the matching gutter icon does.
 */
class QuarkdownStructureContentElement(
    private val file: PsiFile,
    val block: ContentBlock,
) : StructureViewTreeElement {

    /** Which kind of Quarkdown content a block represents. */
    enum class Kind { IMAGE, TABLE, EQUATION, CODE_BLOCK }

    /** A content block detected inside a heading's section. */
    data class ContentBlock(
        val kind: Kind,
        /** Short display label (e.g. the image title, table caption, or language). */
        val label: String,
        /** Absolute offset of the block's first character. */
        val offset: Int,
        val icon: Icon,
        /** Cross-reference id shown in gray after the label, or `null`. */
        val location: String? = null,
    )

    override fun getValue(): PsiElement = BlockPsiElement(file, block.offset)

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = block.label
        override fun getLocationString(): String? = block.location
        override fun getIcon(unused: Boolean): Icon = block.icon
    }

    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true

    override fun navigate(requestFocus: Boolean) {
        PsiNavigationSupport.getInstance()
            .getDescriptor(getValue())
            ?.navigate(requestFocus)
    }
}

/**
 * Scans the content of a heading's section (the text between the heading line and the next
 * heading at the same or higher level) for image / table / equation / code-block blocks.
 *
 * Fenced code blocks and fenced equations are consumed as whole blocks so their inner
 * lines never produce spurious image/table entries; consecutive `|`-rows are grouped into
 * a single table block (including an optional trailing `"label" {#id}` line).
 */
internal object QuarkdownStructureContentScanner {

    fun scan(file: PsiFile, range: IntRange): List<QuarkdownStructureContentElement> {
        val text = file.text
        val result = mutableListOf<QuarkdownStructureContentElement>()
        val tables = QuarkdownTableModificationUtils.findTableBlocks(text)
            .filter { it.startOffset in range }
            .sortedBy { it.startOffset }
        var tableIdx = 0

        var i = range.first
        var fenceChar: Char? = null // '`' / '~' for code, '$' for equation
        while (i < range.last) {
            val lineStart = i
            val lineEnd = nextLineEnd(text, i, range.last)
            val line = text.subSequence(lineStart, lineEnd).toString()
            i = lineEnd + 1

            if (line.isBlank()) continue

            // Inside a fenced block: consume until its closing fence.
            if (fenceChar != null) {
                if (isClosingFence(line.trim(), fenceChar)) fenceChar = null
                continue
            }

            // A whole table block (including its label line) is one entry.
            if (tableIdx < tables.size && tables[tableIdx].startOffset == lineStart) {
                val table = tables[tableIdx++]
                result += tableElement(file, table)
                i = (table.fullEndOffset + 1).coerceAtMost(range.last)
                continue
            }

            // Fenced code block opening line.
            val codeFence = QuarkdownCodeBlockSyntax.parseFenceLine(line)
            if (codeFence != null && codeFence.fence.length >= 3) {
                result += codeElement(
                    file, lineStart,
                    codeFence.caption.ifEmpty { codeFence.language }.ifEmpty { null },
                    codeFence.id.ifEmpty { null }
                )
                fenceChar = codeFence.fence.first()
                continue
            }

            // `.code` function block header line.
            val codeFn = QuarkdownCodeBlockSyntax.parseCodeFunctionLine(line)
            if (codeFn != null) {
                result += codeElement(
                    file, lineStart,
                    codeFn.caption.ifEmpty { codeFn.language }.ifEmpty { null },
                    codeFn.id.ifEmpty { null }
                )
                continue
            }

            // Fenced equation opening line.
            val equationFence = QuarkdownEquationSyntax.parseFenceEquationLine(line)
            if (equationFence != null && equationFence.fence.length >= 3) {
                result += equationElement(file, lineStart, equationFence.id.ifEmpty { null })
                fenceChar = '$'
                continue
            }

            // Standalone inline equation line.
            val inlineEquation = QuarkdownEquationSyntax.parseInlineEquationLine(line)
            if (inlineEquation != null) {
                result += equationElement(file, lineStart, inlineEquation.id.ifEmpty { null })
                continue
            }

            // Image line — captures the path, the optional quoted title, and the `{#id}`.
            val image = IMAGE_LINE_REGEX.find(line)
            if (image != null) {
                val path = image.groupValues[1]
                val title = image.groupValues[2]
                val id = image.groupValues[3]
                result += QuarkdownStructureContentElement(
                    file,
                    QuarkdownStructureContentElement.ContentBlock(
                        QuarkdownStructureContentElement.Kind.IMAGE,
                        title.ifEmpty { path.substringAfterLast('/') }.ifEmpty { "Image" },
                        lineStart,
                        QuarkdownIcons.IMAGE_MARKER,
                        id.ifEmpty { null }
                    )
                )
            }
        }
        return result
    }

    private fun tableElement(file: PsiFile, table: TableBlock): QuarkdownStructureContentElement {
        // Prefer the `"caption" {#id}` label, then the header row's first cells.
        val caption = table.labelLine
            ?.let { line -> """^\s*"([^"]*)"""".toRegex().find(line)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.isNotEmpty() }
        val header = caption
            ?: table.lines.firstOrNull()
                ?.trim('|')
                ?.split('|')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(3)
                ?.joinToString(" | ")
                ?.ifEmpty { null }
        val id = QuarkdownTableModificationUtils.parseLabelLineId(table.labelLine).ifEmpty { null }
        return QuarkdownStructureContentElement(
            file,
            QuarkdownStructureContentElement.ContentBlock(
                QuarkdownStructureContentElement.Kind.TABLE,
                header ?: "Table",
                table.startOffset,
                QuarkdownIcons.TABLE_MARKER,
                id
            )
        )
    }

    private fun codeElement(file: PsiFile, offset: Int, caption: String?, id: String?): QuarkdownStructureContentElement =
        QuarkdownStructureContentElement(
            file,
            QuarkdownStructureContentElement.ContentBlock(
                QuarkdownStructureContentElement.Kind.CODE_BLOCK,
                caption ?: "Code block",
                offset,
                QuarkdownIcons.CODE_MARKER,
                id
            )
        )

    private fun equationElement(file: PsiFile, offset: Int, id: String?): QuarkdownStructureContentElement =
        QuarkdownStructureContentElement(
            file,
            QuarkdownStructureContentElement.ContentBlock(
                QuarkdownStructureContentElement.Kind.EQUATION,
                "Equation",
                offset,
                QuarkdownIcons.EQUATION_MARKER,
                id
            )
        )

    private fun nextLineEnd(text: CharSequence, from: Int, limit: Int): Int {
        var i = from
        while (i < limit && text[i] != '\n') i++
        return i
    }

    private fun isClosingFence(line: String, fenceChar: Char): Boolean =
        line.length >= 3 && line.all { it == fenceChar }

    /**
     * Matches a Quarkdown image line: `!(105%)[label](path "title") {#id}`.
     * Groups: 1 = path, 2 = optional quoted title, 3 = optional cross-reference id.
     */
    private val IMAGE_LINE_REGEX = Regex("""!\s*(?:\([^)]*\)\s*)?\[[^\]]*]\s*\(\s*([^)\s]+)(?:\s+"([^"]*)")?\s*\)\s*(?:\{#([^}]+)})?""")
}

/**
 * A minimal [PsiElement] standing in for a content block so the Structure View can
 * identify and navigate to it. Its text range covers a single character at [offset].
 */
private class BlockPsiElement(
    private val file: PsiFile,
    private val offset: Int,
) : FakePsiElement() {

    override fun getParent(): PsiElement = file

    override fun getProject(): Project = file.project

    override fun getLanguage(): Language = file.language

    override fun getContainingFile(): PsiFile = file

    override fun getTextRange(): TextRange {
        val length = file.textLength
        return TextRange(offset.coerceIn(0, length), (offset + 1).coerceAtMost(length))
    }

    override fun getText(): String = file.text.substring(textRange.startOffset, textRange.endOffset)

    override fun getTextLength(): Int = textRange.length

    /** The block's start offset, so navigation jumps to the content line (not offset 0). */
    override fun getTextOffset(): Int = offset

    override fun isValid(): Boolean = file.isValid

    override fun getNavigationElement(): PsiElement = this
}
