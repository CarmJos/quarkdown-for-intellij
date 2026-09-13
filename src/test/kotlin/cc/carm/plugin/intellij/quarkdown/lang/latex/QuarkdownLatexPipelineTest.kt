package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end check of the LaTeX support through the **real daemon pipeline** (lexer and the
 * annotators registered in plugin.xml), instead of calling the annotator with a proxy holder.
 *
 * This is what catches an extension that never runs in the IDE, and it pins the behaviour the
 * users care about: a `$ … $` equation, a `$$$` block and a `.math` body all get their commands
 * colored and their structural problems reported.
 */
class QuarkdownLatexPipelineTest : BasePlatformTestCase() {

    private fun highlightsAt(text: String, snippet: String): List<HighlightInfo> {
        myFixture.configureByText("test.qd", text)
        val infos = myFixture.doHighlighting()
        val offset = text.indexOf(snippet)
        return infos.filter { it.startOffset <= offset && it.endOffset >= offset + snippet.length }
    }

    fun `test an inline equation command is colored`() {
        val hits = highlightsAt("Let \$ \\frac {a} {b} \$ be the ratio.\n", "\\frac")
        assertTrue(
            "expected a LaTeX color on \\frac, got ${hits.map { it.forcedTextAttributesKey }}",
            hits.any { it.forcedTextAttributesKey?.externalName?.startsWith("QUARKDOWN_LATEX") == true },
        )
    }

    fun `test a fenced equation command is colored as one token`() {
        // Regression: the Quarkdown lexer splits `\begin` into the escape `\b` plus `egin`, so
        // the whole environment must be colored by the LaTeX annotator instead.
        val hits = highlightsAt("\$\$\$\n\\begin{aligned}\nx = 1\n\\end{aligned}\n\$\$\$\n", "\\begin{aligned}")
        assertTrue(
            "expected one LaTeX color span over \\begin{aligned}, got ${hits.map { it.forcedTextAttributesKey }}",
            hits.any { it.forcedTextAttributesKey?.externalName == "QUARKDOWN_LATEX_ENVIRONMENT" },
        )
    }

    fun `test a math body command is colored`() {
        val hits = highlightsAt(".math\n    \\frac {a} {b}\n", "\\frac")
        assertTrue(
            "expected a LaTeX color inside the .math body, got ${hits.map { it.forcedTextAttributesKey }}",
            hits.any { it.forcedTextAttributesKey?.externalName?.startsWith("QUARKDOWN_LATEX") == true },
        )
    }

    fun `test an unclosed environment is reported as an error`() {
        myFixture.configureByText("test.qd", "\$\$\$\n\\begin{cases} x\n\$\$\$\n")
        val reported = myFixture.doHighlighting()
            .any { it.description?.contains("cases") == true && it.severity.toString().contains("ERROR") }
        assertTrue("expected the unclosed environment to be reported", reported)
    }

    fun `test prose without equations is not touched`() {
        val hits = highlightsAt("It costs \$5 and \$10 today.\n", "costs")
        assertTrue(
            "prices must stay plain text, got ${hits.map { it.forcedTextAttributesKey }}",
            hits.none { it.forcedTextAttributesKey?.externalName?.startsWith("QUARKDOWN_LATEX") == true },
        )
    }
}

