package cc.carm.plugin.intellij.quarkdown.lang.latex

import java.io.File

/**
 * Locates the LaTeX renderer that ships **inside the Quarkdown installation**, so equations can be
 * typeset in the IDE without starting the CLI.
 *
 * The installation keeps its static web assets next to the jars:
 *
 * ```
 * <home>/lib/html/
 *   lib/katex/katex.min.js      ← the renderer
 *   lib/katex/katex.min.css     ← its stylesheet
 *   lib/katex/fonts/            ← its web fonts
 *   script/, theme/             ← the rest of the generated page's assets
 * ```
 *
 * These are the very files Quarkdown copies into every compiled document, so reusing them gives
 * the preview the same KaTeX version, the same stylesheet and the same fonts as the final output —
 * offline, with no process to launch and nothing to bundle in this plugin.
 */
object QuarkdownLatexWebAssets {

    /** Asset root the paths below are relative to (the directory the CLI also serves). */
    const val ASSET_ROOT = "lib/html"

    /** Relative path of the renderer inside [ASSET_ROOT]. */
    const val SCRIPT_PATH = "lib/katex/katex.min.js"

    /** Relative path of the stylesheet inside [ASSET_ROOT]. */
    const val STYLE_PATH = "lib/katex/katex.min.css"

    /**
     * The asset root of [home], or `null` when [home] does not look like a Quarkdown installation
     * that ships the LaTeX renderer.
     */
    fun assetRoot(home: File?): File? {
        if (home == null || !home.isDirectory) return null
        val root = File(home, ASSET_ROOT)
        return root.takeIf { File(root, SCRIPT_PATH).isFile }
    }
}
