package net.montycasa.roux.ui.lint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R6: nothing important lives behind an overflow menu or a dialog. The rule is enforced by scanning the
 * source, so a reviewer reading a diff never has to notice a bar that quietly grew an `actions` slot.
 */
class NoHiddenActionsTest {
    @Test
    fun aTopBarWithActionsIsFlagged() {
        val violations =
            findHiddenActions(
                """
                TopAppBar(
                    title = { Text("Scores") },
                    actions = { IconButton(onClick = {}) { Icon(Icons.Default.Refresh, null) } },
                )
                """.trimIndent(),
            )

        assertEquals(listOf(1), violations.map { it.line })
        assertTrue("should name actions =, got: $violations", violations.single().reason.contains("actions ="))
    }

    @Test
    fun anEmptyActionsSlotIsFlaggedToo() {
        // An empty slot is how a bar acquires one later; the rule is about the slot, not its contents.
        val violations = findHiddenActions("""TopAppBar(title = { Text("Scores") }, actions = {})""")

        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("actions ="))
    }

    @Test
    fun aCentreAlignedBarWithoutActionsPasses() {
        val violations =
            findHiddenActions(
                """
                CenterAlignedTopAppBar(
                    title = { Text("Standings") },
                )
                """.trimIndent(),
            )

        assertEquals(emptyList<HiddenActionViolation>(), violations)
    }

    @Test
    fun aDropdownMenuIsFlagged() {
        val violations = findHiddenActions("""DropdownMenu(expanded = open, onDismissRequest = {})""")

        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("overflow menu"))
    }

    @Test
    fun anAlertDialogIsFlagged() {
        val violations = findHiddenActions("""AlertDialog(onDismissRequest = {}, confirmButton = {})""")

        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("ModalBottomSheet"))
    }

    @Test
    fun aModalBottomSheetPasses() {
        val violations =
            findHiddenActions(
                """
                ModalBottomSheet(onDismissRequest = {}) {
                    Button(onClick = {}) { Text("Sign out") }
                }
                """.trimIndent(),
            )

        assertEquals(emptyList<HiddenActionViolation>(), violations)
    }

    @Test
    fun proseAndStringsDoNotTripTheScan() {
        // A comment describing the rule and a string holding a banned name are exactly the false
        // positives that make a guard get deleted instead of obeyed.
        val source =
            """
            // Never pass actions = { } to a TopAppBar.
            /* DropdownMenu( and AlertDialog( are banned by R6. */
            val message = "DropdownMenu( is banned"
            val raw = ""${'"'}
                AlertDialog(
            ""${'"'}
            """.trimIndent()

        assertEquals(emptyList<HiddenActionViolation>(), findHiddenActions(source))
    }

    @Test
    fun anActionsArgumentBelongingToANestedCallIsNotAttributedToTheBar() {
        // The scanner looks inside the bar's own parentheses, so a nested call's argument is the nested
        // call's business - here it is outside the bar entirely.
        // The decoy has to come AFTER the bar: a scanner that reads from the opening paren to the end of
        // the file instead of to the matching one would attribute this to the bar above it.
        val source =
            """
            CenterAlignedTopAppBar(title = { Text("Scores") })
            val handler = Modifier.semantics { actions = 1 }
            """.trimIndent()

        assertEquals(emptyList<HiddenActionViolation>(), findHiddenActions(source))
    }

    @Test
    fun theRealMainTreeHasNoHiddenActions() {
        val root = File("src/main/kotlin")
        assertTrue("expected the main source tree at ${root.absolutePath}", root.isDirectory)
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("expected Kotlin sources under ${root.absolutePath}", sources.isNotEmpty())

        val found =
            sources.flatMap { file ->
                findHiddenActions(file.readText()).map { "${file.relativeTo(root)}:${it.line} ${it.reason}" }
            }

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun anAliasedTopBarIsStillATopBar() {
        // `import ...TopAppBar as Header` then Header(actions = ...) is the same hidden action under a
        // name the scan would otherwise never look for.
        val source =
            """
            import androidx.compose.material3.TopAppBar as Header

            fun bar() = Header(title = { Text("Scores") }, actions = {})
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("actions ="))
    }

    @Test
    fun anAliasedOverflowMenuIsFlagged() {
        val source =
            """
            import androidx.compose.material3.DropdownMenu as Menu

            fun menu() = Menu(expanded = false, onDismissRequest = {}) {}
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("overflow menu"))
    }

    @Test
    fun aFabAlignedByItsParentIsFlagged() {
        // The case that killed the alignment heuristic: the FAB carries no alignment of its own, so a scan
        // reading only the call's arguments never sees that it is at the top.
        val source =
            """
            Box(contentAlignment = Alignment.TopEnd) {
                FloatingActionButton(onClick = {}) { Text("+") }
            }
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("floating action button"))
    }

    @Test
    fun anyFabIsFlaggedIncludingABottomOne() {
        // A bottom FAB is legal under R6 but this app has none, so the scan refuses the control and a
        // future one has to change this rule deliberately. Sound beats clever: the alignment heuristic
        // also flagged a FAB aligned Top inside a short Row at the bottom of the screen.
        val source =
            """
            FloatingActionButton(modifier = Modifier.align(Alignment.BottomEnd), onClick = {}) { Text("+") }
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("R6 bans one at the top"))
    }

    @Test
    fun aCharacterLiteralDoesNotSwallowTheRestOfTheFile() {
        // Reading '"' as an opening string blanks everything after it, so this call would vanish.
        val source =
            """
            val quote = '"'
            DropdownMenu(expanded = false, onDismissRequest = {}) {}
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("overflow menu"))
    }

    @Test
    fun aFunctionWhoseNameEndsInABannedNameIsNotACall() {
        val source =
            """
            fun parseDropdownMenu() = Unit
            fun constructAlertDialogLater() = Unit
            """.trimIndent()

        assertEquals(emptyList<HiddenActionViolation>(), findHiddenActions(source))
    }

    @Test
    fun aNestedBlockCommentIsBlankedToItsRealEnd() {
        // Kotlin nests block comments; stopping at the first */ leaves the rest looking like code.
        val source =
            """
            /* outer /* inner */ DropdownMenu(expanded = false) */
            """.trimIndent()

        assertEquals(emptyList<HiddenActionViolation>(), findHiddenActions(source))
    }

    @Test
    fun anEscapedAliasIsRecognised() {
        val source =
            """
            import androidx.compose.material3.TopAppBar as `Header`

            fun bar() = Header(title = { Text("Scores") }, actions = {})
            """.trimIndent()

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("actions ="))
    }

    @Test
    fun aSpaceBeforeTheParenthesisDoesNotHideTheCall() {
        // Kotlin accepts `TopAppBar (actions = {})`; a scan matching "TopAppBar(" literally does not.
        val source = """TopAppBar (title = { Text("Scores") }, actions = {})"""

        val violations = findHiddenActions(source)
        assertEquals(1, violations.size)
        assertTrue(violations.single().reason.contains("actions ="))
    }

    @Test
    fun aLocalVariableNamedActionsIsNotAnActionsSlot() {
        val source =
            """
            TopAppBar(title = { val actions = listOf("refresh"); Text("Scores") })
            """.trimIndent()

        assertEquals(emptyList<HiddenActionViolation>(), findHiddenActions(source))
    }
}
