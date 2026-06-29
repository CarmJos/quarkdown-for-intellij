package cc.carm.plugin.intellij.quarkdown.lang.codeblock

data class CodeBlockLanguage(
    val name: String,
    val aliases: List<String> = emptyList()
) {
    val allIdentifiers: List<String> get() = listOf(name) + aliases
}

