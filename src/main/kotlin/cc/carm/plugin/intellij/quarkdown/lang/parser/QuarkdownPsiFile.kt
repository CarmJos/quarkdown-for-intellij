package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownReference
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class QuarkdownPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, QuarkdownLanguage.INSTANCE) {

    override fun getFileType(): FileType = QuarkdownFileType.INSTANCE

    override fun toString(): String = "Quarkdown File"

    /**
     * Override to provide references by scanning the file text directly.
     *
     * This approach works reliably with the flat PSI tree (all tokens are
     * direct children of the file node). IntelliJ's Ctrl+click handler walks
     * up the PSI tree from the leaf element to the root, collecting references
     * at each level. By returning references from the file itself, we ensure
     * they are always found regardless of the leaf element structure.
     */
    override fun getReferences(): Array<PsiReference> {
        val fileText = text
        if (fileText.isEmpty()) return PsiReference.EMPTY_ARRAY

        val refs = mutableListOf<PsiReference>()

        // ---- .ref { <id> } ----
        for (match in REF_BLOCK_PATTERN.findAll(fileText)) {
            val contentText = match.groupValues[1].trim()
            if (contentText.isEmpty()) continue

            val contentStartInDoc = match.groups[1]!!.range.first
            val contentEndInDoc = match.groups[1]!!.range.last + 1

            refs.add(
                QuarkdownReference(
                    this, contentText, "ref",
                    TextRange(contentStartInDoc, contentEndInDoc)
                )
            )
        }

        // ---- .read / .include / .css / .code { "path" } ----
        for (match in FILE_PATTERN.findAll(fileText)) {
            val pathText = match.groupValues[2].trim()
            if (pathText.isEmpty()) continue

            val pathStartInDoc = match.groups[2]!!.range.first
            val pathEndInDoc = match.groups[2]!!.range.last + 1

            refs.add(
                QuarkdownReference(
                    this, pathText, match.groupValues[1].lowercase(),
                    TextRange(pathStartInDoc, pathEndInDoc)
                )
            )
        }

        // ---- Image paths ![](<path>) and ![size](<path>) ----
        for (match in IMG_PATH_PATTERN.findAll(fileText)) {
            val pathText = match.groupValues[1].trim()
            if (pathText.isEmpty()) continue

            val pathStartInDoc = match.groups[1]!!.range.first
            val pathEndInDoc = match.groups[1]!!.range.last + 1

            for (seg in computePathSegments(pathText, pathStartInDoc)) {
                refs.add(
                    QuarkdownReference(
                        this, seg.cumulativePath,
                        if (seg.isLast) "image" else "image-dir",
                        TextRange(seg.startInDoc, seg.endInDoc)
                    )
                )
            }
        }

        return refs.toTypedArray()
    }

    // --------------------------------------------------------------------
    // Path segment helpers
    // --------------------------------------------------------------------

    private data class PathSegment(
        val text: String,
        val startInDoc: Int,
        val endInDoc: Int,
        val cumulativePath: String,
        val isLast: Boolean
    )

    private fun computePathSegments(
        pathText: String,
        pathStartInDoc: Int
    ): List<PathSegment> {
        val result = mutableListOf<PathSegment>()
        var pos = 0

        while (pos < pathText.length) {
            val slashIdx = pathText.indexOf('/', pos)
            val segEnd = if (slashIdx < 0) pathText.length else slashIdx
            val seg = pathText.substring(pos, segEnd)

            if (seg.isNotEmpty()) {
                val segStartInDoc = pathStartInDoc + pos
                val segEndInDoc = pathStartInDoc + segEnd
                val cumulative = pathText.substring(0, segEnd)
                result.add(
                    PathSegment(
                        text = seg,
                        startInDoc = segStartInDoc,
                        endInDoc = segEndInDoc,
                        cumulativePath = cumulative,
                        isLast = slashIdx < 0
                    )
                )
            }

            if (slashIdx < 0) break
            pos = slashIdx + 1
        }

        return result
    }

    companion object {
        // Match content inside .ref { ... } braces
        private val REF_BLOCK_PATTERN = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)

        // Match .read/.include/.css/.code { "path" }
        private val FILE_PATTERN = Regex("""\.(read|include|css|code)\s*\{\s*"([^"]+)"\s*\}""", RegexOption.IGNORE_CASE)

        // Match image paths ![](path) and ![size](path)
        private val IMG_PATH_PATTERN = Regex("""!\[[^\]]*\]\(\s*([^)\s]+)""")
    }
}
