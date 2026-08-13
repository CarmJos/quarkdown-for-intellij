package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform-level tests for [QuarkdownSettings] persistence: default values are
 * applied, values are round-tripped, and the project-scoped service is reachable.
 */
class QuarkdownSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        // Reset any mutations from a previous test so each test starts from defaults.
        val settings = QuarkdownSettings.getInstance(project)
        settings.state.previewPort = 8989
        settings.state.outputDirectory = "quarkdown-output"
        settings.state.watchChanges = true
        settings.state.autoSavePreviewFiles = true
        settings.state.quarkdownPath = ""
        super.tearDown()
    }

    fun `test default values`() {
        val settings = QuarkdownSettings.getInstance(project)
        assertEquals(8989, settings.state.previewPort)
        assertEquals("quarkdown-output", settings.state.outputDirectory)
        assertEquals(true, settings.state.watchChanges)
        assertEquals(true, settings.state.autoSavePreviewFiles)
    }

    fun `test value round trip`() {
        val settings = QuarkdownSettings.getInstance(project)
        settings.state.previewPort = 9000
        settings.state.outputDirectory = "build/out"
        settings.state.watchChanges = false
        settings.state.quarkdownPath = "C:/quarkdown"

        val reloaded = QuarkdownSettings.getInstance(project)
        assertEquals(9000, reloaded.state.previewPort)
        assertEquals("build/out", reloaded.state.outputDirectory)
        assertEquals(false, reloaded.state.watchChanges)
        assertEquals("C:/quarkdown", reloaded.state.quarkdownPath)
    }
}
