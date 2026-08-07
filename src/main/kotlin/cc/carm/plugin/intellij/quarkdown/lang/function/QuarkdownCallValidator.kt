package cc.carm.plugin.intellij.quarkdown.lang.function

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Arg
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Call

/**
 * Validates a parsed Quarkdown function call against the stdlib function metadata.
 *
 * Pure logic (no IntelliJ dependencies) so it can be unit-tested and shared by the
 * annotator and any future inspections.
 */
object QuarkdownCallValidator {

    enum class Severity { ERROR, WARNING }

    data class Issue(
        val severity: Severity,
        val start: Int,
        val end: Int,
        val message: String
    )

    data class ResolvedArg(
        val arg: Arg,
        val param: ParameterMetadata?,
        /** Positional index among the non-injected parameters, `-1` for named args. */
        val positionalIndex: Int = -1
    )

    fun resolveFunction(call: Call, functions: List<FunctionMetadata>): FunctionMetadata? =
        functions.find { it.name == call.name }

    /**
     * Resolves every argument to its parameter (named → by name, positional → by index).
     * Unknown parameters and "positional after named" are reported as [Issue]s.
     */
    fun resolveArgs(call: Call, fn: FunctionMetadata): Pair<List<ResolvedArg>, List<Issue>> {
        val visibleParams = fn.parameters.filter { !it.isInjected }
        val issues = mutableListOf<Issue>()
        val resolved = mutableListOf<ResolvedArg>()

        // For chained calls (`::b`), the chained value is the implicit first positional
        // argument, so explicit positional arguments start at index 1.
        var positionalIndex = if (call.isChained) 1 else 0
        var seenNamed = false

        for (arg in call.args) {
            if (arg.isNamed) {
                seenNamed = true
                val param = fn.parameters.find { !it.isInjected && it.name == arg.paramName }
                if (param == null) {
                    issues.add(
                        Issue(
                            Severity.ERROR,
                            arg.nameStart,
                            arg.nameEnd,
                            "Unknown parameter '${arg.paramName}' for '${call.name}'"
                        )
                    )
                    resolved.add(ResolvedArg(arg, null))
                } else {
                    resolved.add(ResolvedArg(arg, param))
                }
            } else {
                if (seenNamed) {
                    issues.add(
                        Issue(
                            Severity.ERROR,
                            arg.fullStart,
                            arg.fullEnd,
                            "All arguments following a named argument must be named as well"
                        )
                    )
                }
                val param = visibleParams.getOrNull(positionalIndex)
                if (param == null) {
                    issues.add(
                        Issue(
                            Severity.ERROR,
                            arg.fullStart,
                            arg.fullEnd,
                            "Too many arguments for '${call.name}'"
                        )
                    )
                } else {
                    resolved.add(ResolvedArg(arg, param, positionalIndex))
                }
                positionalIndex++
            }
        }
        return resolved to issues
    }

    /**
     * Validates the whole call: unknown function/parameters, invalid enum values and
     * missing required arguments.
     *
     * [knownVariables] are document-level variables declared with `.var {name} {value}`;
     * a `.name` that matches a declared variable is a variable reference, not a function
     * call, so it is not reported as an unknown function.
     */
    fun validate(
        call: Call,
        functions: List<FunctionMetadata>,
        knownVariables: Set<String> = emptySet()
    ): List<Issue> {
        val issues = mutableListOf<Issue>()
        val fn = resolveFunction(call, functions)

        if (fn == null) {
            if (call.name in knownVariables) {
                // Variable reference (e.g. `.version` after `.var {version} {…}`).
                return emptyList()
            }
            issues.add(
                Issue(
                    Severity.ERROR,
                    call.nameStart,
                    call.nameEnd,
                    "Unknown function '${call.name}'"
                )
            )
            return issues
        }

        val (resolved, resolveIssues) = resolveArgs(call, fn)
        issues.addAll(resolveIssues)

        // Value validation (enum / constrained values)
        for (r in resolved) {
            val param = r.param ?: continue
            val allowed = param.allowedValues ?: continue
            val value = normalizeValue(r.arg.raw)
            if (value.isNotEmpty() && value !in allowed) {
                // highlight the raw value span (trimmed)
                val trim = r.arg.raw.indexOfFirst { !it.isWhitespace() }
                val trimEnd = r.arg.raw.indexOfLast { !it.isWhitespace() }
                val start = if (trim >= 0) r.arg.rawStart + trim else r.arg.rawStart
                val end = if (trimEnd >= 0) r.arg.rawStart + trimEnd + 1 else r.arg.rawEnd
                issues.add(
                    Issue(
                        Severity.ERROR,
                        start,
                        end,
                        "Invalid value '$value' for '${param.name}'. Expected: ${allowed.joinToString(", ")}"
                    )
                )
            }
        }

        // Missing required arguments → warning (matches the compiler's arity checks).
        // The chained value and an indented body argument each count as one argument.
        val required = fn.parameters.filter { !it.isInjected && !it.isOptional && !it.isLikelyBody }
        val implicitCount = (if (call.isChained) 1 else 0) + (if (call.hasBodyArgument) 1 else 0)
        val writtenCount = resolved.count { it.param != null } + implicitCount
        if (required.isNotEmpty() &&
            writtenCount < required.size &&
            (call.args.isNotEmpty() || call.isChained || call.hasBodyArgument)
        ) {
            issues.add(
                Issue(
                    Severity.WARNING,
                    call.nameStart,
                    call.nameEnd,
                    "Expected ${required.size} arguments, but $writtenCount found"
                )
            )
        }

        return issues
    }

    /**
     * Normalizes an argument's raw value for comparison: trims, strips wrapping braces
     * and quotes. `{bottomcenter}` → `bottomcenter`, `"paged"` → `paged`.
     */
    fun normalizeValue(raw: String): String {
        var v = raw.trim()
        if (v.length >= 2 && v.startsWith("{") && v.endsWith("}")) {
            v = v.substring(1, v.length - 1).trim()
        }
        if (v.length >= 2 && (v[0] == '"' || v[0] == '\'') && v.last() == v[0]) {
            v = v.substring(1, v.length - 1).trim()
        }
        return v
    }
}
