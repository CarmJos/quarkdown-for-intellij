package cc.carm.plugin.intellij.quarkdown.lang.highlighter

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class QuarkdownColorSettingsPage : ColorSettingsPage {

    private val DESCRIPTORS = ATTRIBUTES

    override fun getIcon(): Icon = QuarkdownIcons.FILE
    override fun getHighlighter(): SyntaxHighlighter = QuarkdownSyntaxHighlighter()
    override fun getDemoText(): String = DEMO_TEXT
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getDisplayName(): String = "Quarkdown"

    companion object {
        private val ATTRIBUTES: Array<AttributesDescriptor> = arrayOf(
            AttributesDescriptor("Heading Marker", QuarkdownSyntaxHighlighter.HEADING_MARKER),
            AttributesDescriptor("Heading Content", QuarkdownSyntaxHighlighter.HEADING_CONTENT),

            AttributesDescriptor("Bold", QuarkdownSyntaxHighlighter.BOLD),
            AttributesDescriptor("Italic", QuarkdownSyntaxHighlighter.ITALIC),
            AttributesDescriptor("Strikethrough", QuarkdownSyntaxHighlighter.STRIKETHROUGH),
            AttributesDescriptor("Inline Code", QuarkdownSyntaxHighlighter.INLINE_CODE),

            AttributesDescriptor("Fenced Code Block Delimiter", QuarkdownSyntaxHighlighter.FENCED_CODE_START),
            AttributesDescriptor("Fenced Code Language", QuarkdownSyntaxHighlighter.FENCED_CODE_LANGUAGE),
            AttributesDescriptor("Fenced Code Content", QuarkdownSyntaxHighlighter.FENCED_CODE_CONTENT),
            AttributesDescriptor("Fenced Code End Delimiter", QuarkdownSyntaxHighlighter.FENCED_CODE_END),

            AttributesDescriptor("Blockquote Marker", QuarkdownSyntaxHighlighter.BLOCKQUOTE),
            AttributesDescriptor("List Marker", QuarkdownSyntaxHighlighter.LIST_MARKER),

            AttributesDescriptor("Horizontal Rule / Separator", QuarkdownSyntaxHighlighter.SEPARATOR),
            AttributesDescriptor("Page Break", QuarkdownSyntaxHighlighter.PAGE_BREAK),

            AttributesDescriptor("Table Pipe", QuarkdownSyntaxHighlighter.TABLE_PIPE),
            AttributesDescriptor("Table Separator", QuarkdownSyntaxHighlighter.TABLE_SEPARATOR),

            AttributesDescriptor("Image Prefix", QuarkdownSyntaxHighlighter.IMAGE_PREFIX),
            AttributesDescriptor("Image Label", QuarkdownSyntaxHighlighter.IMAGE_LABEL),
            AttributesDescriptor("Link / Image Description", QuarkdownSyntaxHighlighter.LINK_TEXT),
            AttributesDescriptor("Link / Image URL", QuarkdownSyntaxHighlighter.LINK_URL),
            AttributesDescriptor("Link / Image Title", QuarkdownSyntaxHighlighter.LINK_TITLE),
            AttributesDescriptor("Brackets [ ]", QuarkdownSyntaxHighlighter.BRACKET),
            AttributesDescriptor("Parentheses ( )", QuarkdownSyntaxHighlighter.PAREN),
            AttributesDescriptor("Braces { }", QuarkdownSyntaxHighlighter.BRACE),

            AttributesDescriptor("Function Call Dot", QuarkdownSyntaxHighlighter.FUNCTION_DOT),
            AttributesDescriptor("Function Name", QuarkdownSyntaxHighlighter.FUNCTION_NAME),
            AttributesDescriptor("Function Parameter Name", QuarkdownSyntaxHighlighter.FUNCTION_PARAMETER_NAME),
            AttributesDescriptor("Function Parameter Colon", QuarkdownSyntaxHighlighter.FUNCTION_PARAMETER_COLON),
            AttributesDescriptor("Function Braces { }", QuarkdownSyntaxHighlighter.FUNCTION_BRACE),
            AttributesDescriptor("Function Parameters", QuarkdownSyntaxHighlighter.FUNCTION_PARAMS),

            AttributesDescriptor("Element ID Tag", QuarkdownSyntaxHighlighter.ID_TAG),

            AttributesDescriptor("HTML Comment", QuarkdownSyntaxHighlighter.HTML_COMMENT),

            AttributesDescriptor("Front Matter Delimiter", QuarkdownSyntaxHighlighter.FRONT_MATTER_DELIMITER),
            AttributesDescriptor("Front Matter Content", QuarkdownSyntaxHighlighter.FRONT_MATTER_CONTENT),

            AttributesDescriptor("Escape Sequences", QuarkdownSyntaxHighlighter.ESCAPE),

            AttributesDescriptor("Plain Text", QuarkdownSyntaxHighlighter.TEXT),
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
        }
    }
}
