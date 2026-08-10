package cc.carm.plugin.intellij.quarkdown.action.text

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle

class CodeToggleAction : BaseToggleAction(
    QuarkdownBundle.message("quarkdown.action.inline.code"),
    QuarkdownBundle.message("quarkdown.action.code.description")
) {

    override fun getWrapper(): String = "`"
}
