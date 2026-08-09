package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser

/**
 * Pure (no IntelliJ dependencies) computation of the reference "anchors" in a Quarkdown
 * document — the ranges that navigate somewhere when Ctrl+Clicked:
 *
 *   - `{#id}` label declarations (navigate to their first usage)
 *   - `.ref {id}` usages (navigate to the `{#id}` declaration)
 *   - `.var {name}` declared variables: every `.name` usage that refers to them
 *   - `.read / .include / .css / .code { "path" }` paths
 *   - image paths `![size](path)`
 *
 * Kept dependency-free so it can be unit-tested and shared with the reference provider.
 */
object QuarkdownReferenceParser {

    /** A navigable range (document coordinates) in a Quarkdown source file. */
    data class Anchor(
        val start: Int,
        val end: Int,
        val referenceText: String,
        val referenceType: String
    ) {
        fun overlaps(elemStart: Int, elemEnd: Int): Boolean =
            elemEnd > start && elemStart < end
    }

    private val varRefPattern = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\b""")
    private val refBlockPattern = Regex("""\.ref\s*\{\s*([^}]+?)\s*\}""", RegexOption.IGNORE_CASE)
    private val labelPattern = Regex("""\{#([a-zA-Z0-9_-]+)}""")
    private val filePattern = Regex("""\.(read|include|css|code)\s*\{\s*(?:"([^"]+)"|([^{}"]+?))\s*\}""", RegexOption.IGNORE_CASE)

    /**
     * Pattern matching Quarkdown image syntax:
     *   `![alt](path)`, `!(100%)[alt](path)`, `! [alt](path "title")`.
     * Group 1 captures the path value (before any space/title/close-paren).
     */
    const val IMG_PATH_PATTERN_STRING = """!\s*(?:\([^)]*\)\s*)?\[[^\]]*\]\s*\(\s*([^)\s]+)"""

    private val imgPathPattern = Regex(IMG_PATH_PATTERN_STRING)

    /** Computes all reference anchors for the given document text. */
    fun computeAnchors(fileText: String): List<Anchor> {
        val anchors = mutableListOf<Anchor>()

        // ---- `{#id}` label declarations ----
        for (match in labelPattern.findAll(fileText)) {
            val id = match.groupValues[1]
            if (id.isEmpty()) continue
            val start = match.groups[1]!!.range.first
            val end = match.groups[1]!!.range.last + 1
            anchors.add(Anchor(start, end, id, "label"))
        }

        // ---- .var { <name> } declared variables: `.name` usages ----
        val vars = QuarkdownCallParser.findVarDeclarations(fileText)
        if (vars.isNotEmpty()) {
            for (match in varRefPattern.findAll(fileText)) {
                val varName = match.groupValues[1].lowercase()
                if (varName !in vars) continue
                val start = match.groups[1]!!.range.first
                val end = match.groups[1]!!.range.last + 1
                anchors.add(Anchor(start, end, varName, "var"))
            }
        }

        // ---- `.var { <name> }` declarations (so the declaration itself is navigable) ----
        val varDeclPattern = Regex("""\.var\s*\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}""", RegexOption.IGNORE_CASE)
        for (match in varDeclPattern.findAll(fileText)) {
            val name = match.groupValues[1]
            if (name.isEmpty()) continue
            val start = match.groups[1]!!.range.first
            val end = match.groups[1]!!.range.last + 1
            anchors.add(Anchor(start, end, name, "var-decl"))
        }

        // ---- .ref { <id> } ----
        for (match in refBlockPattern.findAll(fileText)) {
            val contentText = match.groupValues[1].trim()
            if (contentText.isEmpty()) continue
            val start = match.groups[1]!!.range.first
            val end = match.groups[1]!!.range.last + 1
            anchors.add(Anchor(start, end, contentText, "ref"))
        }

        // ---- .read / .include / .css / .code { "path" } or { path } ----
        for (match in filePattern.findAll(fileText)) {
            val pathText = (match.groupValues[2].ifEmpty { match.groupValues[3] }).trim()
            if (pathText.isEmpty()) continue
            val groupIndex = if (match.groupValues[2].isNotEmpty()) 2 else 3
            val start = match.groups[groupIndex]!!.range.first
            val end = match.groups[groupIndex]!!.range.last + 1
            anchors.add(Anchor(start, end, pathText, match.groupValues[1].lowercase()))
        }

        // ---- Image paths ![](<path>) and ![size](<path>) ----
        for (match in imgPathPattern.findAll(fileText)) {
            val pathText = match.groupValues[1].trim()
            if (pathText.isEmpty()) continue
            val pathStartInDoc = match.groups[1]!!.range.first
            for (seg in computePathSegments(pathText, pathStartInDoc)) {
                anchors.add(
                    Anchor(
                        seg.startInDoc,
                        seg.endInDoc,
                        seg.cumulativePath,
                        if (seg.isLast) "image" else "image-dir"
                    )
                )
            }
        }

        return anchors
    }

    private data class PathSegment(
        val text: String,
        val startInDoc: Int,
        val endInDoc: Int,
        val cumulativePath: String,
        val isLast: Boolean
    )

    private fun computePathSegments(pathText: String, pathStartInDoc: Int): List<PathSegment> {
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
}
