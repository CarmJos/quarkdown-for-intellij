package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser

/**
 * Pure (no IntelliJ dependencies) builder for the temporary `.qd` document used to render a
 * single formula with the Quarkdown CLI.
 *
 * Rendering through the CLI (instead of, say, KaTeX on its own) is what makes the preview
 * faithful: the CLI applies the same KaTeX version, theme and — most importantly — the
 * document's own `.texmacro` declarations, which are emitted into the generated page as
 * `window.texMacros`. Those declarations are copied from the source document so a formula
 * using a custom command previews exactly as it renders in the final document.
 *
 * The formula is re-emitted with the syntax that matches where it came from:
 *
 * | Region | Emitted as | Why |
 * | --- | --- | --- |
 * | `$ … $` (inline / one-line) | `$ … $` | Direct TeX, no Quarkdown evaluation |
 * | `$$$` block | `$$$ … $$$` | Multi-line TeX |
 * | `.math` content | `.math` + indented body | Quarkdown *evaluates* `.math` content, so nested calls (`.math {f(.n)}`) must stay evaluable |
 * | `.texmacro` body | `$$$ … $$$` | A macro body is a TeX template (it may contain `#1`), not an evaluable expression |
 */
object QuarkdownFormulaPreviewSource {

    /** Directive keeping the generated page to a bare formula (no title page, no numbering). */
    private const val DOCTYPE = ".doctype { plain }"

    /**
     * Builds the `.qd` source that renders [region]'s content, including every `.texmacro`
     * declaration found in [documentText] so custom commands resolve.
     *
     * [region]'s content is taken verbatim, *including* any nested Quarkdown calls: for
     * `.math` they are meant to be evaluated by the CLI.
     */
    fun build(documentText: CharSequence, region: QuarkdownEquationRegions.Region): String {
        val content = documentText.substring(region.contentStart, region.contentEnd)

        val lines = mutableListOf(DOCTYPE, "")
        lines += texmacroDeclarations(documentText)
        lines += varDeclarations(documentText)
        if (lines.size > 2) lines += ""
        lines += emitFormula(content, region.kind)
        return lines.joinToString("\n")
    }

    /** Renders the formula itself, using the syntax that preserves its semantics. */
    private fun emitFormula(content: String, kind: QuarkdownEquationRegions.Kind): String =
        when (kind) {
            QuarkdownEquationRegions.Kind.INLINE,
            QuarkdownEquationRegions.Kind.BLOCK -> "\$ ${content.trim()} \$"

            QuarkdownEquationRegions.Kind.MULTILINE ->
                "\$\$\$\n${content.trim()}\n\$\$\$"

            // A block body already carries the document's indentation; it is normalised first so
            // the generated body is indented once, not once more.
            QuarkdownEquationRegions.Kind.MATH_CALL ->
                ".math\n${indent(dedent(content).trim())}"

            QuarkdownEquationRegions.Kind.TEX_MACRO ->
                "\$\$\$\n${content.trim()}\n\$\$\$"
        }

    /**
     * Every `.texmacro {name} {body}` declaration of [documentText], rebuilt as a normalised
     * declaration line. Declarations without both a name and a body are skipped (there is
     * nothing usable to define), and later re-declarations of the same name are dropped.
     */
    private fun texmacroDeclarations(documentText: CharSequence): List<String> {
        val source = documentText.toString()
        val seen = mutableSetOf<String>()
        val declarations = mutableListOf<String>()

        for (start in QuarkdownCallParser.findAllCallStarts(source)) {
            val call = QuarkdownCallParser.parseCall(source, start) ?: continue
            if (call.name != "texmacro") continue
            val nameArg = call.args.firstOrNull { it.paramName == "name" }
                ?: call.args.firstOrNull { !it.isNamed }
            val macroArg = call.args.firstOrNull { it.paramName == "macro" }
                ?: call.args.firstOrNull { it !== nameArg && !it.isNamed }
            val name = nameArg?.raw?.trim().orEmpty()
            val body = macroArg?.raw?.trim().orEmpty()
            if (name.isEmpty() || body.isEmpty()) continue
            if (!seen.add(name)) continue
            declarations += ".texmacro {$name} {$body}"
        }
        return declarations
    }

    /**
     * Every `.var {name} {value}` declaration of [documentText], rebuilt as a declaration line.
     *
     * `.math` content is evaluated as Quarkdown, so it may reference document variables
     * (`.math {f(.n)}`); without them the throwaway document would fail to render.
     */
    private fun varDeclarations(documentText: CharSequence): List<String> =
        QuarkdownCallParser.findVarValues(documentText.toString())
            .map { (name, value) -> ".var {$name} {$value}" }

    /** Indents every non-blank line of [content] so it becomes a valid body argument. */
    private fun indent(content: String): String =
        content.lines().joinToString("\n") { if (it.isBlank()) it else "    $it" }

    /** Removes the common leading indentation shared by the non-blank lines of [content]. */
    private fun dedent(content: String): String {
        val lines = content.lines()
        val common = lines.filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
            ?: 0
        if (common == 0) return content
        return lines.joinToString("\n") { line -> line.drop(common.coerceAtMost(line.length)) }
    }
}
