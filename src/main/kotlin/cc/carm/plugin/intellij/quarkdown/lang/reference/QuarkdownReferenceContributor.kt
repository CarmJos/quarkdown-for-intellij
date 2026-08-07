package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
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

            // Anchors are computed once per file and cached; each anchor is a
            // (start, end, text, type) tuple in document coordinates.
            val anchors = anchorsOf(psiFile)

            if (element is PsiFile) {
                // File-level references carry the FULL id/path range (document coords).
                // findReferenceAt prefers these so the whole `button-start-action`
                // is underlined/navigable, even though the lexer splits it into leaves.
                return anchors
                    .map { QuarkdownReference(element, it.referenceText, it.referenceType, TextRange(it.start, it.end)) }
                    .toTypedArray()
            }

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

        // ------------------------------------------------------------------
        // Per-file cached anchor computation (delegates to the pure parser)
        // ------------------------------------------------------------------

        private companion object {
            private val ANCHORS_KEY: Key<CachedValue<List<QuarkdownReferenceParser.Anchor>>> =
                Key.create("quarkdown.reference.anchors")
        }

        private fun anchorsOf(psiFile: PsiFile): List<QuarkdownReferenceParser.Anchor> {
            val manager = CachedValuesManager.getManager(psiFile.project)
            return manager.getCachedValue(
                psiFile,
                ANCHORS_KEY,
                CachedValueProvider {
                    CachedValueProvider.Result.create(
                        QuarkdownReferenceParser.computeAnchors(psiFile.text),
                        psiFile // invalidate when the file's PSI changes
                    )
                },
                false
            )
        }
    }
}
