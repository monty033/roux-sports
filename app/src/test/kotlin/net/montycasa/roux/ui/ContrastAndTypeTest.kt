@file:Suppress("ktlint:standard:function-naming")

package net.montycasa.roux.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import net.montycasa.roux.domain.model.AccentColor
import net.montycasa.roux.ui.drawer.DrawerPalette
import net.montycasa.roux.ui.drawer.LeaguesPanel
import net.montycasa.roux.ui.teams.SHEET_INK
import net.montycasa.roux.ui.teams.SHEET_MUTED
import net.montycasa.roux.ui.teams.SHEET_OFF_FILL
import net.montycasa.roux.ui.teams.SHEET_PANEL
import net.montycasa.roux.ui.theme.RouxDarkCard
import net.montycasa.roux.ui.theme.RouxDarkPage
import net.montycasa.roux.ui.theme.RouxLightCard
import net.montycasa.roux.ui.theme.RouxLightPage
import net.montycasa.roux.ui.theme.RouxTheme
import net.montycasa.roux.ui.theme.rouxAccents
import net.montycasa.roux.ui.theme.rouxColorScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * P5: contrast and minimum type size, asserted over every rendered text node the audit can reach.
 *
 * SHAPE (a), palette-pair assertion, chosen because Compose semantics do not expose the painted colour of a
 * text node. Two things are proven, and they are proven separately:
 *
 *  1. CONTRAST. [palettePairs], [alertsSheetPairs] and [drawerPairs] enumerate the (ink, surface) pairs the UI
 *     paints text with, for every [AccentColor], and assert the WCAG 2.x ratio is >= 4.5:1. They read the real
 *     constants (SHEET_INK, DrawerPalette, the accent table, ...), not copies, so a change in the source is seen
 *     here. It proves the PALETTE PAIRS are compliant. It does NOT prove each rendered node uses one of these
 *     pairs. A NEW text colour or a NEW surface is not covered until its pair is added to one of these functions.
 *
 *  2. TYPE SIZE. Every rendered text node in every case is walked from the unmerged semantics tree, and its
 *     resolved `TextLayoutResult.layoutInput.style.fontSize` (in sp, independent of font scale) is read. This
 *     is a per-node check. Any node under 12 sp fails, and any sentence under 14 sp fails. Semantics
 *     do not carry the style ROLE, so the allowance is per SIZE, not per role. Consequence: a 12 sp node that is
 *     a body label, not a clock, also passes. This is the known weakness of the size-based allowance.
 *
 * Not covered: the calendar month sheet, loading and error states, and the scores/detail states not listed in
 * [auditCases]. Text inside those is not checked by this file.
 *
 * The drawer and the alerts sheet use fixed colours that do not change with the theme, so their pairs are checked
 * once and reported in the table with a theme label of "drawer" or "fixed". The one exception is the accent, which
 * both read from the selected [AccentColor]'s fill; that fill is the same in both themes.
 *
 * WCAG maths is computed here from the sRGB channels, not from Compose's `Color.luminance()`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContrastAndTypeTest {
    @get:Rule
    val rule = createComposeRule()

    private val minContrast = 4.5
    private val minBodySp = 14f

    /**
     * The rule, decided 2026-11-01: nothing renders below 12 sp; prose renders at 14 sp or more.
     *
     * There is deliberately NO allowlist. The earlier shape allowed whole *sizes*, which meant a real label
     * shrinking to 12 sp would pass unnoticed because 12 sp was excused for the clock and the date strip. The
     * honest fix was to make the micro-labels themselves 12 sp, so the floor is the floor and the only exception
     * is prose, which is a longer string rather than a role. Semantics does not carry type roles, so "prose" is
     * identified by length: anything 40 characters or more is a sentence, and a sentence under 14 sp is too small
     * to read comfortably.
     */
    private val minLabelSp = 12f

    /** Longer than this reads as a sentence rather than a label. */
    private val proseLength = 40

    // ----- Palette pairs --------------------------------------------------------------------------------------------

    private class TextPair(
        val what: String,
        val ink: Color,
        val surface: Color,
    )

    /** WCAG 2.x relative luminance from sRGB channels, linearised with the 0.03928 threshold. */
    private fun luminance(c: Color): Double {
        fun lin(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun wcagRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Every (ink, surface) pair the UI paints text with, for one theme and one accent. Built from the same role names
     * the screens use, so a new role on a surface has to be added here by name to be covered.
     */
    private fun palettePairs(
        dark: Boolean,
        accent: AccentColor,
    ): List<TextPair> {
        val scheme = rouxColorScheme(dark, accent)
        val accents = rouxAccents(dark, accent)
        val tag = (if (dark) "dark" else "light") + " $accent"
        val page = scheme.surface
        val card = scheme.surfaceVariant
        val inks =
            listOf(
                "onSurface" to scheme.onSurface,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "signature" to accents.signature,
                "muted" to accents.muted,
            )
        val pairs = mutableListOf<TextPair>()
        for ((name, ink) in inks) {
            pairs += TextPair("$tag $name on page", ink, page)
            pairs += TextPair("$tag $name on card", ink, card)
        }
        pairs += TextPair("$tag onBackground on background", scheme.onBackground, scheme.background)
        pairs += TextPair("$tag onPrimary on primary", scheme.onPrimary, scheme.primary)
        pairs += TextPair("$tag Settings back action on surface", scheme.primary, scheme.surface)
        pairs += TextPair("$tag onFill on live fill", accents.onFill, accents.fill)
        return pairs
    }

    /**
     * The alerts sheet paints every text node with an explicit colour on a fixed dark panel, in both themes. These
     * are the real constants, so the test reads what the sheet draws. The orange is the selected accent's fill, which
     * the sheet reads from [rouxAccents]; it does not change with the theme.
     */
    private fun alertsSheetPairs(accent: AccentColor): List<TextPair> {
        val fill = rouxAccents(false, accent).fill
        return listOf(
            TextPair("sheet title/labels/warnings SHEET_INK on SHEET_PANEL", SHEET_INK, SHEET_PANEL),
            TextPair("sheet disclosure SHEET_MUTED on SHEET_PANEL", SHEET_MUTED, SHEET_PANEL),
            TextPair("sheet secondary action $accent fill on SHEET_PANEL", fill, SHEET_PANEL),
            // The chosen reminder time: near-black on the accent fill. White on that orange is 3.3:1 and was not used.
            TextPair("sheet chosen reminder time SHEET_PANEL on $accent fill", SHEET_PANEL, fill),
            // The default answer, Off: ink on the raised neutral. Deliberately not the accent, which means "on" here.
            TextPair("sheet chosen Off SHEET_INK on SHEET_OFF_FILL", SHEET_INK, SHEET_OFF_FILL),
        )
    }

    /**
     * The league drawer paints its colours from [DrawerPalette], except the accent, which is the selected accent's
     * fill. None of them depend on the theme. Each pair is one the drawer's rows and labels actually produce.
     */
    private fun drawerPairs(accent: AccentColor): List<TextPair> {
        val d = DrawerPalette
        val orange = rouxAccents(false, accent).fill
        // Each row is a pairing LeagueDrawer.DrawerRow / LeaguesPanel actually makes. Muted never sits on the selected
        // row (a selected row trails in orange) and unselected names never sit on the team row, so those are omitted.
        return listOf(
            TextPair("drawer ink on team row (My Teams name)", d.ink, d.teamRow),
            TextPair("drawer unselected name on panel", d.unselected, d.panel),
            TextPair("drawer selected name on selected row", d.selectedName, d.selectedRow),
            TextPair("drawer $accent on panel (edit, selected trailing off-row)", orange, d.panel),
            TextPair("drawer $accent on selected row (selected trailing)", orange, d.selectedRow),
            TextPair("drawer $accent on team row (selected trailing)", orange, d.teamRow),
            TextPair("drawer muted on panel (labels, footer, trailing)", d.muted, d.panel),
            TextPair("drawer muted on team row (trailing, unselected)", d.muted, d.teamRow),
            TextPair("drawer season on panel", d.season, d.panel),
            TextPair("drawer dimmed name on panel", d.dimmedName, d.panel),
            TextPair("drawer dimmed trailing on panel", d.dimmedTrailing, d.panel),
        )
    }

    /** Every accent in both themes. */
    private val palette: List<Pair<String, TextPair>> =
        AccentColor.entries.flatMap { accent ->
            listOf(false, true).flatMap { dark ->
                palettePairs(dark, accent).map { (if (dark) "dark" else "light") to it }
            }
        }

    /** The drawer and sheet pairs for every accent. */
    private val fixedPairs: List<Pair<String, TextPair>> =
        AccentColor.entries.flatMap { accent ->
            drawerPairs(accent).map { "drawer" to it } + alertsSheetPairs(accent).map { "fixed" to it }
        }

    @Test
    fun everyPaintedTextPairMeetsContrastInBothThemes() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        for ((theme, pair) in palette + fixedPairs) {
            val ratio = wcagRatio(pair.ink, pair.surface)
            report.append("%-8s %-40s %6.2f:1\n".format(theme, pair.what, ratio))
            if (ratio < minContrast) {
                failures += "theme=$theme ${pair.what}: ${"%.2f".format(ratio)}:1 < $minContrast:1"
            }
        }
        println("CONTRAST REPORT\n$report")
        if (failures.isNotEmpty()) {
            throw AssertionError("${failures.size} text pair(s) below $minContrast:1:\n" + failures.joinToString("\n"))
        }
    }

    private class RoleFloor(
        val what: String,
        val a: Color,
        val b: Color,
        val floor: Double,
    )

    /**
     * The floors each accent was measured against in the design, by role. The 4.5:1 loop above covers text; these
     * cover the parts that are not text: the live fill and its ink, the light signature, the light rule, and the
     * 6 dp stripe. A fill that drops below one of these fails here by name.
     */
    @Test
    fun everyAccentRoleMeetsItsMeasuredFloor() {
        val failures = mutableListOf<String>()
        for (accent in AccentColor.entries) {
            val light = rouxAccents(dark = false, accent = accent)
            val dark = rouxAccents(dark = true, accent = accent)
            val floors =
                listOf(
                    RoleFloor("$accent fill vs onFill", light.fill, light.onFill, 4.5),
                    RoleFloor("$accent dark fill vs dark page", dark.fill, RouxDarkPage, 4.5),
                    RoleFloor("$accent dark fill vs dark card", dark.fill, RouxDarkCard, 4.5),
                    RoleFloor("$accent dark fill vs drawer team row", dark.fill, DrawerPalette.teamRow, 4.5),
                    RoleFloor("$accent dark fill vs drawer selected row", dark.fill, DrawerPalette.selectedRow, 4.5),
                    RoleFloor("$accent dark fill vs sheet panel", dark.fill, SHEET_PANEL, 4.5),
                    RoleFloor("$accent light signature vs light page", light.signature, RouxLightPage, 4.55),
                    RoleFloor("$accent light signature vs white card", light.signature, RouxLightCard, 4.55),
                    RoleFloor("$accent light stroke vs light page", light.stroke, RouxLightPage, 3.05),
                    RoleFloor("$accent light stroke vs white card", light.stroke, RouxLightCard, 3.05),
                    RoleFloor("$accent light fill vs white (6 dp stripe)", light.fill, Color.White, 3.0),
                )
            for (floor in floors) {
                val ratio = wcagRatio(floor.a, floor.b)
                if (ratio < floor.floor) {
                    failures += "${floor.what}: ${"%.2f".format(ratio)}:1 < ${floor.floor}:1"
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("${failures.size} accent role(s) below their floor:\n" + failures.joinToString("\n"))
        }
    }

    // ----- Rendered text-node type size -----------------------------------------------------------------------------

    private class TextNode(
        val text: String,
        val sp: Float?,
        val sizeUnit: TextUnitType?,
    )

    /** Every text node in the subtree, from the unmerged tree, with its resolved sp size. */
    private fun textNodes(root: SemanticsNode): List<TextNode> {
        val out = mutableListOf<TextNode>()

        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)
            if (text != null) {
                val label = text.joinToString("") { it.text }
                val layouts = mutableListOf<TextLayoutResult>()
                node.config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(layouts)
                val style: TextStyle? = layouts.firstOrNull()?.layoutInput?.style
                val size = style?.fontSize
                out += TextNode(label, size?.value, size?.type)
            }
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    @Test
    fun everyRenderedTextNodeMeetsMinimumBodySizeOrIsAllowlisted() {
        val cases = AuditStates.screens()
        val caseIndex = mutableIntStateOf(0)
        val dark = mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                RouxTheme(dark = dark.value) {
                    cases[caseIndex.intValue].content()
                }
            }
        }

        val failures = mutableListOf<String>()
        val classified = mutableMapOf<String, Int>()
        val labelsAt = mutableMapOf<Float, MutableSet<String>>()
        var nodesSeen = 0
        cases.forEachIndexed { index, case ->
            listOf(false, true).forEach { isDark ->
                val theme = if (isDark) "dark" else "light"
                val prefix = "case=${case.name} theme=$theme: "
                rule.runOnIdle {
                    caseIndex.intValue = index
                    dark.value = isDark
                }
                rule.waitForIdle()
                // A state whose content sits behind a tab is measured with that tab open.
                case.tab?.let { tab ->
                    rule.onNodeWithTag(tab).performClick()
                    rule.waitForIdle()
                }
                val nodes = textNodes(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
                assertTrue(prefix + "rendered no text nodes - the walk saw nothing", nodes.isNotEmpty())
                nodesSeen += nodes.size
                for (node in nodes) {
                    val sp = node.sp
                    if (sp == null || node.sizeUnit != TextUnitType.Sp) {
                        failures += prefix + "text=\"${node.text}\" has no resolvable sp size (unit=${node.sizeUnit})"
                        continue
                    }
                    when {
                        sp < minLabelSp -> {
                            failures +=
                                prefix + "text=\"${node.text}\" is ${sp}sp, under the ${minLabelSp.toInt()}sp floor"
                        }

                        node.text.length >= proseLength && sp < minBodySp -> {
                            failures +=
                                prefix + "prose \"${node.text.take(48)}…\" is ${sp}sp, under the " +
                                "${minBodySp.toInt()}sp prose floor"
                        }

                        else -> {
                            val key = if (sp >= minBodySp) "body>=14sp" else "label ${sp.toInt()}sp"
                            classified[key] = (classified[key] ?: 0) + 1
                            labelsAt.getOrPut(sp) { mutableSetOf() } += node.text
                        }
                    }
                }
            }
        }

        println("TYPE WALK: $nodesSeen text nodes across ${cases.size} cases x 2 themes")
        classified.toSortedMap().forEach { (key, count) -> println("  $key: $count") }
        // Printed so a reader can see what the floor was measured against, and spot a label that has
        // no business being that small.
        labelsAt.toSortedMap().forEach { (sp, labels) ->
            println("  label ${sp.toInt()}sp (${labels.size}): " + labels.sorted().take(24).joinToString(" | "))
        }
        if (failures.isNotEmpty()) {
            throw AssertionError("${failures.size} type-size finding(s):\n" + failures.joinToString("\n"))
        }
    }
}
