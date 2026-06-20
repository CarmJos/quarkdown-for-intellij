package cc.carm.plugin.intellij.quarkdown.actions

class ToggleBoldAction : BaseToggleAction() {

    override fun getWrapper(): String = "**"
}
