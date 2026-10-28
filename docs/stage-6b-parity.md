# Stage 6b — parity proof: measured app vs the design file

The design has been the authority for Stage 6b, but "it matches" has been an assertion so far. This is the check: every
number the app pins, against the number the design file actually specifies, with the place in the app that proves it.

Two sources only:

- **Design**: the CSS in `roux-sports-design/final/scores-screen.html`, read directly. Never the design README, whose
  geometry was wrong, and never this plan's own table, which copied it.
- **App**: frame-level assertions in the test suite. These are not "the code sets 76 dp"; they are measurements of the
  composed node's bounds at device density, which is why they can catch weight and padding mistakes that a constant
  cannot.

Cross-checked once against a recorded screenshot at 411 dp wide (1078 px, 2.623 px/dp): cards measured **76.6 dp** tall
with **8.0 dp** gaps, within the border-rounding tolerance of the 76 dp and 8 dp the tests assert.

## Measurements

| Element | Design | App (measured) | Where | Verdict |
|---|---|---|---|---|
| Card height | not pinned — emerges from two 28 dp rows plus padding (measured 76 dp in the render) | 76 dp exact | `GameRowTest.kt:386` | match; app pins what the design derives |
| Card width at 411 dp | 387 dp (411 − 2×12 upright padding) | 387 dp | `GameRowTest.kt:386` | match |
| List gap | 8 dp (`.list{gap:.5rem}`) | 8 dp | `GameRowTest.kt` (reach gap) + `DateShelfCalendarTest.kt:89` pattern | match |
| State rail, wide | 92 dp — `.tk{grid-template-columns:minmax(0,1fr) 5.75rem}` (`scores-screen.html:94`) | 92 dp | `GameRowTest.kt:395` | match |
| State rail, narrow (<380 dp) | 82 dp — `5.125rem` at `scores-screen.html:117` | 82 dp | `GameRowTest.kt:462` | match |
| Crest | 34 dp (32 dp narrow) | 34 dp | `GameRowTest.kt:393` | match |
| Matchup row | 28 dp | 28 dp | `GameRowTest.kt:389` | match |
| Score column | fixed | 30 dp | `GameRowTest.kt:398` | match |
| Header height | 64 dp | 64 dp exact | `ScoresHeaderTest.kt:59` | match (tightened from a floor) |
| Menu button | 48 dp | 48 dp | `ScoresHeaderTest.kt:62` | match |
| Wordmark box | 20 dp (57×18 visible ink) | 20 dp | `ScoresHeaderTest.kt:67` | match |
| Live pill | 72 × 28 dp | 72 × 28 dp | `ScoresHeaderTest.kt:90` | match |
| Drawer width | 240 dp (15 rem) | 240 dp | `LeagueDrawerTest.kt:142` | match |
| Drawer wordmark | 108 dp | 108 dp | `LeagueDrawerTest.kt:147` | match |
| Drawer row | 56 dp | 56 dp | `LeagueDrawerTest.kt:145` | match |
| Pencil / Done | 48 dp | 48 dp | `LeagueDrawerTest.kt:217` | match |
| Leagues heading | 48 dp min-height | 48 dp exact | `LeagueDrawerTest.kt:224` | match (tightened from a floor) |
| My Teams star | 17 dp | 17 dp | `LeagueDrawerTest.kt:355` | match |
| Date chip | 56 × 56 dp | exactly 56 dp | `DateStripBarTest.kt:100` | match |
| Chip spacing | 8 dp (`.dates{gap:.5rem}`) | 8 dp | `DateShelfCalendarTest.kt:89` | match |
| Calendar tile | 56 × 56 dp | 56 dp | `DateShelfCalendarTest.kt:91` | match |
| Shelf height | 1 dp rule + 2 × 8 dp padding + 56 dp controls = 73 dp | 73 dp | `DateShelfCalendarTest.kt:95` | match |
| Sheet top radius | 28 dp (1.75 rem) | 28 dp | `CalendarMonthSheet.kt` | match |
| Sheet day cell | 48 dp | 48 dp | `CalendarMonthSheet.kt` | match |
| Game dot | 4 dp, 85% opacity, `--sig` | 4 dp, 0.85 | `DateShelfCalendarTest.kt:217,224` | match |
| Sheet action button | 56 dp, pill | 56 dp, pill | `CalendarMonthSheet.kt` | match |

## The rail is pinned by the design, not floated by it

An earlier version of this document claimed the opposite and called it a risk: that the design let the rail size to its
content while the app fixed 92 dp, so a three-digit score would clip. That was wrong, and checking it cost nothing.

`.tk{grid-template-columns:minmax(0,1fr) 5.75rem}` (`scores-screen.html:94`) gives the rail a fixed 92 dp, and
`5.125rem` at `:117` gives 82 dp under 380 px. The app implements the design exactly, narrow variant included — which is
why that variant exists at all.

The clipping risk is real but it belongs to the design: a three-digit score or a 2.0 font scale has nowhere to go in the
file either. Widening the rail is therefore a design deviation for Patrick to approve, not a bug to fix quietly.

One correction inside this document: the rail's padding was cited from `:104`, which `:262-263` overrides with
`padding:0 .4375rem 0 .75rem` on an absolutely positioned `.rstate`. The app's 12 dp start and 7 dp end padding match
that rule; its 8 dp top and bottom (`GameRow.kt:276`) do not come from the final CSS.

## Deltas nobody had recorded

- **Navigation bar glyphs.** The design draws dotted lines, a bar chart and a shield (`:447-449`) inside a 44 × 28 dp
  indicator pill (`:148-150`). The app hand-draws its own and its source comment admits the icon set "is not settled".
- **Drag lift.** `.ditem.drag` (`:293`) lifts a dragged drawer row; the app only raises its z-index.
- **Capsule gap.** 5 px in the design (`:111`), 4 dp in the app (`GameRow.kt:359`).
- **My Teams star colour, unverified.** `.ditem .icn` is orange `#FF4B12` at 18 px (`:178`); the app tints the star with
  ink (`LeagueDrawer.kt:326`). Whether the design's star actually uses `.icn` in its markup was not checked.

## Two assertions were weaker than they looked, and are now equalities

The header was asserted `assertHeightIsAtLeast(64.dp)` and the Leagues heading `assertHeightIsAtLeast(48.dp)` — floors
that an accidental 72 dp header or a 56 dp heading would have passed. Both now assert equality against the design's
number, and both pass, which is the useful outcome: the parity claim is now checked rather than approximated.

## Deliberate departures, still in force

1. **No Settings row** in the drawer — there is no Settings screen to open, and the row would be a dead end. Absence is
   asserted in `LeagueDrawerTest`.
2. **No edit hint text** under the leagues list.
3. **NFL and MLB are greyed but arrangeable.** The file uses `.hid` for leagues the user has hidden, which have no
   handle. Ours means "no data yet", so they keep their handles: greying is about data, not about who may order the list.
4. **Type sizes in the rail** sit at 9–12 sp where the file's smallest sizes are 8–9 sp — the accepted legibility
   deviation recorded in the plan, unchanged.

## Open question for Patrick

In the sheet, tapping a day selects it immediately, so `DONE` and dismissing with the scrim have the same effect. The
file shows both `TODAY` and `DONE`, which implies a confirm step. Current behaviour is "pick immediately, dismiss when
done", documented in the composable. Say if you want the sheet to hold a draft selection until `DONE`.
