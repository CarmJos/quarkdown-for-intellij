package cc.carm.plugin.intellij.quarkdown.lang.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuarkdownLspFunctionSignatureCacheTest {

    @Test
    fun `parses single-line signature`() {
        val md = """
            ```block lang-kotlin
            .multiply a:{Number} by:{Number} -> Number
            ```
        """.trimIndent()
        val sig = parseFunctionSignature(md)
        assertEquals(listOf("a", "by"), sig?.parameterNames)
        assertEquals(".multiply a:{Number} by:{Number} -> Number", sig?.signatureText)
        assertEquals("multiply", sig?.name)
    }

    @Test
    fun `parses multi-line signature with continuations`() {
        val md = """
            ```block lang-kotlin
            .pageformat side:{PageSide? = null} \
                     pages:{Range? = null} \
                      size:{PageSizeFormat? = null} \
               orientation:{PageOrientation = context.documentInfo.type.preferredOrientation} \
                     width:{Size? = null} \
                    height:{Size? = null} \
                    margin:{Sizes? = null}
            -> Void
            ```
        """.trimIndent()
        assertEquals(
            listOf("side", "pages", "size", "orientation", "width", "height", "margin"),
            parseFunctionSignature(md)?.parameterNames
        )
    }

    @Test
    fun `parses single optional parameter`() {
        val md = """
            ```block lang-kotlin
            .docauthor author:{String? = null} -> Any
            ```
        """.trimIndent()
        assertEquals(listOf("author"), parseFunctionSignature(md)?.parameterNames)
    }

    @Test
    fun `parses body-style function`() {
        val md = """
            ```block lang-kotlin
            .row alignment:{Stacked.MainAxisAlignment = Stacked.MainAxisAlignment.START} \
                 cross:{Stacked.CrossAxisAlignment = Stacked.CrossAxisAlignment.CENTER} \
                   gap:{Sizes? = null} \
                  body:{Any}
            ```
        """.trimIndent()
        assertEquals(
            listOf("alignment", "cross", "gap", "body"),
            parseFunctionSignature(md)?.parameterNames
        )
    }

    @Test
    fun `returns null when no signature block`() {
        assertNull(parseFunctionSignature("No code block here"))
    }

    @Test
    fun `returns null for a getter with no parameters`() {
        // A getter renders only the function name with no `name:{` groups.
        val md = """
            ```block lang-kotlin
            .currentpage -> Int
            ```
        """.trimIndent()
        assertNull(parseFunctionSignature(md)?.parameterNames)
    }

    @Test
    fun `deduplicates repeated parameter names`() {
        val md = """
            ```block lang-kotlin
            .row alignment:{A} cross:{B} alignment:{A} body:{C}
            ```
        """.trimIndent()
        assertEquals(
            listOf("alignment", "cross", "body"),
            parseFunctionSignature(md)?.parameterNames
        )
    }
}
