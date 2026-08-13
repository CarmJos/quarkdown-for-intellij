package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

/**
 * A file-based index of Quarkdown reference ids.
 *
 * For every `.qd` file the index records the document positions of every reference
 * anchor, keyed by the (case-insensitive) reference id / path. Lookups use
 * [FileBasedIndex] instead of scanning every `.qd` file on each Go-to-declaration /
 * Find-Usages query, so large projects stay responsive.
 *
 * The index is intentionally dependency-free of PSI: it reads raw file content
 * (during indexing, PSI is not available) and stores plain offsets.
 */
class QuarkdownReferenceIndex : FileBasedIndexExtension<String, List<QuarkdownReferenceIndex.Anchor>>() {

    companion object {
        @JvmField
        val NAME: ID<String, List<QuarkdownReferenceIndex.Anchor>> =
            ID.create("quarkdown.reference.index")

        /**
         * Returns the virtual files in [scope] that contain an anchor with the given
         * (case-insensitive) [id], or an empty list when the index is unavailable
         * (e.g. while the project is being indexed).
         */
        fun findFilesWithAnchor(scope: GlobalSearchScope, id: String): List<VirtualFile> {
            if (id.isBlank()) return emptyList()
            val key = id.lowercase()
            val result = mutableListOf<VirtualFile>()
            FileBasedIndex.getInstance().processValues(
                NAME, key, null,
                { file, _ ->
                    result.add(file)
                    true
                },
                scope
            )
            return result
        }

        /** Whether [file] declares an anchor with the given (case-insensitive) id. */
        fun fileContainsAnchor(scope: GlobalSearchScope, file: VirtualFile, id: String): Boolean {
            if (id.isBlank()) return false
            val project = scope.project ?: return false
            val key = id.lowercase()
            val data = FileBasedIndex.getInstance().getFileData(NAME, file, project)
            return data.containsKey(key)
        }
    }

    /** A single reference anchor: document offsets plus the reference type. */
    data class Anchor(val start: Int, val end: Int, val type: String)

    override fun getName(): ID<String, List<Anchor>> = NAME

    override fun getVersion(): Int = 1

    override fun getIndexer(): DataIndexer<String, List<Anchor>, FileContent> =
        DataIndexer { inputData ->
            val text = inputData.contentAsText.toString()
            val result = HashMap<String, MutableList<Anchor>>()
            for (anchor in QuarkdownReferenceParser.computeAnchors(text)) {
                val key = anchor.referenceText.trim().lowercase()
                if (key.isEmpty()) continue
                result.getOrPut(key) { mutableListOf() }
                    .add(Anchor(anchor.start, anchor.end, anchor.referenceType))
            }
            result
        }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<List<Anchor>> = AnchorListExternalizer

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { file -> file.fileType == QuarkdownFileType.INSTANCE }

    override fun dependsOnFileContent(): Boolean = true

    /** Minimal externalizer for a list of [Anchor]s (start, end, type string). */
    private object AnchorListExternalizer : DataExternalizer<List<Anchor>> {
        override fun save(out: DataOutput, value: List<Anchor>) {
            out.writeInt(value.size)
            for (anchor in value) {
                out.writeInt(anchor.start)
                out.writeInt(anchor.end)
                out.writeUTF(anchor.type)
            }
        }

        override fun read(input: DataInput): List<Anchor> {
            val size = input.readInt()
            return List(size) {
                Anchor(input.readInt(), input.readInt(), input.readUTF())
            }
        }
    }
}
