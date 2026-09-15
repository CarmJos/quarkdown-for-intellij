package cc.carm.plugin.intellij.quarkdown.lang.annotator

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

/**
 * Platform-level tests for [QuarkdownLatexAnnotator]: the LaTeX content of equations is
 * colored (silent annotations) and its structural mistakes are reported as problems.
 *
 * The annotator is invoked directly with a proxy [AnnotationHolder] (not through the full
 * highlighting pipeline), so the tests never start the Quarkdown LSP server.
 */
class QuarkdownLatexAnnotatorTest : BasePlatformTestCase() {

    /** A reported problem: its range and (localized) message. */
    private data class Problem(val start: Int, val end: Int, val message: String)

    /** A color applied to a LaTeX token. */
    private data class Color(val start: Int, val end: Int)

    private fun analyse(text: String): Pair<List<Problem>, List<Color>> {
        myFixture.configureByText("test.qd", text)
        val problems = mutableListOf<Problem>()
        val colors = mutableListOf<Color>()
        QuarkdownLatexAnnotator().annotate(myFixture.file, holder(problems, colors))
        return problems to colors
    }

    fun `test a well-formed equation reports no problem and colors its tokens`() {
        val (problems, colors) = analyse("Let \$ x = 1 \$ be the answer.\n")
        assertTrue("well-formed equation must not report problems: $problems", problems.isEmpty())
        assertTrue("equation tokens must be colored, got $colors", colors.isNotEmpty())
    }

    fun `test a latex command is colored at its own range`() {
        val text = "Let \$ \\frac {a} {b} \$ be the ratio.\n"
        val (_, colors) = analyse(text)
        val commandStart = text.indexOf("\\frac")
        assertTrue(
            "expected the command to be colored at $commandStart, got $colors",
            colors.any { it.start == commandStart && it.end == commandStart + "\\frac".length }
        )
    }

    fun `test an unclosed environment is reported`() {
        val text = "\$\$\$\n\\begin{cases} x = 1\n\$\$\$\n"
        val (problems, _) = analyse(text)
        assertEquals("expected one problem, got $problems", 1, problems.size)
        assertTrue(
            "the problem must name the environment, got '${problems[0].message}'",
            problems[0].message.contains("cases")
        )
        // The problem is anchored on the `\begin` command.
        assertEquals(text.indexOf("\\begin"), problems[0].start)
    }

    fun `test an unmatched group is reported`() {
        val (problems, _) = analyse("Let \$ \\frac {a \$ be broken.\n")
        assertEquals("expected one problem, got $problems", 1, problems.size)
    }

    fun `test an unclosed delimiter is reported`() {
        val (problems, _) = analyse("Let \$ x be the velocity.\n")
        assertEquals("expected one problem for the unclosed delimiter, got $problems", 1, problems.size)
    }

    fun `test prose without equations is left untouched`() {
        val (problems, colors) = analyse("It costs \$5 and \$10 today.\n")
        assertTrue("prices must stay plain text: $problems", problems.isEmpty())
        assertTrue("prices must not be colored: $colors", colors.isEmpty())
    }

    fun `test punctuation after an equation keeps the following tags out of the equation`() {
        val text = "For large values of \$ d_k \$, the dot products grow large.\n" +
                "\n" +
                "\$\$\$ {#eq-multihead}\n" +
                "\\begin{aligned}\n" +
                "x = 1\n" +
                "\\end{aligned}\n" +
                "\$\$\$\n" +
                "\n" +
                "## Heading {#heading}\n"
        val (problems, _) = analyse(text)
        assertTrue(
            "the `{#id}` tags and the heading must not be analysed as TeX: $problems",
            problems.isEmpty()
        )
    }

    fun `test a document without dollar signs is skipped`() {
        val (problems, colors) = analyse("# Title\n\nJust prose, no math here.\n")
        assertTrue(problems.isEmpty())
        assertTrue(colors.isEmpty())
    }


    /**
     * Creates a proxy [AnnotationHolder] that records the range of every silent annotation
     * (a color) into [colors] and of every normal annotation (a problem) into [problems],
     * together with the annotation message.
     */
    private fun holder(
        problems: MutableList<Problem>,
        colors: MutableList<Color>,
    ): AnnotationHolder {
        val pendingRange = AtomicReference<TextRange>()
        val pendingMessage = AtomicReference<String>()

        fun builder(record: (TextRange, String) -> Unit): AnnotationBuilder {
            val proxy = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(AnnotationBuilder::class.java)
            ) { self, method, args ->
                when (method.name) {
                    "range" -> {
                        (args?.firstOrNull { it is TextRange } as? TextRange)?.let(pendingRange::set)
                        self
                    }

                    "create", "createAnnotation" -> {
                        pendingRange.get()?.let { record(it, pendingMessage.get().orEmpty()) }
                        null
                    }

                    else -> when (method.returnType) {
                        AnnotationBuilder::class.java -> self
                        Boolean::class.java -> false
                        Int::class.java -> 0
                        else -> null
                    }
                }
            }
            return proxy as AnnotationBuilder
        }

        val problemBuilder = builder { range, message ->
            problems.add(Problem(range.startOffset, range.endOffset, message))
        }
        val colorBuilder = builder { range, _ ->
            colors.add(Color(range.startOffset, range.endOffset))
        }

        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(AnnotationHolder::class.java)
        ) { _, method, args ->
            when (method.name) {
                "newAnnotation" -> {
                    (args?.firstOrNull { it is String } as? String)?.let(pendingMessage::set)
                    problemBuilder
                }

                "newSilentAnnotation" -> {
                    pendingMessage.set("")
                    colorBuilder
                }

                "createWarningAnnotation", "createAnnotation" -> null
                "getCurrentAnnotationSession" -> null
                "isBatchMode" -> true
                else -> null
            }
        } as AnnotationHolder
    }
}
