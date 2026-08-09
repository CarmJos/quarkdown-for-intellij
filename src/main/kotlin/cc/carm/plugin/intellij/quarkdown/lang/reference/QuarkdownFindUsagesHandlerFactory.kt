package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement

/**
 * [FindUsagesHandlerFactory] for Quarkdown elements.
 *
 * Registers [QuarkdownFindUsagesHandler] so the standard Find Usages action, the
 * Show Usages popup (Ctrl+Shift+F7) and `ShowUsagesAction.startFindUsages` all work
 * for `.ref {id}` / `{#id}` / `.var` references, rendering file + line + context.
 *
 * Only elements that sit inside a reference anchor are handled; everything else is
 * left to the default factory. PSI access is wrapped in [ReadAction] because the
 * factory may be consulted from a background thread.
 */
class QuarkdownFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val file = element.containingFile ?: return@compute false
        if (file.fileType != QuarkdownFileType.INSTANCE) return@compute false

        val range = element.textRange
        QuarkdownReferenceAnchors.of(file).any {
            it.overlaps(range.startOffset, range.endOffset)
        }
    }

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean
    ): FindUsagesHandler = QuarkdownFindUsagesHandler(element)
}