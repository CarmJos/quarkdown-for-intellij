package cc.carm.plugin.intellij.quarkdown.lang.commenter

import com.intellij.lang.Commenter

/**
 * Quarkdown commenter using HTML-style block comments: <!-- ... -->.
 *
 * Quarkdown has no line-comment syntax, so [getLineCommentPrefix] returns null.
 * Ctrl+/ (CommentByLineCommentAction) will fall back to block-comment wrapping:
 *   - No selection         → inserts <!--  --> with caret in the middle
 *   - Single-line selection → wraps with <!-- text -->
 *   - Multi-line selection  → wraps with <!-- at start, --> at end
 */
class QuarkdownCommenter : Commenter {

    // ---- Block comment ----
    override fun getBlockCommentPrefix(): String = "<!-- "

    override fun getBlockCommentSuffix(): String = " -->"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    // ---- Line comment (not supported) ----
    override fun getLineCommentPrefix(): String? = null
}
