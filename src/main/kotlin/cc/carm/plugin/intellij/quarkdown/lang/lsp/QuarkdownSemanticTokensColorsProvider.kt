package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.lang.highlighter.QuarkdownSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider

/**
 * Maps the semantic token legend emitted by `quarkdown-lsp` to the plugin's existing
 * semantic highlight colors (LSP4IJ variant).
 *
 * The quarkdown LSP legend (`TokenType.legend`) contains the following token types:
 *
 * | legend name | quarkdown token           | mapped color                            |
 * |-------------|---------------------------|-----------------------------------------|
 * | `function`  | function call identifier  | `SEMANTIC_KNOWN_FUNCTION`              |
 * | `parameter` | named parameter name      | `SEMANTIC_PARAMETER`                   |
 * | `enum`      | enum literal value        | `SEMANTIC_VALID_ENUM`                  |
 * | `number`    | numeric argument value    | `DefaultLanguageHighlighterColors.NUMBER` |
 * | `keyword`   | chaining separator / delimiter / boolean | `DefaultLanguageHighlighterColors.KEYWORD` |
 */
class QuarkdownSemanticTokensColorsProvider : SemanticTokensColorsProvider {

    override fun getTextAttributesKey(
        tokenType: String,
        tokenModifiers: List<String>,
        file: PsiFile,
    ): TextAttributesKey? = when (tokenType) {
        "function" -> QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION
        "parameter" -> QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER
        "enum" -> QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM
        else -> null
    }
}
