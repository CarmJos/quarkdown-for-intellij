package cc.carm.plugin.intellij.quarkdown.lang.codeblock
import com.intellij.lang.Language
object QuarkdownLanguageMapper {
    fun resolve(id: String): Language? {
        val key = id.lowercase().trim()
        return LANGUAGE_MAP[key] ?: ALIAS_MAP[key]
    }
    private val LANGUAGE_MAP: Map<String, Language> = run {
        val ids = mapOf(
            "java" to "JAVA", "kotlin" to "kotlin", "scala" to "Scala",
            "groovy" to "Groovy", "javascript" to "JavaScript",
            "typescript" to "TypeScript", "jsx" to "JSX Harmony",
            "tsx" to "TSX", "html" to "HTML", "xml" to "XML",
            "css" to "CSS", "json" to "JSON", "yaml" to "yaml",
            "python" to "Python", "ruby" to "ruby", "php" to "PHP",
            "lua" to "Lua", "bash" to "Shell Script", "shell" to "Shell Script",
            "powershell" to "PowerShell", "c" to "ObjectiveC",
            "cpp" to "ObjectiveC", "csharp" to "C#", "go" to "go",
            "rust" to "Rust", "swift" to "Swift", "dart" to "Dart",
            "sql" to "SQL", "graphql" to "GraphQL",
            "dockerfile" to "Dockerfile", "markdown" to "Markdown",
            "properties" to "Properties", "toml" to "TOML",
            "ini" to "Properties", "regexp" to "RegExp",
            "r" to "R", "perl" to "Perl5"
        )
        val result = mutableMapOf<String, Language>()
        for ((key, langId) in ids) {
            Language.findLanguageByID(langId)?.let { result[key] = it }
        }
        result
    }
    private val ALIAS_MAP: Map<String, Language> = run {
        val ids = mapOf(
            "js" to "JavaScript", "ts" to "TypeScript",
            "py" to "Python", "rb" to "ruby", "kt" to "kotlin",
            "cs" to "C#", "rs" to "Rust", "yml" to "yaml",
            "sh" to "Shell Script", "ps1" to "PowerShell",
            "md" to "Markdown", "docker" to "Dockerfile",
            "gql" to "GraphQL", "golang" to "go", "pl" to "Perl5"
        )
        val result = mutableMapOf<String, Language>()
        for ((key, langId) in ids) {
            Language.findLanguageByID(langId)?.let { result[key] = it }
        }
        result
    }
}
