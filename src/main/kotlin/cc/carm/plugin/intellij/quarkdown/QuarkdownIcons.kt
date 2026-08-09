package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Central registry of every icon used by the Quarkdown plugin.
 *
 * Icon files live in `src/main/resources/icons/` and are grouped by domain folder:
 *  - `marker/`     — gutter/line-marker icons
 *  - `action/`     — action toolbar icons, further grouped by target (table, text)
 */
object QuarkdownIcons {

    /** File type icon for `.qd` documents (also the plugin logo). */
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/quarkdown.svg", QuarkdownIcons::class.java)

    // ------------------------------------------------------------------
    // Marker (gutter) domain
    // ------------------------------------------------------------------

    /** Gutter icon for image lines */
    @JvmField
    val IMAGE_MARKER: Icon = IconLoader.getIcon("/icons/marker/image.svg", QuarkdownIcons::class.java)

    /** Gutter icon for table blocks */
    @JvmField
    val TABLE_MARKER: Icon = IconLoader.getIcon("/icons/marker/table.svg", QuarkdownIcons::class.java)

    /** Gutter icon for code blocks (fenced and `.code` function blocks) */
    @JvmField
    val CODE_MARKER: Icon = IconLoader.getIcon("/icons/marker/code.svg", QuarkdownIcons::class.java)

    /** Gutter icon for equations (inline `$...$` and fenced `$$$`) */
    @JvmField
    val EQUATION_MARKER: Icon = IconLoader.getIcon("/icons/marker/equation.svg", QuarkdownIcons::class.java)

    /** Gutter icon for headings (`# Title {#id}` … `###### Title {#id}`) */
    @JvmField
    val HEADING_MARKER: Icon = IconLoader.getIcon("/icons/marker/heading.svg", QuarkdownIcons::class.java)

    // ------------------------------------------------------------------
    // Action domain — table
    // ------------------------------------------------------------------

    /** Move the row up (table toolbar) */
    @JvmField
    val TABLE_MOVE_ROW_UP: Icon = IconLoader.getIcon("/icons/action/table/move_row_up.svg", QuarkdownIcons::class.java)

    /** Move the row down (table toolbar) */
    @JvmField
    val TABLE_MOVE_ROW_DOWN: Icon =
        IconLoader.getIcon("/icons/action/table/move_row_down.svg", QuarkdownIcons::class.java)

    /** Move the column to the left (table toolbar) */
    @JvmField
    val TABLE_MOVE_COLUMN_LEFT: Icon =
        IconLoader.getIcon("/icons/action/table/move_column_left.svg", QuarkdownIcons::class.java)

    /** Move the column to the right (table toolbar) */
    @JvmField
    val TABLE_MOVE_COLUMN_RIGHT: Icon =
        IconLoader.getIcon("/icons/action/table/move_column_right.svg", QuarkdownIcons::class.java)

    /** Select the clicked row/column (table toolbar) */
    @JvmField
    val TABLE_SELECT: Icon = IconLoader.getIcon("/icons/action/table/select.svg", QuarkdownIcons::class.java)

    /** Remove the clicked row (table toolbar) */
    @JvmField
    val TABLE_REMOVE: Icon = IconLoader.getIcon("/icons/action/table/remove.svg", QuarkdownIcons::class.java)

    /** Align column to the left (table toolbar) */
    @JvmField
    val TABLE_ALIGN_LEFT: Icon = IconLoader.getIcon("/icons/action/table/align_left.svg", QuarkdownIcons::class.java)

    /** Align column to the center (table toolbar) */
    @JvmField
    val TABLE_ALIGN_CENTER: Icon =
        IconLoader.getIcon("/icons/action/table/align_center.svg", QuarkdownIcons::class.java)

    /** Align column to the right (table toolbar) */
    @JvmField
    val TABLE_ALIGN_RIGHT: Icon = IconLoader.getIcon("/icons/action/table/align_right.svg", QuarkdownIcons::class.java)

    /** Insert a row above (table toolbar) */
    @JvmField
    val TABLE_ADD_ROW_ABOVE: Icon =
        IconLoader.getIcon("/icons/action/table/add_row_above.svg", QuarkdownIcons::class.java)

    /** Insert a row below (table toolbar) */
    @JvmField
    val TABLE_ADD_ROW_BELOW: Icon =
        IconLoader.getIcon("/icons/action/table/add_row_below.svg", QuarkdownIcons::class.java)

    /** Insert a column to the left (table toolbar) */
    @JvmField
    val TABLE_ADD_COLUMN_LEFT: Icon =
        IconLoader.getIcon("/icons/action/table/add_column_left.svg", QuarkdownIcons::class.java)

    /** Insert a column to the right (table toolbar) */
    @JvmField
    val TABLE_ADD_COLUMN_RIGHT: Icon =
        IconLoader.getIcon("/icons/action/table/add_column_right.svg", QuarkdownIcons::class.java)

    /** Re-align / format the whole table (table toolbar) */
    @JvmField
    val TABLE_FORMAT: Icon = IconLoader.getIcon("/icons/action/table/format_table.svg", QuarkdownIcons::class.java)

    // ------------------------------------------------------------------
    // Action domain — text formatting
    // ------------------------------------------------------------------

    /** Toggle bold text (floating toolbar) */
    @JvmField
    val TEXT_BOLD: Icon = IconLoader.getIcon("/icons/action/text/bold.svg", QuarkdownIcons::class.java)

    /** Toggle italic text (floating toolbar) */
    @JvmField
    val TEXT_ITALIC: Icon = IconLoader.getIcon("/icons/action/text/italic.svg", QuarkdownIcons::class.java)

    /** Toggle strikethrough text (floating toolbar) */
    @JvmField
    val TEXT_STRIKETHROUGH: Icon =
        IconLoader.getIcon("/icons/action/text/strikethrough.svg", QuarkdownIcons::class.java)

    /** Toggle inline code (floating toolbar) */
    @JvmField
    val TEXT_CODE: Icon = IconLoader.getIcon("/icons/action/text/code.svg", QuarkdownIcons::class.java)

    /** Insert a link (floating toolbar) */
    @JvmField
    val TEXT_LINK: Icon = IconLoader.getIcon("/icons/action/text/link.svg", QuarkdownIcons::class.java)

    // ------------------------------------------------------------------
    // Action domain — preview
    // Each icon uses a distinct color to visually separate its function:
    //   green  = start preview,  red = stop / clean,
    //   blue   = view (peek),    yellow = watch,
    //   orange = refresh,         purple = build,
    //   teal   = open in browser.
    // ------------------------------------------------------------------

    /** Start the live preview (green play) */
    @JvmField
    val PREVIEW_PLAY: Icon = IconLoader.getIcon("/icons/action/preview/play.svg", QuarkdownIcons::class.java)

    /** Stop the live preview (red stop) */
    @JvmField
    val PREVIEW_STOP: Icon = IconLoader.getIcon("/icons/action/preview/stop.svg", QuarkdownIcons::class.java)

    /** View the running preview in the external browser (blue eye, shown while running) */
    @JvmField
    val PREVIEW_VIEW: Icon = IconLoader.getIcon("/icons/action/preview/view.svg", QuarkdownIcons::class.java)

    /** Watch changes / auto-refresh (yellow eye) */
    @JvmField
    val PREVIEW_WATCH: Icon = IconLoader.getIcon("/icons/action/preview/watch.svg", QuarkdownIcons::class.java)

    /** Manually refresh the live preview (orange) */
    @JvmField
    val PREVIEW_REFRESH: Icon = IconLoader.getIcon("/icons/action/preview/refresh.svg", QuarkdownIcons::class.java)

    /** Clean the preview output cache and refresh (red) */
    @JvmField
    val PREVIEW_CLEAN: Icon = IconLoader.getIcon("/icons/action/preview/clean.svg", QuarkdownIcons::class.java)

    /** Build (compile to PDF) through the IDE *Run* tool window (purple) */
    @JvmField
    val PREVIEW_BUILD: Icon = IconLoader.getIcon("/icons/action/preview/build.svg", QuarkdownIcons::class.java)

    /** Open the port-based preview in an external browser (teal globe) */
    @JvmField
    val PREVIEW_BROWSER: Icon = IconLoader.getIcon("/icons/action/preview/browser.svg", QuarkdownIcons::class.java)
}
