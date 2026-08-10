package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider

/**
 * Makes the floating toolbar group ("Quarkdown.Toolbar.Floating") visible in
 * Settings → Appearance → Menus and Toolbars so users can customize it
 * (mirrors the Markdown plugin's FloatingToolbarCustomizableGroupProvider).
 */
class FloatingToolbarGroupProvider : CustomizableActionGroupProvider() {

    override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
        registrar.addCustomizableActionGroup(
            "Quarkdown.Toolbar.Floating",
            QuarkdownBundle.message("quarkdown.floating.toolbar.group.name")
        )
    }
}
