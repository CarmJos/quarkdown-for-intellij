package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.openapi.util.TextRange

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

            val text = element.text
            val offset = element.textRange.startOffset
            val fileText = psiFile.text

            // ---- .ref { <id> } ----
            for (match in REF_BLOCK_PATTERN.findAll(fileText)) {
                val contentText = match.groupValues[1].trim()
                if (contentText.isEmpty()) continue

                val contentStartInDoc = match.groups[1]!!.range.first
                val contentEndInDoc = match.groups[1]!!.range.last + 1

                if (offset in contentStartInDoc until contentEndInDoc) {
                    val localStart = offset - contentStartInDoc
                    val localEnd = minOf(localStart + text.length, contentText.length)
                    if (localEnd <= localStart) continue

                    return arrayOf(
                        QuarkdownReference(
                            element, contentText, "ref",
                            TextRange(localStart, localEnd)
                        )
                    )
                }
            }

            // ---- .read / .include / .css / .code { "path" } ----
            for (match in FILE_PATTERN.findAll(fileText)) {
                val pathText = match.groupValues[2].trim()
                if (pathText.isEmpty()) continue

                val pathStartInDoc = match.groups[2]!!.range.first
                val pathEndInDoc = match.groups[2]!!.range.last + 1

                if (offset in pathStartInDoc until pathEndInDoc) {
                    val localStart = offset - pathStartInDoc
                    val localEnd = minOf(localStart + text.length, pathText.length)
                    if (localEnd <= localStart) continue

                    return arrayOf(
                        QuarkdownReference(
                            element, pathText, match.groupValues[1].lowercase(),
                            TextRange(localStart, localEnd)
                        )
                    )
                }
            }

            // ---- Image paths ![](<path>) and ![size](<path>) ----
            val refs = mutableListOf<PsiReference>()
            for (match in IMG_PATH_PATTERN.findAll(fileText)) {
                val pathText = match.groupValues[1].trim()
                if (pathText.isEmpty()) continue

                val pathStartInDoc = match.groups[1]!!.range.first
                val pathEndInDoc = match.groups[1]!!.range.last + 1

                if (offset >= pathEndInDoc || offset + text.length <= pathStartInDoc) continue

                for (seg in computePathSegments(pathText, pathStartInDoc)) {
                    if (offset + text.length <= seg.startInDoc || offset >= seg.endInDoc) continue

                    val localStart = maxOf(0, offset - seg.startInDoc)
                    val localEnd = minOf(text.length, seg.endInDoc - offset)
                    if (localEnd <= localStart) continue

                    refs.add(
                        QuarkdownReference(
                            element, seg.cumulativePath,
                            if (seg.isLast) "image" else "image-dir",
                            TextRange(localStart, localEnd)
                        )
                    )
                }
            }

            return refs.toTypedArray()
        }

        // --------------------------------------------------------------------
        // Path segment helpers (used for image path navigation)
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
}
