package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewTypeLocation

/**
 * Provides a searchable "name" for Quarkdown PSI elements.
 *
 * Find Usages relies on `ElementDescriptionUtil.getElementDescription(element, NAMES)`
 * to determine the word used by the optimized word search. Plain text leaves in a
 * Quarkdown file have no `PsiNamedElement` name, so without this provider the word
 * search cannot find candidate elements.
 *
 * The returned name is the BARE id (`plc-symbol-output`), not the token text
 * (`{#plc-symbol-output}` / `{plc-symbol-output}`), so the Find Usages tool window
 * and search key show the bare id.
 *
 * For [UsageViewTypeLocation] (the type part of the Show Usages window title) it
 * returns `References`, so the title renders as the grey "References" followed by the
 * white id instead of repeating the id twice.
 */
class QuarkdownElementDescriptionProvider : ElementDescriptionProvider {

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val file = element.containingFile ?: return null
        if (file.fileType != QuarkdownFileType.INSTANCE) return null

        // The Show Usages window title's type part: "References".
        if (location === UsageViewTypeLocation.INSTANCE) return "References"

        val text = element.text?.trim() ?: return null
        if (text.isEmpty()) return null
        return when {
            text.startsWith("{#") && text.endsWith("}") -> text.substring(2, text.length - 1)
            text.startsWith("{") && text.endsWith("}") -> text.substring(1, text.length - 1)
            else -> text
        }
    }
}
