package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext

/**
 * Accepts Quarkdown ids in the Rename dialog.
 *
 * `RenameUtil.isValidName` uses the first `RenameInputValidator` matching the element;
 * without one it falls back to the Java identifier rules which reject the hyphen
 * (`plc-symbol-output` → "'plc-symbol-output' is not a valid identifier").
 *
 * Quarkdown ids may contain letters, digits, `-` and `_`. We only forbid characters
 * that would break the syntax: braces, whitespace and line breaks.
 */
class QuarkdownRenameInputValidator : RenameInputValidator {

    override fun getPattern(): ElementPattern<PsiElement> =
        PlatformPatterns.psiElement().withLanguage(QuarkdownLanguage.INSTANCE)

    override fun isInputValid(
        newName: String,
        psiElement: PsiElement,
        processingContext: ProcessingContext
    ): Boolean = newName.isNotBlank() &&
            newName.none { it == '{' || it == '}' || it == ' ' || it == '\t' || it == '\n' || it == '\r' }
}