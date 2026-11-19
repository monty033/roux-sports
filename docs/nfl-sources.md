# NFL sources: where each section will come from

NFL work talks to ESPN's public site API. It is undocumented and unversioned: the league publishes nothing and the
endpoints below are the ones a browser uses. That makes this document a contract with an unaccountable party, so every
row records what was actually observed on a date, and the census in Stage 1.3 records what each payload looks like in
each game state.

Authorized by Patrick on 2026-11-18. Hosts and the isolation rule are recorded in `stage-plan.md`. The staged plan that
consumes this document is `nfl-plan.md`.

Probed 2026-11-17 with anonymous `GET`s: no key, no account, no cookies. Sizes are as received. No live game existed at
probe time, so every live shape below is **unverified until Stage 1.2 captures one**.

Captured with `scripts/capture-fixtures.sh nfl-base`, which writes the `nfl-*` fixtures and a `SIZES.txt` into
`data/src/test/resources/fx-nfl/<label>/`. `nfl-live` adds a scoreboard and a summary taken while a game is being
played, and refuses to run when nothing is in progress.

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
- **A game log for a player who has not played.** The game log answers with `{"filters": [...]}` and nothing else —
  no `seasonTypes`, no columns. Confirmed on a defensive end the roster lists. The player page must render that as
  "no games yet", not as a parse failure, and the capture keeps one such payload on purpose.

## The image host

Every logo and headshot URL in the payloads points at `a.espncdn.com`, with no second image host:

- `https://a.espncdn.com/i/teamlogos/nfl/500/{abbr}.png` — light
- `https://a.espncdn.com/i/teamlogos/nfl/500-dark/{abbr}.png` — dark, which is what the app's dark theme needs
- `https://a.espncdn.com/i/headshots/nfl/players/full/{athleteId}.png`

## Stage 1.3a census — committed scoreboard corpus

Read-only census of the four committed scoreboard fixtures in `data/src/test/resources/fx-nfl/2026-11-18/`
(capture label 2026-11-18). Every number below is recomputed from those bytes. The test
`data/src/test/kotlin/net/montycasa/roux/data/nfl/NflScoreboardCensusTest.kt` guards the status triples, the
abbreviation sets, the logo host sets, the calendar rows, the late-game ids and dates, and the tie counts: changing one
of those by one fails that test. The other counts (linescores, notes, broadcasts, week and season values, the
`"status":{` totals, and the logo `href` counts) are recorded from the same bytes but have no test literal yet. No parser and no domain model are involved —
this records what the bytes contain, not how to read them. The fixtures are single-line JSON, so each command below
runs from the fixture directory and uses `grep -oP` plus `wc -l` / `sort` / `uniq` / `paste`.

### Requested days and event counts

| fixture | request (from `SIZES.txt`) | events |
|---|---|---|
| `nfl-scoreboard-sunday-a.json` | `?dates=20261115` | 12 |
| `nfl-scoreboard-sunday-b.json` | `?dates=20261108` | 13 |
| `nfl-scoreboard-today.json` | no `dates` (current week) | 13 |
| `nfl-scoreboard-emptyday.json` | `?dates=20261117` | 0 |

Command: `grep -o '"uid":"s:20~l:28~e:[0-9]*"' <file> | wc -l`

### 1. `{(status.type.name, state, completed): n}` — `events[].status.type`

| fixture | counts |
|---|---|
| sunday-a | `{(STATUS_FINAL, post, true): 12}` |
| sunday-b | `{(STATUS_FINAL, post, true): 13}` |
| today | `{(STATUS_SCHEDULED, pre, false): 13}` |
| emptyday | `{}` |

Command: `grep -oP '"status":\{.*?"name":"\K[^"]+(?=","state")' <file> | sort | uniq -c` (and the same expression
with `state` and `completed` as the captured group). Counting every `"status":{` occurrence gives 24 / 26 / 26 / 0 —
exactly double the event counts, because `competitions[].status.type` repeats the same triple. Only `pre` and `post`
appear in this corpus; halftime, end-of-period, delayed, postponed and in-progress remain unverified until the live
capture lands.

### 2. Field presence

| field | where | sunday-a | sunday-b | today | emptyday |
|---|---|---|---|---|---|
| `situation` | `competitions[].situation` | no | no | no | no |
| `linescores` | `competitions[].competitors[].linescores` | yes (24 = 12 × 2) | yes (26 = 13 × 2) | no | no |
| `week.number` | `events[].week.number` | yes (10) | yes (9) | yes (11) | no |
| `season.type` | `events[].season.type` | yes (`2`, regular season) | yes (`2`) | yes (`2`) | no |
| `notes` | `events[].competitions[].notes` | yes (12) | yes (13) | yes (13) | no |
| `broadcasts` | `events[].competitions[].broadcasts` | yes (12) | yes (13) | yes (13) | no |

Command: `grep -o '"situation"' <file> | wc -l` (and likewise for `linescores`, `notes`, `broadcasts`);
`grep -oP '"week":\{"number":\K[0-9]+' <file>` and `grep -oP '"season":\{"year":[0-9]+,"type":\K[0-9]+' <file>`
for the two scalars. `situation` is absent from every fixture — the live shape is still unobserved. `linescores` is a
final-only field here: the pre-kickoff `today` payload omits it entirely, confirming the skeleton rule.

### 3. Competitor abbreviations

- sunday-a (24 competitors, 24 distinct): `ARI ATL BUF CAR CIN CLE DAL DET GB HOU IND JAX KC LAR LV MIA MIN NE NO NYJ PIT SEA SF TEN`
- sunday-b (26 competitors, 26 distinct): `ARI ATL CAR CHI CIN CLE DAL DEN DET GB HOU IND KC LAC LAR LV MIA NE NO NYG NYJ PHI SEA SF TB WSH`
- today (26 competitors, 26 distinct): `ARI BAL BUF CAR CHI CIN DAL DEN DET HOU IND JAX KC LAC LV MIA MIN NO NYG NYJ PHI PIT SF TB TEN WSH`
- emptyday: none.
- Union across the three non-empty fixtures: all **32** clubs. This confirms the ten NHL collisions in `nfl-plan.md`
  §0.1 — `PHI PIT BUF DAL DET MIN SEA CAR WSH CHI` all appear.

Command: `grep -oP '"team":\{"id":"[0-9]+","uid":"[^"]+","location":"[^"]*","name":"[^"]*","abbreviation":"\K[A-Z]+' <file> | sort -u`

### 4. Logo hosts

All four fixtures: **`{a.espncdn.com}` exactly** — no second image host.

| fixture | image/logo `href` hosts |
|---|---|
| sunday-a | `{a.espncdn.com}` (2) |
| sunday-b | `{a.espncdn.com}` (2) |
| today | `{a.espncdn.com}` (30: 2 NFL league logos and 28 DraftKings marks, 14 odds providers × 2; team logos are not `href`s, they are `competitors[].team.logo` strings) |
| emptyday | `{a.espncdn.com}` (2 league logos) |

Command: `grep -oP '"href":"https?://\K[^/]+(?=/i/)' <file> | sort -u`. Other `href` hosts in these payloads
(`www.espn.com`, `www.vividseats.com`, `sportsbook.draftkings.com`, `www.accuweather.com`) are article and ticket
links, not images, and are excluded from this census.

### 5. Calendar week ranges

`leagues[0].calendar` carries 26 entries, identical in all four captures. Table as `label | value | startDate | endDate`:

| label | value | startDate | endDate |
|---|---|---|---|
| Hall of Fame Weekend | 1 | 2026-08-06T07:00Z | 2026-08-13T06:59Z |
| Preseason Week 1 | 2 | 2026-08-13T07:00Z | 2026-08-20T06:59Z |
| Preseason Week 2 | 3 | 2026-08-20T07:00Z | 2026-08-27T06:59Z |
| Preseason Week 3 | 4 | 2026-08-27T07:00Z | 2026-09-06T06:59Z |
| Week 1 | 1 | 2026-09-06T07:00Z | 2026-09-16T06:59Z |
| Week 2 | 2 | 2026-09-16T07:00Z | 2026-09-23T06:59Z |
| Week 3 | 3 | 2026-09-23T07:00Z | 2026-09-30T06:59Z |
| Week 4 | 4 | 2026-09-30T07:00Z | 2026-10-07T06:59Z |
| Week 5 | 5 | 2026-10-07T07:00Z | 2026-10-14T06:59Z |
| Week 6 | 6 | 2026-10-14T07:00Z | 2026-10-21T06:59Z |
| Week 7 | 7 | 2026-10-21T07:00Z | 2026-10-28T06:59Z |
| Week 8 | 8 | 2026-10-28T07:00Z | 2026-11-04T07:59Z |
| Week 9 | 9 | 2026-11-04T08:00Z | 2026-11-11T07:59Z |
| Week 10 | 10 | 2026-11-11T08:00Z | 2026-11-18T07:59Z |
| Week 11 | 11 | 2026-11-18T08:00Z | 2026-11-25T07:59Z |
| Week 12 | 12 | 2026-11-25T08:00Z | 2026-12-02T07:59Z |
| Week 13 | 13 | 2026-12-02T08:00Z | 2026-12-09T07:59Z |
| Week 14 | 14 | 2026-12-09T08:00Z | 2026-12-16T07:59Z |
| Week 15 | 15 | 2026-12-16T08:00Z | 2026-12-23T07:59Z |
| Week 16 | 16 | 2026-12-23T08:00Z | 2026-12-30T07:59Z |
| Week 17 | 17 | 2026-12-30T08:00Z | 2027-01-06T07:59Z |
| Week 18 | 18 | 2027-01-06T08:00Z | 2027-01-13T07:59Z |
| Wild Card | 1 | 2027-01-13T08:00Z | 2027-01-20T07:59Z |
| Divisional Round | 2 | 2027-01-20T08:00Z | 2027-01-27T07:59Z |
| Conference Championship | 3 | 2027-01-27T08:00Z | 2027-02-03T07:59Z |
| Super Bowl | 4 | 2027-02-03T08:00Z | 2027-02-16T07:59Z |

Command: `grep -oP '\{"label":"[^"]+","alternateLabel":"[^"]+","detail":"[^"]+","value":"[0-9]+","startDate":"[^"]+","endDate":"[^"]+"\}' <file>`

### 6. Late-game `event.date` against the requested day

| fixture | requested day | late game | `event.date` | UTC day vs request |
|---|---|---|---|---|
| sunday-a | 2026-11-15 | PIT @ CIN (`401873064`, Sunday night) | `2026-11-16T01:20Z` | day + 1 |
| sunday-b | 2026-11-08 | TB @ CHI (`401873050`, Sunday night) | `2026-11-09T01:20Z` | day + 1 |
| today | none (no `dates=`) | IND @ HOU (`401873066`, Thursday night) | `2026-11-20T01:15Z` | 20:15 ET on 11-19, UTC 11-20 |
| emptyday | 2026-11-17 | — | — | no events |

Command: `grep -oP '"uid":"s:20~l:28~e:[0-9]+","date":"[^"]+","name":"[^"]+' <file>`. The last event of each dated
request is the late game and its `date` is on the **following UTC day**, so a day lookup must not assume that
`event.date`'s date part equals the requested day.

### 7. Ties

| fixture | events with equal competitor scores | completed ties |
|---|---|---|
| sunday-a | 0 | 0 |
| sunday-b | 0 | 0 |
| today | 13 (all unplayed `0`–`0` skeletons) | 0 |
| emptyday | 0 | 0 |

Command: `grep -oP '"score":"\K[0-9]+' <file> | paste - -` and count equal pairs. No final tie exists in this corpus;
the 13 equal scores in `today` are pre-kickoff placeholders and must not be read as a tie result.

## What the census must still establish

The scoreboard questions in this list are answered by the Stage 1.3a census above; the rest stay open until their
fixtures are censused, one table per question, each with the command that produced it. No model may be written against
a guess:

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
