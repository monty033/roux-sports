# Changelog

All notable changes to Roux Sports. Versions are the `versionName` in `app/build.gradle.kts`, and each release's
`versionCode` has a matching file under `fastlane/metadata/android/en-US/changelogs/`.

## [1.0.0] - 2026-11-03

First release.

- **Scores** — every game for the day with live state, period-by-period scoring, shots, team stats, penalties and the
  three stars. Swipe between days, or pick any date from the month picker.
- **Standings** — Eastern, Western and league tables, with playoffs, division and conference views. The chosen
  conference and ranking are remembered between launches.
- **Teams** — all 32 clubs, each with a full-season schedule centred on the next game, the season roster, and
  league-relative team stats where every number carries its rank across the league.
- **Players** — skaters and goalies with season totals in one list, and a player page with career and season history.
- **Game alerts** — notifications for chosen teams when a game starts or its score changes. Off by default, no
  account, scheduled on the device.

Nothing in this release collects data: no ads, no trackers, no analytics, no telemetry. The app talks to three NHL
endpoints and nothing else, which a test enforces.
