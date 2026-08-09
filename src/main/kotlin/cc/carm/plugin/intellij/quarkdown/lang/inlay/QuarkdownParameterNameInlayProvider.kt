@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.inlay

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.openapi.editor.Editor
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
        return if (file.fileType is QuarkdownFileType) Collector(editor) else null
    }

    override fun createSettings(): NoSettings = NoSettings()
    override val key: SettingsKey<NoSettings> = QUARKDOWN_PARAMETER_NAME_KEY
    override val name: String = "Quarkdown parameter name hints"
    override val previewText: String =
        ".pagemargin {bottomcenter}\n.multiply {6} by:{3}\n.row {.col {content}}"

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
        object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JComponent = JPanel()
        }

    internal class CollectorForTest(
        private val editor: Editor,
        private val functions: List<FunctionMetadata>
    ) : Collector(editor, functions)

    internal open class Collector(
        private val editor: Editor,
        private val injectedFunctions: List<FunctionMetadata>? = null
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
            val functions = injectedFunctions ?: FunctionRegistry.getInstance(file.project).getFunctions()
            if (functions.isEmpty()) return

            for (dotStart in QuarkdownCallParser.findAllCallStarts(text)) {
                val call = QuarkdownCallParser.parseCall(text, dotStart) ?: continue
                val fn = QuarkdownCallValidator.resolveFunction(call, functions) ?: continue
                if (call.args.isEmpty()) continue

                val (resolved, _) = QuarkdownCallValidator.resolveArgs(call, fn)
                for (r in resolved) {
                    val arg = r.arg
                    // Only positional arguments need an inline name hint; named
                    // arguments already write the parameter name in the source.
                    if (arg.isNamed) continue
                    val param = r.param ?: continue
                    if (arg.braceStart <= 0 || arg.braceStart > text.length) continue
                    sink.addInlineElement(
                        arg.braceStart,
                        false,
                        hintPresentation(param.name),
                        false
                    )
                }
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
