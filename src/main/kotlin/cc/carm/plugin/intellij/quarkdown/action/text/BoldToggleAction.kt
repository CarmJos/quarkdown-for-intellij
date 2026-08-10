package cc.carm.plugin.intellij.quarkdown.action.text

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle

class BoldToggleAction : BaseToggleAction(
    QuarkdownBundle.message("quarkdown.action.bold"),
    QuarkdownBundle.message("quarkdown.action.bold.description")
) {

    override fun getWrapper(): String = "**"
}
