@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.inlay

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspFunctionSignatureCache
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

private val QUARKDOWN_PARAMETER_NAME_KEY = SettingsKey<NoSettings>("quarkdown.parameter.name.hints")

/**
 * Inlay hints showing the parameter name before each positional argument of a known
 * Quarkdown function call:
 *
 *   .pagemargin {bottomcenter}        →  .pagemargin [position:]{bottomcenter}
 *   .multiply {6} by:{3}              →  .multiply [a:]{6} by:{3}
 *
 * Function signatures (the ordered user-facing parameter names) come from the official
 * `quarkdown language-server` via [QuarkdownLspFunctionSignatureCache] — the same
 * source that drives completion/diagnostics — replacing the legacy reflective stdlib
 * introspection. The signatures are fetched asynchronously; when they arrive the
 * modification tracker is bumped so the hints re-collect.
 *
 * Named arguments (`name:{value}`) already carry their parameter name in the source, so
 * only positional arguments get an inline hint. The hint is rendered before the opening
 * brace with a subtle color and is not focusable, mirroring the "Inline Parameter Name"
 * behavior of the IntelliJ Java parameter hints.
 */
class QuarkdownParameterNameInlayProvider : InlayHintsProvider<NoSettings> {

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (file.fileType !is QuarkdownFileType) return null

        // When the LSP cache fetches new signatures (async), re-run the inlay pass so
        // hints that were waiting on the fetch appear without requiring an edit.
        val project = file.project
        val cache = QuarkdownLspFunctionSignatureCache.getInstance(project)
        cache.onSignaturesUpdated = {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart()
        }
        return Collector(editor)
    }

    override fun createSettings(): NoSettings = NoSettings()
    override val key: SettingsKey<NoSettings> = QUARKDOWN_PARAMETER_NAME_KEY
    override val name: String = QuarkdownBundle.message("quarkdown.inlay.parameter.name")
    override val previewText: String =
        ".pagemargin {bottomcenter}\n.multiply {6} by:{3}\n.row {.col {content}}"

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    internal open class Collector(
        private val editor: Editor,
    ) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (!element.isValid) return true
            if (element is PsiFile && element.fileType is QuarkdownFileType) {
                processFile(element, sink)
            }
            return true
        }

        internal fun processFile(file: PsiFile, sink: InlayHintsSink) {
            val text = file.text
            if (text.isBlank()) return

            val cache = QuarkdownLspFunctionSignatureCache.getInstance(file.project)

            // Collect the distinct function names called in this document and ask the LSP
            // cache for any signatures it doesn't have yet (async; hints appear once ready).
            val calls = QuarkdownCallParser.findAllCallStarts(text)
                .mapNotNull { QuarkdownCallParser.parseCall(text, it) }
                .filter { it.args.isNotEmpty() }

            cache.requestSignatures(calls.map { it.name }, file)

            for (call in calls) {
                val paramNames = cache.getParameterNames(call.name) ?: continue
                if (paramNames.isEmpty()) continue
                addHintsForCall(text, sink, call, paramNames)
            }
        }

        /**
         * Maps positional arguments to parameter names and emits an inlay hint before each
         * positional arg's opening brace. Mirrors the legacy `resolveArgs`: named arguments
         * don't consume a positional slot; chained calls reserve slot 0 for the chained value.
         */
        internal fun addHintsForCall(
            text: String,
            sink: InlayHintsSink,
            call: QuarkdownCallParser.Call,
            paramNames: List<String>
        ) {
            // For chained calls (`::b`), the chained value is the implicit first
            // positional argument, so explicit positional arguments start at index 1.
            var positionalIndex = if (call.isChained) 1 else 0
            for (arg in call.args) {
                if (arg.isNamed) continue
                val paramName = paramNames.getOrNull(positionalIndex) ?: break
                positionalIndex++
                if (arg.braceStart <= 0 || arg.braceStart > text.length) continue
                sink.addInlineElement(
                    arg.braceStart,
                    false,
                    hintPresentation(paramName),
                    false
                )
            }
        }

        private fun hintPresentation(paramName: String) =
            SequencePresentation(
                listOf(
                    factory.text(paramName),
                    factory.text(": ")
                )
            )
    }
}
