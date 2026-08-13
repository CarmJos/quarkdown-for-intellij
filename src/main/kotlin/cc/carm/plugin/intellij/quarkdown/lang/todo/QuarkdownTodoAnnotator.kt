package cc.carm.plugin.intellij.quarkdown.lang.todo

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

/**
 * Highlights TODO / FIXME markers inside Quarkdown HTML comments (`<!-- ... -->`).
 *
 * The annotated text is displayed with the IDE's TODO highlight color.
 * (Indexing for the TODO tool window is handled by [QuarkdownTodoIndexer].)
 */
class QuarkdownTodoAnnotator : Annotator {

    companion object {
        private val TODO_PATTERN = Regex("""(?i)\b(TODO|FIXME)\b""")

        val TODO_COMMENT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_TODO_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiComment) return

        val text = element.text
        val commentStart = element.textRange.startOffset

        for (match in TODO_PATTERN.findAll(text)) {
            val start = commentStart + match.range.first
            val end = commentStart + match.range.last + 1
            val range = TextRange(start, end)

            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(TODO_COMMENT)
                .create()
        }
    }
}
