package cc.carm.plugin.intellij.quarkdown.lang.codeblock

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * PSI element representing a fenced code block in a Quarkdown file.
 *
 * Structure:
 * - FENCED_CODE_START (```)
 * - FENCED_CODE_LANGUAGE (optional, e.g. "java")
 * - FENCED_CODE_CONTENT* (zero or more content lines)
 * - FENCED_CODE_END (```)
 *
 * Implements [PsiLanguageInjectionHost] to enable language injection
 * (syntax highlighting, code completion, etc.) for the code block content.
 */
class QuarkdownCodeBlock(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {

    /**
     * The detected language identifier, e.g. "java", "kotlin", or empty if none.
     */
    val languageIdentifier: String
        get() {
            val langNode = node.findChildByType(QuarkdownTokenTypes.FENCED_CODE_LANGUAGE)
            return langNode?.text?.trim() ?: ""
        }

    /**
     * The text range of the content inside this code block
     * (between the opening fence line and the closing fence).
     * Range is relative to this PSI element's text offset.
     */
    val contentRange: TextRange?
        get() {
            val startNode = node.findChildByType(QuarkdownTokenTypes.FENCED_CODE_START) ?: return null
            val endNode = node.findChildByType(QuarkdownTokenTypes.FENCED_CODE_END) ?: return null
            val nodeStart = node.startOffset
            val startOffset = startNode.textRange.endOffset - nodeStart
            val endOffset = endNode.textRange.startOffset - nodeStart
            if (startOffset >= endOffset) return null
            // Skip the language / caption / id portion of the opening line: the content
            // begins right after the first newline that follows the opening fence.
            val contentStart = node.getChildren(null)
                .firstOrNull { child ->
                    child.elementType == QuarkdownTokenTypes.NEWLINE &&
                            child.textRange.startOffset - nodeStart >= startOffset
                }
                ?.textRange?.endOffset?.minus(nodeStart)
                ?: startOffset
            if (contentStart >= endOffset) return null
            return TextRange(contentStart, endOffset)
        }

    /**
     * The text content inside this code block.
     */
    val contentText: String
        get() {
            val range = contentRange ?: return ""
            return node.text.substring(range.startOffset, range.endOffset)
        }

    // ── PsiLanguageInjectionHost ──────────────────────────────────

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost {
        // Quarkdown code blocks are read-only; changes go through the document.
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        CodeBlockLiteralEscaper(this)

    private class CodeBlockLiteralEscaper(host: QuarkdownCodeBlock) :
        LiteralTextEscaper<QuarkdownCodeBlock>(host) {

        override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
            outChars.append(myHost.text, rangeInsideHost.startOffset, rangeInsideHost.endOffset)
            return true
        }

        override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int =
            rangeInsideHost.startOffset + offsetInDecoded

        override fun isOneLine(): Boolean = false
    }

    override fun toString(): String {
        val lang = languageIdentifier
        return if (lang.isNotEmpty()) "QuarkdownCodeBlock($lang)" else "QuarkdownCodeBlock"
    }
}
