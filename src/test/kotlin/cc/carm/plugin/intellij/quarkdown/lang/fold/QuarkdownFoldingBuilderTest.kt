package cc.carm.plugin.intellij.quarkdown.lang.fold

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the Quarkdown folding builder produces the expected fold regions, including
 * the `.var` variable-reference and `.ref` cross-reference folds (CustomFold preview).
 */
class QuarkdownFoldingBuilderTest : BasePlatformTestCase() {

    fun `test var reference folds to its assigned value`() {
        myFixture.configureByText("test.qd", ".var {status} {ok}\n\nCurrent status is .status .")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val varFolds = descriptors.filter { it.placeholderText == "ok" }
        System.out.println("var folds: ${varFolds.map { document.getText(it.range) to it.placeholderText }}")
        assertEquals("should fold the .status reference", 1, varFolds.size)
        // The fold covers the raw reference including the leading dot.
        assertEquals(".status", document.getText(varFolds[0].range))
        // It should be collapsed by default so the value is directly visible.
        assertEquals(true, varFolds[0].isCollapsedByDefault)
    }

    fun `test var reference fold respects case-insensitive declaration`() {
        myFixture.configureByText("test.qd", ".var {Version} {1.0}\n\nCurrent version is .version .")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val varFolds = descriptors.filter { it.placeholderText == "1.0" }
        assertEquals("should fold the lowercase reference to the declared value", 1, varFolds.size)
        assertEquals(".version", document.getText(varFolds[0].range))
    }

    fun `test no fold for undeclared variable references`() {
        myFixture.configureByText("test.qd", "This uses .unknown but nothing is declared.")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()
        assertTrue(
            "no variable folds should exist, got: ${descriptors.map { it.placeholderText }}",
            descriptors.none { it.placeholderText == "unknown" }
        )
    }

    fun `test section folding still works alongside var folds`() {
        myFixture.configureByText(
            "test.qd",
            "# Title {#t}\n\n.var {status} {ok}\n\nCurrent status is .status .\n\nMore text."
        )
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val varFolds = descriptors.filter { it.placeholderText == "ok" }
        assertEquals("should fold the .status reference", 1, varFolds.size)
        // Section folds still present (heading → next heading / EOF).
        val sectionFolds = descriptors.filter { it.placeholderText?.startsWith("...") == true }
        assertTrue("section folds should still be produced", sectionFolds.isNotEmpty())
    }

    // ------------------------------------------------------------------
    // .ref cross-reference folds
    // ------------------------------------------------------------------

    fun `test ref folds to Reference id when target is unresolvable`() {
        myFixture.configureByText("test.qd", "See .ref {first} for details.")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val refFolds = descriptors.filter { it.placeholderText == "Reference(first)" }
        System.out.println("ref folds: ${refFolds.map { document.getText(it.range) to it.placeholderText }}")
        assertEquals("should fold the .ref reference", 1, refFolds.size)
        assertEquals(".ref {first}", document.getText(refFolds[0].range))
        assertEquals(true, refFolds[0].isCollapsedByDefault)
    }

    fun `test ref folds to table caption`() {
        myFixture.configureByText(
            "test.qd",
            "As shown in .ref {data}.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Beverage preferences\" {#data}\n"
        )
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val refFolds = descriptors.filter { it.placeholderText == "Table Beverage preferences" }
        assertEquals("should fold .ref {data} to its table caption", 1, refFolds.size)
        assertEquals(".ref {data}", document.getText(refFolds[0].range))
    }

    fun `test ref folds to heading caption`() {
        myFixture.configureByText("test.qd", "See .ref {intro}.\n\n## Introduction {#intro}\n")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val refFolds = descriptors.filter { it.placeholderText == "Section Introduction" }
        assertEquals("should fold .ref {intro} to its heading text", 1, refFolds.size)
    }

    fun `test no ref fold inside a fenced code block`() {
        myFixture.configureByText("test.qd", "```\n.ref {first}\n```\n")
        val file = myFixture.file
        val document = file.viewProvider.document!!
        val builder = QuarkdownFoldingBuilder()
        val descriptors = builder.buildFoldRegions(file, document, false).toList()

        val refFolds = descriptors.filter { it.placeholderText?.startsWith("Reference") == true }
        assertTrue("no .ref fold inside fenced code block, got: $refFolds", refFolds.isEmpty())
    }
}
