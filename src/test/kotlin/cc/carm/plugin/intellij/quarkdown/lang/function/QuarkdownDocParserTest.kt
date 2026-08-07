package cc.carm.plugin.intellij.quarkdown.lang.function

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuarkdownDocParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeSampleDocs(): File {
        // Layout that mirrors the Dokka HTML generated for the Quarkdown stdlib:
        // <home>/docs/quarkdown-stdlib/<package>/<name>.html
        val moduleDir = File(tempFolder.root, "docs/quarkdown-stdlib/com.quarkdown.stdlib.module.Document")
        moduleDir.mkdirs()
        File(moduleDir, "pagemargin.html").writeText(
            """
            <!doctype html><html><head><title>pagemargin</title></head><body>
            <div class="main-content" data-page-type="member">
              <div class="cover ">
                <h1 class="cover"><span><span>pagemargin</span></span></h1>
              </div>
              <div class="platform-hinted">
                <div class="sample-container"><pre><code class="block lang-kotlin" theme="idea">
                  <span class="token punctuation">.</span><span class="token function">pagemargin</span>
                  <span class="token constant">position</span><span class="token operator">:</span><span class="token punctuation">{</span>PageMarginPosition<span class="token punctuation">}</span>
                  <span class="token constant">content</span><span class="token operator">:</span><span class="token punctuation">{</span>MarkdownContent<span class="token punctuation">}</span>
                  <span class="token operator">-&gt;</span> Node</code></pre>
                </div>
                <p class="paragraph">Displays content on each page of a document.</p>
                <p class="paragraph">In case of <code>paged</code> documents, the content is displayed in a dedicated area.</p>
                <div class="sample-container"><pre><code class="block lang-kotlin" theme="idea">.pagemargin {bottomcenter} content:{hello}</code></pre></div>
                <span class="kdoc-tag"><h4 class="">Return</h4><p class="paragraph">a PageMarginContentInitializer node</p></span>
                <h4 class="">Parameters</h4>
                <div class="table">
                  <div class="table-row">
                    <div class="main-subrow keyValue ">
                      <div class=""><span class="inline-flex"><div><u><span><span>position</span></span></u></div></span></div>
                      <div><div class="title"><p class="paragraph">position of the content within the page</p></div></div>
                    </div>
                  </div>
                  <div class="table-row">
                    <div class="main-subrow keyValue ">
                      <div class=""><span class="inline-flex"><div><u><span><span>content</span></span></u></div></span></div>
                      <div><div class="title"><p class="paragraph">content to display</p></div></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            </body></html>
            """.trimIndent()
        )
        return tempFolder.root
    }

    @Test
    fun `parses function docs from sample html`() {
        val home = writeSampleDocs()
        val docs = QuarkdownDocParser.parseDocs(home.absolutePath)
        assertEquals(1, docs.size)

        val doc = docs["pagemargin"]
        assertNotNull(doc)
        assertEquals("pagemargin", doc!!.name)
        assertTrue(doc.description.contains("Displays content on each page"))
        assertTrue(doc.description.contains("dedicated area"))
        assertEquals("a PageMarginContentInitializer node", doc.returnDescription)
        assertEquals("position of the content within the page", doc.parameterDescriptions["position"])
        assertEquals("content to display", doc.parameterDescriptions["content"])
        assertTrue(doc.signature.contains(".pagemargin"))
        assertEquals(1, doc.samples.size)
        assertTrue(doc.samples[0].contains("bottomcenter"))
        assertEquals("Document", doc.module)
    }

    @Test
    fun `returns empty when docs directory missing`() {
        val docs = QuarkdownDocParser.parseDocs(tempFolder.root.absolutePath)
        assertTrue(docs.isEmpty())
    }

    @Test
    fun `skips non-member pages`() {
        val dir = File(tempFolder.root, "docs/quarkdown-stdlib/com.quarkdown.stdlib")
        dir.mkdirs()
        File(dir, "-document.html").writeText("<!doctype html><html><body><h1>Document</h1></body></html>")
        val docs = QuarkdownDocParser.parseDocs(tempFolder.root.absolutePath)
        assertTrue(docs.isEmpty())
    }
}
