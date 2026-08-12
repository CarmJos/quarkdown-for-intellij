package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Arg
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownPathUtil
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Structural file-path completion for Quarkdown documents.
 *
 * Function-name / parameter / enum-value completion is provided by the official
 * `quarkdown language-server` (see `lang/lsp`), which is always the semantic layer of
 * this plugin. This contributor only handles what LSP does not cover: completing
 * file paths for the path-taking functions of the core grammar
 * (`.include {…}`, `.read {…}`, `.css {…}`, `.code {…}`).
 *
 * It is deliberately registry-independent: the path-taking functions are part of the
 * core grammar, so the completion works without any Quarkdown installation.
 */
class QuarkdownCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), QuarkdownPathCompletionProvider())
    }

    class QuarkdownPathCompletionProvider : CompletionProvider<CompletionParameters>() {

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

            val ctx = FunctionCallTokenizer.parseContext(text, offset)
            if (!ctx.hasCall) return

            when {
                ctx.inFunctionName -> maybeSuggestVariables(parameters, ctx, result)
                ctx.currentArg != null -> maybeSuggestFilePath(parameters, ctx, result)
                ctx.afterNamedColon -> maybeSuggestFilePathAfterColon(parameters, ctx, result)
            }
        }

        // ------------------------------------------------------------------
        // Variable completion for `.var` declarations
        // ------------------------------------------------------------------

        /**
         * Suggests document-level variables declared via `.var {name} {value}` while the
         * user is typing a `.name` reference (e.g. `.sta…`). Uses a dedicated variable icon
         * so declared variables are visually distinct from the LSP's function completions.
         */
        private fun maybeSuggestVariables(
            parameters: CompletionParameters,
            ctx: FunctionCallTokenizer.FunctionCallContext,
            result: CompletionResultSet
        ) {
            val file = parameters.originalFile
            if (file.fileType != QuarkdownFileType.INSTANCE) return

            val vars = QuarkdownCallParser.findVarValues(file.text)
            if (vars.isEmpty()) return

            val prefix = ctx.namePrefix.lowercase()
            for ((name, value) in vars) {
                if (prefix.isNotEmpty() && !name.startsWith(prefix)) continue
                result.addElement(
                    LookupElementBuilder.create(name)
                        .withIcon(QuarkdownIcons.VARIABLE)
                        .withTypeText(QuarkdownBundle.message("quarkdown.completion.type.variable"), true)
                        .withTailText("  = $value", true)
                )
            }
        }

        // ------------------------------------------------------------------
        // File-path completion for `.include {path}` / `.read {path}` etc.
        // ------------------------------------------------------------------

        /** Functions whose value argument is a file path. */
        private val PATH_FUNCTIONS = setOf("include", "read")

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
            val pathValueStart = pathValueStartOffset(arg, frontTrimmed, quoted)

            // Detect whether a closing quote already follows the caret.
            val hasClosingQuote = hasClosingQuoteAfterCaret(parameters, arg, quoted)

            val lowerName = searchName.lowercase()
            val showHidden = lowerName.startsWith(".")
            for (child in base.children.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() }))) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (!child.name.lowercase().startsWith(lowerName)) continue
                result.addElement(
                    buildPathLookup(
                        child, basePath, quoted, hasClosingQuote, wrapInBraces, pathValueStart, project
                    )
                )
            }
        }

        /**
         * Document offset where the path value begins (after whitespace/quotes).
         * `-1` means after a named colon there is no value yet; the platform offsets are used.
         */
        private fun pathValueStartOffset(arg: Arg?, frontTrimmed: Int, quoted: Boolean): Int =
            if (arg != null) arg.rawStart + frontTrimmed + if (quoted) 1 else 0 else -1

        /** Detects whether a closing quote already follows the caret. */
        private fun hasClosingQuoteAfterCaret(parameters: CompletionParameters, arg: Arg?, quoted: Boolean): Boolean {
            val psiFile = parameters.originalFile
            val caret = parameters.offset
            return quoted && arg != null && caret < arg.braceEnd && psiFile.text.startsWith("\"", caret)
        }

        /** Builds a path completion lookup element for [child]. */
        private fun buildPathLookup(
            child: VirtualFile,
            basePath: String,
            quoted: Boolean,
            hasClosingQuote: Boolean,
            wrapInBraces: Boolean,
            pathValueStart: Int,
            project: Project
        ): LookupElement {
            val isDir = child.isDirectory
            val displayText = child.name + if (isDir) "/" else ""
            val relativePath = basePath + displayText
            val icon = if (isDir) AllIcons.Nodes.Folder
            else child.fileType.icon ?: AllIcons.FileTypes.Any_type

            return LookupElementBuilder.create(displayText)
                .withLookupString(relativePath)
                .withIcon(icon)
                .withTypeText(
                    if (isDir) {
                        QuarkdownBundle.message("quarkdown.completion.type.directory")
                    } else {
                        child.extension?.uppercase()
                            ?: QuarkdownBundle.message("quarkdown.completion.type.file")
                    },
                    true
                )
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
    }
}
