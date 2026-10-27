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
| State rail, wide | not pinned — content width from `padding:.5rem .4375rem .5rem .625rem` | 92 dp fixed | `GameRowTest.kt:395` | **app pins what the design floats** |
| State rail, narrow (<380 dp) | same rule | 82 dp fixed | `GameRowTest.kt:462` | as above |
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

## Where the app pins what the design leaves to content

**The state rail.** The design gives the rail padding and lets its content set the width; the app fixes 92 dp, or 82 dp
below a 380 dp viewport. That is why the narrow variant exists at all — the app is approximating a measurement it took,
not implementing a rule the file states.

The consequence is a real risk rather than a style preference: a three-digit score, a long state label or a wider font
scale has nowhere to go. The design's rail would grow and push the matchup block narrower; the app's will clip or
overflow. Worth watching when Stage 7 adds more states to that rail. If the rail ever needs to hold something wider,
this is the line that should change from a constant to a weight.

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
