package cc.carm.plugin.intellij.quarkdown.lang.function

/**
 * Metadata describing a Quarkdown function available in the standard library.
 *
 * The documentation fields ([description], [returnDescription], [signature], [samples],
 * [module] and per-parameter [ParameterMetadata.description]) are parsed from the
 * Quarkdown stdlib documentation (Dokka HTML) while building the index, and are
 * preserved as-is (including structure) so completion and documentation popups can
 * display them without re-parsing.
 */
data class FunctionMetadata(
    val name: String,
    val parameters: List<ParameterMetadata>,
    val description: String = "",
    val returnDescription: String = "",
    val signature: String = "",
    val module: String = "",
    val samples: List<String> = emptyList(),
    val docUrl: String? = null,
    val isLikelyChained: Boolean = false
)

data class ParameterMetadata(
    val name: String,
    val type: String,
    val index: Int,
    val description: String = "",
    val isOptional: Boolean = false,
    val isInjected: Boolean = false,
    val isNullable: Boolean = false,
    val isLikelyNamed: Boolean = false,
    val isLikelyBody: Boolean = false,
    val allowedValues: List<String>? = null
)
