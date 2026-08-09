package cc.carm.plugin.intellij.quarkdown.lang.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * ViewModel for the Quarkdown Structure View.
 *
 * Uses [StructureViewModelBase] which handles filtering, sorting, and
 * auto-expansion. The tree is built from [QuarkdownStructureTreeElement]
 * nodes that walk the PSI heading hierarchy.
 */
class QuarkdownStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    StructureViewModelBase(psiFile, editor, QuarkdownStructureTreeElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(structureViewTreeElement: StructureViewTreeElement): Boolean {
        // Show "+" expand icon when there are nested headings
        return (structureViewTreeElement as? QuarkdownStructureTreeElement)?.hasNestedHeadings() ?: false
    }

    override fun isAlwaysLeaf(structureViewTreeElement: StructureViewTreeElement): Boolean {
        // A leaf has no child headings
        return (structureViewTreeElement as? QuarkdownStructureTreeElement)?.hasNestedHeadings() == false
    }

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)
}

