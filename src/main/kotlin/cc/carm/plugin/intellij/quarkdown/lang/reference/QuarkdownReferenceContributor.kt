package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext

class QuarkdownReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Match all PSI elements in Quarkdown files (handled via text scanning)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement().withLanguage(QuarkdownLanguage.INSTANCE),
            QuarkdownReferenceProvider()
        )
    }

    class QuarkdownReferenceProvider : PsiReferenceProvider() {

        override fun getReferencesByElement(
            element: PsiElement,
            context: ProcessingContext
        ): Array<PsiReference> {
            val psiFile = element.containingFile ?: return PsiReference.EMPTY_ARRAY
            if (psiFile.fileType != QuarkdownFileType.INSTANCE) {
                return PsiReference.EMPTY_ARRAY
            }

            // CRITICAL: Only provide references for leaf elements (and the file itself).
            // Composite elements (e.g. HEADING) span whole paragraphs and overlap every
            // anchor, which would make EVERY piece of text navigable (Ctrl+Click underline).
            if (element is PsiFile) {
                val anchors = QuarkdownReferenceAnchors.of(psiFile)
                return anchors
                    .map {
                        QuarkdownReference(
                            element,
                            it.referenceText,
                            it.referenceType,
                            TextRange(it.start, it.end)
                        )
                    }
                    .toTypedArray()
            }
            if (element !is LeafPsiElement) return PsiReference.EMPTY_ARRAY

            val anchors = QuarkdownReferenceAnchors.of(psiFile)

            val elemStart = element.textRange.startOffset
            val elemEnd = elemStart + element.textLength

            // Leaf-level references (for Find Usages) are attached to the leaf that
            // overlaps the anchor, with the range mapped into leaf-local coordinates.
            val result = anchors
                .filter { it.overlaps(elemStart, elemEnd) }
                .map { anchor ->
                    QuarkdownReference(
                        element,
                        anchor.referenceText,
                        anchor.referenceType,
                        TextRange(
                            maxOf(elemStart, anchor.start) - elemStart,
                            minOf(elemEnd, anchor.end) - elemStart
                        )
                    )
                }
                .toList()
            return result.toTypedArray()
        }
    }
}
