package cc.carm.plugin.intellij.quarkdown.actions

class ToggleStrikethroughAction : BaseToggleAction() {

    override fun getWrapper(): String = "~~"
}
