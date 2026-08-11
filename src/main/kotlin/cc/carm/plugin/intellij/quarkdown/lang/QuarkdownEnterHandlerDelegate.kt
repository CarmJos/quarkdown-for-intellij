package cc.carm.plugin.intellij.quarkdown.lang

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

/**
 * Handles Enter in Quarkdown files.
 *
 * When the current line ends with `\` (a Quarkdown line continuation), pressing Enter
 * inserts the newline AND indents the new (empty) line so the continued arguments align:
 *
 * ```
 * .tableofcontents \
 *     title:{**Table of Contents**} maxdepth:{3} \
 *     indexheading:{false} numberheading:{false} breakpage:{true}
 * ```
 *
 * Nested continuations keep the same indent level as their parent continuation.
 */
class QuarkdownEnterHandlerDelegate : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        if (file.fileType != QuarkdownFileType.INSTANCE) return EnterHandlerDelegate.Result.Continue
        if (!currentLineEndsWithBackslash(editor)) return EnterHandlerDelegate.Result.Continue

        // The default handler will insert "\n" and any indentation it deems fit.
        // We let it run (Default), then post-process to replace the indentation with
        // the continuation indent.
        return EnterHandlerDelegate.Result.Default
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        if (file.fileType != QuarkdownFileType.INSTANCE) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caret = editor.caretModel.offset
        if (caret <= 0) return EnterHandlerDelegate.Result.Continue
        val chars = document.charsSequence

        // Find the start of the current (new) line — it may already have auto-indent.
        var lineStart = caret
        while (lineStart > 0 && chars[lineStart - 1] != '\n' && chars[lineStart - 1] != '\r') lineStart--

        // Current line text (should be empty or whitespace-only).
        val curLine = chars.subSequence(lineStart, caret).toString()
        if (curLine.isNotBlank()) return EnterHandlerDelegate.Result.Continue

        // The line before this one ends with `\`? Then compute the continuation indent.
        val indent = computeContinuationIndent(chars, lineStart)
            ?: return EnterHandlerDelegate.Result.Continue

        // Replace any existing indentation on the new line with the computed one.
        val spaces = " ".repeat(indent)
        document.replaceString(lineStart, caret, spaces)
        editor.caretModel.moveToOffset(lineStart + spaces.length)
        return EnterHandlerDelegate.Result.Stop
    }

    /**
     * Computes the continuation indentation for the line starting at [lineStart].
     * Returns `null` when the line before it does not end with `\`.
     * Nested continuations (the line before also ends with `\`) keep the same indent
     * level as their parent continuation.
     */
    private fun computeContinuationIndent(chars: CharSequence, lineStart: Int): Int? {
        if (lineStart <= 0) return null
        var prevLineEnd = lineStart
        if (chars[prevLineEnd - 1] == '\n' || chars[prevLineEnd - 1] == '\r') prevLineEnd--
        var prevLineStart = prevLineEnd
        while (prevLineStart > 0 && chars[prevLineStart - 1] != '\n' && chars[prevLineStart - 1] != '\r') prevLineStart--
        val prevLine = chars.subSequence(prevLineStart, prevLineEnd).toString()
        if (!prevLine.trimEnd().endsWith("\\")) return null

        val prevIndent = prevLine.takeWhile { it == ' ' || it == '\t' }.length
        val nested = isNestedContinuation(chars, prevLineStart)
        return if (nested) prevIndent else prevIndent + 4
    }

    /** True when the line starting at [lineStart] is itself a continuation (ends with `\`). */
    private fun isNestedContinuation(chars: CharSequence, lineStart: Int): Boolean {
        if (lineStart <= 0) return false
        var p2End = lineStart
        if (chars[p2End - 1] == '\n' || chars[p2End - 1] == '\r') p2End--
        var p2Start = p2End
        while (p2Start > 0 && chars[p2Start - 1] != '\n' && chars[p2Start - 1] != '\r') p2Start--
        return chars.subSequence(p2Start, p2End).toString().trimEnd().endsWith("\\")
    }

    private fun currentLineEndsWithBackslash(editor: Editor): Boolean {
        val document = editor.document
        val offset = editor.caretModel.offset
        if (offset <= 0) return false
        val chars = document.charsSequence
        // find start of current line
        var lineStart = offset
        while (lineStart > 0 && chars[lineStart - 1] != '\n' && chars[lineStart - 1] != '\r') lineStart--
        val line = chars.subSequence(lineStart, offset).toString()
        return line.trimEnd().endsWith("\\")
    }
}
