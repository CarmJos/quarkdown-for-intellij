package cc.carm.plugin.intellij.quarkdown.action.text

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle

class ItalicToggleAction : BaseToggleAction(
    QuarkdownBundle.message("quarkdown.action.italic"),
    QuarkdownBundle.message("quarkdown.action.italic.description")
) {

    override fun getWrapper(): String = "*"
}
