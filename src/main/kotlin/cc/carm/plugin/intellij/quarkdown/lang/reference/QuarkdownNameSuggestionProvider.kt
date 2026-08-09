package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.NameSuggestionProvider

/**
 * Provides the initial name for the Rename dialog (Shift+F6).
 *
 * Since [cc.carm.plugin.intellij.quarkdown.lang.parser.QuarkdownLeafPsiElement] deliberately
 * does NOT implement [com.intellij.psi.PsiNamedElement] (otherwise every plain-text leaf
 * would be a Ctrl+Click target), the default name suggestion provider cannot derive a name.
 * This provider returns the bare id (`plc-symbol-output`) for id tokens so the dialog is
 * pre-filled correctly.
 */
class QuarkdownNameSuggestionProvider : NameSuggestionProvider {

    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {
        val file = element.containingFile ?: return null
        if (file.fileType != QuarkdownFileType.INSTANCE) return null

        val name = bareId(element) ?: return null
        if (name.isEmpty()) return null
        result.add(name)
        return SuggestedNameInfo.NULL_INFO
    }

    /** The bare id for `{#id}` / `{id}` tokens, else null. */
    private fun bareId(element: PsiElement): String? {
        val text = element.text?.trim() ?: return null
        return when {
            text.startsWith("{#") && text.endsWith("}") -> text.substring(2, text.length - 1)
            text.startsWith("{") && text.endsWith("}") -> text.substring(1, text.length - 1)
            else -> null
        }
    }
}