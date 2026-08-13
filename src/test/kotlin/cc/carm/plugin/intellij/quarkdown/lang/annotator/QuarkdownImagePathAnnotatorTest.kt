package cc.carm.plugin.intellij.quarkdown.lang.annotator

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Proxy

/**
 * Platform-level tests for [QuarkdownImagePathAnnotator]: missing image files are
 * flagged with a warning, while existing files and external URLs are not.
 *
 * The annotator is invoked directly with a proxy [AnnotationHolder] (not via the full
 * highlighting pipeline), so the tests never start the Quarkdown LSP server and never
 * touch real editor highlighting.
 */
class QuarkdownImagePathAnnotatorTest : BasePlatformTestCase() {

    private fun warnings(text: String): List<TextRange> {
        myFixture.configureByText("test.qd", text)
        val results = mutableListOf<TextRange>()
        val holder = warningHolder { start, end -> results.add(TextRange(start, end)) }
        QuarkdownImagePathAnnotator().annotate(myFixture.file, holder)
        return results
    }

    fun `test missing image file is annotated as warning`() {
        val ranges = warnings("Some text.\n\n![alt](missing.png)\n")
        assertTrue("expected a warning for the missing image, got $ranges", ranges.isNotEmpty())
    }

    fun `test existing image file is not annotated`() {
        myFixture.addFileToProject("images/logo.png", "png")
        val ranges = warnings("![alt](images/logo.png)\n")
        assertTrue("expected no warning for an existing image, got $ranges", ranges.isEmpty())
    }

    fun `test external url image is not annotated`() {
        val ranges = warnings("![alt](https://example.com/logo.png)\n")
        assertTrue("expected no warning for an external URL, got $ranges", ranges.isEmpty())
    }

    /**
     * Creates a proxy [AnnotationHolder] whose [AnnotationHolder.newAnnotation] returns
     * a builder that captures the range passed to `range(...)` and reports it to
     * [onWarning] when the annotation is created.
     */
    private fun warningHolder(onWarning: (Int, Int) -> Unit): AnnotationHolder {
        val pendingRange = java.util.concurrent.atomic.AtomicReference<TextRange>()

        val builder = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(AnnotationBuilder::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "range" -> {
                    (args?.firstOrNull { it is TextRange } as? TextRange)?.let(pendingRange::set)
                    proxy
                }
                "create" -> {
                    pendingRange.get()?.let { onWarning(it.startOffset, it.endOffset) }
                    null
                }
                "createAnnotation" -> {
                    pendingRange.get()?.let { onWarning(it.startOffset, it.endOffset) }
                    null
                }
                else -> when (method.returnType) {
                    AnnotationBuilder::class.java -> proxy
                    Boolean::class.java -> false
                    Int::class.java -> 0
                    else -> null
                }
            }
        }

        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(AnnotationHolder::class.java)
        ) { _, method, args ->
            when (method.name) {
                "newAnnotation", "newSilentAnnotation" -> builder
                "createWarningAnnotation", "createAnnotation" -> {
                    val range = args?.firstOrNull { it is TextRange } as? TextRange
                    if (range != null) onWarning(range.startOffset, range.endOffset)
                    null
                }
                "getCurrentAnnotationSession" -> null
                "isBatchMode" -> true
                else -> null
            }
        } as AnnotationHolder
    }
}
