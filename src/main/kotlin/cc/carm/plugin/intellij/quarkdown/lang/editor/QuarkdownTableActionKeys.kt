package cc.carm.plugin.intellij.quarkdown.lang.editor
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.psi.PsiElement
import java.lang.ref.WeakReference
internal object QuarkdownTableActionKeys {
    val ELEMENT = DataKey.create<WeakReference<PsiElement>>("QuarkdownTableBarElement")
    val TABLE_OFFSET = DataKey.create<Int>("QuarkdownTableBarOffset")
    val ROW_INDEX = DataKey.create<Int>("QuarkdownTableBarRowIndex")
    val COLUMN_INDEX = DataKey.create<Int>("QuarkdownTableBarColumnIndex")
    fun putRowSnapshot(sink: DataSink, block: QuarkdownTableModificationUtils.TableBlock, rowIndex: Int) {
        sink.lazy(TABLE_OFFSET) { block.startOffset }
        sink.lazy(ROW_INDEX) { rowIndex }
        sink.lazy(ELEMENT) { WeakReference<PsiElement>(null) }
    }
    fun putColumnSnapshot(sink: DataSink, block: QuarkdownTableModificationUtils.TableBlock, columnIndex: Int) {
        sink.lazy(TABLE_OFFSET) { block.startOffset }
        sink.lazy(COLUMN_INDEX) { columnIndex }
        sink.lazy(ELEMENT) { WeakReference<PsiElement>(null) }
    }
}
internal object QuarkdownTableActionPlaces {
    const val TABLE_INLAY_TOOLBAR = "QuarkdownTableInlayToolbar"
}
