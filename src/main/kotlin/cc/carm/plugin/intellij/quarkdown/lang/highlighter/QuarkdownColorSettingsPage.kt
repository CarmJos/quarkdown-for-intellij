package cc.carm.plugin.intellij.quarkdown.lang.highlighter

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexHighlighting
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class QuarkdownColorSettingsPage : ColorSettingsPage {

    private val descriptors = ATTRIBUTES

    override fun getIcon(): Icon = QuarkdownIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = QuarkdownSyntaxHighlighter()
    override fun getDemoText(): String = DEMO_TEXT

    /**
     * LaTeX inside equations is colored by `QuarkdownLatexAnnotator`, not by the Quarkdown
     * lexer the demo text is run through, so the demo relies on tags to preview those colors.
     */
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = DEMO_TAGS
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = descriptors
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = QuarkdownBundle.message("quarkdown.settings.name")

    companion object {
        private fun attr(key: String, attributesKey: TextAttributesKey) =
            AttributesDescriptor(QuarkdownBundle.message(key), attributesKey)

        private val ATTRIBUTES: Array<AttributesDescriptor> = arrayOf(
            attr("quarkdown.color.attr.heading.marker", QuarkdownSyntaxHighlighter.HEADING_MARKER),
            attr("quarkdown.color.attr.heading.content", QuarkdownSyntaxHighlighter.HEADING_CONTENT),

            attr("quarkdown.color.attr.bold", QuarkdownSyntaxHighlighter.BOLD),
            attr("quarkdown.color.attr.italic", QuarkdownSyntaxHighlighter.ITALIC),
            attr("quarkdown.color.attr.strikethrough", QuarkdownSyntaxHighlighter.STRIKETHROUGH),
            attr("quarkdown.color.attr.inline.code", QuarkdownSyntaxHighlighter.INLINE_CODE),

            attr("quarkdown.color.attr.fenced.code.delimiter", QuarkdownSyntaxHighlighter.FENCED_CODE_START),
            attr("quarkdown.color.attr.fenced.code.language", QuarkdownSyntaxHighlighter.FENCED_CODE_LANGUAGE),
            attr("quarkdown.color.attr.fenced.code.content", QuarkdownSyntaxHighlighter.FENCED_CODE_CONTENT),
            attr("quarkdown.color.attr.fenced.code.end.delimiter", QuarkdownSyntaxHighlighter.FENCED_CODE_END),
            attr("quarkdown.color.attr.fenced.code.caption", QuarkdownSyntaxHighlighter.FENCED_CODE_CAPTION),

            attr("quarkdown.color.attr.blockquote.marker", QuarkdownSyntaxHighlighter.BLOCKQUOTE),
            attr("quarkdown.color.attr.list.marker", QuarkdownSyntaxHighlighter.LIST_MARKER),

            attr("quarkdown.color.attr.horizontal.rule", QuarkdownSyntaxHighlighter.SEPARATOR),
            attr("quarkdown.color.attr.page.break", QuarkdownSyntaxHighlighter.PAGE_BREAK),

            attr("quarkdown.color.attr.table.pipe", QuarkdownSyntaxHighlighter.TABLE_PIPE),
            attr("quarkdown.color.attr.table.separator", QuarkdownSyntaxHighlighter.TABLE_SEPARATOR),

            attr("quarkdown.color.attr.image.prefix", QuarkdownSyntaxHighlighter.IMAGE_PREFIX),
            attr("quarkdown.color.attr.image.label", QuarkdownSyntaxHighlighter.IMAGE_LABEL),
            attr("quarkdown.color.attr.link.description", QuarkdownSyntaxHighlighter.LINK_TEXT),
            attr("quarkdown.color.attr.link.url", QuarkdownSyntaxHighlighter.LINK_URL),
            attr("quarkdown.color.attr.link.title", QuarkdownSyntaxHighlighter.LINK_TITLE),
            attr("quarkdown.color.attr.brackets", QuarkdownSyntaxHighlighter.BRACKET),
            attr("quarkdown.color.attr.parentheses", QuarkdownSyntaxHighlighter.PAREN),
            attr("quarkdown.color.attr.braces", QuarkdownSyntaxHighlighter.BRACE),

            attr("quarkdown.color.attr.function.dot", QuarkdownSyntaxHighlighter.FUNCTION_DOT),
            attr("quarkdown.color.attr.function.name", QuarkdownSyntaxHighlighter.FUNCTION_NAME),
            attr("quarkdown.color.attr.function.parameter.name", QuarkdownSyntaxHighlighter.FUNCTION_PARAMETER_NAME),
            attr("quarkdown.color.attr.function.parameter.colon", QuarkdownSyntaxHighlighter.FUNCTION_PARAMETER_COLON),
            attr("quarkdown.color.attr.function.braces", QuarkdownSyntaxHighlighter.FUNCTION_BRACE),
            attr("quarkdown.color.attr.function.parameters", QuarkdownSyntaxHighlighter.FUNCTION_PARAMS),

            attr("quarkdown.color.attr.semantic.known.function", QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION),
            attr("quarkdown.color.attr.semantic.valid.enum", QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM),
            attr("quarkdown.color.attr.semantic.parameter", QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER),

            attr("quarkdown.color.attr.id.tag.marker", QuarkdownSyntaxHighlighter.ID_TAG_MARKER),
            attr("quarkdown.color.attr.id.tag", QuarkdownSyntaxHighlighter.ID_TAG),

            attr("quarkdown.color.attr.html.comment", QuarkdownSyntaxHighlighter.HTML_COMMENT),

            attr("quarkdown.color.attr.front.matter.delimiter", QuarkdownSyntaxHighlighter.FRONT_MATTER_DELIMITER),
            attr("quarkdown.color.attr.front.matter.content", QuarkdownSyntaxHighlighter.FRONT_MATTER_CONTENT),

            attr("quarkdown.color.attr.escape", QuarkdownSyntaxHighlighter.ESCAPE),

            attr("quarkdown.color.attr.plain.text", QuarkdownSyntaxHighlighter.TEXT),

            attr("quarkdown.color.attr.latex.command", QuarkdownLatexHighlighting.COMMAND),
            attr("quarkdown.color.attr.latex.environment", QuarkdownLatexHighlighting.ENVIRONMENT),
            attr("quarkdown.color.attr.latex.brace", QuarkdownLatexHighlighting.BRACE),
            attr("quarkdown.color.attr.latex.bracket", QuarkdownLatexHighlighting.BRACKET),
            attr("quarkdown.color.attr.latex.superscript", QuarkdownLatexHighlighting.SUPERSCRIPT),
            attr("quarkdown.color.attr.latex.subscript", QuarkdownLatexHighlighting.SUBSCRIPT),
            attr("quarkdown.color.attr.latex.parameter", QuarkdownLatexHighlighting.PARAMETER),
            attr("quarkdown.color.attr.latex.number", QuarkdownLatexHighlighting.NUMBER),
            attr("quarkdown.color.attr.latex.operator", QuarkdownLatexHighlighting.OPERATOR),
            attr("quarkdown.color.attr.latex.comment", QuarkdownLatexHighlighting.COMMENT),
            attr("quarkdown.color.attr.latex.text", QuarkdownLatexHighlighting.TEXT),
        )

        private val DEMO_TEXT = buildString {
            appendLine("---")
            appendLine("title: Sample Document")
            appendLine("author: Quarkdown User")
            appendLine("---")
            appendLine()
            appendLine("# Chapter 1: Getting Started")
            appendLine()
            appendLine("This is **bold** text, this is *italic*, and ~~strikethrough~~.")
            appendLine("Here is some `inline code` and a `code` span.")
            appendLine()
            appendLine("> This is a blockquote with multiple")
            appendLine("> lines of quoted text.")
            appendLine()
            appendLine("- Unordered list item")
            appendLine("- Another item")
            appendLine()
            appendLine("1. Ordered item one")
            appendLine("2. Ordered item two")
            appendLine()
            appendLine("```kotlin")
            appendLine("fun hello() = \"world\"")
            appendLine("```")
            appendLine()
            appendLine("***")
            appendLine("<<<")
            appendLine()
            appendLine("| Column A | Column B |")
            appendLine("|----------|:--------:|")
            appendLine("| Cell 1   | Cell 2   |")
            appendLine()
            appendLine("Here is an image: ![50%](path/to/image.png \"Example\")")
            appendLine()
            appendLine("And a [link to the docs](https://quarkdown.com).")
            appendLine()
            appendLine(".doctype { paged }")
            appendLine(".docname { \"My Document\" }")
            appendLine(".pageformat size:{a4} margin:{2.54cm 3.18cm 2.54cm 3.18cm}")
            appendLine(".row { .col { content } }")
            appendLine()
            appendLine("A reference to .ref {chapter-1} and a .var {version} variable.")
            appendLine()
            appendLine("<!-- This is a comment -->")
            appendLine()
            appendLine("Escape characters: \\* \\` \\[")
            appendLine()
            appendLine("Equations (LaTeX content is highlighted):")
            appendLine()
            appendLine("Let \$ <latexCmd>\\overline</latexCmd> <latexText>v</latexText> = <latexCmd>\\frac</latexCmd> <latexBrace>{</latexBrace><latexCmd>\\Delta</latexCmd> <latexText>x</latexText><latexBrace>}</latexBrace> <latexBrace>{</latexBrace><latexCmd>\\Delta</latexCmd> <latexText>t</latexText><latexBrace>}</latexBrace> \$ be the average velocity.")
            appendLine()
            appendLine("Let <latexText>cost</latexText> \$ be literal: no equation without whitespace-delimited delimiters.")
            appendLine()
            appendLine("$$$")
            appendLine("<latexText>x</latexText><latexSup>^</latexSup><latexNum>2</latexNum> <latexOp>+</latexOp> <latexText>y</latexText><latexSub>_</latexSub><latexNum>1</latexNum> <latexComment>% a TeX comment</latexComment>")
            appendLine("<latexEnv>\\begin{matrix}</latexEnv> <latexText>a</latexText> <latexOp>&amp;</latexOp> <latexText>b</latexText> <latexEnv>\\end{matrix}</latexEnv>")
            appendLine("$$$")
        }

        /**
         * Demo-only tags for the colors applied by `QuarkdownLatexAnnotator`: the demo text is
         * run through the Quarkdown lexer, which sees equation content as plain text.
         */
        private val DEMO_TAGS = mapOf(
            "latexCmd" to QuarkdownLatexHighlighting.COMMAND,
            "latexEnv" to QuarkdownLatexHighlighting.ENVIRONMENT,
            "latexBrace" to QuarkdownLatexHighlighting.BRACE,
            "latexBracket" to QuarkdownLatexHighlighting.BRACKET,
            "latexSup" to QuarkdownLatexHighlighting.SUPERSCRIPT,
            "latexSub" to QuarkdownLatexHighlighting.SUBSCRIPT,
            "latexParam" to QuarkdownLatexHighlighting.PARAMETER,
            "latexNum" to QuarkdownLatexHighlighting.NUMBER,
            "latexOp" to QuarkdownLatexHighlighting.OPERATOR,
            "latexComment" to QuarkdownLatexHighlighting.COMMENT,
            "latexText" to QuarkdownLatexHighlighting.TEXT,
        )
    }
}
