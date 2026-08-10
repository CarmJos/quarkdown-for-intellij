package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class QuarkdownFileType : LanguageFileType(QuarkdownLanguage.INSTANCE) {

    override fun getName(): String = "Quarkdown"

    override fun getDescription(): String =
        QuarkdownBundle.message("quarkdown.filetype.description")

    override fun getDefaultExtension(): String = "qd"

    override fun getIcon(): Icon = QuarkdownIcons.FILE

    override fun isReadOnly(): Boolean = false

    companion object {
        @JvmField
        val INSTANCE = QuarkdownFileType()
    }
}
