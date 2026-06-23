package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object QuarkdownIcons {

    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/quarkdown.svg", QuarkdownIcons::class.java)

    /** Gutter icon for image lines */
    @JvmField
    val IMAGE: Icon = IconLoader.getIcon("/icons/image.svg", QuarkdownIcons::class.java)
}
