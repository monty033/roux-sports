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

Each row was checked against the file rather than assumed. The verdict is the honest one, not the hoped-for one.

| Item | The file says | The app does | Verdict |
|---|---|---|---|
| Navigation bar glyphs | 24 × 24 SVG line art at 1.7 px stroke — sliders, a ranking chart, a shield (`:595-597`) inside a 44 × 28 pill (`:148`) | 24 dp hand-drawn **filled** `ImageVector`s — two bars, narrowing rows, four boxes (`RouxNavHost.kt:249`), inside Material 3's stock indicator | **not matched.** The 24 dp box agrees; the artwork does not: fills where the file strokes, four boxes where it draws a shield, and a wider pill. |
| Drag lift | `.ditem.drag` (`:293`) lifts the dragged row: `background:#1C1E24`, `box-shadow:0 14px 26px -10px #000`, `border-radius:.625rem` | `zIndex` plus a `translationY` (`LeagueDrawer.kt:447`) — no shadow, no background, no radius change | **not matched.** The row rises above its neighbours and nothing else happens, so a drag reads as "moved" rather than "picked up". |
| Drawer icon colour | `.drawer .ditem .icn` (`:178`): `#FF4B12`, 18 px, on every row's leading icon | The My Teams star is 17 dp tinted ink; only the edit pencil is 20 dp at `#FF4B12` (`LeagueDrawer.kt:246,356`) | **not matched.** The file's orange-per-row treatment is implemented nowhere; one control happens to be orange. |
| State capsule gap | `.tk .st` `gap:.3125rem` = 5 px (`:110-111`) | `Arrangement.spacedBy(4.dp)` between dot and label (`GameRow.kt:354`) | not matched, by 1 px. The earlier citation for this row pointed at the dot itself, not the gap. |

None of the three not-matched rows above is a regression — they were never built, and no test claimed them. That is the
point of recording them: the rest of this document is a table of things that *were* matched, and a reader could
reasonably have assumed the list was complete.

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

## Settled: the sheet holds a draft

This document used to end with an open question: tapping a day selected it immediately, so `DONE` and the scrim did the
same thing, while the file showed both `TODAY` and `DONE` and implied a confirm step.

Patrick answered it by having the sheet hold a draft. Tapping a day, or `TODAY`, moves the draft only; `DONE` applies
it, and the scrim or back discards it. The two controls now mean different things, which is why the file draws both.
