package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import kotlinx.coroutines.CoroutineScope

/**
 * Floating formatting toolbar for Quarkdown documents, shown when the user selects text
 * (mirrors the IntelliJ Markdown plugin's MarkdownFloatingToolbar).
 *
 * The toolbar is hidden when the caret/selection is inside an element that must not be
 * styled inline (fenced code blocks, inline code spans, links).
 */
class FormattingFloatingToolbar(
    editor: Editor,
    coroutineScope: CoroutineScope
) : FloatingToolbar(editor, coroutineScope) {

    override fun createActionGroup(): ActionGroup? {
        return CustomActionsSchema.getInstance().getCorrectedAction("Quarkdown.Toolbar.Floating") as? ActionGroup
    }

    /**
     * True when the selection is inside content that must not get inline formatting:
     *  - fenced code blocks / inline code spans,
     *  - image paths `![alt](path)` or link destinations,
     *  - Quarkdown function-call arguments (e.g. `.fullwidth { … }`), and
     *  - front matter / HTML blocks.
     */
    override fun hasIgnoredParent(element: PsiElement): Boolean {
        val file = element.containingFile ?: return true
        if (file.fileType !is QuarkdownFileType) return true

        val type = element.node?.elementType
        if (type in IGNORED_TYPES) return true

        // Quarkdown's PSI is flat, so additionally scan the document around the
        // element for non-prose context (function calls, image/link destinations…).
        if (editor.isDisposed) return true
        val offset = element.textRange.startOffset
        return isNonProseContext(editor.document.immutableCharSequence, offset)
    }

    override fun isEnabled(): Boolean = true

    companion object {
        /** Token types whose content must never get inline styling from the toolbar. */
        private val IGNORED_TYPES = setOf(
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_START,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_END,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_LANGUAGE,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FENCED_CODE_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.CODE_MARKER,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.CODE_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.IMAGE_PREFIX,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.LINK_URL,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.LINK_TITLE,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_DOT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_NAME,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_PARAMS,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FRONT_MATTER_DELIMITER,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FRONT_MATTER_CONTENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_TAG,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_COMMENT,
            cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.HTML_COMMENT_CONTENT
        )

        /**
         * Scans the line around [offset] to decide whether the caret/selection sits in
         * non-prose content: a Quarkdown function call (including its name, positional
         * and named arguments such as `margin:{0}`), an image path `![alt](path)`, a
         * link destination `[…] (…)` or an inline code span.
         */
        internal fun isNonProseContext(text: CharSequence, offset: Int): Boolean {
            val lineStart = text.lastIndexOf('\n', offset - 1) + 1
            val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            val line = text.subSequence(lineStart, lineEnd).toString()

            // Inline code span backticks.
            if (line.contains('`')) return true

            // Inside a Quarkdown function call (name, positional/named arguments).
            if (isInFunctionCall(text, offset)) return true

            // Image syntax `![alt](path)` or link `[text](url)`: caret inside `(…)`.
            if (line.contains('!') && line.contains("](")) return true
            if (line.contains("](")) {
                val parenOpen = line.indexOf("](")
                val parenClose = line.indexOf(')', parenOpen + 2)
                val caretInLine = offset - lineStart
                if (parenOpen >= 0 && parenClose < 0) return true          // unfinished destination
                if (parenOpen >= 0 && caretInLine > parenOpen + 1 && (parenClose < 0 || caretInLine < parenClose)) return true
            }

            return false
        }

        /**
         * True when [offset] lies inside a Quarkdown function call: from its `.name`
         * through all arguments (positional `{…}` and named `name:{…}`). Uses the real
         * [QuarkdownCallParser] so every call form is covered (e.g. `.resetpagenumber`,
         * `.pageformat pages:{..1} margin:{0}`).
         */
        private fun isInFunctionCall(text: CharSequence, offset: Int): Boolean {
            val source = text.toString()
            val start = QuarkdownCallParser.findCallStart(source, offset)
            if (start < 0) return false
            val call = QuarkdownCallParser.parseCall(source, start) ?: return false
            return offset >= call.start && offset <= call.end
        }
    }
}
