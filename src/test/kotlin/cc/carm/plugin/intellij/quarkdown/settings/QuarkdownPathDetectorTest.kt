package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspServerDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [QuarkdownPathDetector.resolveHome] — the logic that maps a configured
 * path (home / `bin/` folder / launcher file) back to the real installation home, fixing
 * the LSP `ClassNotFoundException` caused by launching with a `bin/` classpath.
 */
class QuarkdownPathDetectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Creates a fake Quarkdown installation home: a `lib` folder with `*.jar` files plus `bin` launchers. */
    private fun fakeHome(): File {
        val home = tmp.newFolder("quarkdown-home")
        val lib = File(home, "lib").apply { mkdirs() }
        File(lib, "quarkdown-lsp.jar").writeText("fake jar")
        File(lib, "quarkdown-stdlib.jar").writeText("fake jar")
        val bin = File(home, "bin").apply { mkdirs() }
        for (name in listOf("quarkdown", "quarkdown.cmd", "quarkdown.bat")) {
            File(bin, name).writeText("#!/bin/sh\n")
        }
        return home
    }

    @Test
    fun `blank or nonexistent paths resolve to null`() {
        assertNull(QuarkdownPathDetector.resolveHome(null))
        assertNull(QuarkdownPathDetector.resolveHome(""))
        assertNull(QuarkdownPathDetector.resolveHome("  "))
        assertNull(QuarkdownPathDetector.resolveHome(File(tmp.root, "does-not-exist").absolutePath))
    }

    @Test
    fun `a proper home directory resolves to itself`() {
        val home = fakeHome()
        assertEquals(home.absolutePath, QuarkdownPathDetector.resolveHome(home.absolutePath))
    }

    @Test
    fun `a bin directory resolves back to the installation home`() {
        val home = fakeHome()
        val bin = File(home, "bin")
        // Compare canonical paths: the resolver resolves symlinks (e.g. macOS `/var`
        // -> `/private/var`, Windows 8.3 short names), so the returned home may be the
        // canonical form of the same directory.
        assertEquals(home.canonicalFile.absolutePath, QuarkdownPathDetector.resolveHome(bin.absolutePath))
    }

    @Test
    fun `a launcher file resolves back to the installation home`() {
        val home = fakeHome()
        val launcher = File(File(home, "bin"), "quarkdown")
        assertEquals(home.canonicalFile.absolutePath, QuarkdownPathDetector.resolveHome(launcher.absolutePath))
    }

    @Test
    fun `a directory without lib jars or launcher resolves to null`() {
        val random = tmp.newFolder("random-dir")
        File(random, "readme.txt").writeText("hi")
        assertNull(QuarkdownPathDetector.resolveHome(random.absolutePath))
    }

    @Test
    fun `an empty home without lib jars resolves to null`() {
        val empty = tmp.newFolder("empty-dir")
        assertNull(QuarkdownPathDetector.resolveHome(empty.absolutePath))
    }

    @Test
    fun `hasLspLibraries requires a non-empty lib folder with jars`() {
        val home = fakeHome()
        assertTrue(QuarkdownLspServerDescriptor.hasLspLibraries(home.absolutePath))

        val noLib = tmp.newFolder("no-lib")
        assertFalse(QuarkdownLspServerDescriptor.hasLspLibraries(noLib.absolutePath))

        val emptyLib = tmp.newFolder("empty-lib")
        File(emptyLib, "lib").mkdirs()
        assertFalse(QuarkdownLspServerDescriptor.hasLspLibraries(emptyLib.absolutePath))
    }
}
