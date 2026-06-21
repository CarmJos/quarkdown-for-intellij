package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.lang.QuarkdownTypedHandlerDelegate
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName

@Service(Service.Level.APP)
class QuarkdownCompletionRegistrar {

    init {
        val epName = ExtensionPointName.create<CompletionContributorEP>("com.intellij.completion.contributor")

        val epAny = CompletionContributorEP().apply {
            language = ""
            implementationClass = "cc.carm.plugin.intellij.quarkdown.lang.completion.QuarkdownCompletionContributor"
        }
        epName.point.registerExtension(epAny)

        val epQuarkdown = CompletionContributorEP().apply {
            language = "Quarkdown"
            implementationClass = "cc.carm.plugin.intellij.quarkdown.lang.completion.QuarkdownCompletionContributor"
        }
        epName.point.registerExtension(epQuarkdown)

        val typedHandlerPoint = ExtensionPointName.create<TypedHandlerDelegate>("com.intellij.typedHandler")
        typedHandlerPoint.point.registerExtension(QuarkdownTypedHandlerDelegate())
    }
}
