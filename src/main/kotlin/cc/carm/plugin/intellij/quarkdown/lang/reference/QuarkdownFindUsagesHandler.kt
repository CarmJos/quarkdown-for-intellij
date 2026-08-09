package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Standard Find Usages handler for Quarkdown `.ref {id}` / `{#id}` / `.var` references.
 *
 * Used by the platform's [com.intellij.find.actions.ShowUsagesAction] (Ctrl+Shift+F7),
 * the Find Usages action (Alt+F7) and the usage popup. It scans every Quarkdown file,
 * recomputes the reference anchors and reports one [UsageInfo] per matching anchor. The
 * platform then renders each usage with its file, line number and surrounding code, exactly
 * like Java method/class usages.
 *
 * `processElementUsages` runs on a background progress thread, so every PSI access is
 * wrapped in [ReadAction].
 */
class QuarkdownFindUsagesHandler(psiElement: PsiElement) : FindUsagesHandler(psiElement) {

    override fun getPrimaryElements(): Array<PsiElement> = arrayOf(myPsiElement)

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        val targetId = targetIdOf(element) ?: return@compute true

        val scope = options.searchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(element.project)
        val psiManager = PsiManager.getInstance(element.project)
        val files = FileTypeIndex.getFiles(QuarkdownFileType.INSTANCE, scope)
            .mapNotNull { psiManager.findFile(it) }

        for (file in files) {
            for (anchor in QuarkdownReferenceAnchors.of(file)) {
                if (anchor.referenceType !in ID_REFERENCE_TYPES) continue
                if (!anchor.referenceText.trim().equals(targetId, ignoreCase = true)) continue

                val usage = usageInfoAt(file, anchor.start, anchor.end) ?: continue
                if (!processor.process(usage)) return@compute false
            }
        }
        true
    }

    /** Returns the id of the anchor overlapping [element], read-access safe. */
    private fun targetIdOf(element: PsiElement): String? {
        if (!element.isValid) return null
        val targetFile = element.containingFile ?: return null
        return QuarkdownReferenceAnchors.of(targetFile)
            .firstOrNull { it.overlaps(element.textRange.startOffset, element.textRange.endOffset) }
            ?.referenceText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun usageInfoAt(file: PsiFile, start: Int, end: Int): UsageInfo? {
        val leaf = file.findElementAt(start) ?: return null
        val leafStart = leaf.textRange.startOffset
        val localStart = (start - leafStart).coerceIn(0, leaf.textLength)
        val localEnd = (end - leafStart).coerceIn(localStart, leaf.textLength)
        return UsageInfo(leaf, localStart, localEnd)
    }

    private companion object {
        val ID_REFERENCE_TYPES = setOf("ref", "label", "var", "var-decl")
    }
}