package cc.carm.plugin.intellij.quarkdown.lang.parser

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType

/**
 * Leaf PSI element for id-bearing tokens: `{#id}` (ID_TAG) and `{id}` (FUNCTION_PARAMS,
 * e.g. `.ref {id}` / `.var {name}`).
 *
 * Implements [PsiNamedElement] so the platform's Symbol model (`namedElement` in
 * `TargetsKt.declaredReferencedData`) recognises the token as a declaration. Ctrl+Click
 * then routes to the Java-style Show Usages popup (all `.ref {id}` usages with file, line
 * and context), exactly like Java method/class navigation.
 *
 * Ordinary plain-text leaves use [QuarkdownLeafPsiElement] and deliberately do NOT
 * implement [PsiNamedElement] (otherwise every word would be a Ctrl+Click target).
 */
class QuarkdownIdLeafPsiElement(type: IElementType, text: CharSequence) :
    LeafPsiElement(type, text), ContributedReferenceHost, PsiNamedElement {

    override fun getName(): String? {
        val t = this.text
        return when {
            t.startsWith("{#") && t.endsWith("}") -> t.substring(2, t.length - 1)
            t.startsWith("{") && t.endsWith("}") -> t.substring(1, t.length - 1)
            else -> t
        }
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
}