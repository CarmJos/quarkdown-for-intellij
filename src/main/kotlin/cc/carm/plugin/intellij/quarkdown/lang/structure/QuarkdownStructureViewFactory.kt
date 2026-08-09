package cc.carm.plugin.intellij.quarkdown.lang.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Factory registered via `lang.psiStructureViewFactory` extension point.
 * Creates a [TreeBasedStructureViewBuilder] that produces the heading
 * hierarchy shown in the Structure tool window (Alt+7 / Cmd+7).
 */
class QuarkdownStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return QuarkdownStructureViewModel(psiFile, editor)
            }

            override fun isRootNodeShown() = false
        }
    }
}
