package cc.carm.plugin.intellij.quarkdown.action.text

class BoldToggleAction : BaseToggleAction() {

    override fun getWrapper(): String = "**"
}
