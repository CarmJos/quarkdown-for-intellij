package cc.carm.plugin.intellij.quarkdown.lang.parser

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlock
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownLexer
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownHeading
import cc.carm.plugin.intellij.quarkdown.lang.psi.QuarkdownTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class QuarkdownParserDefinition : ParserDefinition {

    override fun createLexer(project: Project): Lexer = QuarkdownLexer()

    override fun createParser(project: Project): PsiParser = QuarkdownPsiParser()

    override fun getFileNodeType(): IFileElementType =
        IFileElementType(QuarkdownLanguage.INSTANCE)

    override fun getCommentTokens(): TokenSet = COMMENT_TOKENS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createFile(viewProvider: FileViewProvider): PsiFile =
        QuarkdownPsiFile(viewProvider)

    override fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            QuarkdownTypes.HEADING -> QuarkdownHeading(node)
            // Fenced code blocks are PsiLanguageInjectionHosts so the
            // QuarkdownCodeBlockInjector can highlight their content with the
            // declared language (e.g. ```python → Python highlighting).
            QuarkdownTypes.FENCED_CODE_BLOCK -> QuarkdownCodeBlock(node)
            // All other elements implement ContributedReferenceHost so the platform's
            // PsiReferenceService consults our PsiReferenceContributor (required for
            // Ctrl+Click, Go-to-declaration and Find Usages).
            else -> QuarkdownPsiElement(node)
        }
    }

    companion object {
        private val COMMENT_TOKENS = TokenSet.create(
            QuarkdownTokenTypes.HTML_COMMENT,
            QuarkdownTokenTypes.HTML_COMMENT_CONTENT
        )
    }
}
