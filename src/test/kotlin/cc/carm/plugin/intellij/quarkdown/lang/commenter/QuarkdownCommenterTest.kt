package cc.carm.plugin.intellij.quarkdown.lang.commenter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [QuarkdownCommenter] — pure string-config checks.
 */
class QuarkdownCommenterTest {

    private val commenter = QuarkdownCommenter()

    @Test
    fun `block comment uses html comment syntax`() {
        assertEquals("<!-- ", commenter.blockCommentPrefix)
        assertEquals(" -->", commenter.blockCommentSuffix)
    }

    @Test
    fun `no line comment syntax is defined`() {
        assertNull(commenter.lineCommentPrefix)
    }

    @Test
    fun `nested block comment prefixes are not supported`() {
        assertNull(commenter.commentedBlockCommentPrefix)
        assertNull(commenter.commentedBlockCommentSuffix)
    }
}
