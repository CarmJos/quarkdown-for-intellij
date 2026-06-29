package cc.carm.plugin.intellij.quarkdown.lang.codeblock

/**
 * Provides the list of supported code block languages (from highlight.js).
 *
 * The language data is auto-generated at build time into [GENERATED_CODE_BLOCK_LANGUAGES].
 * If that is unavailable, a built-in fallback list is used.
 */
object CodeBlockLanguageProvider {

    @JvmStatic
    val languages: List<CodeBlockLanguage> = run {
        // Use generated list from build task if available, otherwise fallback
        @Suppress("UNUSED_EXPRESSION")
        try {
            GENERATED_CODE_BLOCK_LANGUAGES
        } catch (_: Exception) {
            FALLBACK_LANGUAGES
        }
    }

    /**
     * Find matching language identifiers for the given prefix.
     * Matches both the canonical name and aliases (case-insensitive).
     */
    @JvmStatic
    fun findSuggestions(prefix: String): List<String> {
        val lower = prefix.lowercase()
        return languages
            .flatMap { it.allIdentifiers }
            .filter { it.lowercase().startsWith(lower) }
            .distinct()
            .sorted()
    }

    /**
     * Check if the given identifier is a known code block language.
     */
    @JvmStatic
    fun isKnown(identifier: String): Boolean {
        val lower = identifier.lowercase()
        return languages.any { lang ->
            lang.name.lowercase() == lower || lang.aliases.any { it.lowercase() == lower }
        }
    }
}

/**
 * Built-in fallback list used when the generated list is unavailable.
 * Contains commonly used languages.
 */
private val FALLBACK_LANGUAGES = listOf(
    CodeBlockLanguage("plaintext", listOf("txt", "text")),
    CodeBlockLanguage("java", listOf("jsp")),
    CodeBlockLanguage("python", listOf("py", "gyp")),
    CodeBlockLanguage("javascript", listOf("js", "jsx")),
    CodeBlockLanguage("typescript", listOf("ts", "tsx")),
    CodeBlockLanguage("kotlin", listOf("kt")),
    CodeBlockLanguage("c", listOf("h")),
    CodeBlockLanguage("cpp", listOf("hpp", "cc", "hh", "cxx", "hxx")),
    CodeBlockLanguage("csharp", listOf("cs")),
    CodeBlockLanguage("go", listOf("golang")),
    CodeBlockLanguage("rust", listOf("rs")),
    CodeBlockLanguage("sql"),
    CodeBlockLanguage("html", listOf("xml", "xhtml", "svg")),
    CodeBlockLanguage("css"),
    CodeBlockLanguage("bash", listOf("sh", "zsh")),
    CodeBlockLanguage("yaml", listOf("yml")),
    CodeBlockLanguage("json"),
    CodeBlockLanguage("markdown", listOf("md", "mkdown", "mkd")),
    CodeBlockLanguage("ruby", listOf("rb", "gemspec", "podspec", "thor", "irb")),
    CodeBlockLanguage("scala"),
    CodeBlockLanguage("swift"),
    CodeBlockLanguage("php"),
    CodeBlockLanguage("perl", listOf("pl", "pm")),
    CodeBlockLanguage("lua"),
    CodeBlockLanguage("r"),
    CodeBlockLanguage("dart"),
    CodeBlockLanguage("shell", listOf("console")),
    CodeBlockLanguage("powershell", listOf("ps", "ps1")),
    CodeBlockLanguage("dockerfile", listOf("docker")),
    CodeBlockLanguage("graphql", listOf("gql")),
)

