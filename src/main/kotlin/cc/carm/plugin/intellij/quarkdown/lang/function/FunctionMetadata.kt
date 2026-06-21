package cc.carm.plugin.intellij.quarkdown.lang.function

data class FunctionMetadata(
    val name: String,
    val parameters: List<ParameterMetadata>,
    val description: String = "",
    val isLikelyChained: Boolean = false
)

data class ParameterMetadata(
    val name: String,
    val type: String,
    val index: Int,
    val isOptional: Boolean = false,
    val isInjected: Boolean = false,
    val isNullable: Boolean = false,
    val isLikelyNamed: Boolean = false,
    val isLikelyBody: Boolean = false,
    val allowedValues: List<String>? = null
)
