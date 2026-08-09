package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

/**
 * Verifies the floating table editor (inlay provider) and the table gutter marker are
 * registered on the platform for the Quarkdown language.
 */
class QuarkdownTableEditorPlatformTest : BasePlatformTestCase() {

    fun `test inlay provider registered for quarkdown`() {
        val providers = InlayHintsProviderExtension.findProviders()
        assertTrue(
            "QuarkdownTableEditorProvider should be registered, got: ${providers.map { it.provider.javaClass.simpleName }}",
            providers.any { it.provider is QuarkdownTableEditorProvider }
        )
    }

    fun `test inlay provider supports the quarkdown language`() {
        val provider = QuarkdownTableEditorProvider()
        assertTrue(
            "provider should support Quarkdown language",
            provider.isLanguageSupported(QuarkdownLanguage.INSTANCE)
        )
    }

    fun `test table line marker still registered`() {
        val providers = LineMarkerProviders.getInstance()
            .allForLanguage(QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownTableLineMarkerProvider should be registered",
            providers.any { it is QuarkdownTableLineMarkerProvider }
        )
    }
}
