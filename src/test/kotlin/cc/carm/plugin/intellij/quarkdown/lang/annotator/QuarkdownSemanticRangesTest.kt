package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.ParameterMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.highlighter.QuarkdownSyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure semantic-highlight computation ([QuarkdownSemanticRanges]).
 * Uses the real metadata shape reflected from the Quarkdown stdlib (lowercase names,
 * enum values with underscores stripped).
 */
class QuarkdownSemanticRangesTest {

    private val functions = listOf(
        FunctionMetadata(
            name = "pagemargin",
            parameters = listOf(
                ParameterMetadata(
                    "position", "pagemarginposition", 0,
                    allowedValues = listOf("bottomcenter", "topleft", "topright")
                ),
                ParameterMetadata("content", "markdowncontent", 1, isOptional = true)
            )
        ),
        FunctionMetadata(
            name = "multiply",
            parameters = listOf(
                ParameterMetadata("a", "number", 0),
                ParameterMetadata("by", "number", 1)
            )
        )
    )

    @Test
    fun `known function name is highlighted`() {
        val text = ".pagemargin {bottomcenter}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        val fn = ranges.find { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION }
        assertTrue("known function should be highlighted", fn != null)
        assertEquals(".pagemargin".substring(1).length, fn!!.end - fn.start)
    }

    @Test
    fun `valid enum value is highlighted`() {
        val text = ".pagemargin {bottomcenter}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        val enum = ranges.find { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM }
        assertTrue("valid enum value should be highlighted", enum != null)
        assertEquals("bottomcenter", text.substring(enum!!.start, enum.end))
    }

    @Test
    fun `invalid enum value is not highlighted as valid`() {
        val text = ".pagemargin {bottom_center}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        assertTrue(
            "invalid enum value must not be highlighted as valid",
            ranges.none { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM }
        )
    }

    @Test
    fun `named parameter name is highlighted`() {
        val text = ".pagemargin position:{topleft} content:{hello}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        val param = ranges.find { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER }
        assertTrue("named parameter name should be highlighted", param != null)
        assertEquals("position", text.substring(param!!.start, param.end))
    }

    @Test
    fun `variable reference is highlighted instead of known function`() {
        val text = ".var {version} {1.0}\n\nVersion .version"
        val variables = QuarkdownCallParser.findVarDeclarations(text)
        assertTrue(variables.containsKey("version"))
        val ranges = QuarkdownSemanticRanges.compute(text, functions, variables)
        val varRef = ranges.find { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_VARIABLE_REF }
        assertTrue("declared variable reference should be highlighted", varRef != null)
        assertEquals("version", text.substring(varRef!!.start, varRef.end))
    }

    @Test
    fun `unknown function is not highlighted`() {
        val text = ".notafunction {x}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        assertTrue(
            "unknown functions must not be highlighted as known",
            ranges.none { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION }
        )
    }

    @Test
    fun `multiple positional args of known function are resolved`() {
        val text = ".multiply {6} {3}"
        val ranges = QuarkdownSemanticRanges.compute(text, functions, emptyMap())
        assertTrue(
            "known function should be highlighted once",
            ranges.count { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION } == 1
        )
        // No named args → no parameter highlights.
        assertTrue(ranges.none { it.key == QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER })
    }
}

