package cc.carm.plugin.intellij.quarkdown.lang.function

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.xml.util.XmlStringUtil

/**
 * Renders a [FunctionMetadata] into the HTML shown in IntelliJ's documentation popup /
 * Quick Documentation (Ctrl+Q). Uses [DocumentationMarkup] for consistent styling with
 * the rest of the IDE.
 */
object QuarkdownDocRenderer {

    fun render(fn: FunctionMetadata): String {
        val sb = StringBuilder()

        val definition = fn.signature.ifEmpty { ".${fn.name} {…}" }
        sb.append(DocumentationMarkup.DEFINITION_START)
            .append("<pre>").append(XmlStringUtil.escapeString(definition)).append("</pre>")
            .append(DocumentationMarkup.DEFINITION_END)

        if (fn.description.isNotEmpty() || fn.docUrl != null) {
            sb.append(DocumentationMarkup.CONTENT_START)
            if (fn.description.isNotEmpty()) {
                val paragraphs = fn.description.split("\n\n").joinToString("") { paragraph ->
                    "<p class=\"paragraph\">${XmlStringUtil.escapeString(paragraph)}</p>"
                }
                sb.append(paragraphs)
            }
            fn.docUrl?.let { url ->
                sb.append("<p class=\"paragraph\"><a href=\"")
                    .append(XmlStringUtil.escapeString(url)).append("\">Open documentation</a></p>")
            }
            sb.append(DocumentationMarkup.CONTENT_END)
        }

        val sections = buildList {
            val visibleParams = fn.parameters.filter { !it.isInjected }
            if (visibleParams.isNotEmpty()) {
                add("Parameters" to renderParameters(visibleParams))
            }
            if (fn.returnDescription.isNotEmpty()) {
                add("Return" to "<p class=\"paragraph\">${XmlStringUtil.escapeString(fn.returnDescription)}</p>")
            }
            if (fn.samples.isNotEmpty()) {
                val samplesHtml = fn.samples.joinToString("") {
                    "<pre>${XmlStringUtil.escapeString(it)}</pre>"
                }
                add("Examples" to samplesHtml)
            }
            if (fn.module.isNotEmpty()) {
                add("Module" to "<p class=\"paragraph\">${XmlStringUtil.escapeString(fn.module)}</p>")
            }
        }

        if (sections.isNotEmpty()) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            for ((title, content) in sections) {
                sb.append(DocumentationMarkup.SECTION_START)
                sb.append(DocumentationMarkup.SECTION_HEADER_START)
                sb.append(XmlStringUtil.escapeString(title))
                sb.append(DocumentationMarkup.SECTION_SEPARATOR)
                sb.append(content)
                sb.append(DocumentationMarkup.SECTION_END)
            }
            sb.append(DocumentationMarkup.SECTIONS_END)
        }

        return sb.toString()
    }

    private fun renderParameters(params: List<ParameterMetadata>): String {
        val sb = StringBuilder()
        sb.append("<dl>")
        for (p in params) {
            sb.append("<dt>")
            sb.append("<code>").append(XmlStringUtil.escapeString(p.name)).append("</code>")
            if (p.type.isNotEmpty()) {
                sb.append(" ").append(DocumentationMarkup.GRAYED_START)
                    .append(XmlStringUtil.escapeString(p.type))
                    .append(DocumentationMarkup.GRAYED_END)
            }
            if (p.isOptional) {
                sb.append(" ").append(DocumentationMarkup.GRAYED_START).append("(optional)")
                    .append(DocumentationMarkup.GRAYED_END)
            }
            if (p.isLikelyNamed) {
                sb.append(" ").append(DocumentationMarkup.GRAYED_START).append("(named)")
                    .append(DocumentationMarkup.GRAYED_END)
            }
            sb.append("</dt>")
            val details = buildList {
                if (p.description.isNotEmpty()) add(p.description)
                p.allowedValues?.takeIf { it.isNotEmpty() }?.let {
                    add("Allowed values: ${it.joinToString(", ")}")
                }
            }
            if (details.isNotEmpty()) {
                sb.append("<dd>").append(XmlStringUtil.escapeString(details.joinToString("; "))).append("</dd>")
            }
        }
        sb.append("</dl>")
        return sb.toString()
    }
}
