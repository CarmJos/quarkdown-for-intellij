package cc.carm.plugin.intellij.quarkdown.lang.psi

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.psi.tree.IElementType

object QuarkdownTypes {

    /** Root PSI node for a .qd file (file-level) */
    val FILE = IElementType("QUARKDOWN_FILE", QuarkdownLanguage.INSTANCE)

    /** A Quarkdown function call, e.g. .doctype { paged } */
    val FUNCTION_CALL = IElementType("FUNCTION_CALL", QuarkdownLanguage.INSTANCE)

    /** An reference expression, e.g. .ref { id } */
    val REFERENCE_EXPR = IElementType("REFERENCE_EXPR", QuarkdownLanguage.INSTANCE)

    /** An include expression, e.g. .read { path } / .include { path } */
    val INCLUDE_EXPR = IElementType("INCLUDE_EXPR", QuarkdownLanguage.INSTANCE)

    /** An image expression ![size](path "title") */
    val IMAGE_EXPR = IElementType("IMAGE_EXPR", QuarkdownLanguage.INSTANCE)

    /** A link expression [text](url "title") */
    val LINK_EXPR = IElementType("LINK_EXPR", QuarkdownLanguage.INSTANCE)

    /** A heading line */
    val HEADING = IElementType("HEADING", QuarkdownLanguage.INSTANCE)

    /** A fenced code block */
    val FENCED_CODE_BLOCK = IElementType("FENCED_CODE_BLOCK", QuarkdownLanguage.INSTANCE)
}
