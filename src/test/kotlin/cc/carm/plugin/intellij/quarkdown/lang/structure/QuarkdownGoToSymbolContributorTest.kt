package cc.carm.plugin.intellij.quarkdown.lang.structure

import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FindSymbolParameters

/**
 * Platform-level tests for [QuarkdownGoToSymbolContributor]: heading text and `{#id}`
 * tags (including on images, code blocks and equations) must be discoverable through
 * `processNames` / `processElementsWithName` and produce navigable items with a
 * non-empty name and presentation.
 */
class QuarkdownGoToSymbolContributorTest : BasePlatformTestCase() {

    private val contributor = QuarkdownGoToSymbolContributor()

    private fun collectNames(): Set<String> {
        val names = mutableSetOf<String>()
        contributor.processNames(
            { names.add(it); true },
            GlobalSearchScope.projectScope(project),
            null
        )
        return names
    }

    private fun itemsFor(name: String): List<NavigationItem> {
        val items = mutableListOf<NavigationItem>()
        contributor.processElementsWithName(
            name,
            { items.add(it); true },
            FindSymbolParameters.wrap(name, project, false)
        )
        return items
    }

    fun `test heading text is a searchable symbol with a proper name`() {
        myFixture.addFileToProject("symbols.qd", "# Chapter One\nSome prose.\n")

        val names = collectNames()
        assertTrue("heading text must be a symbol name, got $names", names.contains("Chapter One"))

        val items = itemsFor("Chapter One")
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("Chapter One", item.name)
        assertEquals("Chapter One", item.presentation?.presentableText)
        assertTrue("symbol must be navigable", item.canNavigate())
    }

    fun `test image id tag is a searchable symbol`() {
        myFixture.addFileToProject("symbols.qd", "![alt](images/logo.png){#img-logo}\n")

        val names = collectNames()
        assertTrue("image id must be a symbol name, got $names", names.contains("img-logo"))

        val items = itemsFor("img-logo")
        assertEquals(1, items.size)
        assertEquals("img-logo", items[0].name)
        assertTrue("image id symbol must be navigable", items[0].canNavigate())
    }

    fun `test code block id tag is a searchable symbol`() {
        myFixture.addFileToProject(
            "symbols.qd",
            "```kotlin {#code-sample}\nval x = 1\n```\n"
        )

        val names = collectNames()
        assertTrue("code id must be a symbol name, got $names", names.contains("code-sample"))

        val items = itemsFor("code-sample")
        assertEquals(1, items.size)
        assertEquals("code-sample", items[0].name)
        assertTrue("code id symbol must be navigable", items[0].canNavigate())
    }

    fun `test equation id tag is a searchable symbol`() {
        myFixture.addFileToProject("symbols.qd", "$ E=mc^2 $ {#eq-energy}\n")

        val names = collectNames()
        assertTrue("equation id must be a symbol name, got $names", names.contains("eq-energy"))

        val items = itemsFor("eq-energy")
        assertEquals(1, items.size)
        assertEquals("eq-energy", items[0].name)
        assertTrue("equation id symbol must be navigable", items[0].canNavigate())
    }

    fun `test returned items are not PsiElementNavigationItem`() {
        // If the item were a PsiElementNavigationItem, SearchEverywhere would unwrap it
        // to the raw PSI element (a heading is not a PsiNamedElement) and render
        // "<unnamed>". A plain NavigationItem with its own presentation avoids that.
        myFixture.addFileToProject("symbols.qd", "# Chapter One\n")

        val items = itemsFor("Chapter One")
        assertEquals(1, items.size)
        assertFalse(
            "must not expose the raw PSI element to the platform",
            items[0] is com.intellij.navigation.PsiElementNavigationItem
        )
    }
}
