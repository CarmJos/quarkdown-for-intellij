package cc.carm.plugin.intellij.quarkdown.lang.function

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.parseCall
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator.Issue
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator.Severity
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownCallValidatorTest {

    // Real-world shape of the reflected metadata (quarkdown naming: underscores stripped).
    private val positionValues = listOf(
        "topleftcorner", "topleft", "topcenter", "topright", "toprightcorner",
        "righttop", "rightmiddle", "rightbottom",
        "bottomrightcorner", "bottomright", "bottomcenter", "bottomleft", "bottomleftcorner",
        "leftbottom", "leftmiddle", "lefttop",
        "topoutsidecorner", "topoutside", "bottomoutsidecorner", "bottomoutside",
        "topinsidecorner", "topinside", "bottominsidecorner", "bottominside"
    )

    private val functions = listOf(
        FunctionMetadata(
            name = "pagemargin",
            parameters = listOf(
                ParameterMetadata("position", "pagemarginposition", 0, allowedValues = positionValues),
                ParameterMetadata("content", "markdowncontent", 1)
            )
        ),
        FunctionMetadata(
            name = "doctype",
            parameters = listOf(
                ParameterMetadata(
                    "type", "documenttype", 0, isOptional = true,
                    allowedValues = listOf("plain", "paged", "slides", "docs")
                )
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
    fun `valid positional enum value produces no error`() {
        val call = parseCall(".pagemargin {bottomcenter}", 0)!!
        val issues = validate(call, functions)
        assertEquals(emptyList<Issue>(), issues.filter { it.severity == Severity.ERROR })
    }

    @Test
    fun `valid named enum value produces no error`() {
        val call = parseCall(".pagemargin position:{bottomcenter} content:{hello}", 0)!!
        val issues = validate(call, functions)
        assertEquals(emptyList<Issue>(), issues.filter { it.severity == Severity.ERROR })
    }

    @Test
    fun `snake-case enum value is invalid`() {
        val call = parseCall(".pagemargin position:{bottom_center}", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("bottom_center"))
        assertTrue(errors[0].message.contains("bottomcenter"))
    }

    @Test
    fun `unknown function is reported`() {
        val call = parseCall(".notafunction {x}", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Unknown function 'notafunction'"))
    }

    @Test
    fun `unknown named parameter is reported`() {
        val call = parseCall(".pagemargin positionx:{topcenter} content:{hello}", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertTrue(errors.any { it.message.contains("Unknown parameter 'positionx'") })
    }

    @Test
    fun `positional after named is reported`() {
        val call = parseCall(".multiply by:{3} {6}", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertTrue(errors.any { it.message.contains("following a named argument") })
    }

    @Test
    fun `missing required arguments produce a warning`() {
        val call = parseCall(".pagemargin {bottomcenter}", 0)!!
        val warnings = validate(call, functions).filter { it.severity == Severity.WARNING }
        assertTrue(warnings.any { it.message.contains("Expected 2 arguments") })
    }

    @Test
    fun `optional-only function with positional arg has no missing-arg warning`() {
        val call = parseCall(".doctype {paged}", 0)!!
        val warnings = validate(call, functions).filter { it.severity == Severity.WARNING }
        assertEquals(emptyList<Issue>(), warnings)
    }

    @Test
    fun `positional argument with braces around value is normalized`() {
        val call = parseCall(".pagemargin {{bottomcenter}} content:{hello}", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertEquals(emptyList<Issue>(), errors.filter { it.message.startsWith("Invalid value") })
    }

    @Test
    fun `positional arguments map to params in order`() {
        val fn = functions.first { it.name == "multiply" }
        val call = parseCall(".multiply {6} {3}", 0)!!
        val (resolved, issues) = QuarkdownCallValidator.resolveArgs(call, fn)
        assertEquals(0, issues.size)
        assertEquals("a", resolved[0].param!!.name)
        assertEquals("by", resolved[1].param!!.name)
        assertEquals(0, resolved[0].positionalIndex)
        assertEquals(1, resolved[1].positionalIndex)
    }

    @Test
    fun `mixed positional then named resolves correctly`() {
        val fn = functions.first { it.name == "multiply" }
        val call = parseCall(".multiply {6} by:{3}", 0)!!
        val (resolved, issues) = QuarkdownCallValidator.resolveArgs(call, fn)
        assertEquals(0, issues.size)
        assertEquals("a", resolved[0].param!!.name)
        assertEquals("by", resolved[1].param!!.name)
    }

    @Test
    fun `normalizeValue strips braces and quotes`() {
        assertEquals("bottomcenter", QuarkdownCallValidator.normalizeValue("{bottomcenter}"))
        assertEquals("paged", QuarkdownCallValidator.normalizeValue("paged"))
        assertEquals("hello", QuarkdownCallValidator.normalizeValue("\"hello\""))
        assertEquals("hello", QuarkdownCallValidator.normalizeValue("{'hello'}"))
    }

    @Test
    fun `chained call maps explicit args after the implicit first`() {
        val fn = functions.first { it.name == "multiply" }
        val text = ".sum {10} {5}::multiply {2}"
        val start = QuarkdownCallParser.findCallStart(text, text.length - 1)
        val call = QuarkdownCallParser.parseCall(text, start)!!
        assertTrue(call.isChained)
        val (resolved, issues) = QuarkdownCallValidator.resolveArgs(call, fn)
        assertEquals(0, issues.size)
        // `{2}` is the second positional argument (index 1) → param `by`
        assertEquals("by", resolved[0].param!!.name)
        assertEquals(1, resolved[0].positionalIndex)
    }

    @Test
    fun `declared variable reference is not an unknown function`() {
        val call = parseCall(".version", 0)!!
        val errors = validate(call, functions, knownVariables = setOf("version"))
            .filter { it.severity == Severity.ERROR }
        assertEquals(emptyList<Issue>(), errors)
    }

    @Test
    fun `undeclared dot-name is an unknown function`() {
        val call = parseCall(".version", 0)!!
        val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Unknown function 'version'"))
    }

    @Test
    fun `body argument satisfies the missing content parameter`() {
        val call = parseCall(".pagemargin {bottomcenter}\n    .currentpage", 0)!!
        assertTrue(call.hasBodyArgument)
        val warnings = validate(call, functions).filter { it.severity == Severity.WARNING }
        assertEquals(emptyList<Issue>(), warnings)
    }

    @Test
    fun `without body argument the missing content is still warned`() {
        val call = parseCall(".pagemargin {bottomcenter}", 0)!!
        assertFalse(call.hasBodyArgument)
        val warnings = validate(call, functions).filter { it.severity == Severity.WARNING }
        assertTrue(warnings.any { it.message.contains("Expected 2 arguments") })
    }

    @Test
    fun `quarkdown enum names are accepted`() {
        // Real quarkdown naming: underscores are stripped (BOTTOM_CENTER → bottomcenter).
        for (value in listOf("bottomcenter", "topleftcorner", "topinside")) {
            val call = parseCall(".pagemargin {$value}", 0)!!
            val errors = validate(call, functions).filter { it.severity == Severity.ERROR }
            assertEquals(emptyList<Issue>(), errors)
        }
    }
}
