package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class QuarkdownFileType : FileType {

    override fun getName(): String = "Quarkdown"

    override fun getDescription(): String = "Quarkdown document"

    override fun getDefaultExtension(): String = "qd"

    override fun getIcon(): Icon = QuarkdownIcons.FILE

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null

    companion object {
        @JvmField
        val INSTANCE = QuarkdownFileType()
    }
}
