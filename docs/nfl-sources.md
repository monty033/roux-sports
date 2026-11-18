# NFL sources: where each section will come from

NFL work talks to ESPN's public site API. It is undocumented and unversioned: the league publishes nothing and the
endpoints below are the ones a browser uses. That makes this document a contract with an unaccountable party, so every
row records what was actually observed on a date, and the census in Stage 1.3 records what each payload looks like in
each game state.

Authorized by Patrick on 2026-11-18. Hosts and the isolation rule are recorded in `stage-plan.md`. The staged plan that
consumes this document is `nfl-plan.md`.

Probed 2026-11-17 with anonymous `GET`s: no key, no account, no cookies. Sizes are as received. No live game existed at
probe time, so every live shape below is **unverified until Stage 1.2 captures one**.

## The hosts

| host | carries | authorized |
|---|---|---|
| `site.api.espn.com` | scores, standings, teams, rosters, schedules, game summary, team statistics, athlete profile and game log | 2026-11-18 |
| `a.espncdn.com` | team logos (light and dark variants) and player headshots | 2026-11-18 |

`site.web.api.espn.com` is **not** authorized. If a captured payload shows a call that only the web host answers, that
becomes a new decision with its own line here, not an extension of this one.

## The endpoints

| what a screen needs | path | observed 2026-11-17 | carries |
|---|---|---|---|
| scores for a day | `/apis/site/v2/sports/football/nfl/scoreboard`, `?dates=YYYYMMDD` | 200, 153 KB, 14 events | `events[]` with `status.type.{name,state,completed}`, `competitions[]`, week number |
| scores for the season | same, no `dates` | 200 | current week only, not the season |
| team identity | `/apis/site/v2/sports/football/nfl/teams` | 200, 148 KB, 32 teams | `abbreviation`, `displayName`, `color`, `logos[]` (17 variants per team, incl. `dark` and `scoreboard`) |
| standings | `/apis/v2/sports/football/nfl/standings` | 200, 157 KB, 2 conference groups | `children[].standings.entries[]`, 21 `stats` per entry (`wins`, `losses`, `ties`, `winPercent`, `playoffSeed`, `differential`, …) |
| game detail | `/apis/site/v2/sports/football/nfl/summary?event={id}` | 200, 579 KB | `boxscore`, `drives.previous[]` (24 in a final), `scoringPlays[]`, `leaders`, `winprobability`, `injuries`, `odds`, `plays[]` |
| roster | `/apis/site/v2/sports/football/nfl/teams/{abbr}/roster` | 200, 390 KB | `athletes[]` grouped by unit (`offense`, `defense`, …) |
| team schedule | `/apis/site/v2/sports/football/nfl/teams/{abbr}/schedule` | 200, 306 KB, 17 events | `events[]` with status |
| team statistics | `/apis/site/v2/sports/football/nfl/teams/{abbr}/statistics` | 200, 88 KB | `team` + `results` |
| player profile | `/apis/common/v3/sports/football/nfl/athletes/{id}` | 200, 62 KB | `athlete` (incl. `headshot.href`), `season`, `standings` |
| player game log | `/apis/common/v3/sports/football/nfl/athletes/{id}/gamelog` | 200, 70 KB | `seasonTypes[] → categories[] → events[]` with `stats[]` and `labels` |

Absent, so no screen may assume it:

- **A league-leaders endpoint.** `/apis/site/v2/sports/football/nfl/leaders` returns **404**. Leaders exist per game
  (`summary.leaders`) and per team (`teams/{abbr}/statistics`); a league view has to be assembled from those.
- **A bare athlete query.** `/apis/common/v3/sports/football/nfl/athletes` without a path id returns **400**.

## The image host

Every logo and headshot URL in the payloads points at `a.espncdn.com`, with no second image host:

- `https://a.espncdn.com/i/teamlogos/nfl/500/{abbr}.png` — light
- `https://a.espncdn.com/i/teamlogos/nfl/500-dark/{abbr}.png` — dark, which is what the app's dark theme needs
- `https://a.espncdn.com/i/headshots/nfl/players/full/{athleteId}.png`

## What the census must still establish

Filled in by Stage 1.3 from captured fixtures, one table per question, each with the command that produced it. Until
then these are open and no model may be written against a guess:

- The `status.type.name` values actually used across all states, including halftime, end of period, delayed, postponed
  and overtime.
- Where the live situation lives (`competitions[].situation`: `down`, `distance`, `yardLine`, `possession`, `isRedZone`)
  and whether it exists outside an in-progress game.
- `linescores[]` per quarter: presence before kickoff, shape in overtime.
- Which of the 21 standings fields carry ties, win percentage and seeding, and whether a tied record appears as a field
  at zero.
- Roster unit names and position abbreviations.
- Season identification (`season.year` with `season.type` 1/2/3/4) against the NHL's `20262027` string.
- Team abbreviations, confirming the collision list in `nfl-plan.md` §0.1 — ten abbreviations already mean an NHL club.
- Game-log columns per position: a quarterback's line is not a kicker's and neither is a defender's.
- Response headers: `Cache-Control`, `ETag`, and any sign of rate limiting.

The rule that matters more than any row: **pre-kickoff payloads are skeletons.** A parser written against one passes
every test and fails at kickoff, which is exactly how the NHL game page went wrong.
