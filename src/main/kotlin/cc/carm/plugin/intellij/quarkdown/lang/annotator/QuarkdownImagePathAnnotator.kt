package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownPathUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Annotates non-existent image paths in Quarkdown files.
 *
 * Scans the file text for image syntax `!(size)[alt](path)` and checks
 * whether the referenced file actually exists on disk. If the file is
 * not found, a WARNING annotation is created.
 *
 * Skips external URLs (http://, https://) and data URIs.
 */
class QuarkdownImagePathAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val text = element.text
        if (text.isEmpty()) return

        val project = element.project
        val virtualFile = element.virtualFile ?: return

        for (match in QuarkdownPathUtil.IMG_PATH_PATTERN.findAll(text)) {
            val path = match.groupValues[1].trim().removeSurrounding("\"")
            if (path.isEmpty()) continue
            if (QuarkdownPathUtil.isNonFileReference(path)) continue

            val pathStart = match.groups[1]!!.range.first
            val pathEnd = match.groups[1]!!.range.last + 1

            // Try to resolve the path
            val resolved = QuarkdownPathUtil.resolveToVirtualFile(project, virtualFile, path)
            if (resolved == null || !resolved.exists()) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Image file not found: '$path'"
                ).range(TextRange(pathStart, pathEnd)).create()
            }
        }
    }
}
