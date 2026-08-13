package cc.carm.plugin.intellij.quarkdown.lang.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

/**
 * LSP4IJ factory for the Quarkdown language server.
 *
 * Registered via the `com.redhat.devtools.lsp4ij.server` extension point (see
 * META-INF/plugin.xml). LSP4IJ starts the server automatically when a `.qd` file is
 * opened, in every IntelliJ product (including Community and Android Studio), because
 * LSP4IJ itself only depends on `com.intellij.modules.platform`.
 */
class QuarkdownLanguageServerFactory : LanguageServerFactory {

    override fun createConnectionProvider(project: Project): StreamConnectionProvider =
        QuarkdownLanguageServerConnectionProvider(project)

    override fun createLanguageClient(project: Project): LanguageClientImpl =
        QuarkdownLanguageClient(project)
}
