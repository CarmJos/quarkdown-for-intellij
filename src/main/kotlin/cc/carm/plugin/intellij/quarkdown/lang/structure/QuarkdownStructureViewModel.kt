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
 *
 * No sorters are registered on purpose: headings must follow their document
 * order (a chapter outline must not be alphabetized).
 */
class QuarkdownStructureViewModel(psiFile: PsiFile, editor: Editor?) :
    StructureViewModelBase(psiFile, editor, QuarkdownStructureTreeElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(structureViewTreeElement: StructureViewTreeElement): Boolean =
        // Show "+" expand icon when there are nested headings or content blocks
        (structureViewTreeElement as? QuarkdownStructureTreeElement)?.hasChildren() ?: false

    override fun isAlwaysLeaf(structureViewTreeElement: StructureViewTreeElement): Boolean =
        // A leaf has no child headings or content blocks
        (structureViewTreeElement as? QuarkdownStructureTreeElement)?.hasChildren() == false

    override fun getSorters(): Array<Sorter> = emptyArray()
}
