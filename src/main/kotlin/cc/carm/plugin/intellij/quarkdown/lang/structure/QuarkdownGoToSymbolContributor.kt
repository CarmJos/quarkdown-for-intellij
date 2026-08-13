package cc.carm.plugin.intellij.quarkdown.lang.structure

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.heading.QuarkdownHeadingSyntax
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
 * Symbol naming rules (to avoid duplicates and ugly names):
 *  - A **heading** contributes its text with the trailing `{#id}` stripped
 *    (e.g. `# Chapter One {#chapter-one}` → `Chapter One`).
 *  - An **id tag on a heading** is folded into the heading itself (it is not a separate
 *    symbol name), so searching the heading text finds the heading — no double entries.
 *  - An **id tag on an image / code block / equation / standalone label** is a symbol
 *    name on its own, because those elements have no text of their own.
 *
 * Display names are formatted for recognizability:
 *  - Heading → `Heading: Chapter One`
 *  - Image id → `Image: logo.png`
 *  - Code-block id → `Code block`
 *  - Equation id → `Equation`
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
                processor.process(SymbolNavigationItem(element, formatDisplayName(file, element)))
            }
        }
    }

    private fun allQuarkdownFiles(scope: GlobalSearchScope): List<PsiFile> {
        val project = scope.project ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, scope)
            .mapNotNull { psiManager.findFile(it) }
    }

    /**
     * Returns the symbol names declared in [file]:
     * heading texts (with `{#id}` stripped) and non-heading `{#id}` values.
     */
    private fun collectSymbolNames(file: PsiFile): Set<String> {
        val names = LinkedHashSet<String>()
        collectHeadings(file).forEach { heading ->
            headingTextOf(heading).takeIf { it.isNotBlank() }?.let { names.add(it) }
        }
        file.text.run {
            for (match in ID_TAG_REGEX.findAll(this)) {
                val offset = match.groups[1]!!.range.first
                // An id tag on a heading line is folded into the heading symbol;
                // standalone ids (image/code/equation/label) are their own symbol.
                if (!isHeadingLineAt(this, offset)) {
                    names.add(match.groupValues[1])
                }
            }
        }
        return names
    }

    /**
     * Returns the PSI elements in [file] that declare the given [name]: either a
     * [QuarkdownHeading] whose text matches, or the leaf at every `{#name}` occurrence.
     * Results are deduplicated by element so the same location is never reported twice.
     */
    private fun findSymbolsByName(file: PsiFile, name: String): List<PsiElement> {
        val result = LinkedHashSet<PsiElement>()
        collectHeadings(file).forEach { heading ->
            if (headingTextOf(heading) == name) result.add(heading)
        }
        // Resolve each `{#name}` occurrence. The leaf is found directly at the offset
        // (do not traverse `psi.children` — it skips leaf tokens). An id on a heading
        // line is folded into the heading element itself.
        val escaped = Regex.escape(name)
        val occurrencePattern = Regex("""\{#\s*($escaped)\s*}""")
        for (match in occurrencePattern.findAll(file.text)) {
            val idStart = match.groups[1]!!.range.first
            val element = if (isHeadingLineAt(file.text, idStart)) {
                headingAtLineStart(file, lineStartOf(file.text, idStart))
            } else {
                file.findElementAt(idStart)
            }
            element?.let { result.add(it) }
        }
        return result.toList()
    }

    /** The heading's display text ([QuarkdownHeading.headingText] already strips `{#id}`). */
    private fun headingTextOf(heading: QuarkdownHeading): String = heading.headingText

    /** True when the line containing [offset] is a Quarkdown heading line. */
    private fun isHeadingLineAt(text: String, offset: Int): Boolean {
        val lineStart = lineStartOf(text, offset)
        val lineEnd = lineEndOf(text, lineStart)
        return QuarkdownHeadingSyntax.parseHeadingLine(text.substring(lineStart, lineEnd)) != null
    }

    /** Returns the heading element whose line starts at [lineStart], or `null`. */
    private fun headingAtLineStart(file: PsiFile, lineStart: Int): QuarkdownHeading? {
        val leaf = file.findElementAt(lineStart) ?: return null
        var parent: PsiElement? = leaf.parent
        while (parent != null && parent !is QuarkdownHeading) parent = parent.parent
        return parent as? QuarkdownHeading
    }

    private fun lineStartOf(text: String, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEndOf(text: String, offset: Int): Int {
        var i = offset
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    /**
     * Builds a human-readable display name for [element].
     *
     * @return e.g. `Heading: Chapter One`, `Image: logo.png`, `Code block`, `Equation`,
     *         or the element text when the context is unknown.
     */
    private fun formatDisplayName(file: PsiFile, element: PsiElement): String {
        if (element is QuarkdownHeading) {
            return "Heading: ${headingTextOf(element)}"
        }
        val offset = element.textOffset
        val text = file.text
        val lineStart = lineStartOf(text, offset)
        val lineEnd = lineEndOf(text, lineStart)
        val line = text.substring(lineStart, lineEnd)

        return when {
            IMAGE_LINE_REGEX.containsMatchIn(line) ->
                "Image: ${IMAGE_LINE_REGEX.find(line)?.groupValues?.get(1)?.substringAfterLast('/') ?: "?"}"

            line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~") ->
                "Code block"

            line.contains('$') -> "Equation"

            else -> element.text
        }
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

        /** Matches an image line, capturing the path. */
        val IMAGE_LINE_REGEX = Regex("""!\s*(?:\([^)]*\)\s*)?\[[^\]]*]\s*\(([^)\s]+)""")
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
    private val displayName: String,
) : NavigationItem {

    private val presentation = object : ItemPresentation {
        override fun getPresentableText(): String = displayName

        override fun getLocationString(): String? = element.containingFile?.name

        override fun getIcon(unused: Boolean): Icon? = QuarkdownIcons.FILE
    }

    override fun getName(): String = displayName

    override fun getPresentation(): ItemPresentation = presentation

    override fun navigate(requestFocus: Boolean) {
        val navigatable = PsiNavigationSupport.getInstance().getDescriptor(element)
            ?: return
        navigatable.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = element.isValid && element.containingFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()
}
