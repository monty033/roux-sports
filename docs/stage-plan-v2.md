# Stage plan v2 — hardening, game detail, notifications, release

Supersedes the remaining stages of `stage-plan.md` from Stage 6b onward. Produced by an Opus 5.5 planning session
(`ses_e80894ad5ffeo6CmWew7rT33cJ`) reviewing this tree at `a724a94`, read-only, after a seven-section architecture review.
The step text below is the planner's, unedited — a paraphrase of a plan is how this project's geometry got wrong once
already.

Two decisions since that session, applied to the steps that depended on them:

- **Times are device-local.** Start times, "today" and the stale banner all use the device zone and the user's 12/24-hour
  preference. The league day does not roll over separately for Pacific users; it is the device's civil date.
- **The calendar sheet confirms.** Tapping a day or TODAY moves a draft and highlights it; only DONE applies it.
  Dismissing - scrim, back, or the dialog dismiss action - discards the draft, and TODAY is a draft too rather than an
  instant jump. Step H18 is therefore in scope, not conditional.

Where the review and this plan disagree with the merged documentation, this plan wins: the review found that the parity
document's "the app pins what the design floats" claim is false — the design pins the state rail at `5.75rem` (92 dp),
`5.125rem` (82 dp) under 380 px — and that the `:domain`/`:data` scanner cannot see `.toFile()` or fully qualified
`java.` references, a real violation already in the tree.

---

**Notes on reading this plan:**
- **Path shorthands:** `dom/`, `domT/`, `dat/`, `datT/`, `appM/` and `appT/` are the same as in `stage-plan.md`.
- **Stage numbering:** the brief's labels don't match `docs/stage-plan.md`. In the doc, Stage 9 is the accessibility audit, Stage 10 is release and Stage 11 is optional CI. I followed the doc's content and kept the accessibility work before release.
- **Step IDs:** H = finishing Stage 6b plus hardening; G = Stage 7; N = Stage 8 notifications; P = Stage 9 accessibility and polish; E = Stage 10 release; C = Stage 11 CI.

**What I changed from the previous plan, and why:**
1. **A new hardening phase (H, 19 steps)** comes before Stages 7 and 8. Both reuse the crashing poll path and the preference store.
2. **Free steps now use `opencode-go/beam-free`.** It is the only free model currently answering. With only one free model available, free steps are reviewed by DeepSeek.
3. **Fixture capture moves off the free model**, because it touches live data.
4. **Detail parsing is split by endpoint** into separate files, which removes a chokepoint and isolates failures. Detail data is modelled with one `Loadable` per endpoint.
5. **Notifications get three changes:**
   - an atomic ledger-claim step first;
   - a WorkerFactory step that shares the single DataStore;
   - an explicit intent from the notification tap instead of an exported `roux://` deep link.
6. **Release gets two changes:**
   - a backup-rules step;
   - a release-to-release upgrade check that replaces the impossible debug-to-release one.
7. **Accessibility gains** a Teams-list stability step, a rail stress test, a type-size decision and corrections to the parity document.

STEP H1a: LoadError gains retryAfter and Blocked
- Files: `dom/Loadable.kt`; `appM/ui/scores/ScoresScreen.kt` (the exhaustive `when` at :204), plus strings
- Depends on: none
- Acceptance test (file + assertion): `domT/LoadableTest.kt`: `LoadError.Http(429, 30.seconds).retryAfter == 30.seconds`, `LoadError.Http(500).retryAfter == null`, `LoadError.Blocked("evil.example").host == "evil.example"`; `appT/ui/scores/ScoresScreenTest.kt`: Missing + `Blocked` renders the blocked-error string
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Live-path type change rules out free (sync); the compiler finds every site (Q3 N)

STEP H1b: ScoresRepositoryImpl never throws
- Files: `dat/repo/ScoresRepositoryImpl.kt`
- Depends on: H1a
- Acceptance test (file + assertion): `datT/repo/ScoresRepositoryImplTest.kt`:
  - 429 with `Retry-After: 30` → `error == Http(429, 30.seconds)`, no throw
  - 301 to `evil.example` → `error is Blocked`, and the engine never saw that host
  - a `ForwardingFileSystem` whose `atomicMove` throws → `data != null && error == Local`
  - cache + 500 → `Stale`, `Http(500)`
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Cross-module "never throws" invariant with no tests → start at DeepSeek

STEP H2: StandingsRepositoryImpl never throws
- Files: `dat/repo/StandingsRepositoryImpl.kt`
- Depends on: H1a
- Acceptance test (file + assertion): `datT/repo/StandingsRepositoryImplTest.kt`: the same four cases as H1b, applied to `standings()` and `seasons()`
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Single file following H1b's pattern; sync rules out free

STEP H3: Scores poll backs off, honours Retry-After, guards writes
- Files: `appM/ui/scores/ScoresViewModel.kt` (adds an injected `jitter` parameter)
- Depends on: H1a
- Acceptance test (file + assertion): `appT/ui/scores/ScoresViewModelTest.kt`:
  - Missing + Network forever, jitter 0.5 → `times == [0, 25_000, 75_000, 175_000, 375_000]`
  - `Http(429, 120.seconds)` → next call at ≥ 120_000
  - `Blocked` → no automatic call in 10 min, but `refresh()` still calls
  - a throwing `setFavouritesOnly` → `runTest` finishes with no uncaught exception
  - success resets the cadence
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Live polling path (sync); one file with exact virtual-time assertions

STEP H4: Parse and cache I/O off the main thread
- Files: `dat/repo/ScoresRepositoryImpl.kt`, `dat/repo/StandingsRepositoryImpl.kt` (injected `ioDispatcher`)
- Depends on: H1b, H2
- Acceptance test (file + assertion): both repository tests use a `ForwardingFileSystem` that records `Thread.currentThread().name` on `source()`/`sink()`; with a single-thread dispatcher named `roux-io-test`, every recorded name equals `roux-io-test`
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Mechanical edit across two files in one module; sync rules out free

STEP H5: refreshDay bypasses the fresh-cache short-circuit
- Files: `dom/repo/ScoresRepository.kt` (`refreshDay` with a default body so existing fakes compile), `dat/repo/ScoresRepositoryImpl.kt`
- Depends on: H4
- Acceptance test (file + assertion): `datT/repo/ScoresRepositoryImplTest.kt`:
  - after a fresh `loadDay`, `refreshDay` → `requests.size == 2`, `Fresh`
  - with the network down → cached data, `Stale`, `Network`
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Interface contract across modules (Q3)

STEP H6: User-triggered loads force; ticks don't; honest test names
- Files: `appM/ui/scores/ScoresViewModel.kt`
- Depends on: H3, H5
- Acceptance test (file + assertion): `appT/ui/scores/ScoresViewModelTest.kt`:
  - pull → exactly one `refreshDay`; the next 25 s tick → `loadDay`
  - `pullToRefreshForcesAFetch` is renamed `pullToRefreshBypassesTheFreshCache`
  - `resubscribingRefreshesImmediatelyWhenStale` is renamed `resubscribingUsesTheCacheAwareLoad` and asserts `loadDay`
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: One view-model file on the live path

STEP H7: Wire ±1-day prefetch after the selection settles
- Files: `appM/ui/scores/ScoresViewModel.kt`
- Depends on: H6
- Acceptance test (file + assertion): `appT/ui/scores/ScoresViewModelTest.kt`:
  - select 2026-10-27, advance 1_000 → `prefetchCalls == ["2026-10-27"]`
  - select the 28th then the 29th within 500 ms → only the 29th
  - no prefetch after a failed load with no data
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Makes tested-but-dead code reachable; one file

STEP H8: Observable preferences
- Files: `dom/repo/PreferencesRepository.kt` (`observeFavourites`, `observeFavouritesOnly`, `observeLeagueOrder`, with default bodies), `dat/prefs/DataStorePreferencesRepository.kt`
- Depends on: none
- Acceptance test (file + assertion): `datT/prefs/DataStorePreferencesRepositoryTest.kt` (Turbine):
  - `observeFavourites()` emits `{}` then `{TOR}` after `setFavourites({TOR})`
  - a store with `prefs_version=2` emits `{}` without throwing
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Interface plus implementation across modules (Q3)

STEP H9: DataStore through createWithPath, with a migration test
- Files: `dat/prefs/DataStorePreferencesRepository.kt`
- Depends on: H8
- Acceptance test (file + assertion): `datT/prefs/DataStorePreferencesRepositoryTest.kt`:
  - a file written by the legacy `PreferenceDataStoreFactory.create(produceFile = { File })` with favourites `{TOR, MTL}` is read as `{TOR, MTL}`
  - a following `setFavouritesOnly(false)` round-trips
- Risk class: data loss
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: data-loss
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Changes the storage path of durable user state (data-loss trigger)

STEP H10: Make the KMP scanner honest
- Files: test only (`domT/KmpBoundaryTest.kt`)
- Depends on: H9
- Acceptance test (file + assertion): `domT/KmpBoundaryTest.kt`:
  - `scanSource("val f = java.io.File(\"x\")").size == 1`
  - `scanSource("val p = path.toFile()").size == 1`
  - `scanSource("val x = a.java.b").size == 0`
  - the renamed real-tree test asserts both `src/main` dirs exist and hold ≥ 1 `.kt` file, with no violations
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Bounded, test-only change with exact assertions (Q1, Q2, Q4)

STEP H11: Drawer state independent of the poll
- Files: `appM/ui/scores/ScoresViewModel.kt` (`drawerState: StateFlow<DrawerUi>` built from the observed preferences)
- Depends on: H7, H8
- Acceptance test (file + assertion): `appT/ui/scores/ScoresViewModelTest.kt`: collecting only `drawerState`:
  - `favouriteCount` follows a preference emission
  - `setLeagueOrder` is reflected
  - `scores.calls.isEmpty()` after 60 s
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: One file with behaviour and tests; not free (behaviour change in a chokepoint file)

STEP H12: Drawer live on every tab; selection follows the filter
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/ui/drawer/LeagueDrawer.kt`
- Depends on: H11
- Acceptance test (file + assertion): `appT/ui/nav/ShellTest.kt`:
  - on Standings, swipe the drawer open, enter edit, drag `leagues.nfl.handle` up one row → `leagues.nfl` top < `leagues.nhl` top
  - after My Teams, reopen → `leagues.myTeams` is selected and `leagues.nhl` is not
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Two UI files with behaviour (bug fix)

STEP H13: Time display per Patrick's decision
- Files: `appM/ui/common/StaleBanner.kt`, `appM/ui/scores/GameRow.kt`
- Depends on: Patrick's time-zone decision
- Acceptance test (file + assertion): `appT/ui/common/StaleBannerTest.kt` (new), with default zone America/Vancouver and 12-hour time:
  - since = 1 h ago → "Updated 2:00 PM"
  - yesterday → contains "Yesterday"
  - `ScoresIntegrationTest:122` updated to match
  - `GameRowTest`: 9:30 PM ET shows "6:30 PM" (if local) or ends in "ET" (if Eastern)
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Bug fix in two files that rewrites a pinned expectation

STEP H14: LeagueCalendar policy (conditional on the rollover decision)
- Files: `domain/build.gradle.kts` (add kotlinx-datetime), `dom/policy/LeagueCalendar.kt`
- Depends on: Patrick's rollover decision
- Acceptance test (file + assertion): `domT/policy/LeagueCalendarTest.kt`:
  - with rollover R, `leagueDay(2026-10-27T04:30Z) == "2026-10-26"`
  - both sides of the 2026-11-01 DST change
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Pure function with a table test (Q1, Q2, Q4)

STEP H15: One league day for Scores and Standings
- Files: `appM/ui/scores/ScoresViewModel.kt`, `dat/repo/StandingsRepositoryImpl.kt`
- Depends on: H14, H11, H4
- Acceptance test (file + assertion):
  - `appT/ui/scores/ScoresViewModelTest.kt`: `today` equals `LeagueCalendar.leagueDay(now)` for 48 instants across the DST weekend
  - `datT/repo/StandingsRepositoryImplTest.kt`: the request date equals the same function for those instants
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Same-day invariant across modules (Q3) on the live path

STEP H16: Correct the parity document
- Files: `docs/stage-6b-parity.md`
- Depends on: none
- Acceptance test (file + assertion):
  - `grep -q '5.75rem' docs/stage-6b-parity.md`
  - `grep -q 'padding:0 .4375rem 0 .75rem'`
  - rows exist for nav glyphs, drag lift and drawer icon colour, each marked "not matched" or "unverified"
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Docs checkable with grep

STEP H17: Design-parity screenshots (the original S6b.6)
- Files: test only; baselines in `appT/screenshots/design-parity/`
- Depends on: H12, H13
- Acceptance test (file + assertion): `appT/ui/DesignParityTest.kt` records 411 × 923 and 360 × 780 in light and dark; `recordRoborazziDebug` then verify passes; Patrick's sign-off recorded in `docs/device-checklist.md`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test only (Q1, Q2, Q4)

STEP H18: Calendar holds a draft until DONE - ALREADY SHIPPED, struck from the plan
- Files: none. The draft-then-DONE behaviour and its tests already exist in `CalendarMonthSheet.kt` and
  `DateShelfCalendarTest.kt`
- Status: confirmed by Patrick on 2026-10-28 as in the app and working. Nothing to build; the acceptance below already
  passes, and recording it as pending would invite a second implementation.
- Acceptance test (file + assertion): `appT/ui/scores/DateShelfCalendarTest.kt`:
  - tap a day, then the scrim → `onSelect` is never called
  - tap a day, then DONE → called once with that day
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One bounded UI file

STEP G1: Capture gamecenter fixtures and map A6 sources
- Files: `scripts/capture-fixtures.sh` (gamecenter mode), `docs/a6-sources.md`; generates `fx/<cap>/gc-{final,live}-{landing,boxscore,right-rail}.json`
- Depends on: none (needs a live game in progress)
- Acceptance test (file + assertion):
  - every gc file passes `jq -e .` and is larger than 1 KB
  - `a6-sources.md` has a row (endpoint plus JSON path, or "NO SOURCE") for each of the five sections
  - any "NO SOURCE" stops the step
- Risk class: none
- Privacy class: live-data (no free)
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Live-data capture rules out free

STEP G2: Gamecenter paths take GameId
- Files: `dat/net/NhlApi.kt`
- Depends on: none
- Acceptance test (file + assertion): `datT/net/NhlApiTest.kt`:
  - `gamecenterLanding(GameId("2025020123"))` requests `/v1/gamecenter/2025020123/landing`
  - `GameId("../x")` passed to any gamecenter method → `IllegalArgumentException`, with no request made
- Note: the refusal lives in the URL builder, not in `GameId`'s constructor. `Game.kt` requires value types to
  tolerate messy feed data so an adapter can represent a bad id and let the caller decide; a constructor that threw
  would make such a row unrepresentable. The URL is the boundary and the last point before the id becomes part of a
  request, so it is where the check belongs.
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Network path rules out free; one file

STEP G3: GameDetail model with one Loadable per endpoint
- Files: `dom/model/GameDetail.kt`
- Depends on: none
- Acceptance test (file + assertion): `domT/model/GameDetailTest.kt`:
  - `sections()` returns the non-null sections in the order PeriodScoring, Shots, PowerPlay, Goalies, ThreeStars
  - boxscore `Loadable(null, Missing, Http(500))` still yields the landing sections
  - `staleSince()` returns the earliest `Stale.since`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Pure types (Q1, Q2, Q4)

STEP G4: A cache kind per detail endpoint
- Files: `dat/cache/ResponseCache.kt`
- Depends on: none
- Acceptance test (file + assertion): `datT/cache/ResponseCacheTest.kt`: 21 landing puts evict exactly one landing entry and no boxscore entry
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Enum change with an exact eviction test

STEP G5: Landing adapter
- Files: `dat/nhl/dto/LandingDto.kt`, `dat/nhl/LandingAdapter.kt`
- Depends on: G1, G3
- Acceptance test (file + assertion): `datT/nhl/LandingAdapterTest.kt`:
  - final and live fixtures → `Success`
  - removing each source node listed in `a6-sources.md` → only that section is null
  - `{}` → `Failure`, with no throw
  - `AdapterIsolationTest` passes
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Sets the G5-boundary degradation pattern for detail data (Q3)

STEP G6: Boxscore adapter
- Files: `dat/nhl/dto/BoxscoreDto.kt`, `dat/nhl/BoxscoreAdapter.kt`
- Depends on: G5
- Acceptance test (file + assertion): `datT/nhl/BoxscoreAdapterTest.kt`: the same shape of assertions as G5, applied to the boxscore fixtures
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Follows G5's pattern, so no new invariant (Q3 N)

STEP G7: Right-rail adapter
- Files: `dat/nhl/dto/RightRailDto.kt`, `dat/nhl/RightRailAdapter.kt`
- Depends on: G5
- Acceptance test (file + assertion): `datT/nhl/RightRailAdapterTest.kt`: the same shape of assertions as G5, applied to the right-rail fixtures
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Follows G5's pattern

STEP G8: GameDetailRepository
- Files: `dom/repo/GameDetailRepository.kt`, `dat/repo/GameDetailRepositoryImpl.kt`
- Depends on: G2, G4, G5, G6, G7, H1b
- Acceptance test (file + assertion): `datT/repo/GameDetailRepositoryImplTest.kt`:
  - a cached final game → 0 further requests
  - a live game refetches after 30 s
  - boxscore 500 with landing OK → landing `Fresh`, boxscore `Http(500)`
  - 429 → no throw
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Partial failure across three endpoints (Q3)

STEP G9: GameDetailViewModel
- Files: `appM/ui/detail/GameDetailViewModel.kt`
- Depends on: G8, H3
- Acceptance test (file + assertion): `appT/ui/detail/GameDetailViewModelTest.kt`:
  - a live game polls every 30 s and stops 5 s after unsubscribe
  - a final game never polls
  - failures follow `PollPolicy.backoff`
  - a throwing loader does not crash
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Live polling (sync); one file

STEP G10: GameDetailScreen
- Files: `appM/ui/detail/GameDetailScreen.kt`, plus strings
- Depends on: G3
- Acceptance test (file + assertion): `appT/ui/detail/GameDetailScreenTest.kt`:
  - each of the five single-null cases hides only that section's tag
  - bottom Back is a primary control in the bottom 30%
  - reach passes at both sizes and at font scale 2.0
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One bounded UI file against fixed models

STEP G11: Wire the detail route and real row clicks
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/RouxApp.kt`
- Depends on: G9, G10, H12
- Acceptance test (file + assertion): `appT/ui/detail/DetailNavigationTest.kt`:
  - tapping the first fixture game shows the detail with both abbreviations
  - Back and the system back gesture return to Scores with the same date selected
  - the Scores fake stops being called 5 s after the detail opens
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Navigation and lifecycle state across screens (Q3)

STEP N1: Atomic notification-ledger claim
- Files: `dom/repo/PreferencesRepository.kt`, `dat/prefs/DataStorePreferencesRepository.kt` (`claimGoal`/`claimStart` inside one `store.edit`)
- Depends on: H9
- Acceptance test (file + assertion): `datT/prefs/DataStorePreferencesRepositoryTest.kt`:
  - 50 concurrent `claimGoal("2025020123", 3)` → exactly one returns true
  - claiming 2 after 3 → false
  - a newer-version store → false, with no write
- Risk class: data loss
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / N / N
- Hard trigger: data-loss
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Durable dedup state and the at-most-once guarantee (data-loss trigger)

STEP N2: NotificationDecider (pure)
- Files: `dom/policy/NotificationDecider.kt`
- Depends on: N1
- Acceptance test (file + assertion): `domT/policy/NotificationDeciderTest.kt`:
  - S8.1's cases
  - the decider emits claims, never writes
  - a goal count that goes down → 0 events
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Hidden dedup and score-correction invariants (Q3)

STEP N3: GameWatchScheduler and its WorkManager implementation
- Files: `dom/notify/GameWatchScheduler.kt`, `appM/notify/WorkManagerGameWatchScheduler.kt`
- Depends on: H8
- Acceptance test (file + assertion): `appT/notify/WorkManagerGameWatchSchedulerTest.kt`: S8.2's cases (one `watch-<id>` with a 2 h ± 1 s delay, idempotent reconcile, cancelled when prefs are off)
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Interface across modules plus unique-work invariants (Q3)

STEP N4: WorkerFactory sharing the single DataStore
- Files: `appM/RouxApp.kt` (`Configuration.Provider`), `MANIFEST` (remove the default initializer)
- Depends on: G11
- Acceptance test (file + assertion): `appT/notify/WorkerWiringTest.kt`:
  - a worker built by the app's factory satisfies `assertSame(app.container.preferences, worker.preferences)`
  - the merged manifest lacks `WorkManagerInitializer`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: One-DataStore-per-process invariant across components (Q3)

STEP N5: Notifier and channels (explicit intent)
- Files: `appM/notify/Notifier.kt`, plus strings
- Depends on: none
- Acceptance test (file + assertion): `appT/notify/NotifierTest.kt`:
  - S8.3's cases
  - the content intent is explicit to `MainActivity` with a `gameId` extra and no data URI
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One file with explicit assertions

STEP N6: GameWatchWorker
- Files: `appM/notify/GameWatchWorker.kt`
- Depends on: N1, N2, N3, N4, N5, H1b
- Acceptance test (file + assertion): `appT/notify/GameWatchWorkerTest.kt`:
  - S8.4's cases, using claims
  - a throwing repository → `Result.retry()` and 0 posts
  - 429 → follow-up delay ≥ Retry-After
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: sync
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Ledger-before-post order and re-chaining (Q3)

STEP N7: TeamsViewModel alert preferences
- Files: `appM/ui/teams/TeamsViewModel.kt`
- Depends on: N3, H8
- Acceptance test (file + assertion): `appT/ui/teams/TeamsViewModelNotificationsTest.kt`: S8.5's cases
- Risk class: sync
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: sync
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Triggers scheduling (sync rules out free); one file

STEP N8: Alert toggles, POST_NOTIFICATIONS and disclosure
- Files: `appM/ui/teams/TeamsScreen.kt`, `MANIFEST`, plus strings
- Depends on: N7, N4
- Acceptance test (file + assertion): `appT/ui/teams/TeamsScreenNotificationsTest.kt`: S8.6's cases
- Risk class: auth/permissions
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: auth
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Runtime-permission boundary

STEP N9: Wire the scheduler and the notification tap
- Files: `appM/RouxApp.kt`, `appM/ui/nav/RouxNavHost.kt`
- Depends on: N6, N8, G11
- Acceptance test (file + assertion): `appT/notify/NotificationsIntegrationTest.kt`:
  - S8.7's cases
  - an extra `gameId="abc"` is ignored and the app stays on Scores
  - the manifest has no `android:scheme="roux"`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; second review
- Reason: Wiring that spans every layer (Q3)

STEP N10: Pixel 9 alert-latency record
- Files: `docs/device-checklist.md`
- Depends on: N9
- Acceptance test (file + assertion): `grep -q 'Stage 8 latency' docs/device-checklist.md`, with measured start and goal delays recorded by Patrick
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: N / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Records manual results only

STEP P1: GameRow TalkBack description
- Files: `appM/ui/scores/GameRow.kt`
- Depends on: G11, H13
- Acceptance test (file + assertion): `appT/ui/AccessibilityLabelsTest.kt`: a live row's merged description equals "Boston Bruins 3, New York Rangers 2, live, 2nd period, 7:42", with no duplicate "live"
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One file with an exact string assertion

STEP P2: Stable Teams list
- Files: `appM/ui/teams/TeamsViewModel.kt`, `appM/ui/teams/TeamsScreen.kt`
- Depends on: N8
- Acceptance test (file + assertion): `appT/ui/teams/TeamsScreenTest.kt`:
  - toggling TOR leaves `teams.row.TOR` bounds unchanged
  - the order changes only after `retry()`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Bug fix across two files

STEP P3: Reach audit matrix
- Files: test only
- Depends on: N9
- Acceptance test (file + assertion): `appT/ui/ReachAuditTest.kt`: S9.1's matrix passes (including Detail and the drawer)
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test-only matrix

STEP P4: Rail stress at font scale 2.0
- Files: test only
- Depends on: P1
- Acceptance test (file + assertion): `appT/ui/scores/GameRowStressTest.kt`: at 2.0 and 360 dp, with score 103 and "OT 12:34", every rail text node lies inside `gameRowStateRail` bounds and none is ellipsised. A failure goes to Patrick as a design decision.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test only

STEP P5: Contrast and minimum type-size assertions
- Files: test only
- Depends on: Patrick's decision on the drawer's 8–9 sp text versus R10
- Acceptance test (file + assertion): `appT/ui/ContrastAndTypeTest.kt`:
  - every text node has contrast ≥ 4.5:1 in both themes
  - body text ≥ 14 sp, except the allowlisted, decided tags
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test only

STEP P6: Screenshot matrix and baseline policy
- Files: test only; `README.md` section on baselines
- Depends on: N9
- Acceptance test (file + assertion):
  - `appT/ui/ScreenshotMatrixTest.kt` verify passes in both themes
  - `grep -q 'baseline changes need diff images' README.md`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test plus docs

STEP P7: Navigation glyphs and indicator per the design (conditional)
- Files: `appM/ui/nav/RouxNavHost.kt`
- Depends on: N9; Patrick confirms the design's glyphs are final
- Acceptance test (file + assertion): `appT/ui/nav/ShellTest.kt`: the selected item's indicator measures 44 × 28 dp; Roborazzi nav baseline re-recorded
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One bounded visual change

STEP P8: Drawer drag lift and icon colour (conditional)
- Files: `appM/ui/drawer/LeagueDrawer.kt`
- Depends on: H12; confirm the markup for `.icn`
- Acceptance test (file + assertion): `appT/ui/drawer/LeagueDrawerTest.kt`:
  - while dragging, the row's pixel at (2, 2) equals #1C1E24
  - the star's tint equals #FF4B12, if confirmed
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One bounded visual change

STEP P9: One-thumb walkthrough sign-off
- Files: `docs/device-checklist.md`
- Depends on: P1–P8
- Acceptance test (file + assertion): every Stage 9 item is ticked by Patrick on the Pixel 9
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: N / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Records manual results

STEP E1: Backup and data-extraction rules
- Files: `MANIFEST`, `app/src/main/res/xml/data_extraction_rules.xml`
- Depends on: N8; Patrick's backup decision
- Acceptance test (file + assertion): `scripts/check-release-apk.sh --backup`:
  - `aapt2 dump xmltree` shows `dataExtractionRules`
  - the XML excludes `domain="file" path="nhl-cache/"`, and excludes `datastore/` too if Patrick chooses no cloud backup
- Risk class: data loss
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: data-loss
- Tier: Sonnet
- Effort: low
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Decides where personal state is restored from and sent to

STEP E2: Release build, R8, signing and version
- Files: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Depends on: P9
- Acceptance test (file + assertion): `scripts/check-release-signing.sh`: S10.1's cases, plus `versionName` 1.0.0 reflected in the User-Agent (no "dev")
- Risk class: deployment
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: deployment
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Deployment boundary

STEP E3: Release APK audit script
- Files: `scripts/check-release-apk.sh`
- Depends on: E2
- Acceptance test (file + assertion): S10.2's assertions, including `--self-test` exiting 1
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Script with positive and negative checks

STEP E4: Network-host source audit
- Files: test only
- Depends on: N9
- Acceptance test (file + assertion): `domT/NetworkHostAuditTest.kt`: S10.3's assertions; it fails if a configured directory is missing
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Test-only scan

STEP E5: About section
- Files: `appM/ui/teams/AboutSection.kt`, `appM/ui/teams/TeamsScreen.kt`
- Depends on: P2
- Acceptance test (file + assertion): `appT/ui/teams/AboutSectionTest.kt`: S10.4's assertions
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: mimo-v2.6-flash via OpenCode Go; routine
- Reason: Two production files with behaviour

STEP E6: F-Droid metadata
- Files: `fastlane/metadata/android/en-US/*`
- Depends on: E2
- Acceptance test (file + assertion): `scripts/check-metadata.sh`: S10.5's assertions
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Docs with a length check

STEP E7: Release docs, including the upgrade procedure
- Files: `README.md`, `CHANGELOG.md`
- Depends on: E2
- Acceptance test (file + assertion):
  - S10.6's greps
  - `grep -q 'release-to-release upgrade' README.md`
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Docs

STEP E8: Release-to-release upgrade check
- Files: `docs/device-checklist.md`
- Depends on: E2, E7
- Acceptance test (file + assertion): Patrick records that a throwaway-key `versionCode 2` installed over `versionCode 1` keeps favourites and league order
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: N / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: Records a manual check; replaces the impossible debug-to-release check

STEP E9: Release build script and v1.0.0 publication
- Files: `scripts/build-release.sh`
- Depends on: E1, E3–E8
- Acceptance test (file + assertion): S10.7's assertions; Patrick runs it with the real keystore and publishes
- Risk class: deployment
- Privacy class: secret-or-personal (no free)
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: deployment
- Tier: Sonnet
- Effort: low
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Publishing a signed release cannot be reverted

STEP C1: Forgejo runner probe
- Files: `.forgejo/workflows/runner-probe.yml`
- Depends on: none
- Acceptance test (file + assertion): S11.1's `PROBE_OK` / `PROBE_FAIL:<check>` contract
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Go
- Reviewer: deepseek-v4.1-flash via OpenCode Go; routine
- Reason: One YAML file with an explicit marker

STEP C2: PR-gate workflow for the trusted fork only
- Files: `.forgejo/workflows/pr-gate.yml`
- Depends on: C1 (PROBE_OK)
- Acceptance test (file + assertion): S11.2's greps, plus a green `openclaw` PR
- Risk class: auth/permissions
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / N / N
- Hard trigger: auth
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: Luna (gpt-6-luna) via Codex; independent review
- Reason: Decides which code runs on a self-hosted runner

---

## Summary

### 1. Step order and what can run in parallel

**Hardening lanes:**
- **Scores lane:** H1a → {H1b ∥ H2 ∥ H3} → H4 → H5 → H6 → H7 → H11 → H12 → H15.
- **Preferences and KMP lane:** H8 → H9 → H10, and H8 also feeds H11.
- **Any time:** H16 and H18.
- **After their decisions:** H13 and H14.
- **Late:** H17, after H12 and H13.

**Stage 7 runs alongside the hardening lanes:**
- Start immediately: G1 (needs a live game), G2, G3 and G4.
- G5 needs G1 and G3; then G6 ∥ G7.
- G8 needs G2, G4, G5, G6, G7 and H1b; then G9 (also needs H3).
- G10 needs only G3.
- G11 needs G9, G10 and H12.

**Stage 8:**
- N1 needs H9; then N2.
- N3 needs H8, and N5 can start at any time.
- N4 needs G11.
- N6 needs N1–N5.
- N7 needs N3; then N8 (also needs N4).
- N9 needs N6, N8 and G11; then N10.

**Stage 9:**
- P1 needs G11 and H13; P4 follows P1.
- P2 needs N8.
- P3 ∥ P6 ∥ P7 after N9.
- P5 after its decision; P8 after H12.
- P9 comes last.

**Stage 10:**
- E2 after P9.
- E3 ∥ E6 ∥ E7 after E2; E4 can run any time after N9.
- E1 after N8.
- E5 after P2.
- E8 after E2 and E7.
- E9 comes last.

**CI:** C1 → C2 at any time.

**Files that force serialisation:**
- `ScoresViewModel.kt`: H3 → H6 → H7 → H11 → H15.
- `ScoresRepositoryImpl.kt`: H1b → H4 → H5.
- `DataStorePreferencesRepository.kt`: H8 → H9 → N1.
- `RouxNavHost.kt`: H12 → G11 → N9 → P7.
- `RouxApp.kt`: G11 → N4 → N9.
- `MANIFEST`: N4 → N8 → E1.
- `TeamsScreen.kt`: N8 → P2 → E5.
- `GameRow.kt`: H13 → P1.

### 2. Steps that need Patrick's confirmation before starting
- **Sonnet uses:** H9, N1, N8, E1, E2, E9 and C2. **Opus:** none. **Deployment:** E2 and E9.
- **Decisions:**
  - time display and league-day rollover (H13, H14, H15);
  - the calendar confirm step (H18);
  - drawer type size versus R10 (P5);
  - the rail redesign, if P4 fails;
  - whether the design's nav glyphs are final (P7);
  - backup policy (E1).
- **Timing and sign-offs:**
  - G1 needs a live game;
  - visual sign-offs H17, P6 and P9;
  - manual checks N10 and E8;
  - the disclosure wording in N8.

### 3. Steps that could not be split cleanly, and why
- **H1a:** a new sealed `LoadError` subtype and every exhaustive `when` over it must land together, or nothing compiles. I verified the one in `ScoresScreen.kt`; the compiler will list any others.
- **H14:** the new `:domain` dependency and its first user land together.
- **N4:** the WorkManager `Configuration.Provider` and removing the default initializer from the manifest only work as a pair.
- **G11 and N9:** a route and its container dependency are only testable together.
- **N8:** the permission request needs both the manifest declaration and the UI launcher.
- **E2:** the build type and its R8 keep rules are one unit.
- **P3, P4, P5:** these are audits, so any fix step they spawn can't be known in advance.

### 4. Steps per level
Compared with the remaining steps (S6b.6 → S11.2) in `docs/stage-plan.md`:

| Level | This plan | Previous plan, remaining |
|---|---|---|
| Free | 24 | 13 |
| DeepSeek | 18 | 3 |
| Haiku | 11 | 7 |
| Sonnet | 7 | 4 |
| Opus | 0 | 0 |
| **Total** | **60** | **27** |

The growth comes from four places:
- 19 hardening steps;
- splitting detail parsing and the detail screen by file;
- the new ledger, WorkerFactory and backup steps;
- moving the steps on the live path off the free model.
