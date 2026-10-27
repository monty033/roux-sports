package net.montycasa.roux.ui.lint

/**
 * R6: no hidden actions. A top app bar may hold a title and nothing else, overflow menus are out, and a
 * confirmation is a [androidx.compose.material3.ModalBottomSheet] with its buttons at the bottom rather
 * than an `AlertDialog` whose choices sit wherever the dialog puts them.
 *
 * This is a source scan, not a rendering check: it reads Kotlin text. That is deliberate - the rule is
 * about the code a reviewer reads, and a scan can be run in `check` without a device.
 */
internal data class HiddenActionViolation(
    val line: Int,
    val reason: String,
)

private val topBarCalls =
    listOf(
        "TopAppBar(",
        "CenterAlignedTopAppBar(",
        "MediumTopAppBar(",
        "LargeTopAppBar(",
    )

/** Calls R6 bans outright, with the replacement to reach for. */
private val bannedCalls =
    mapOf(
        "DropdownMenu(" to "an overflow menu - put the action on screen instead",
        "AlertDialog(" to "a dialog - confirm with ModalBottomSheet and buttons at the bottom",
    )

/**
 * Scans one file's [source] for R6 violations.
 *
 * Comments and string literals are blanked first, so prose that mentions `actions =` cannot trip the
 * scan and a call written inside a string cannot hide from it.
 */
internal fun findHiddenActions(source: String): List<HiddenActionViolation> {
    val code = blankCommentsAndStrings(source)
    val violations = mutableListOf<HiddenActionViolation>()

    bannedCalls.forEach { (call, why) ->
        var index = code.indexOf(call)
        while (index >= 0) {
            violations += HiddenActionViolation(lineAt(code, index), "$call is $why")
            index = code.indexOf(call, index + call.length)
        }
    }

    topBarCalls.forEach { call ->
        var index = code.indexOf(call)
        while (index >= 0) {
            val open = index + call.length - 1
            val close = matchingParen(code, open)
            val arguments = if (close > open) code.substring(open, close) else ""
            if (Regex("""\bactions\s*=""").containsMatchIn(arguments)) {
                violations +=
                    HiddenActionViolation(
                        lineAt(code, index),
                        "$call carries actions = - the top bar holds a title only (R6)",
                    )
            }
            index = code.indexOf(call, index + call.length)
        }
    }

    return violations.sortedBy { it.line }
}

private fun lineAt(
    code: String,
    index: Int,
): Int = code.take(index).count { it == '\n' } + 1

/** Index of the `)` matching the `(` at [open], or -1 when the call is unbalanced. */
private fun matchingParen(
    code: String,
    open: Int,
): Int {
    var depth = 0
    for (index in open until code.length) {
        when (code[index]) {
            '(' -> {
                depth++
            }

            ')' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return -1
}

/**
 * Replaces comment and string bodies with spaces, preserving length so line numbers stay right. Raw
 * strings are handled because a JSON fixture inside `"""` is the usual place a bracket appears.
 */
private fun blankCommentsAndStrings(source: String): String {
    val out = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        val char = source[index]
        when {
            char == '/' && source.getOrNull(index + 1) == '/' -> {
                while (index < source.length && source[index] != '\n') {
                    out.append(' ')
                    index++
                }
            }

            char == '/' && source.getOrNull(index + 1) == '*' -> {
                out.append("  ")
                index += 2
                while (index < source.length) {
                    if (source[index] == '*' && source.getOrNull(index + 1) == '/') {
                        out.append("  ")
                        index += 2
                        break
                    }
                    out.append(if (source[index] == '\n') '\n' else ' ')
                    index++
                }
            }

            source.startsWith("\"\"\"", index) -> {
                out.append("   ")
                index += 3
                while (index < source.length) {
                    if (source.startsWith("\"\"\"", index)) {
                        out.append("   ")
                        index += 3
                        break
                    }
                    out.append(if (source[index] == '\n') '\n' else ' ')
                    index++
                }
            }

            char == '"' -> {
                out.append(' ')
                index++
                while (index < source.length) {
                    val inner = source[index]
                    when {
                        inner == '\\' -> {
                            out.append("  ")
                            index += 2
                        }

                        inner == '"' -> {
                            out.append(' ')
                            index++
                            break
                        }

                        else -> {
                            out.append(if (inner == '\n') '\n' else ' ')
                            index++
                        }
                    }
                }
            }

            else -> {
                out.append(char)
                index++
            }
        }
    }
    return out.toString()
}
