package cc.carm.plugin.intellij.quarkdown.action.table

import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink

internal object TableActionKeys {
    val TABLE_OFFSET = DataKey.create<Int>("QuarkdownTableBarOffset")
    val ROW_INDEX = DataKey.create<Int>("QuarkdownTableBarRowIndex")
    val COLUMN_INDEX = DataKey.create<Int>("QuarkdownTableBarColumnIndex")
    fun putRowSnapshot(sink: DataSink, block: QuarkdownTableModificationUtils.TableBlock, rowIndex: Int) {
        sink.lazy(TABLE_OFFSET) { block.startOffset }
        sink.lazy(ROW_INDEX) { rowIndex }
    }

    fun putColumnSnapshot(sink: DataSink, block: QuarkdownTableModificationUtils.TableBlock, columnIndex: Int) {
        sink.lazy(TABLE_OFFSET) { block.startOffset }
        sink.lazy(COLUMN_INDEX) { columnIndex }
    }
}

internal object TableActionPlaces {
    const val TABLE_INLAY_TOOLBAR = "QuarkdownTableInlayToolbar"
}
