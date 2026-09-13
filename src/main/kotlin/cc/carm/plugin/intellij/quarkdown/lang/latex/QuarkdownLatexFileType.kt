package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * File type of [QuarkdownLatexLanguage], needed because an embedded editor takes its language — and
 * therefore its syntax highlighter — from its file type
 * ([com.intellij.ui.EditorTextField] accepts nothing else).
 *
 * No extension is registered for it, so it never claims a file on disk; it exists only so the
 * equation editor's input field can be a real editor with line numbers and TeX coloring.
 */
class QuarkdownLatexFileType private constructor() : LanguageFileType(QuarkdownLatexLanguage.INSTANCE) {

    override fun getName(): String = "QuarkdownLaTeX"

    override fun getDescription(): String = "TeX content of a Quarkdown equation"

    override fun getDefaultExtension(): String = "tex"

    override fun getIcon(): Icon? = null

    companion object {
        @JvmField
        val INSTANCE = QuarkdownLatexFileType()
    }
}
