package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.CodeBlockLanguageProvider
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completion contributor for code block language identifiers in Quarkdown files.
 *
 * When the user types ` ``` ` followed by a language name (e.g. ` ```jav`),
 * this contributor provides completion suggestions for known highlight.js languages.
 *
 * Supports completing both canonical names (e.g. "javascript") and aliases (e.g. "js").
 */
class CodeBlockLanguageCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            CodeBlockLanguageCompletionProvider()
        )
    }

    private class CodeBlockLanguageCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val file = parameters.originalFile
            if (file.fileType != QuarkdownFileType.INSTANCE) return

            val editor = parameters.editor
            val offset = editor.caretModel.offset
            val document = editor.document
            val text = document.charsSequence

            // Use case-insensitive prefix matching so that e.g. "java" matches "Java"
            val insensitiveResult = result.caseInsensitive()

            // Find the start of the current line
            val lineStart = findLineStart(text, offset)

            // Extract the text from line start to cursor
            val linePrefix = text.subSequence(lineStart, offset).toString()

            // Check if we are inside a code block fence opening: ```<lang>
            val fenceEndIndex = linePrefix.indexOf("```")
            if (fenceEndIndex < 0) return

            val afterFence = linePrefix.substring(fenceEndIndex + 3)

            // Only provide completions if we are right after the fence (possibly with text)
            // and there's no whitespace (language doesn't have spaces typically)
            if (afterFence.isEmpty() || afterFence.any { it == ' ' || it == '\t' }) {
                // If empty, show all languages
                if (afterFence.isEmpty()) {
                    for (identifier in CodeBlockLanguageProvider.findSuggestions("")) {
                        insensitiveResult.addElement(buildLookupElement(identifier))
                    }
                }
                return
            }

            // Provide completions matching the current prefix
            val prefix = afterFence.trimStart()
            for (identifier in CodeBlockLanguageProvider.findSuggestions(prefix)) {
                insensitiveResult.addElement(buildLookupElement(identifier))
            }
        }

        private fun findLineStart(text: CharSequence, offset: Int): Int {
            if (offset <= 0) return 0
            for (i in (offset - 1) downTo 0) {
                if (text[i] == '\n') return i + 1
            }
            return 0
        }

        private fun buildLookupElement(identifier: String): LookupElementBuilder {
            val isCanonical = CodeBlockLanguageProvider.languages.any { it.name == identifier }
            val typeText = QuarkdownBundle.message(
                if (isCanonical) "quarkdown.completion.type.language" else "quarkdown.completion.type.alias"
            )
            val tailText = if (isCanonical) {
                ""
            } else {
                QuarkdownBundle.message("quarkdown.completion.alias.tail")
            }
            return LookupElementBuilder.create(identifier)
                .withTypeText(typeText, true)
                .withTailText(tailText, true)
        }
    }
}
