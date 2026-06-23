package cc.carm.plugin.intellij.quarkdown.action

class ToggleStrikethroughAction : BaseToggleAction() {

    override fun getWrapper(): String = "~~"
}
