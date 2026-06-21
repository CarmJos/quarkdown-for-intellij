package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

class QuarkdownCompletionConfidence : CompletionConfidence() {

    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        if (psiFile.fileType == QuarkdownFileType.INSTANCE) {
            return ThreeState.NO
        }
        return ThreeState.UNSURE
    }
}
