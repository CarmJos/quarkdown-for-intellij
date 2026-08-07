package cc.carm.plugin.intellij.quarkdown.lang.function

/**
 * Naming conventions Quarkdown uses for its source syntax.
 *
 * Verified against the Quarkdown compiler (`NamingKt.toQuarkdownNamingFormat`):
 * an enum constant `BOTTOM_CENTER` is written as `bottomcenter` in a source file
 * (lowercase, underscores removed).
 */
object QuarkdownNaming {

    /** Converts an enum constant name (e.g. `BOTTOM_CENTER`) to its Quarkdown spelling. */
    fun enumValueName(constantName: String): String =
        constantName.lowercase().replace("_", "")

    /** Converts a function name to its lowercase lookup form. */
    fun functionName(name: String): String = name.lowercase()
}
