package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import javax.swing.Icon

/**
 * Registers Quarkdown headings (and `{#id}` element IDs) as searchable symbols,
 * so `Ctrl+Alt+Shift+O` (Search Everywhere → Symbols) can find them project-wide.
 *
 * Headings are collected from every `.qd` file in the project scope by walking the
 * PSI tree for [QuarkdownHeading] nodes. `{#id}` tags are located by scanning the raw
 * file text with a regular expression and resolving the PSI leaf at the matched offset
 * (the PSI `children` walk cannot see leaf tokens — [com.intellij.extapi.psi.ASTDelegatePsiElement]
 * only returns composite children).
 */
class QuarkdownGoToSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(
        processor: Processor<in String>,
        scope: GlobalSearchScope,
        idFilter: IdFilter?,
    ) {
        for (file in allQuarkdownFiles(scope)) {
            collectSymbolNames(file).forEach { name -> processor.process(name) }
        }
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters,
    ) {
        val scope = parameters.searchScope
        for (file in allQuarkdownFiles(scope)) {
            for (element in findSymbolsByName(file, name)) {
                processor.process(SymbolNavigationItem(element, name))
            }
        }
    }

    private fun allQuarkdownFiles(scope: GlobalSearchScope): List<PsiFile> {
        val project = scope.project ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, scope)
            .mapNotNull { psiManager.findFile(it) }
    }

    /** Returns the symbol names (heading text and `{#id}` values) declared in [file]. */
    private fun collectSymbolNames(file: PsiFile): Set<String> {
        val names = LinkedHashSet<String>()
        collectHeadings(file).forEach { heading ->
            heading.headingText.takeIf { it.isNotBlank() }?.let { names.add(it) }
        }
        file.text.run {
            ID_TAG_REGEX.findAll(this).forEach { match ->
                names.add(match.groupValues[1])
            }
        }
        return names
    }

    /**
     * Returns the PSI elements in [file] that declare the given [name]: either a
     * [QuarkdownHeading] whose text matches, or the leaf at every `{#id}` occurrence.
     */
    private fun findSymbolsByName(file: PsiFile, name: String): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        collectHeadings(file).forEach { heading ->
            if (heading.headingText == name) result.add(heading)
        }
        // Resolve the PSI leaf at each `{#name}` occurrence. The leaf is the ID_TAG
        // token (a PsiNamedElement), found directly at the offset — do not traverse
        // `psi.children` here, because it skips leaf tokens.
        val escaped = Regex.escape(name)
        val occurrencePattern = Regex("""\{#\s*($escaped)\s*}""")
        for (match in occurrencePattern.findAll(file.text)) {
            val idStart = match.groups[1]!!.range.first
            file.findElementAt(idStart)?.let { result.add(it) }
        }
        return result
    }

    private fun collectHeadings(file: PsiFile): List<QuarkdownHeading> {
        val result = mutableListOf<QuarkdownHeading>()
        val stack = ArrayDeque<PsiElement>()
        stack.add(file)
        while (stack.isNotEmpty()) {
            val element = stack.removeLast()
            if (element is QuarkdownHeading) result.add(element)
            element.children.forEach { stack.addLast(it) }
        }
        return result
    }

    private companion object {
        /** Matches `{#id}` element ID tags. */
        val ID_TAG_REGEX = Regex("""\{#([a-zA-Z0-9_\-]+)\}""")
    }
}

/**
 * Navigation item for a symbol found via the contributor.
 *
 * This is a plain [NavigationItem] (NOT a [com.intellij.navigation.PsiElementNavigationItem]):
 * the Search Everywhere symbol tab unwraps `PsiElementNavigationItem.getTargetElement()` and
 * renders/navigates the raw PSI element, which is wrong for our elements (a heading is not a
 * `PsiNamedElement`, so it would display as `<unnamed>` and fail to navigate). Presenting the
 * name here and navigating through [PsiNavigationSupport] keeps the display and navigation
 * working regardless of how the platform consumes the item.
 */
private class SymbolNavigationItem(
    private val element: PsiElement,
    private val name: String,
) : NavigationItem {

    private val presentation = object : ItemPresentation {
        override fun getPresentableText(): String = name

        override fun getLocationString(): String? = element.containingFile?.name

        override fun getIcon(unused: Boolean): Icon? = QuarkdownIcons.FILE
    }

    override fun getName(): String = name

    override fun getPresentation(): ItemPresentation = presentation

    override fun navigate(requestFocus: Boolean) {
        val navigatable = PsiNavigationSupport.getInstance().getDescriptor(element)
            ?: return
        navigatable.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = element.isValid && element.containingFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()
}
