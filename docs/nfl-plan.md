# NFL parity in Roux Sports — staged implementation plan

Status: PLAN ONLY. Nothing here is built, and the repository was not modified.
Written 2026-11-17 against `main` at `0b5cbe8` (version 2026.11.17.1, code 202611171).
Checkout read: `/var/lib/hermes/workspace/roux-sports` (clean, on `0b5cbe8`).

---

## 0. Where this plan disagrees with, or adds to, the brief

These were read out of the code at `0b5cbe8`. Each one changes the plan, so they come first.

1. **Team identity is abbreviation-only, and ten abbreviations collide.**
   `domain/model/Team.kt`: `value class TeamKey(val abbrev: String)`. Favorites are stored as a string set of bare
   abbreviations (`DataStorePreferencesRepository.kt:97,104`), and every per-team alert switch is keyed by
   `startKey(team)` / `goalsKey(team)` etc. on the same abbreviation (`:139-169`). NHL and ESPN-NFL abbreviations that
   collide: PHI, PIT, BUF, DAL, DET, MIN, SEA, CAR, WSH, CHI (to be confirmed against the captured `teams` payload in
   step 1.3). Starring the Eagles today would star the Flyers. **This is the single largest hidden cost of NFL, and it
   is a storage migration, so it gets its own stage (Stage 2) ahead of any NFL screen.** The brief did not mention it.

2. **The brief says American spellings everywhere; the code does not follow that today.** 164 occurrences of
   `Favourite`/`Colour` in main sources (`PreferencesRepository.getFavourites`, `observeFavourites`,
   `getAccentColor` is American but its doc says "accent colour", `FavouriteStar.kt`, `favouritesOnly`, …). New NFL code
   should use American spellings, but renaming the existing identifiers is a drive-by refactor across storage-facing
   code. Plan: new code is American; the existing names are left alone unless Patrick asks for a separate rename PR
   (decision D9). Persisted DataStore key strings must never be renamed regardless — that would silently drop every
   user's favorites.

3. **The host allowlist is enforced in three places, not one.** `data/net/NhlHttpClientFactory.kt:27` (runtime Ktor
   plugin, throws `HostNotAllowedException` before the engine, including on redirects),
   `domain/src/test/.../NetworkHostAuditTest.kt:15` (scans every `*/src/main` string for hosts), and the league crest
   URLs in `ui/common/LeagueCrest.kt:20-21`. Adding ESPN means touching all three deliberately, and the audit test is
   the thing that proves no other host crept in. Logos are a separate host question: ESPN team logos are served from
   `a.espncdn.com`, not `site.api.espn.com` (to be confirmed from the captured payload). The brief frames the
   authorization as one host; it is at least two.

4. **"Games are per day" is baked into the Scores contract.** `ScoresRepository.loadDay(date, today)`,
   `ScoreDay.dateIndex`, `DateStrip`, `CalendarMonthSheet`, `prefetchAdjacent`, `openingNight` (walks a weekly NHL
   schedule for `gameType 2`). An NFL week has games on 3–4 days and nothing on the others. The plan keeps the per-day
   contract (it works for NFL: `scoreboard?dates=YYYYMMDD`) and adds a week label rather than replacing the date strip
   with a week picker — see S4 and decision D6.

5. **`GameDetail` is three NHL endpoints with five NHL sections hard-coded as an enum** (`GameSection` =
   PeriodScoring, Shots, PowerPlay, Goalies, ThreeStars; `GameDetail(landing, boxscore, rightRail)`). This is the
   screen where a league-agnostic model would cost the most and buy the least. See §2.

6. **The notification ledger and decider are written in hockey terms** (`lastNotifiedGoalCount`, `claimGoal`,
   `claimIntermission(period)`). The logic is actually "score total went up" and "a break was reached", which carries
   over to football, but "goal" on an NFL notification is wrong copy and a field goal plus a safety in one poll window is
   a real case. See Stage 8.

7. **The capture tooling is NHL-only** (`scripts/capture-fixtures.sh` hard-codes `API=https://api-web.nhle.com/v1`)
   and its `gamecenter` mode refuses to run with no live game. The NFL capture gets a sibling mode with the same refusal
   property, not an edit of the NHL one.

Evidence the brief carries that I could not re-check from here: every ESPN row in the brief's table (status codes,
sizes, counts). I took them as verified, as instructed. Nothing in this plan depends on a field the brief did not name
except where marked "confirm in 1.3".

---

## 1. Design decision: shared domain contracts, per-league data adapters, per-league detail models

### Decision

**Generalize identity and the thin, list-shaped models; keep a parallel `data/nfl` package; give game detail, player
pages and club stats their own NFL models behind a league-tagged sealed type.** Not a rewrite of NHL, and not a fully
parallel NFL app either.

Concretely:

- `:domain` gains `League` (`NHL`, `NFL`; `MLB` stays catalog-only), and identity becomes league-qualified:
  `TeamKey(league, abbrev)`, `GameId(league, value)`. Player ids are already `Long`; they gain a league at the
  repository boundary (`PlayerRef(league, id)`), because ESPN and NHL ids are separate numbering spaces.
- Repository interfaces stay the same names and gain a league parameter where the screen is shared
  (`ScoresRepository`, `StandingsRepository`, `ClubScheduleRepository`, `ClubRosterRepository`). The app wires a
  `LeagueRouter` that holds one implementation per league; existing NHL `*RepositoryImpl` classes are unchanged inside
  and become the `NHL` entry.
- `:data` gains `data/nfl/*` (ESPN adapters + DTOs) and `data/net/EspnApi.kt` + a boundary factory. **`data/nhl/*`
  is not touched** except where a model it produces gains a field (league tag).

### Why, per model

| model | today | NFL fit | decision | cost |
|---|---|---|---|---|
| `TeamKey`, `TeamRef` | abbrev only | collides (§0.1) | **generalize** (add league) | storage migration, every call site that constructs a `TeamKey` (~mechanical) |
| `GameId` | opaque string | ESPN event id is a string | **generalize** (add league) | ledger keys, cache keys, nav routes |
| `Game`, `ScoreDay` | mostly league-neutral; `PeriodInfo`, `FinalKind{REG,OT,SO}`, shots, `SeriesStatus` are hockey | scores, state, start time, broadcasts fit; NFL needs down/distance/possession, quarter, no shots, no shootout | **generalize** with a league-specific tail: `Game.situation: GameSituation?` sealed (`Hockey(period, shots)`, `Football(quarter, clock, possession, down, distance, yardLine, redZone)`) | `GameRow` reads the tail; NHL rows render byte-identical (screenshot-proven) |
| `GameState` | NHL codes | ESPN `status.type.name`/`state` (`pre`/`in`/`post`) map onto Scheduled/Pre/Live/Final; postponed/delayed must map, not fall into `Unknown` | **keep**, add an ESPN mapper in `data/nfl` | none to NHL |
| `StandingRow` | `otl`, wildcard sequence, NHL `Conference`/`Division` enums | NFL needs `ties`, win %, `playoffSeed`, AFC/NFC and 8 divisions | **parallel** `NflStandingRow` under a sealed `Standings` result; NHL row untouched | Standings screen gets a second table renderer |
| `ClubRoster` | forwards/defense/goalies | offense/defense/special teams + position codes (QB, WR, …) | **generalize** to `groups: List<RosterGroup(label, players)>`; NHL adapter fills three groups | Roster tab renders groups generically; NHL screenshot must not move |
| `ClubSchedule`/`ClubGame` | season games list | 17 games + bye week | **keep**, add `week: Int?` and a bye marker | small |
| `GameDetail` | 3 NHL endpoints, 5 hockey sections | 1 ESPN `summary` endpoint, different sections | **parallel**: `GameDetail` becomes sealed `HockeyGameDetail` (today's class, renamed only by typealias) / `FootballGameDetail(summary: Loadable<FootballSummary>)` | Game page dispatches on type; NHL composables untouched |
| `PlayerProfile`, `PlayerGame`, `PlayerCareerTable` | skater vs goalie | stat columns depend on position group (passing/rushing/receiving/defense/kicking) | **parallel** `FootballPlayerProfile` with `categories: List<StatCategory(name, columns, rows)>` — ESPN already ships categories, so the table is data-driven | Player page gets a second body |
| `ClubStats`, `LeagueSummary`, `LeagueRanking`, `LeaguePenalties` | NHL stats-host tables | `teams/<abbr>/statistics` has categories; no league leaders feed (404) | **parallel**, data-driven category tables; league ranking computed from 32 per-team statistics calls only if Patrick accepts the cost (D7) | see S7 |
| `PollPolicy`, `FreshnessPolicy`, `NotificationDecider`, `GameWatchScheduler` | NHL constants | same shape, different numbers and copy | **parameterize** by a `LeagueTiming` value; NHL instance = today's constants exactly | Stage 8 |

### Why not fully league-agnostic everywhere

The NHL detail and player models are the most-reviewed code in the repo (five review rounds on poll policy alone, two
corrected-brief rounds on the scoring card). Folding them into a generic "sections" model would re-open settled
behavior to save a second renderer. The screens where both leagues share layout (Scores list, Teams list, Standings
shell, Team page shell, Roster) get shared models; the screens whose content is sport-specific (game body, player stat
tables, team stats) get a sealed type and a second body composable.

### Why not a fully parallel NFL app

Favorites, the drawer, alerts, the date strip, theming and the stale banner are the app. Duplicating them doubles every
future change. And identity must be generalized anyway (§0.1), so the shared shell is the cheap part.

---

## 2. Vertical slice order and why

**First screen: Scores (day list) for NFL, read-only, no notifications.** Reasons:

- It exercises the whole path once: host allowlist → `EspnApi` → cache → adapter → domain `Game` → league routing in
  `ScoresViewModel` (`DEFAULT_LEAGUE = "nhl"`, `selectLeague`) → drawer un-greying → `GameRow`.
- It forces the two decisions everything else depends on: league-qualified identity and `GameState` mapping from ESPN.
- It is the screen with a live state, so it is the first consumer of the live capture (Stage 1).
- It does not need favorites or alerts to be useful, so it can ship before the notification work is settled.

Then: Standings (one request, no live dependence, forces the W-L-T model) → Teams list + Team page shell + Schedule +
Roster (forces favorites migration to be real) → Game detail (largest, needs live fixtures) → Player page → Team stats
/ league stats → Notifications last, because a wrong NFL alert is the most visible failure and it depends on everything
above being correct.

---

## Stages

Conventions for every step:
- One worker session per step, branch `task/nfl-<stage>.<step>`, from the previous step's head if unmerged
  (skill rule), one PR per step unless noted.
- "Gate" = `nix develop --command ./gradlew spotlessCheck lint testDebugUnitTest :domain:test :data:test :app:assembleDebug`, run serially, test count reported.
- "Mutation" = the named test must fail when the named line is reverted/inverted; the worker records it in the PR body.
- Blast radius = what Patrick would see if the step were wrong.

### Stage 0 — Source authorization (gate, no code)

**0.1 Decision packet to Patrick.** A one-page note (in chat, not in the repo) with decisions D1–D4 below and the
recommendation. No implementation step starts until D1 and D2 are answered. If the answer is "no ESPN", the plan stops
here and §9 describes what the alternative costs.

- Proves: Patrick's recorded answer.
- Blast radius: none.

**0.2 Record the authorization in the repo.** Edit `docs/stage-plan.md` (the canonical plan) with a new NFL section:
the authorized hosts, the date, and the endpoint list from the brief. Add `docs/nfl-sources.md` (sibling of
`docs/a6-sources.md`) with the endpoint table, filled in by Stage 1.
- Proves: doc review only.
- Blast radius: none.

### Stage 1 — Fixtures before parsers

**1.1 `scripts/capture-fixtures.sh` gains `nfl-base` and `nfl-live` modes.** Same rollback/refusal design as
`gamecenter`: `nfl-live` refuses to run unless the scoreboard shows at least one event with `status.type.state == "in"`,
and validates each captured file against the shape that makes it worth keeping (a live `summary` must carry a
non-empty `drives` or `situation`, otherwise roll back). Writes to `data/src/test/resources/fx-nfl/<label>/` plus a
`SIZES.txt`. Captures: scoreboard (today + a Sunday + a bye-heavy week), teams, standings, two teams' roster /
schedule / statistics, `summary` for one final and one live game, athlete profile + gamelog for a QB, a WR, a
defender, and a kicker.
- Proves: `scripts/` shellcheck in the gate if present, plus a dry run against a non-game hour that must refuse.
- Blast radius: none (scripts and test resources only).

**1.2 Live capture during a real window.** Runs by hand (Bernie, not a worker) during one of:
Thu 2026-11-19 20:15 ET (IND at HOU), Sun 2026-11-22 13:00 ET (preferred — 11 games, several states at once),
Mon 2026-11-23 20:15 ET. Capture three times per game: pre-kickoff (T-15 min), mid-game (Q2), and halftime; plus
one overtime game if any occurs that weekend. Halftime is the NFL analog of the intermission state that bit NHL.
Commit the fixtures in their own PR with no code.
- Proves: the fixture PR exists and `SIZES.txt` lists a `live` summary whose status is `in`.
- Blast radius: none. **If no live game is captured, no Stage 3+ parser step may start.** The NHL lesson: pre-game
  payloads are skeletons, and a parser written against them passes every test and fails at kickoff.

**1.3 Shape census (read-only, written into `docs/nfl-sources.md`).** For every fixture, per state, count field
presence the way the skill requires (`{(state, hasX): count}`), print values, not keys. Questions this must answer
before any model is written:
- `status.type.name` values seen across all states (incl. halftime, end of period, delayed, postponed, OT).
- Where the live situation lives (`competitions[].situation`: `down`, `distance`, `yardLine`, `possession`,
  `isRedZone`, `downDistanceText`) and whether it exists outside `in`.
- `linescores[]` per quarter: presence pre-game, shape in OT.
- Standings: which of the 21 stat fields carry `W-L-T`, `ties`, win %, `playoffSeed`, `clincher`; whether a tie
  appears as a field even at zero.
- Roster: unit names (`offense`, `defense`, `specialTeam`?), position abbreviations, jersey presence.
- Season id format (`season.year`, `season.type` 1/2/3/4 = pre/reg/post/off) vs NHL `20262027`.
- Logo URL host(s) in `teams` (expected `a.espncdn.com`; confirm) and whether dark variants exist.
- Team abbreviations — confirm the collision list in §0.1.
- Athlete gamelog `categories` per position (QB vs kicker vs defender) — columns and labels.
- `summary.leaders` and `teams/<abbr>/statistics` — what a league-leaders view could be built from.
- Response headers: caching (`Cache-Control`, `ETag`), and whether anything resembling rate limiting appears.
- Proves: the census tables in the doc, each with the command that produced it.
- Blast radius: none; but a wrong census becomes a wrong model, so the reviewer re-runs two of the counts.

### Stage 2 — League-qualified identity (NHL-only behavior change: none)

This stage ships with no NFL visible. It is a storage migration, so it is isolated (skill: isolate storage, migration
and boundary changes from UI work).

**2.1 `League` and qualified keys in `:domain`.** Add `enum class League { NHL, NFL }` with stable ids matching
`LeagueOrder.canonical` (`"nhl"`, `"nfl"`). `TeamKey(league, abbrev)` and `GameId(league, value)`. Constructors
used by NHL adapters pass `League.NHL`. `LeagueOrder` keeps string ids (persisted) and gains a mapping to `League`.
- Files: `domain/model/Team.kt`, `Game.kt`, `domain/policy/LeagueOrder.kt`, all NHL adapter call sites.
- Proves: new `TeamKeyTest` (`TeamKey(NHL,"PHI") != TeamKey(NFL,"PHI")`), existing suite green, screenshot matrix
  unchanged byte-for-byte.
- Blast radius: compile-wide; if wrong, NHL favorites/alerts disappear (caught by 2.2's tests).

**2.2 Persisted-key migration in `DataStorePreferencesRepository`.** Legacy unqualified entries are read as NHL. New
writes store `nhl:PHI` / `nfl:PHI`. Alert-switch keys, upcoming-lead keys and ledger keys get the same prefix rule.
Migration is read-tolerant (old and new forms both read) and write-forward (writes only new form); the file's existing
version marker is bumped so an older build refuses to write (the repo already has this `isWritable` mechanism).
Ledger game ids: prefix `nfl:` for NFL; NHL ids read unprefixed as NHL.
- Proves: `DataStorePreferencesMigrationTest` with a fixture DataStore written by the current schema (favorites
  `{PHI, CAR}`, alerts on PHI, a ledger row) → after upgrade, `getFavorites()` = `{nhl:PHI, nhl:CAR}`, alerts still on
  for `nhl:PHI`, `nfl:PHI` has none, ledger row still found. Mutation: drop the legacy-read branch → test fails.
- Blast radius: **high** — a slip silently un-stars every team and turns alerts off. Ships alone, device-tested on
  Patrick's phone over the existing install (upgrade, not clean install) before merge.

**2.3 Cache keys and nav routes carry the league.** `ResponseCache.CacheKey` kinds gain an NFL family (separate
prefixes so eviction budgets don't cross), and nav routes encode `league` in team/game/player arguments.
- Proves: `ResponseCacheTest` cases for colliding ids across leagues; `ShellTest` route round-trip.
- Blast radius: medium — a wrong route opens the Flyers page from an Eagles row.

### Stage 3 — Network boundary and the first NFL read

**3.1 `EspnHttpClientFactory` + `EspnApi`.** A second boundary plugin with its own allowlist
(`site.api.espn.com`, plus the logo host if D3 allows), same `RateLimited` / 429 handling, same User-Agent, same
`followRedirects` + `SendingRequest` host check. Do not merge allowlists into one set: a per-client allowlist means the
NHL client can never be redirected to ESPN and vice versa. `NetworkHostAuditTest.allowedHosts` gains the new hosts
with the authorization date in its doc comment.
- Files: `data/net/EspnHttpClientFactory.kt`, `data/net/EspnApi.kt`, `domain/src/test/.../NetworkHostAuditTest.kt`.
- Proves: `EspnHttpClientFactoryTest` (MockEngine): espn host passes; `api-web.nhle.com` is **blocked** on the ESPN
  client; a redirect to an unlisted host throws before the engine. Audit test still rejects `site.api.espn.com.evil.test`.
- Blast radius: none visible; a slip here is a privacy failure (Patrick's hard rule), which is why it has its own PR.

**3.2 `NflScoreboardAdapter` + `NflScoresRepository`.** Maps `scoreboard?dates=` to `ScoreDay`: `Game` with
`League.NFL` ids, ESPN status → `GameState`, scores, `startTimeUtc`, broadcasts, and `GameSituation.Football`. Date
index comes from the scoreboard's `leagues[].calendar` if the census shows it carries week date ranges; otherwise from
the week's own event dates.
- Proves: `NflScoreboardAdapterTest` over the Stage 1 fixtures, one case per state seen in the census (scheduled,
  pre, live, halftime, final, final/OT, postponed if captured). Census-driven: one assertion per distinct
  `status.type.name`. Mutation: map halftime to `Final` → poll-policy test in Stage 8 and this test fail.
- Blast radius: none until 3.3 exposes it.

**3.3 Scores screen shows NFL.** Drawer enables NFL (`LeagueDrawer.kt:379` `false` → `true`), `ScoresViewModel`
routes the selected league to the right repository, `GameRow` renders the football tail. **Scores mapping:**
- Period → quarter: "Q1–Q4", "OT"; "Half" at halftime; "End Q1/Q3" at quarter breaks if the census shows them.
- Shots column → for a live game, possession + down & distance ("BAL ● 3rd & 7 at PIT 34"), red-zone accent;
  for an upcoming game, broadcasts (same as NHL).
- `FinalKind` REG/OT/SO → "Final" / "Final/OT"; no shootout. Ties: "Final" with equal scores (a real NFL outcome).
- Series status (NHL playoffs) → nothing; NFL playoff round label from the event's `season.type`/notes if present.
- League crest URL for NFL in `LeagueCrest.kt` (ESPN crest from the logo host, or none if D3 says no).
- Week label in the header ("Week 11") derived from the scoreboard's `week.number`.
- Proves: new `ScreenshotMatrixTest` rows for NFL (see §6), `ScoresViewModelTest` for league switching, `GameRowTest`
  for each football state. NHL matrix images unchanged.
- Blast radius: high visibility; the NHL Scores screen is the most-used screen, and a routing slip shows NFL games under
  NHL. Device check on Patrick's phone during the Sun 2026-11-22 window if timing allows; otherwise the following
  Thursday.

**3.4 NFL date strip / calendar behavior (D6).** NFL days without games: the strip skips to game days (Thu, Sun, Mon,
plus Saturdays late season and international games) using the date index; `CalendarMonthSheet` dims non-game days
(it already reads per-day counts). `openingNight` for NFL: first `season.type == 2` event, from the scoreboard
calendar.
- Proves: `DateStripTest` NFL cases (Tuesday opens on the next game day, not an empty day), `CalendarMonthSheet`
  screenshot row.
- Blast radius: medium — an empty Tuesday is the most likely "it's broken" report.

### Stage 4 — Standings

**4.1 `NflStandingsAdapter` + sealed standings result.** `NflStandingRow(team, w, l, t, pct, pf, pa, diff,
streak, divisionRecord, conferenceRecord, playoffSeed, clincher)`, conference AFC/NFC, divisions as feed strings
preserved (same `Unknown` pattern as NHL). Win % taken from the feed if present, else computed as
`(w + t/2) / gp` — the census in 1.3 decides which, and the test pins the fixture value.
- Proves: `NflStandingsAdapterTest` (32 rows, 8 divisions of 4, AFC/NFC split 16/16, a team with a tie if one exists in
  the fixture, else a synthetic tie case clearly labeled synthetic).
- Blast radius: none until 4.2.

**4.2 Standings screen for NFL.** **Mapping:** NHL Division / Wild Card / League tabs → NFL Division / Conference
(playoff picture: seeds 1–7, with the seed-7 line where NHL draws the wildcard cutoff) / League. Columns: W, L, T, PCT,
PF, PA, DIFF, STRK. Points column does not exist in NFL; it is removed, not zero-filled. Record format `W-L-T`; the T is
always shown in the NFL table (an NFL record without T is ambiguous late season). Clinch markers (`x`, `y`, `z`, `*`,
`e`) from `clincher` if the census shows it.
`StandingsGrouping` gets an NFL implementation; NHL grouping untouched.
- Proves: matrix rows (NFL, three tabs, light/dark), `StandingsGroupingTest` NFL cases, width-fit check per
  `references/stat-tables-and-audit-coverage.md` (8 numeric columns on a 360 dp phone).
- Blast radius: medium; wrong seeds are a correctness complaint, not a crash.

### Stage 5 — Teams list and Team page shell, Schedule, Roster

**5.1 Teams list for NFL.** From `teams` (32). Star and bell work through the qualified keys from Stage 2.
Mapping: grouped by division, full name with city (current settled label).
- Proves: `TeamsScreenTest` (NFL tab, star an NFL team → NHL team with same abbrev not starred), screenshot row.
- Blast radius: high — this is where the §0.1 collision would surface.

**5.2 Team page shell + Schedule tab.** `teams/<abbr>/schedule`. **Mapping:** NHL season id `20262027` → NFL
`season.year` + `season.type` (the app keeps one `SeasonId` value per league, formatted "2026" for NFL vs "2026–27"
for NHL); 17 games + a bye row ("Week 9 — Bye"), week number column, `W 24–17`/`L`/`T` result; preseason and playoffs
as separate sections the way NHL splits game types. `ClubGameStatus` gets football states.
- Proves: `NflClubScheduleAdapterTest` (17 regular-season games and exactly one bye week inferred from the gap in
  `week.number`), screenshot row.
- Blast radius: low-medium.

**5.3 Roster tab.** `ClubRoster` generalized to groups (§1); NHL adapter emits Forwards/Defense/Goalies in the same
order (screenshot unchanged). NFL groups from ESPN units: Offense / Defense / Special Teams, sorted by position group
then jersey. Jersey absent → blank, never invented (existing rule).
- Proves: `RosterAdapterTest` (NHL unchanged), `NflRosterAdapterTest`, NHL roster screenshot byte-identical.
- Blast radius: medium — the NHL roster is the change risk, hence the byte-identical image requirement.

### Stage 6 — Game detail (largest; live fixtures required)

**6.1 `FootballSummary` model + adapter from `summary?event=`.** One endpoint, so one `Loadable`, but sections are
still decided by payload presence (the NHL rule). Sections, in page order:
1. Header/hero: score, quarter + clock, possession, down & distance, venue, broadcasts, records (W-L-T).
2. **Scoring by quarter** (`linescores`) — NHL period table analog; "OT" column only when played.
3. **Scoring summary** (`scoringPlays`) — NHL scoring card analog. Columns: quarter, clock (printed verbatim from the
   feed, per Patrick's NHL ruling — no conversion), team crest, play text, running score (away left, home right).
   Ordering derived from the payload, proven monotone in the fixture the way the NHL card was.
4. **Drives** (`drives`) — no NHL analog. Collapsed list: team, result (TD/FG/Punt/Turnover), plays-yards-time.
   Candidate to defer (D8).
5. **Team stats** (`boxscore.teams[].statistics`) — NHL Shots + Power Play analogs collapse into one comparison
   table: total yards, passing, rushing, first downs, third-down eff, turnovers, penalties-yards, time of possession.
   Values that are strings ("5-12", "31:04") stay strings — the NHL `powerPlay "0/1"` trap, again.
6. **Player stats** (`boxscore.players[]` categories) — NHL Goalies analog: passing / rushing / receiving / defense /
   kicking tables per team, category columns taken from the feed's `labels`.
7. **Leaders** (`leaders`) — NHL Three Stars analog: passing/rushing/receiving leader per team. Not "three stars";
   no invented ranking.
8. Not shown: `winprobability`, `odds` (gambling content; recommend omit — D10), `injuries` (deferred, §7).
**NHL → NFL mapping for this screen:** periods → quarters; penalties card → penalties appear only as the team-stats
row "Penalties 6-45" (NFL penalties are not discrete in the summary the way NHL penalties are; confirm in census);
goalies → none; three stars → leaders; power play → third-down/red-zone efficiency; shots → total yards.
- Proves: `NflSummaryAdapterTest` over final + live + halftime fixtures, section-presence table per state (the
  pre-game skeleton must produce zero body sections and no error), census-driven assertions only.
- Blast radius: none until 6.2.

**6.2 Game page renders `FootballGameDetail`.** `GameDetailScreen` dispatches on the sealed type; NHL path is
untouched. `FreshnessPolicy.detailMaxAge` per league (§5).
- Proves: matrix rows: NFL pre, live, halftime, final, OT final; `GameDetailViewModelTest` NFL cases; NHL detail
  images byte-identical.
- Blast radius: high — the page Patrick opens during a game. Device check required during a live window; a render
  from fixtures is not product evidence (skill rule).

Split: 6.1/6.2 will exceed one session. Plan for 6.1a (hero + linescore + scoring summary), 6.1b (team + player stats),
6.1c (leaders + drives), each with its renderer, each its own PR.

### Stage 7 — Player page, team stats, league stats

**7.1 Player page.** `athletes/<id>` + `athletes/<id>/gamelog`. **Mapping:** skater vs goalie → position group
(QB, RB, WR/TE, OL (no stats), defense, K/P). Career table and game log are built from the feed's `categories` with
its own column labels, not hand-typed column lists, so a kicker's log is FG/XP columns without new code. Headshot URL
from the feed (logo host question applies: D3). Season id handling from 5.2.
- Proves: `NflPlayerProfileAdapterTest` across the four captured positions, census of `categories` per position;
  `PlayerTablesScreenshotTest` NFL rows incl. width-fit for the QB table (widest).
- Blast radius: medium.

**7.2 Team Stats tab.** `teams/<abbr>/statistics` → category tables (passing, rushing, receiving, defense, kicking,
returns) for the club's players, and the team totals.
- Proves: adapter test, screenshot row.
- Blast radius: low.

**7.3 League rankings on the team page (D7).** NHL's `LeagueRanking` comes from one league-wide stats-host table. ESPN
has no leaders feed (404). Options: (a) build rankings from 32 `statistics` calls, cached 24 h, fetched only when the tab
is opened — 32 × ~88 KB ≈ 2.8 MB per refresh; (b) rank only what `standings` carries (PF, PA, differential,
win %) — one request, already cached; (c) skip. Recommend (b).
- Proves: `LeagueRankingTest` NFL cases.
- Blast radius: low.

### Stage 8 — Polling, freshness, notifications for a weekly league

See §5 for the policy. Steps:

**8.1 `LeagueTiming` value + NHL instance equal to today's constants.** Pure refactor; the NHL `PollPolicyTest`,
`FreshnessPolicyTest` and `NotificationDeciderTest` stay green unedited — that is the proof the refactor changed
nothing.
**8.2 NFL timing instance + tests** (numbers in §5).
**8.3 NFL notifications:** the decider reuses the score-total-went-up logic; copy says "Score" with the play type when
the feed supplies it ("Touchdown, Eagles — PHI 14, DAL 10"), and a jump of more than one scoring play between polls is
one alert stating the new score, not several guesses. Intermission → halftime only (not every quarter break) unless D5
says otherwise. Ledger keys `nfl:<eventId>`. The `goals` switch is labeled "Scores" in the NFL alert sheet; the stored
key stays the same name.
**8.4 `GameWatchReconciler` reads both leagues' slates for today** (it currently reads one `ScoresRepository.loadDay`).
On a non-NFL day the NFL slate is empty and costs one cached request.
- Proves: `NotificationDeciderTest` NFL cases (TD+XP in one poll = one alert with total 7; FG then safety; halftime once;
  OT announced as OT start, not a third "half"); `GameWatchSchedulerTest` for a Sunday with 11 kickoffs at 13:00 (one
  watch per followed game, no duplicates); mutation: drop the league prefix from the ledger key → the cross-league
  collision test fails.
- Blast radius: **highest of the plan** — a duplicate or phantom alert on a Sunday is immediately visible and erodes
  trust in NHL alerts too. Ships last, alerts default off (existing rule), device-tested across a full Sunday window.

### Stage 9 — Docs and release

**9.1 Docs:** `docs/nfl-sources.md` complete, README/About list ESPN as a data source with the "unofficial" caveat,
F-Droid metadata anti-feature check (D4). **9.2** No pipeline change: NFL ships in an ordinary dated release cut from
`main`, as now.

---

## 5. Polling / freshness / notification policy (weekly league)

The NHL rules (memory: S2.1, five review rounds) are kept as the structure; only the numbers and the evidence fields
change. Every number below is a proposal to confirm against the live capture.

| | NHL today | NFL proposal | why |
|---|---|---|---|
| live poll | 25 s | 30 s | NFL scoring is rarer than goals per minute of wall clock; ESPN payload (153 KB scoreboard) is larger than NHL score |
| warming window | start −9 min … +20 min | start −10 min … +25 min | kickoff follows anthem/coin toss; same idea |
| plausible game length (Unknown state) | 4 h | 4 h 30 min | NFL games run ~3 h 10 min, OT and weather delays push past 4 h |
| idle on a game day (viewing today) | 5 min | 5 min | same |
| no game today | idle 5 min / future day 6 h | next-kickoff-aware: cache valid until min(6 h, next kickoff −1 min) | Tue/Wed should make ~zero requests |
| live detail | 30 s | 30 s | `summary` is 579 KB — **watch data cost**; see below |
| final detail | 24 h | 24 h | same |
| standings live / idle | 10 min / 1 h | 30 min / 6 h | NFL standings change only when a game ends, max ~3 days a week |
| roster / schedule / club stats | 6 h | 12 h / 6 h / 12 h | weekly sport; roster moves cluster on Tue/Wed |
| player profile / gamelog | 6 h / 1 h | 12 h / 6 h | totals move once a week |
| terminal-state evidence | `Final` or `finalKind != null` | `status.type.completed == true` or state `post` | census decides which is reliable across every final in the fixture — the NHL defect #2/#4 lesson |
| live evidence | `clock.running` | state `in` and not halftime; census decides | same lesson: do not assert what a field implies, count it |

Data cost note: a live NFL game page at 30 s fetches ~579 KB per poll ≈ 70 MB per game hour. Proposal: when the page is
open, poll `summary` at 30 s; the background watcher never reads `summary`, only the scoreboard (153 KB), and only
while a followed game is live. If the census shows `summary` honors `ETag`/`If-None-Match`, use it.

Notifications: Sunday 13:00 ET puts up to 11 games live at once. The scheduler already polls a slate, not a game,
when it can; the NFL watcher must poll **one scoreboard for all followed live games**, not one request per game, or a
user following four teams makes four 153 KB requests every 30 s.

---

## 6. Gate, screenshots, release — per stage

| stage | gate impact | screenshots | release impact |
|---|---|---|---|
| 0, 1 | none (docs, fixtures) | none | none |
| 2 | migration tests added | **zero images may change** — any diff fails the PR | can ship alone; it is the riskiest NHL-visible change |
| 3 | boundary + adapter tests | new NFL rows only | first release with NFL Scores (decide if NFL ships "preview" behind the drawer, D11) |
| 4–7 | adapter + VM tests per screen | new NFL rows; NHL rows unchanged except where 5.3 genericizes roster (must be byte-identical) | each can ship in an ordinary release |
| 8 | NHL policy tests unedited + NFL cases | alert-sheet NFL row | ship only after a full Sunday on device |
| 9 | none | none | ordinary release; pipeline untouched |

How screenshots are kept from hiding regressions:
1. **New-league rows are additive files** under `app/src/test/screenshots/matrix/nfl-*`; an NFL PR must show
   `git diff --stat` touching only new paths under `screenshots/`. Any modified NHL image in an NFL PR is a review
   blocker unless the PR's stated purpose is that change (only 5.3 and 3.3's header may legitimately touch NHL frames,
   and both must be byte-identical by design).
2. Re-records happen in a commit by themselves, named for the images they change, so the reviewer sees the image list
   in the commit, not buried in a code diff.
3. NFL rows are rendered from the Stage 1 fixtures, never from hand-built models, so a re-record cannot quietly bless
   an adapter bug.
4. Roborazzi verify stays on (`roborazzi.test.verify=true` in `gradle.properties`); no step may flip it.
5. Matrix growth: roughly +40 images (Scores 6 states, Standings 3 tabs, Teams, Team page 4 tabs, Game 5 states,
   Player 3 positions, alerts sheet — each light/dark). Watch gate time; if it grows more than ~25%, split the NFL
   matrix into its own test class so it can be run alone while iterating (still in the gate).

---

## 7. Deliberately not built, and what breaks if it never is

| not built | why | consequence if never built |
|---|---|---|
| Win probability, odds | gambling-adjacent; Patrick's no-noise product | none; some fans expect a win-probability chart |
| Injuries report | feed carries it, but it changes constantly and is partial | Team page has no injury list; users go elsewhere on Wednesdays |
| Play-by-play | `summary` has drives, not every play, at the volume we fetch | no play-by-play; drives (if built) cover most of it |
| League leaders page | no feed (404); computing it is 32 requests | no "who leads the league in rushing"; per-team stats and game leaders still shown |
| Playoff bracket view | NFL bracket is seeded, single-elimination; NHL `PlayoffSeries` does not fit | January: playoff games show as games with a round label; no bracket picture. **Build before the season's wildcard weekend if Patrick wants it** (D12) |
| Fantasy stats, pick'em | out of scope | none |
| MLB | catalog only | drawer keeps MLB greyed, as today |
| Rename of `Favourite`/`Colour` identifiers | not part of NFL | the house spelling rule stays unmet in old code; persisted key strings must never change anyway |
| Offline logos bundling | trademark + F-Droid NonFreeAssets | logos depend on the logo host authorization (D3); without it, NFL rows show abbreviations only |

---

## 8. Risk register

| risk | likelihood | mitigation |
|---|---|---|
| ESPN changes shape | medium over a season | adapters drop malformed events and count them (`droppedCount`, as NHL); a fixture-drift check script run monthly; NHL unaffected because the clients are separate |
| ESPN blocks or rate-limits the app | low–medium | per-league failure stays inside the NFL `Loadable`; NHL screens never wait on ESPN; a 429 uses the existing `RateLimited` backoff; kill switch = drawer greys NFL out via a build flag in the next release |
| Live shape differs from captured | high on first Sunday | Stage 1.2 captures it; 3.3/6.2/8.3 each need a live-window device check before merge |
| Identity migration loses favorites | low with tests, catastrophic if it happens | Stage 2 alone, upgrade test on device |
| Wrong census claim becomes a model field | the repo's history says high | census commands committed in the doc; reviewer re-runs two counts per step |
| Gate time growth | medium | split NFL matrix class (§6.5) |

---

## 9. If Patrick says no to ESPN

Paid official feeds (Sportradar, SportsDataIO, Genius) require an account and an API key in the app or a relay. Either
ships a secret in a GPL APK (extractable) or adds a server Patrick runs, which turns a client app into a service with
uptime and cost. Both conflict with "no account, no backend" more than an unofficial anonymous GET does. If ESPN is
refused, my recommendation is to pause NFL rather than add a relay.

---

## Decisions needed from Patrick

D1. Data source: unofficial ESPN site API vs a paid official feed — recommend ESPN (anonymous, no key, no backend), accepting it can change without notice.
D2. Authorize `site.api.espn.com` (and `site.web.api.espn.com` only if the census shows athlete calls need it) — recommend yes, NFL client only, never the NHL client.
D3. Authorize the logo/headshot host (expected `a.espncdn.com`) — recommend yes; without it NFL shows text abbreviations and no headshots.
D4. Second host for a user-visible feature, plus README/About/F-Droid disclosure of an unofficial source — recommend yes, disclosed in About and README.
D5. Football alerts: score alerts per scoring play, halftime only (not every quarter break), final — recommend exactly that.
D6. Scores navigation stays per day with a "Week N" label, skipping non-game days — recommend yes over a week picker.
D7. Team-page league rankings: standings-derived only (one request) vs 32-request full rankings — recommend standings-derived.
D8. Drives section in game detail — recommend build it, after scoring/stats/leaders.
D9. Rename existing British-spelled identifiers to American in a separate PR — recommend no for now; new code American, persisted keys never renamed.
D10. Omit odds and win probability — recommend omit.
D11. Ship NFL Scores as soon as Stage 3 is done, or hold NFL until Stages 3–7 are all in — recommend ship Scores + Standings first, drawer label unchanged.
D12. Playoff bracket view before January — recommend defer; round labels on games are enough for this season.
D13. Live capture window — recommend Sunday 2026-11-22 13:00 ET, with Thursday 2026-11-19 as the fallback dry run of the capture script.
