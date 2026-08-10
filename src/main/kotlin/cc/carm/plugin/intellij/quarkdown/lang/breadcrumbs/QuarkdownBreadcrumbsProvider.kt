package cc.carm.plugin.intellij.quarkdown.lang.breadcrumbs

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

/**
 * Breadcrumbs provider that shows the heading hierarchy for Quarkdown documents.
 *
 * With the structured PSI tree, heading nodes naturally nest:
 * - H1 contains H2 contains H3 ...
 * - Content between headings is nested under the preceding heading.
 *
 * The breadcrumbs bar at the bottom of the editor then displays the chain:
 * ```
 * file.qd > Chapter 1 > Section 1.1 > Subsection
 * ```
 *
 * Each breadcrumb is clickable — clicking navigates to that heading.
 */
class QuarkdownBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> =
        arrayOf(QuarkdownLanguage.INSTANCE)

    override fun acceptElement(e: PsiElement): Boolean {
        // Accept the file itself plus any heading node
        return e is PsiFile && e.language == QuarkdownLanguage.INSTANCE
                || e is QuarkdownHeading
    }

    override fun getElementInfo(e: PsiElement): String {
        return when (e) {
            is QuarkdownHeading -> {
                val prefix = "#".repeat(e.level)
                val text = e.headingText.ifEmpty { QuarkdownBundle.message("quarkdown.heading.empty") }
                "$prefix $text"
            }

            is PsiFile -> e.name
            else -> e.text.take(32)
        }
    }

    override fun getElementTooltip(e: PsiElement): String? {
        return when (e) {
            is QuarkdownHeading ->
                QuarkdownBundle.message("quarkdown.breadcrumbs.heading.tooltip", e.level, e.headingText)
            is PsiFile -> e.virtualFile?.path
            else -> null
        }
    }

    override fun getElementIcon(e: PsiElement) = null
}
