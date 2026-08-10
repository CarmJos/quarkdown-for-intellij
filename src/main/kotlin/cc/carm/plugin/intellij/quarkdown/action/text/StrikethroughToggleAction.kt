package cc.carm.plugin.intellij.quarkdown.action.text

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle

class StrikethroughToggleAction : BaseToggleAction(
    QuarkdownBundle.message("quarkdown.action.strikethrough"),
    QuarkdownBundle.message("quarkdown.action.strikethrough.description")
) {

    override fun getWrapper(): String = "~~"
}
