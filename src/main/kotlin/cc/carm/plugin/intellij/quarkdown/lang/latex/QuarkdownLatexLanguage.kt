package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.lang.Language

/**
 * The language of a *bare* TeX/LaTeX fragment — one equation's content, without its delimiters.
 *
 * Inside a document the TeX of an equation is plain text to the Quarkdown lexer and is colored by
 * [cc.carm.plugin.intellij.quarkdown.lang.annotator.QuarkdownLatexAnnotator]. A dialog is not a
 * document, so there is no Quarkdown PSI to annotate there: the equation editor's input field needs
 * a language of its own so the platform can highlight it with a real
 * [com.intellij.openapi.fileTypes.SyntaxHighlighter] (see [QuarkdownLatexSyntaxHighlighter]).
 *
 * Both paths end up in [QuarkdownLatexHighlighting], so a formula is colored identically in the
 * editor and in the dialog.
 *
 * [Language] registers itself on construction, which is what makes the `lang.syntaxHighlighterFactory`
 * extension resolvable by [ID].
 */
class QuarkdownLatexLanguage private constructor() : Language(ID) {

    companion object {

        /** Id the `lang.syntaxHighlighterFactory` extension refers to. */
        const val ID = "QuarkdownLaTeX"

        @JvmField
        val INSTANCE = QuarkdownLatexLanguage()
    }
}
