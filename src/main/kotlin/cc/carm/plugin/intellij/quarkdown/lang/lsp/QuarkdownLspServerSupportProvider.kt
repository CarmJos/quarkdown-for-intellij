package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider

/**
 * Starts the `quarkdown language-server` for every Quarkdown file opened in the editor.
 *
 * The IntelliJ platform LSP plugin calls [fileOpened] when a file matching our file type
 * is opened; we only hand the server starter a [QuarkdownLspServerDescriptor] when the
 * file actually belongs to the Quarkdown language (defensive, in case other file types
 * are ever routed here) and the LSP semantics are enabled ([QuarkdownLspSupport]). The
 * platform then manages the process lifecycle.
 */
class QuarkdownLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        starter: LspServerSupportProvider.LspServerStarter,
    ) {
        if (file.fileType != QuarkdownFileType.INSTANCE) return
        if (!QuarkdownLspSupport.isEnabled(project)) return
        starter.ensureServerStarted(QuarkdownLspServerDescriptor(project, file))
    }
}
