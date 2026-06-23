package cc.carm.plugin.intellij.quarkdown.action

class ToggleBoldAction : BaseToggleAction() {

    override fun getWrapper(): String = "**"
}
