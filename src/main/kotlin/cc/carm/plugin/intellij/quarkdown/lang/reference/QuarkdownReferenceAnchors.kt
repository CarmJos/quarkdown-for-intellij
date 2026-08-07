package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Per-file cached reference anchors for a Quarkdown document.
 *
 * Both [QuarkdownPsiFile.getReferences] and [QuarkdownReferenceContributor] need the
 * anchors computed by [QuarkdownReferenceParser.computeAnchors]; this caches the result
 * on the PSI file and invalidates it whenever the file's PSI changes.
 */
object QuarkdownReferenceAnchors {

    private val ANCHORS_KEY: Key<CachedValue<List<QuarkdownReferenceParser.Anchor>>> =
        Key.create("quarkdown.reference.anchors")

    fun of(psiFile: PsiFile): List<QuarkdownReferenceParser.Anchor> {
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
