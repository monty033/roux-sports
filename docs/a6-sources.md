# A6 sources: where each game-page section comes from

The game page is built from three endpoints, not one, because they fail separately and carry different halves of the
same story. This document records which field feeds which section, what the field looks like in each game state, and the
shapes that will bite a parser. It is the mapping G1 asked for: every section has a source, none is a guess.

Captured with `scripts/capture-fixtures.sh gamecenter`, which writes `gc-final-*` and `gc-live-*` into
`data/src/test/resources/fx/<label>/`. Prices of the capture are in `SIZES.txt` beside them.

## The three endpoints

| endpoint | path | carries |
|---|---|---|
| landing | `/v1/gamecenter/{id}/landing` | teams, score, `summary` (scoring, three stars, penalties), `clock` |
| boxscore | `/v1/gamecenter/{id}/boxscore` | teams, score, `playerByGameStats` (forwards, defense, goalies), `periodDescriptor`, `clock` |
| right rail | `/v1/gamecenter/{id}/right-rail` | `linescore`, `shotsByPeriod`, `teamGameStats`, season series, game info |

## Section sources

| section | endpoint | JSON path | notes |
|---|---|---|---|
| Period scoring | landing | `summary.scoring[]` → `periodDescriptor.{number,periodType}` + `goals[]` | one entry per period played, in order; `periodType` is `REG`, `OT` or `SO`, so a header printing "Period 4" is wrong |
| Shots | right rail | `shotsByPeriod[]` → `{periodDescriptor, away, home}`, and `teamGameStats[category=sog]` | **two sources for one number**; `shotsByPeriod` is per period, `sog` is the total. Pick one authority per row and prove they agree, or the same screen shows two different shot counts |
| Power play | right rail | `teamGameStats[category=powerPlay]` → `"0/1"`, `powerPlayPctg` → `0.0` | **the value is a string**, not a number, and it sits in an array whose other members are numbers |
| Goalies | boxscore | `playerByGameStats.{away,home}Team.goalies[]` | **includes the backup**, who reads `toi 00:00, saves 0, shotsAgainst 0, goalsAgainst 0`; `decision` (`W`/`O`) exists only for the goalie of record, and `starter` is the reliable filter |
| Three stars | landing | `summary.threeStars[]` — exactly 3 | `star`, `playerId`, `teamAbbrev`, `name.default`, `sweaterNo`, `position`, `goals`, `assists`, `points` |

## What each state actually contains

The sections are not present early. A page for a game that has not started is not an error or a fetch failure — it is
the normal state for most of the day.

| | FUT (not started) | OFF / FINAL | LIVE |
|---|---|---|---|
| landing | `matchup` (season comparisons) instead of `summary` | `summary.scoring`, `summary.threeStars`, `summary.penalties`, `clock` | sampled during a game; see the `gc-live-*` fixtures |
| boxscore | **no** `playerByGameStats`, no `clock`, no `periodDescriptor` — 1.3 KB of team headers | full `playerByGameStats`, `periodDescriptor`, `clock` | sampled during a game; see the `gc-live-*` fixtures |
| right rail | `teamSeasonStats`, `last10Record` instead of `linescore`, `shotsByPeriod`, `teamGameStats` | `linescore`, `shotsByPeriod`, `teamGameStats`, `gameVideo`, `gameReports` | sampled during a game; see the `gc-live-*` fixtures |

So section presence is decided by **payload**, not by game state and not by the freshness of the HTTP response. A
payload with no `summary` has no scoring section; a fresh response does not conjure one.

## Shapes that will bite

1. **`teamGameStats` mixes types.** Of ten categories in the captured final, two carry strings: `powerPlay` (`"0/1"`)
   and `faceoffWins` (`"28/50"`). The rest carry numbers. A strict numeric parser fails on exactly the stats a hockey
   fan looks for first.
2. **`linescore` is an object, not an array.** `{byPeriod: [...], totals: {away, home}}`. `totals` independently
   reproduces the final score, which makes it a free cross-check against the scores on landing and boxscore.
3. **The backup goalie wears a shutout.** In the captured final, the home team's backup reads `toi 00:00, saves 0,
   shotsAgainst 0, goalsAgainst 0`, with no `decision`. Rendering every element of `goalies[]` prints a fake shutout
   beside the real line.
4. **Shots are stated twice.** `shotsByPeriod` and `teamGameStats[sog]` both answer "how many shots", and nothing forces
   them to agree after a correction upstream. Choose one, and assert the other agrees rather than displaying both.
5. **Periods are typed.** `periodDescriptor.periodType` distinguishes `REG` from `OT`/`SO`; the number alone is
   ambiguous once a game goes past 60 minutes.

## Recapturing

```
scripts/capture-fixtures.sh gamecenter                  # picks the final from the pinned game day, the live one from score/now
scripts/capture-fixtures.sh gamecenter --final ID --live ID
```

The mode refuses to run when no game is in progress, and each file is validated against the shape that makes it worth
keeping — a finished game passed as `--live` fails the landing check and the whole capture rolls back, leaving the
existing corpus untouched. That refusal is the point: a corpus that never sampled LIVE would let a parser test pass
against states it never saw.

Recapturing produces a diff. Read it before committing: the fixtures are a point-in-time snapshot, and a game in
progress advances between requests, so the three live files are not mutually consistent by construction.
