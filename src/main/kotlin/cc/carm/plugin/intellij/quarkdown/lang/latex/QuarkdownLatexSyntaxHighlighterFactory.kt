package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Provides [QuarkdownLatexSyntaxHighlighter] for [QuarkdownLatexLanguage]. */
class QuarkdownLatexSyntaxHighlighterFactory : SyntaxHighlighterFactory() {

    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        QuarkdownLatexSyntaxHighlighter()
}
