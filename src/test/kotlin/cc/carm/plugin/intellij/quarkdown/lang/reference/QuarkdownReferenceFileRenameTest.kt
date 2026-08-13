package cc.carm.plugin.intellij.quarkdown.lang.reference

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the file-path rename/move helpers of [QuarkdownReference].
 * The methods are pure text transformations, so they are tested without a platform.
 */
class QuarkdownReferenceFileRenameTest {

    @Test
    fun `renames bare filename`() {
        assertEquals(
            "bar.qd",
            QuarkdownReference.renamePathSegment("foo.qd", "bar.qd")
        )
    }

    @Test
    fun `renames last segment preserving directory`() {
        assertEquals(
            "chapters/bar.qd",
            QuarkdownReference.renamePathSegment("chapters/foo.qd", "bar.qd")
        )
    }

    @Test
    fun `renames deeply nested path`() {
        assertEquals(
            "a/b/c/image.png",
            QuarkdownReference.renamePathSegment("a/b/c/old.png", "image.png")
        )
    }

    @Test
    fun `renames image with size syntax not applied`() {
        assertEquals(
            "img/new-photo.png",
            QuarkdownReference.renamePathSegment("img/photo.png", "new-photo.png")
        )
    }
}
