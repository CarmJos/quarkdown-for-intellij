package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object QuarkdownIcons {

    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/quarkdown.svg", QuarkdownIcons::class.java)

    /** Gutter icon for image lines */
    @JvmField
    val IMAGE: Icon = IconLoader.getIcon("/icons/image.svg", QuarkdownIcons::class.java)

    /** Gutter icon for table blocks */
    @JvmField
    val TABLE: Icon = IconLoader.getIcon("/icons/table.svg", QuarkdownIcons::class.java)

    // ------------------------------------------------------------------
    // Table editor toolbar icons
    // ------------------------------------------------------------------

    @JvmField
    val ALIGN_LEFT: Icon = IconLoader.getIcon("/icons/actions/leftAlign.svg", QuarkdownIcons::class.java)

    @JvmField
    val ALIGN_CENTER: Icon = IconLoader.getIcon("/icons/actions/centerAlign.svg", QuarkdownIcons::class.java)

    @JvmField
    val ALIGN_RIGHT: Icon = IconLoader.getIcon("/icons/actions/rightAlign.svg", QuarkdownIcons::class.java)

    @JvmField
    val ADD_ROW_ABOVE: Icon = IconLoader.getIcon("/icons/actions/addRowAbove.svg", QuarkdownIcons::class.java)

    @JvmField
    val ADD_ROW_BELOW: Icon = IconLoader.getIcon("/icons/actions/addRowBelow.svg", QuarkdownIcons::class.java)

    @JvmField
    val ADD_COLUMN_LEFT: Icon = IconLoader.getIcon("/icons/actions/addColumnLeft.svg", QuarkdownIcons::class.java)

    @JvmField
    val ADD_COLUMN_RIGHT: Icon = IconLoader.getIcon("/icons/actions/addColumnRight.svg", QuarkdownIcons::class.java)
}
