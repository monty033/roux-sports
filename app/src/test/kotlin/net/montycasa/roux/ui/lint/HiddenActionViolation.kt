package net.montycasa.roux.ui.lint

/**
 * R6: no hidden actions. A top app bar may hold a title and nothing else, overflow menus are out, no
 * floating action button sits at the top, and a confirmation is a `ModalBottomSheet` with its buttons at
 * the bottom rather than an `AlertDialog` whose choices sit wherever the dialog puts them.
 *
 * This is a source scan, not a rendering check: it reads Kotlin text. That is deliberate - the rule is
 * about the code a reviewer reads, and a scan can run in `check` without a device.
 *
 * Its limit is lexical: it recognises calls written by name, including names imported with `as`. A
 * composable that wraps a top bar (`AppBar { ... }`), a FAB hoisted into a top-aligned parent, or a menu
 * opened by raw `pointerInput` cannot be seen here. R6 says nothing may be hidden, so those are review
 * questions rather than scan questions.
 */
internal data class HiddenActionViolation(
    val line: Int,
    val reason: String,
)

private val topBarNames =
    listOf(
        "TopAppBar",
        "CenterAlignedTopAppBar",
        "MediumTopAppBar",
        "LargeTopAppBar",
    )

/** Calls R6 bans outright, with the replacement to reach for. */
private val bannedCalls =
    mapOf(
        "DropdownMenu" to "an overflow menu - put the action on screen instead",
        "AlertDialog" to "a dialog - confirm with ModalBottomSheet and buttons at the bottom",
    )

private const val FAB = "FloatingActionButton"

/**
 * Scans one file's [source] for R6 violations.
 *
 * Comments, string literals and character literals are blanked first, so prose that mentions
 * `actions =` cannot trip the scan and a call written inside a string cannot hide from it.
 */
internal fun findHiddenActions(source: String): List<HiddenActionViolation> {
    val code = blankCommentsAndStrings(source)
    val aliases = importedAliases(code)
    val violations = mutableListOf<HiddenActionViolation>()

    // An aliased import is still a call to the banned composable: `import ...TopAppBar as Header`
    // followed by `Header(actions = ...)` hides a top-bar action from any scan that knows only the
    // original name.
    val topBarTokens = topBarNames + aliases.filterValues { it in topBarNames }.keys
    val bannedTokens =
        bannedCalls +
            aliases
                .filterValues { it in bannedCalls }
                .mapValues { (_, canonical) -> bannedCalls.getValue(canonical) }

    bannedTokens.forEach { (token, why) ->
        eachCall(code, token) { index ->
            violations += HiddenActionViolation(lineAt(code, index), "$token is $why")
        }
    }

    topBarTokens.forEach { token ->
        eachCall(code, token) { index ->
            val open = index + token.length
            val close = matchingParen(code, open)
            val arguments = if (close > open) code.substring(open, close) else ""
            if (Regex("""\bactions\s*=""").containsMatchIn(arguments)) {
                violations +=
                    HiddenActionViolation(
                        lineAt(code, index),
                        "$token carries actions = - the top bar holds a title only (R6)",
                    )
            }
        }
    }

    (aliases.filterValues { it == FAB }.keys + FAB).forEach { token ->
        eachCall(code, token) { index ->
            val open = index + token.length
            val close = matchingParen(code, open)
            val arguments = if (close > open) code.substring(open, close) else ""
            // R6 bans a FAB at the TOP. The alignment has to be inside the call's own modifier chain to
            // be visible lexically; a FAB placed in a top-aligned wrapper around it escapes this scan.
            if (arguments.contains("Alignment.Top")) {
                violations +=
                    HiddenActionViolation(
                        lineAt(code, index),
                        "$token is aligned to the top - a floating action button belongs at the bottom (R6)",
                    )
            }
        }
    }

    return violations.sortedBy { it.line }
}

/** Alias to the composable it renames, read from `import a.b.Composable as Alias` lines. */
private fun importedAliases(code: String): Map<String, String> =
    Regex("""(?m)^\s*import\s+[\w.]+\.(\w+)\s+as\s+(\w+)""")
        .findAll(code)
        .associate { match -> match.groupValues[2] to match.groupValues[1] }

/**
 * Visits every call to [token], skipping identifiers that merely end in it: `parseDropdownMenu(` is a
 * different function, and flagging it is how a guard earns a reputation for lying.
 */
private fun eachCall(
    code: String,
    token: String,
    action: (Int) -> Unit,
) {
    val needle = "$token("
    var index = code.indexOf(needle)
    while (index >= 0) {
        val before = code.getOrNull(index - 1) ?: ' '
        val partOfLongerName = before.isLetterOrDigit() || before == '_' || before == '$'
        if (!partOfLongerName) action(index)
        index = code.indexOf(needle, index + needle.length)
    }
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
 * Replaces comment and string bodies with spaces, preserving length so line numbers stay right.
 *
 * Kotlin has nested block comments and character literals, and both break a naive blanker in ways that
 * matter: `'"'` read as an opening quote blanks the rest of the file, hiding every call after it.
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
                var depth = 1
                while (index < source.length && depth > 0) {
                    when {
                        source[index] == '/' && source.getOrNull(index + 1) == '*' -> {
                            out.append("  ")
                            index += 2
                            depth++
                        }

                        source[index] == '*' && source.getOrNull(index + 1) == '/' -> {
                            out.append("  ")
                            index += 2
                            depth--
                        }

                        else -> {
                            out.append(if (source[index] == '\n') '\n' else ' ')
                            index++
                        }
                    }
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

            char == '\'' -> {
                // A character literal, including the escaped ones: '"', '\'', '\n'.
                out.append(' ')
                index++
                while (index < source.length) {
                    val inner = source[index]
                    when {
                        inner == '\\' -> {
                            out.append("  ")
                            index += 2
                        }

                        inner == '\'' -> {
                            out.append(' ')
                            index++
                            break
                        }

                        inner == '\n' -> {
                            break
                        }

                        else -> {
                            out.append(' ')
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
