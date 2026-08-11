package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Leaf PSI element for id-bearing tokens: the id content of `{#id}` (ID_TAG — the `{#`
 * prefix and `}` are separate `ID_TAG_MARKER` / `BRACE_CLOSE` leaves) and the content of
 * `.ref {id}` / `.var {name}` (FUNCTION_PARAMS).
 *
 * Implements [PsiNamedElement] so the platform's Symbol model recognises the token as a
 * declaration, and [PsiNameIdentifierOwner] so the Symbol model uses the **id-only** range
 * for the Ctrl+hover underline and declaration navigation. Because the leaf itself IS the
 * bare id (no surrounding `{#`/`}`), the name identifier covers the whole leaf.
 *
 * The [GotoDeclarationHandler][cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownGotoDeclarationHandler]
 * returns the single usage (or no target for the SU path), so the platform underlines only
 * the id — never the whole `{#id}` token.
 *
 * Ordinary plain-text leaves use [QuarkdownLeafPsiElement] and deliberately do NOT
 * implement [PsiNamedElement] (otherwise every word would be a Ctrl+Click target).
 */
class QuarkdownIdLeafPsiElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), ContributedReferenceHost, PsiNamedElement, PsiNameIdentifierOwner {

    override fun getName(): String? {
        // Only declaration tokens have a name: `{#id}` labels and `.var {name}` names.
        // A `.ref {id}` / `.read {path}` / `.include {path}` argument is a REFERENCE, not
        // a declaration — exposing a name here would make the platform Symbol model treat
        // every usage as a declaration and break Ctrl+Click / Find Usages.
        if (!isDeclarationContext()) return null
        val t = this.text
        return when {
            t.startsWith("{#") && t.endsWith("}") -> t.substring(2, t.length - 1)
            t.startsWith("{") && t.endsWith("}") -> t.substring(1, t.length - 1)
            else -> t
        }
    }

    /**
     * True when this token declares an id that other tokens reference: `{#id}` (ID_TAG) or
     * the `.var { name }` declaration name. Everything else (`.ref {id}`, `.read {path}`,
     * `.include {path}`, `.css`/`.code`/`.image` arguments) is a reference, not a declaration.
     */
    private fun isDeclarationContext(): Boolean {
        val tokenType = tokenType
        if (tokenType == QuarkdownTokenTypes.ID_TAG) return true
        if (tokenType != QuarkdownTokenTypes.FUNCTION_PARAMS) return false
        return isVarNameArgument()
    }

    /**
     * Determines whether this FUNCTION_PARAMS token is the *name* argument of a
     * `.var { name }` call (as opposed to a `.ref`/`.read`/... reference argument, or the
     * `.var` value argument). The name is the first positional `{...}` right after `.var`.
     */
    private fun isVarNameArgument(): Boolean {
        // Walk backwards: skip the opening `{` and any whitespace immediately before it,
        // then expect a FUNCTION_NAME "var" preceded by a FUNCTION_DOT. If we encounter the
        // closing brace of a previous argument first, this is not the (first) name argument.
        var sibling: PsiElement? = prevSibling
        while (sibling != null &&
            (sibling is LeafPsiElement && sibling.tokenType == QuarkdownTokenTypes.FUNCTION_BRACE_OPEN ||
                sibling is LeafPsiElement && sibling.tokenType == QuarkdownTokenTypes.TEXT)
        ) {
            sibling = sibling.prevSibling
        }
        if (sibling == null) return false
        val tt = (sibling as? LeafPsiElement)?.tokenType
        if (tt == QuarkdownTokenTypes.FUNCTION_BRACE_CLOSE) return false
        if (tt != QuarkdownTokenTypes.FUNCTION_NAME) return false
        if (!sibling.text.equals("var", ignoreCase = true)) return false
        return (sibling.prevSibling as? LeafPsiElement)?.tokenType == QuarkdownTokenTypes.FUNCTION_DOT
    }

    override fun setName(name: String): PsiElement {
        val t = this.text
        val newText = when {
            t.startsWith("{#") && t.endsWith("}") -> "{#$name}"
            t.startsWith("{") && t.endsWith("}") -> "{$name}"
            else -> name
        }
        val file = containingFile ?: return this
        val document = file.viewProvider.document ?: return this
        val range = textRange
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(range.startOffset, range.endOffset, newText)
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        return file.findElementAt(range.startOffset) ?: this
    }

    /**
     * Returns a lightweight [PsiElement] whose [getTextRange] covers only the id portion
     * of the token (e.g. `id` in `{#id}`), excluding the surrounding braces and `#`.
     *
     * The platform's Symbol model ([PsiElement2Declaration]) uses this element's range to
     * draw the Ctrl+hover underline and to compute the declaration's absolute range. Without
     * this, the underline would cover the entire `{#id}` token.
     */
    override fun getNameIdentifier(): PsiElement? {
        if (!isValid) return null
        if (!isDeclarationContext()) return null
        val t = this.text
        val start = idStartInElement(t)
        val end = idEndInElement(t)
        if (start >= end) return null
        return IdNameIdentifier(this, start, end)
    }

    private fun idStartInElement(text: String): Int = when {
        text.startsWith("{#") -> 2
        text.startsWith("{") -> 1
        else -> 0
    }

    private fun idEndInElement(text: String): Int = when {
        text.endsWith("}") -> text.length - 1
        else -> text.length
    }

    /**
     * A lightweight wrapper [PsiElement] that represents the id portion of the parent
     * [QuarkdownIdLeafPsiElement]. Its [getTextRange] is the absolute file range of the id
     * (e.g. `id` in `{#id}`), so the Symbol model underlines only the id on Ctrl+hover.
     */
    private class IdNameIdentifier(
        private val parent: QuarkdownIdLeafPsiElement,
        private val startInParent: Int,
        private val endInParent: Int
    ) : FakePsiElement() {

        override fun getParent(): PsiElement = parent

        override fun getTextRange(): TextRange {
            val parentRange = parent.textRange
            return TextRange(parentRange.startOffset + startInParent, parentRange.startOffset + endInParent)
        }

        override fun getText(): String {
            val parentText = parent.text
            return parentText.substring(startInParent, endInParent)
        }

        override fun getTextLength(): Int = endInParent - startInParent

        override fun isValid(): Boolean = parent.isValid

        override fun getContainingFile(): PsiFile? = parent.containingFile

        override fun getName(): String = text
    }
}