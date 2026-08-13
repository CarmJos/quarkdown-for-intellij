package cc.carm.plugin.intellij.quarkdown.lang.lsp

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for the LSP server connection provider — verifies the command line the plugin
 * would launch without starting an actual process (that is covered by
 * [QuarkdownLspServerIntegrationTest]).
 */
class QuarkdownLanguageServerConnectionProviderTest {

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
        val java = QuarkdownLanguageServerConnectionProvider.resolveJavaExecutable(home)
        assertTrue("java executable should resolve, got null", java != null)
        assertTrue("java executable must exist", java!!.isFile)
    }
}
