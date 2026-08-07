package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement

/**
 * Provides a searchable "name" for Quarkdown PSI elements.
 *
 * Find Usages relies on `ElementDescriptionUtil.getElementDescription(element, NAMES)`
 * to determine the word used by the optimized word search. Plain text leaves in a
 * Quarkdown file have no `PsiNamedElement` name, so without this provider the word
 * search cannot find candidate elements. This provider returns the leaf text, enabling
 * Find Usages to discover `.ref {id}` / `.var` usages.
 */
class QuarkdownElementDescriptionProvider : ElementDescriptionProvider {

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        val file = element.containingFile ?: return null
        if (file.fileType != QuarkdownFileType.INSTANCE) return null
        return element.text?.takeIf { it.isNotBlank() }
    }
}
