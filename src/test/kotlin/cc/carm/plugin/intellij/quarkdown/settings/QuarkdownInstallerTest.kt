package cc.carm.plugin.intellij.quarkdown.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuarkdownInstallerTest {

    @Test
    fun `parses tag name from GitHub API response`() {
        val json = """{"tag_name":"v2.5.0","name":"Quarkdown 2.5.0"}"""
        assertEquals("2.5.0", QuarkdownInstaller.parseTagName(json))
    }

    @Test
    fun `strips leading v prefix`() {
        assertEquals("1.0.0", QuarkdownInstaller.parseTagName("""{"tag_name":"v1.0.0"}"""))
    }

    @Test
    fun `returns null for missing tag name`() {
        assertNull(QuarkdownInstaller.parseTagName("""{"name":"no version here"}"""))
        assertNull(QuarkdownInstaller.parseTagName(""))
    }

    @Test
    fun `platform asset name is never blank`() {
        val name = QuarkdownInstaller.platformAssetName()
        assertEquals(true, name.endsWith(".zip"))
    }
}
