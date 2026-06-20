package cc.carm.plugin.intellij.quarkdown.actions;

import org.jetbrains.annotations.NotNull;

public class ToggleStrikethroughAction extends BaseToggleAction {

    @Override
    protected @NotNull String getWrapper() {
        return "~~";
    }
}
