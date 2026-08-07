package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.ParameterMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Arg
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownPathUtil
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Context-aware completion for Quarkdown function calls, following the real grammar:
 *
 *   .doctype {paged}                      positional argument
 *   .pagemargin position:{bottomcenter}   named argument
 *
 * Supports:
 *  - function-name completion after `.`
 *  - next-argument hints after a complete function name or written arguments
 *  - enum value completion inside `{…}` and right after `name:`
 */
class QuarkdownCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), QuarkdownProvider())
    }

    class QuarkdownProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val file = parameters.originalFile
            if (file.fileType != QuarkdownFileType.INSTANCE) return

            val editor = parameters.editor
            val offset = editor.caretModel.offset
            val text = editor.document.charsSequence

            val registry = FunctionRegistry.getInstance(file.project)
            val functions = registry.getFunctions()

            val ctx = FunctionCallTokenizer.parseContext(text, offset)
            if (!ctx.hasCall) return

            // File-path completion for `.include`/`.read`/`.css`/`.code` paths.
            // Kept registry-independent so it works even before the Quarkdown SDK
            // has been indexed (the path-taking functions are part of the core grammar).
            when {
                ctx.currentArg != null -> maybeSuggestFilePath(parameters, ctx, result)
                ctx.afterNamedColon -> maybeSuggestFilePathAfterColon(parameters, ctx, result)
            }

            if (functions.isEmpty()) return

            when {
                ctx.inFunctionName -> suggestFunctionNames(ctx.namePrefix, functions, result)

                ctx.currentArg != null -> {
                    val fn = functions.find { it.name == ctx.functionName } ?: return
                    val param = resolveParam(fn, ctx.currentArg, ctx.allArgs, ctx.call?.isChained == true)
                    if (param?.allowedValues != null) {
                        suggestValues(param, ctx.valuePrefix, result, wrapInBraces = false)
                    }
                }

                ctx.afterNamedColon -> {
                    val fn = functions.find { it.name == ctx.functionName } ?: return
                    val param = fn.parameters.find { !it.isInjected && it.name == ctx.pendingNamedParam }
                    if (param?.allowedValues != null) {
                        suggestValues(param, "", result, wrapInBraces = true)
                    }
                }

                else -> suggestNextArguments(ctx, functions, result)
            }
        }

        // ------------------------------------------------------------------
        // File-path completion for `.include {path}` / `.read {path}` etc.
        // ------------------------------------------------------------------

        /** Functions whose value argument is a file path. */
        private val PATH_FUNCTIONS = setOf("include", "read", "css", "code")

        /** Parameter names that carry a file path (named-argument syntax). */
        private val PATH_PARAM_NAMES = setOf("path", "file", "source", "src", "url")

        private fun maybeSuggestFilePath(
            parameters: CompletionParameters,
            ctx: FunctionCallTokenizer.FunctionCallContext,
            result: CompletionResultSet
        ) {
            if (ctx.functionName !in PATH_FUNCTIONS) return
            val arg = ctx.currentArg ?: return
            if (!isPathArg(ctx.functionName, arg, ctx.allArgs)) return
            suggestFilePaths(parameters, arg, ctx.valuePrefix, wrapInBraces = false, result)
        }

        private fun maybeSuggestFilePathAfterColon(
            parameters: CompletionParameters,
            ctx: FunctionCallTokenizer.FunctionCallContext,
            result: CompletionResultSet
        ) {
            if (ctx.functionName !in PATH_FUNCTIONS) return
            if ((ctx.pendingNamedParam ?: "") !in PATH_PARAM_NAMES) return
            suggestFilePaths(parameters, null, "", wrapInBraces = true, result)
        }

        /** True when [arg] carries the file path of a path-taking function. */
        private fun isPathArg(functionName: String, arg: Arg, allArgs: List<Arg>): Boolean {
            if (functionName !in PATH_FUNCTIONS) return false
            if (arg.isNamed) return (arg.paramName ?: "") in PATH_PARAM_NAMES
            // The path is the first positional argument of include/read/css/code.
            var pos = 0
            for (a in allArgs) {
                if (a === arg) return pos == 0
                if (!a.isNamed) pos++
            }
            return false
        }

        /** Resolved base directory for path completion plus the path prefix to insert. */
        private data class PathBase(val dir: VirtualFile, val basePath: String, val namePart: String)

        /**
         * Resolves the directory whose children should be offered for the typed
         * [dirPart]. When the directory doesn't exist yet, walks up to its nearest
         * existing ancestor so `{docs/ima…}` still suggests `images/` inside `docs/`.
         */
        private fun findPathBase(
            project: Project,
            sourceFile: VirtualFile,
            dirPart: String,
            namePart: String
        ): PathBase? {
            var searchDir = dirPart.trimEnd('/', '\\')
            var searchName = namePart
            while (true) {
                val resolved = if (searchDir.isEmpty()) {
                    sourceFile.parent
                } else {
                    QuarkdownPathUtil.resolveToVirtualFile(project, sourceFile, searchDir)
                        ?.takeIf { it.isDirectory }
                }
                if (resolved != null) {
                    val basePath = if (searchDir.isEmpty()) "" else searchDir.trimEnd('/', '\\') + "/"
                    return PathBase(resolved, basePath, searchName)
                }
                val idx = searchDir.lastIndexOf('/').coerceAtLeast(searchDir.lastIndexOf('\\'))
                if (idx < 0) {
                    // No existing ancestor — fall back to the file's own directory.
                    val parent = sourceFile.parent ?: return null
                    return PathBase(parent, "", searchDir + searchName)
                }
                searchName = searchDir.substring(idx + 1) + searchName
                searchDir = searchDir.substring(0, idx)
            }
        }

        /**
         * Suggests files and directories reachable from the current document, driven by
         * the partial path typed in [valuePrefix]. Supports quoted values
         * (`{"docs/intro.qd"}`), directory navigation (`{docs/…}`) and, when a directory
         * part doesn't exist yet, falls back to its nearest existing ancestor.
         *
         * @param arg the parsed argument the caret is inside, or `null` when completing
         *            right after a named-argument colon (`path:`).
         */
        private fun suggestFilePaths(
            parameters: CompletionParameters,
            arg: Arg?,
            valuePrefix: String,
            wrapInBraces: Boolean,
            result: CompletionResultSet
        ) {
            val psiFile = parameters.originalFile
            val project = psiFile.project
            val virtualFile = psiFile.virtualFile ?: return

            // Normalize the typed value: strip leading whitespace and an optional
            // opening/closing quote.
            val trimmed = valuePrefix.trimStart()
            val quoted = trimmed.startsWith("\"")
            val prefix = (if (quoted) trimmed.removePrefix("\"") else trimmed).removeSuffix("\"")

            // Split the value into a directory part and a (partial) file name.
            val lastSep = prefix.lastIndexOf('/').coerceAtLeast(prefix.lastIndexOf('\\'))
            val dirPart = if (lastSep >= 0) prefix.substring(0, lastSep + 1) else ""
            val namePart = if (lastSep >= 0) prefix.substring(lastSep + 1) else prefix

            // Resolve the directory whose children should be offered. If the typed
            // directory doesn't exist yet, walk up to its nearest existing ancestor so
            // `{docs/ima…}` still suggests `images/` inside `docs/`.
            val pathBase = findPathBase(project, virtualFile, dirPart, namePart) ?: return
            val base = pathBase.dir
            if (!base.isValid) return
            val basePath = pathBase.basePath
            val searchName = pathBase.namePart

            // Document offset where the path value begins (after whitespace/quotes).
            val frontTrimmed = valuePrefix.length - trimmed.length
            val pathValueStart = if (arg != null) {
                arg.rawStart + frontTrimmed + (if (quoted) 1 else 0)
            } else {
                -1 // after a named colon there is no value yet; use the platform offsets
            }

            // Detect whether a closing quote already follows the caret.
            var hasClosingQuote = false
            if (quoted && arg != null) {
                val caret = parameters.offset
                if (caret < arg.braceEnd) {
                    hasClosingQuote = psiFile.text.startsWith("\"", caret)
                }
            }

            val lowerName = searchName.lowercase()
            val showHidden = lowerName.startsWith(".")
            for (child in base.children.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() }))) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (!child.name.lowercase().startsWith(lowerName)) continue

                val isDir = child.isDirectory
                val displayText = child.name + if (isDir) "/" else ""
                val relativePath = basePath + displayText
                val icon = if (isDir) AllIcons.Nodes.Folder
                else child.fileType?.icon ?: AllIcons.FileTypes.Any_type

                val lookup = LookupElementBuilder.create(displayText)
                    .withLookupString(relativePath)
                    .withIcon(icon)
                    .withTypeText(if (isDir) "directory" else (child.extension?.uppercase() ?: "file"), true)
                    .withTailText(if (basePath.isNotEmpty()) "  $basePath" else null, true)
                    .withInsertHandler(
                        pathInsertHandler(
                            pathValueStart = pathValueStart,
                            relativePath = relativePath,
                            isDirectory = isDir,
                            quoted = quoted,
                            hasClosingQuote = hasClosingQuote,
                            wrapInBraces = wrapInBraces,
                            project = project
                        )
                    )
                result.addElement(lookup)
            }
        }

        /**
         * Builds the insertion handler for a path completion: replaces the typed path
         * value with the full [relativePath], preserving the surrounding quotes/braces
         * and leaving the caret after directories so further navigation keeps working.
         */
        private fun pathInsertHandler(
            pathValueStart: Int,
            relativePath: String,
            isDirectory: Boolean,
            quoted: Boolean,
            hasClosingQuote: Boolean,
            wrapInBraces: Boolean,
            project: Project
        ): InsertHandler<LookupElement> {
            return InsertHandler { context, _ ->
                val editor = context.editor
                val document = editor.document
                val caret = editor.caretModel.offset

                val start = if (pathValueStart >= 0) pathValueStart else context.startOffset
                var insertText = relativePath
                if (wrapInBraces) insertText = "{$insertText}"
                else if (quoted && !hasClosingQuote) insertText += "\""

                document.replaceString(start, caret, insertText)
                editor.caretModel.moveToOffset(start + insertText.length)

                // After completing a directory, pop up its children right away.
                if (isDirectory && !wrapInBraces && !quoted) {
                    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                }
            }
        }

        // ------------------------------------------------------------------
        // Function name completion
        // ------------------------------------------------------------------

        private fun suggestFunctionNames(
            prefix: String,
            functions: List<FunctionMetadata>,
            result: CompletionResultSet
        ) {
            val lower = prefix.lowercase()
            val matching = if (lower.isEmpty()) functions
            else functions.filter { it.name.startsWith(lower) }

            for (fn in matching) {
                result.addElement(buildFunctionLookup(fn))
            }
        }

        private fun buildFunctionLookup(fn: FunctionMetadata): LookupElementBuilder {
            val visible = fn.parameters.filter { !it.isInjected }
            // Concise type text: parameter names only, so the popup is not crammed.
            val paramsText = if (visible.isNotEmpty()) visible.joinToString(", ") { it.name } else ""

            return LookupElementBuilder.create(fn, fn.name)
                .withTypeText(paramsText, true)
                .withTailText("  ${fn.module.ifEmpty { "stdlib" }}", true)
                .withInsertHandler { ctx, _ ->
                    val ed = ctx.editor
                    val pos = ed.caretModel.offset
                    if (visible.isEmpty()) {
                        // No arguments → insert a trailing space: `.currentpage `.
                        ed.document.insertString(pos, " ")
                        ed.caretModel.moveToOffset(pos + 1)
                    } else {
                        // Functions take arguments in `{...}` — insert `.background {}`
                        // with the caret inside the braces, ready to type the first value.
                        // This also prevents the next-argument popup from immediately
                        // offering `name:{}` after the name.
                        ed.document.insertString(pos, " {}")
                        ed.caretModel.moveToOffset(pos + 2)
                    }
                }
        }

        // ------------------------------------------------------------------
        // Next-argument completion (after `.name ` or after written arguments)
        // ------------------------------------------------------------------

        private fun suggestNextArguments(
            ctx: FunctionCallTokenizer.FunctionCallContext,
            functions: List<FunctionMetadata>,
            result: CompletionResultSet
        ) {
            val fn = functions.find { it.name == ctx.functionName } ?: return
            val visible = fn.parameters.filter { !it.isInjected }
            if (visible.isEmpty()) return

            val consumed = consumedParams(fn, ctx.allArgs, ctx.call?.isChained == true)
            val remaining = visible.filter { it.name !in consumed }
            if (remaining.isEmpty()) return

            // Suggest the remaining parameters as named arguments: `name:{…}`.
            // Positional shorthand values (`{bottomcenter}`) are suggested once the
            // caret is inside the argument's braces.
            for (param in remaining) {
                result.addElement(buildNamedArgLookup(param))
            }
        }

        private fun buildNamedArgLookup(param: ParameterMetadata): LookupElementBuilder {
            return LookupElementBuilder.create(param.name)
                .withTypeText(param.type, true)
                .withTailText("  named argument", true)
                .withInsertHandler { ctx, _ ->
                    val ed = ctx.editor
                    val start = ctx.startOffset
                    val end = ed.caretModel.offset
                    ed.document.replaceString(start, end, "${param.name}:{}")
                    ed.caretModel.moveToOffset(start + param.name.length + 2)
                }
        }

        private fun consumedParams(fn: FunctionMetadata, args: List<Arg>, chained: Boolean): Set<String> {
            val visible = fn.parameters.filter { !it.isInjected }
            val consumed = mutableSetOf<String>()
            // For chained calls the chained value is the implicit first positional argument.
            var pos = if (chained) 1 else 0
            for (arg in args) {
                if (arg.isNamed) {
                    fn.parameters.find { !it.isInjected && it.name == arg.paramName }
                        ?.let { consumed.add(it.name) }
                } else {
                    visible.getOrNull(pos)?.let { consumed.add(it.name) }
                    pos++
                }
            }
            return consumed
        }

        // ------------------------------------------------------------------
        // Enum / constrained value completion
        // ------------------------------------------------------------------

        /**
         * Resolves the parameter an argument refers to: named → by name,
         * positional → by its position among the non-injected parameters.
         */
        private fun resolveParam(
            fn: FunctionMetadata,
            arg: Arg,
            allArgs: List<Arg>,
            chained: Boolean
        ): ParameterMetadata? {
            if (arg.isNamed) {
                return fn.parameters.find { !it.isInjected && it.name == arg.paramName }
            }
            val visible = fn.parameters.filter { !it.isInjected }
            // For chained calls the chained value occupies the first positional slot.
            var pos = if (chained) 1 else 0
            for (a in allArgs) {
                if (a === arg) return visible.getOrNull(pos)
                if (!a.isNamed) pos++
            }
            return null
        }

        private fun suggestValues(
            param: ParameterMetadata,
            prefix: String,
            result: CompletionResultSet,
            wrapInBraces: Boolean
        ) {
            val values = param.allowedValues ?: return
            val lower = prefix.lowercase()
            for (value in values.filter { it.startsWith(lower) }) {
                val lookup = LookupElementBuilder.create(value)
                    .withTypeText(param.type, true)
                    .withTailText("  ${param.name}", true)
                if (wrapInBraces) {
                    // Inserting after `name:` — wrap the value in braces: `name:{value}`
                    lookup.withInsertHandler { ctx, _ ->
                        val ed = ctx.editor
                        val start = ctx.startOffset
                        val end = ed.caretModel.offset
                        ed.document.replaceString(start, end, "{$value}")
                        ed.caretModel.moveToOffset(start + value.length + 2)
                    }
                }
                result.addElement(lookup)
            }
        }
    }
}
