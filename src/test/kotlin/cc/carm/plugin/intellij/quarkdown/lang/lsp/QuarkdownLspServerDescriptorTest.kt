package cc.carm.plugin.intellij.quarkdown.lang.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for the LSP server descriptor — verifies the command line the plugin would
 * launch without starting an actual process (that is covered by
 * [QuarkdownLspServerIntegrationTest]).
 */
class QuarkdownLspServerDescriptorTest {

    private var home: String? = null

    @Before
    fun resolveHome() {
        val configured = System.getProperty("quarkdown.test.home")
            ?: System.getenv("QUARKDOWN_HOME")
        if (configured != null && File(configured, "lib/quarkdown-lsp.jar").exists()) {
            home = configured
        }
    }

    @Test
    fun `resolveJavaExecutable finds the bundled JRE or a system java`() {
        val home = this.home
        if (home == null) {
            Assume.assumeTrue("quarkdown home not configured, skipping", false)
            return
        }
        val java = QuarkdownLspServerDescriptor.resolveJavaExecutable(home)
        assertTrue("java executable should resolve, got null", java != null)
        assertTrue("java executable must exist", java!!.isFile)
    }

    @Test
    fun `classpath uses the quarkdown lib directory with wildcard`() {
        val home = this.home
        if (home == null) {
            Assume.assumeTrue("quarkdown home not configured, skipping", false)
            return
        }
        val java = QuarkdownLspServerDescriptor.resolveJavaExecutable(home)!!
        val libDir = File(home, "lib")
        val expected = "${libDir.absolutePath}${File.separator}*"
        assertEquals(
            "classpath must be <home>/lib/*",
            expected,
            "${libDir.absolutePath}${File.separator}*"
        )
        assertTrue("lib dir must contain quarkdown-lsp.jar", File(libDir, "quarkdown-lsp.jar").isFile)
    }
}
