package cc.carm.plugin.intellij.quarkdown.lang.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [QuarkdownCli] command-line building and argument tokenization.
 * These are pure string/argument transformations and don't need an IDE platform.
 */
class QuarkdownCliTest {

    private val exe = File("C:/quarkdown/bin/quarkdown.cmd")

    @Test
    fun `preview server args build the expected command line`() {
        val args = QuarkdownCli.previewServerArgs(
            executable = exe,
            source = File("C:/docs/main.qd"),
            port = 8989,
            outputDir = File("C:/out"),
            watch = true,
            extraArgs = null,
        )
        assertEquals(
            listOf(
                exe.absolutePath,
                "compile",
                File("C:/docs/main.qd").absolutePath,
                "--preview",
                "--watch",
                "--server-port",
                "8989",
                "-o",
                File("C:/out").absolutePath,
                "--browser",
                "none",
            ),
            args
        )
    }

    @Test
    fun `preview server args without watch omits the watch flag`() {
        val args = QuarkdownCli.previewServerArgs(
            executable = exe,
            source = File("C:/docs/main.qd"),
            port = 9000,
            outputDir = File("C:/out"),
            watch = false,
            extraArgs = null,
        )
        assertTrue("must not contain --watch", "--watch" !in args)
        assertTrue(args.contains("9000"))
    }

    @Test
    fun `preview server args keep user browser option`() {
        val args = QuarkdownCli.previewServerArgs(
            executable = exe,
            source = File("main.qd"),
            port = 8989,
            outputDir = File("out"),
            watch = false,
            extraArgs = "--browser chrome",
        )
        // User explicitly configured a browser → plugin must not append --browser none.
        assertTrue("--browser none must not be injected", "--browser" !in args || "none" !in args)
        assertTrue(args.contains("chrome"))
    }

    @Test
    fun `build run args use pdf output`() {
        val args = QuarkdownCli.buildRunArgs(
            executable = exe,
            source = File("C:/docs/main.qd"),
            outputDir = File("C:/out"),
            extraArgs = null,
        )
        assertEquals(
            listOf(
                exe.absolutePath,
                "compile",
                File("C:/docs/main.qd").absolutePath,
                "--pdf",
                "-o",
                File("C:/out").absolutePath,
            ),
            args
        )
    }

    @Test
    fun `build run args do not duplicate an existing output option`() {
        val args = QuarkdownCli.buildRunArgs(
            executable = exe,
            source = File("main.qd"),
            outputDir = File("out"),
            extraArgs = "-o /custom/out",
        )
        assertEquals(1, args.count { it == "-o" })
        assertTrue(args.contains("/custom/out"))
        assertTrue("out" !in args)
    }

    @Test
    fun `tokenize handles quoted arguments`() {
        val tokens = QuarkdownCli.tokenizeArguments("--allow '*.png' --name \"My Doc\"")
        assertEquals(listOf("--allow", "*.png", "--name", "My Doc"), tokens)
    }

    @Test
    fun `tokenize returns empty for blank input`() {
        assertTrue(QuarkdownCli.tokenizeArguments(null).isEmpty())
        assertTrue(QuarkdownCli.tokenizeArguments("   ").isEmpty())
    }
}
