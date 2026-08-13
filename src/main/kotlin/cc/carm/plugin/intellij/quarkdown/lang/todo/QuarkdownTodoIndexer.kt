package cc.carm.plugin.intellij.quarkdown.lang.todo

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownLexer
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.psi.impl.cache.impl.BaseFilterLexer
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer
import com.intellij.psi.tree.TokenSet

/**
 * TODO / FIXME indexer for Quarkdown files.
 *
 * Registers HTML comments (`<!-- ... -->`) as comment tokens for the platform's
 * TODO index, so that TODO / FIXME markers inside comments are picked up by the
 * IDE's TODO tool window (not just highlighted in the editor).
 */
class QuarkdownTodoIndexer : LexerBasedTodoIndexer() {

    override fun createLexer(consumer: OccurrenceConsumer): BaseFilterLexer {
        return object : BaseFilterLexer(QuarkdownLexer(), consumer) {
            override fun advance() {
                if (COMMENT_TOKENS.contains(delegate.tokenType)) {
                    scanWordsInToken(2, false, false)
                    advanceTodoItemCountsInToken()
                }
                delegate.advance()
            }
        }
    }

    companion object {
        private val COMMENT_TOKENS = TokenSet.create(
            QuarkdownTokenTypes.HTML_COMMENT,
            QuarkdownTokenTypes.HTML_COMMENT_CONTENT
        )
    }
}
