package cc.carm.plugin.intellij.quarkdown.lang.codeblock

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Injects language-specific highlighting and editing support into fenced code blocks
 * within Quarkdown (.qd) files.
 *
 * Uses IntelliJ's [MultiHostInjector] SPI. For each [QuarkdownCodeBlock] PSI element
 * (representing a fenced code block like ```java ... ```), this injector:
 * 1. Reads the language identifier from the code block's PSI structure
 * 2. Resolves it to an IntelliJ [Language] via [QuarkdownLanguageMapper]
 * 3. Registers a temporary language injection for the code content region
 *
 * This enables:
 * - Syntax highlighting inside code blocks (e.g., Java keywords colored correctly)
 * - Inline inspections / annotations for the injected language
 * - Code completion for the injected language
 * - Brace matching and other editor features
 */
class QuarkdownCodeBlockInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val codeBlock = context as? QuarkdownCodeBlock ?: return
        val langId = codeBlock.languageIdentifier
        if (langId.isEmpty()) return

        val injectedLanguage = resolveLanguage(langId) ?: return
        val contentRange = codeBlock.contentRange ?: return

        if (contentRange.isEmpty) return

        val host = codeBlock as? PsiLanguageInjectionHost ?: return

        registrar
            .startInjecting(injectedLanguage)
            .addPlace(null, null, host, contentRange)
            .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(QuarkdownCodeBlock::class.java)

    /**
     * Resolve a language identifier to an IntelliJ [Language].
     * Returns null for plaintext / unsupported languages (no injection needed).
     */
    private fun resolveLanguage(id: String): Language? {
        if (id.isEmpty()) return null
        val lower = id.lowercase()
        // Skip plaintext — no injection needed for plain text
        if (lower == "plaintext" || lower == "text" || lower == "txt" || lower == "plain") return null
        return QuarkdownLanguageMapper.resolve(id)
    }
}
