package cc.carm.plugin.intellij.quarkdown.lang.documentation

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.completion.FunctionCallTokenizer
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownDocRenderer
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspSupport
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Provides Quick Documentation (Ctrl+Q / hover) for Quarkdown function calls and
 * completion items. Documentation content comes from the stdlib docs parsed at index
 * build time and carried inside [FunctionMetadata].
 *
 * When the official Quarkdown Language Server is running
 * ([QuarkdownLspSupport.isServerRunning]), hover documentation is delegated to the LSP
 * provider; this class acts as the offline fallback only.
 */
class QuarkdownDocumentationProvider : AbstractDocumentationProvider() {

    /** Completion lookup items carry a [FunctionMetadata] as their lookup object. */
    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        lookupElement: Any,
        contextElement: PsiElement?
    ): PsiElement? {
        if (QuarkdownLspSupport.isServerRunning(psiManager.project)) return null
        val metadata = lookupElement as? FunctionMetadata ?: return null
        val contextFile = contextElement?.containingFile ?: return null
        return QuarkdownDocElement(psiManager.project, metadata, contextFile)
    }

    /** Editor positions: resolve the function call under the caret via the tokenizer. */
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        offset: Int
    ): PsiElement? {
        if (file.fileType != QuarkdownFileType.INSTANCE) return null
        if (QuarkdownLspSupport.isServerRunning(file.project)) return null

        val context = FunctionCallTokenizer.parseContext(file.text, offset)
        if (!context.hasCall) return null
        val name = context.functionName
        val metadata = FunctionRegistry.getInstance(file.project).getFunction(name) ?: return null
        return QuarkdownDocElement(file.project, metadata, file)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val docElement = element as? QuarkdownDocElement ?: return null
        return QuarkdownDocRenderer.render(docElement.metadata)
    }
}
