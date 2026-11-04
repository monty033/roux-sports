# Roux Sports v1: plan, sections 1–7

> **Plan v2.** A post-review plan for the remaining stages lives in `stage-plan-v2.md` (60 tiered steps, and it supersedes this document from Stage 6b onward, including two corrections it makes to `stage-6b-parity.md`).

**Before you start.** I read only `roux-plan-brief.md`. To get the version numbers in §5 right, I also made read-only HTTP GETs to Maven Central, Google Maven, services.gradle.org and developer.android.com on 2026-10-25. Three things in the brief need attention before work begins:
- **SDK gap.** Current stable Compose (1.12.x, BOM 2026.09.00) needs **compileSdk 37 and AGP ≥ 9.2**. AGP 9.4 also defaults to **build-tools 36.0.0**. The host only has `android-35` and build-tools 34/35. See §3 R1 and blocking question B1.
- **Notifications (A7, D4).** WorkManager-only notifications can't deliver near-real-time goal alerts while the phone is in Doze. A7 as written can only be met on a best-effort basis. See R5 and B3.
- **Fork flow (D6).** Forgejo, like Gitea, is expected to refuse forking an empty repository. Someone has to make the first commit upstream by hand. See R11 and B5.

---

## 1. Stages

**PR gate** (required at every stage exit, plus the stage-specific checks below):
`nix develop --command ./gradlew spotlessCheck lint testDebugUnitTest :app:assembleDebug`

**Manual device gate:** the human sideloads the debug APK onto the Pixel 9 (Android 16) and works through the stage checklist.

| # | Stage | Goal (one line) | Exit gate |
|---|---|---|---|
| 0 | Toolchain & skeleton | One-line debug build from a clean checkout, with `:domain`, `:data`, `:app` modules and every quality gate wired in from day one | G1 command produces `app/build/outputs/apk/debug/app-debug.apk`. PR gate green, including the KMP-boundary test (G4) and the banned-dependency check. `./gradlew testDebugUnitTest --dry-run` lists `:domain:test` and `:data:test`. README documents the command. Human installs a blank app on the Pixel 9. |
| 1 | Domain model & NHL adapter | Turn fixture bytes into domain types behind one defensive adapter; no network yet | Adapter tests pass on every captured fixture (score, schedule, standings, standings-season) and on mutated fixtures (fields removed, unknown enums, empty `gameWeek`). The adapter-isolation scan passes. |
| 2 | Data layer | Ktor client (with redirect handling and a host allowlist), raw-response cache, freshness/poll policies, DataStore prefs, repositories | MockEngine tests pass for: 200, the **307 → dated standings** path, a non-allowlisted host being rejected, and failure falling back to cached data marked Stale. Prefs survive re-creating the store (G6, JVM level). PollPolicy table tests pass. |
| 3 | App shell & one-handed design system | Theme (light/dark), bottom nav (Scores / Standings / Teams), reach rules as code, AppContainer DI, edge-to-edge | Robolectric reach tests pass for the shell at Pixel 9 size. Light/dark Roborazzi baselines recorded. Pixel 9 check: nav is reachable one-handed. |
| 4 | Scores screen | A1, A2, A3, A11, plus the A5 favourites/all toggle | ViewModel tests with virtual time: 25 s cadence when a game is live, back-off when none are, polling stops when unsubscribed. Compose tests for row states, empty date, stale banner, empty-favourites hint. Reach test passes. Pixel 9 check on a game night. |
| 5 | Favourites | Team picker in the Teams tab, persistence, favourites-only by default | Picker and persistence tests pass. Pixel 9 check: favourites survive force-stop and an upgrade install (G6). |
| 6 | Standings | A4: league, conference, division and wildcard views, favourites pinned when the filter is on | Grouping and wildcard tests on the standings fixture, pinning test, reach test. Pixel 9 check. |
| 7 | Game detail | A6 boxscore surface that degrades one section at a time | **Entry check:** capture gamecenter fixtures (R9). Adapter tests with each section removed. Compose tests confirm a missing section is hidden and nothing crashes. Pixel 9 check (live and final game). |
| 8 | Notifications | A7: opt-in game-start and goal alerts per favourite team via WorkManager and local notifications | WorkManager `TestDriver` tests: a new goal produces exactly one notification, no duplicate across runs, nothing enqueued when the user opted out, POST_NOTIFICATIONS denial handled. Pixel 9 live-game check with latency recorded. |
| 9 | Design & accessibility audit | A8/A10 sign-off across all screens: reach, light/dark, contrast, font scale, TalkBack labels | Reach tests pass on every screen at font scale 1.0 and 2.0. Roborazzi verify passes in both themes. Contrast assertions pass. Human signs off a one-thumb walkthrough. |
| 10 | Release readiness | Signed release APK, F-Droid-compatible metadata, A9/D4 audits | `assembleRelease` succeeds with R8. Banned-dependency and network-host audits are clean. `dependenciesInfo` is disabled. Human installs the release build over the debug build's data and runs the smoke checklist. The human tags the release. |
| 11 | CI (optional, off the critical path) | Forgejo Actions running the PR gate, only if the runner check passes | The probe job (see §6) passes, then the gate job goes green on a PR from the `openclaw` fork. |
| 12 | Player detail (added after the club page landed) | Tapping a player in the club page's Players tab opens that player's own screen | Adapter tests on captured `player/{id}/landing` fixtures (one skater, one goalie): name, sweater number, position, height/weight, birth date and city, career totals, season totals, last-five-game rows. A player with no season rows still renders with dashes rather than an empty card. Navigation test: the Players row is the tap target, back returns to the club page with the same tab and scroll position. Reach test on the row at 48 dp and on the back control. Pixel 9 check. |

| 13 | Team Stats tab (the club page's third tab) | The club page's `TeamStats` seat stops being a coming-soon placeholder and shows the club's league-relative numbers | Adapter test on the captured league summary: 32 clubs, one row per club, the club's own ten values, and a null percentage staying null rather than becoming zero. Domain tests for the rank directions (a lower-is-better metric ranking its lowest value first) and for competition ties (`1, 2, 2, 4`). A club with no row shows "no league stats yet" rather than zeros. Navigation test: tapping the tab selects it and shows the table. TalkBack reads metric, value and `of 32` per row. Pixel 9 check. |

**Changes from the brief's stage list, with reasons:**
- **Data/domain is split into Stage 1 (parsing) and Stage 2 (I/O).** Parsing is the riskiest and most testable part, and it needs nothing but fixtures. Splitting keeps each gate small and lets drift be tested without a network.
- **The one-handed design work moved to Stage 3, before any screen.** A8 is a hard requirement. Retrofitting reach onto finished screens is the most expensive way to get it. Stage 9 becomes an audit, not the first time reach is designed.
- **Favourites (5) comes right after Scores (4).** Scores defaults to favourites-only, so the picker should land next. Stage 4 handles an empty favourites list with a bottom-zone "Pick teams" prompt and falls back to the whole league.
- **Offline behaviour (A11)** is built in Stage 2 (cache plus freshness) and shown in Stage 4, not given its own stage.
- **Stages 6 and 7 are independent of each other** once Stage 4 exists.
- **Stage 12 sits last because it was asked for after the club page shipped, not because it depends on the release work.** Its data source needs no new host: `api-web.nhle.com/v1/player/{id}/landing` answers 200 on the already-allowlisted host and carries the name, sweater number, position, height, weight, birth date and city, `careerTotals.regularSeason`, `seasonTotals[]` and `last5Games[]` for a skater and a goalie alike, so nothing about it is blocked on the Team Stats host decision.
- **Stage 13 is unblocked and sits beside stage 12 because both are club-page tabs.** The league summary is one request that returns all 32 clubs (`api.nhle.com/stats/rest/en/team/summary`, verified today: 200, 32 rows, 16 KB), which is why it needed the one host approval rather than a 32-request fan-out; `api-web.nhle.com` has no equivalent and answers 404 on `team-stats` and `club-stats-season`. It carries no club abbreviation, only `teamFullName` and `teamId`, and that name matches the standings' `teamName.default` for all 32 clubs, so the join is on the name the club page already holds.

---

## 2. Architecture

### Module and package layout
```
roux-sports/
├─ flake.nix, flake.lock          # devShell only (§4)
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
├─ gradle/libs.versions.toml, gradle/wrapper/{gradle-wrapper.jar,.properties}, gradlew
├─ domain/  (org.jetbrains.kotlin.jvm + com.android.lint)   pkg net.montycasa.roux.domain
│   model/    Team, TeamId, Game, GameState(Scheduled|Pre|Live|Crit|Final|Off|Unknown(raw)),
│             PeriodInfo, ScoreDay, DateIndex, StandingsTable, GameDetail(sections all nullable)
│   repo/     ScoresRepository, StandingsRepository, GameDetailRepository, PreferencesRepository (interfaces)
│   policy/   PollPolicy, FreshnessPolicy, StandingsGrouping (incl. wildcard), FavoritesOrdering
│   Loadable<T>(data: T?, freshness: Fresh|Stale(since)|Missing, error: LoadError?)
├─ data/    (org.jetbrains.kotlin.jvm + kotlin.serialization + com.android.lint)  pkg net.montycasa.roux.data
│   nhl/      ◀── SINGLE ADAPTER BOUNDARY
│     dto/    internal @Serializable DTOs, every field nullable with a default
│     NhlAdapter.kt   parseScore / parseStandings / parseSeasons / parseGameDetail(bytes): ParseResult<Domain>
│   net/      NhlHttpClientFactory (engine injected, host allowlist, UA, timeouts, redirects), NhlApi (paths)
│   cache/    ResponseCache (okio, raw bytes + envelope {key, fetchedAtMs, envelopeVersion}), LRU eviction
│   prefs/    DataStorePreferencesRepository (datastore-preferences-core, okio Path)
│   repo/     *RepositoryImpl = api + cache + adapter + policy
└─ app/     (com.android.application, AGP built-in Kotlin, compose plugin)   pkg net.montycasa.roux
    RouxApp (AppContainer: manual DI), MainActivity
    ui/theme, ui/reach (Reach constants, Modifier.primaryControl tag), ui/nav,
    ui/scores, ui/standings, ui/detail, ui/teams (favourites + notification prefs + about)
    notify/   GameWatchScheduler, GameWatchWorker, Notifier, channels
```

### The adapter boundary (G5)
- **Single entry point.** `data/nhl/NhlAdapter` is the only code that reads NHL JSON. DTOs are `internal`. A source-scan test asserts that only files under `data/.../nhl/` import `...data.nhl.dto`.
- **JSON settings.** `Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }`.
- **Enums never fail.** Values are parsed as strings and mapped to `Unknown(raw)`.
- **Per-game isolation.** A malformed game is dropped and counted; it doesn't fail the whole day.
- **Boxscore sections are nullable.** If a section is missing, it's `null` and the UI hides it.
- **The adapter never throws.** Top-level failures become `ParseResult.Failure`, which the repository treats like a network failure: it serves the cached copy marked Stale.
- **Versioning.** `NhlAdapter.VERSION` is a constant. Fixtures live in dated folders (`fixtures/2026-10-25/...`), so new captures sit next to old ones and old shapes keep being tested.

### KMP-readiness (G4)
There are two independent locks:
1. **Structural.** `:domain` and `:data` are pure Kotlin/JVM modules, so `android.jar` isn't on their classpath and an `android.*` reference can't compile.
2. **Test.** `domain/src/test/.../KmpBoundaryTest.kt` scans `domain/src/main` and `data/src/main`:
   - It fails on any `android.*` import or fully qualified `android.` reference.
   - It fails on `java.*` / `javax.*` imports, for KMP hygiene.
   - It fails on `androidx.*` imports unless they're on an allowlist: `androidx.datastore.core`, `androidx.datastore.preferences.core`, `androidx.annotation`.
   - The scanner itself has a unit test fed a planted `import android.os.Bundle`.

Libraries were chosen so they don't break the boundary: Ktor (the OkHttp engine is injected from `:app`), kotlinx.serialization, kotlinx-datetime (no `java.time`), okio, DataStore core, coroutines.

### State management
- **Pattern.** One `ViewModel` per screen, exposing a single `StateFlow<UiState>`. User events are plain functions. There's no MVI framework.
- **Polling.** The poll loop lives inside the upstream flow, which uses `stateIn(viewModelScope, WhileSubscribed(5_000))` and is collected with `collectAsStateWithLifecycle`. Going to the background stops collection, which cancels polling. Returning to the foreground resubscribes and refreshes immediately if data is stale. A3's lifecycle behaviour therefore comes from the platform, not hand-written code.
- **Why this approach.** It's the standard Android approach, it's testable with `runTest` virtual time and Turbine, and androidx `ViewModel` is now KMP-capable, so it can move into a shared module later.
- **DI.** Manual `AppContainer`, with no Hilt/KSP. That means fewer build parts, and it's friendly to a later KMP move.

### Storage
DataStore Preferences (core, KMP) holds:
- favourites, stored as a `Set<String>` of numeric **team IDs**, not abbreviations (abbreviations do change);
- the favourites-only toggle;
- per-team notification prefs (`start` / `goals`);
- a small notification dedup ledger (`gameId → lastNotifiedGoalCount, startNotified`).

Room isn't used because none of this is relational. The files live in `filesDir`, so they survive process death and upgrades (G6).

### Caching and freshness
The cache stores **raw response bytes**, so an adapter change never invalidates it, and cold starts re-parse (cheap). Policy:

| Data | Fetched | Fresh for | Prefetch | Retention |
|---|---|---|---|---|
| `/v1/score/{date}` for the selected date | on selection, pull-to-refresh, poll | today with a live game: 25 s; today, nothing live: 5 min (or until the next start minus 1 min); future: 6 h; past and all games final: never refreshed except by pull-to-refresh | ±1 day around the selected date, only after it settles and only if not already fresh. **No season prefetch.** | LRU, 30 files (~3 MB) |
| `/v1/standings/{date}` (always the dated form) | Standings tab | 10 min if any game is live, else 1 h | none | the latest 3 |
| `/v1/standings-season` | first launch, then daily | 24 h | none | 1 |
| gamecenter detail (Stage 7) | when the detail screen opens | live: 30 s while visible; final: never refreshed | none | LRU, 20 games |
| Logos (Coil disk cache) | on demand | HTTP semantics | none | 10 MB |

- **Schedule endpoint.** `/v1/schedule/{date}` isn't called in v1. Score is a superset of what the list needs, and its `gameWeek[].numberOfGames` drives the date strip. One schedule fixture is still captured for comparison.
- **Stale indicator.** If a fetch fails while cached data exists, the UI shows `Stale(since)` as a bottom-zone banner ("Updated 19:42 · Retry"). A cold start with no network renders the cache. An error screen appears only when the cache is `Missing`.
- **PollPolicy (pure function in domain).** Any visible game Live or Crit → 25 s. A visible game starting within 10 minutes, or in Pre → 60 s. Today otherwise → 5 min. Any other date with nothing live → no polling. Failures back off 25 → 50 → 100 → 300 s (cap) with ±10% jitter.

### One-handed design rules (A8), all checked by tests
Rules are given as fractions of window height H, so they hold on a 6.1″ phone and on the Pixel 9 (≈411×923 dp). Tests run at Robolectric qualifiers `w411dp-h923dp-420dpi` and at a small-phone `w360dp-h780dp`.

| Rule | Requirement |
|---|---|
| R1 Reach zone | The bounding box of every **primary control** (tagged with `Modifier.primaryControl`) starts at or below **0.50·H** |
| R2 Frequent controls | Date strip, favourites/all toggle and bottom nav start at or below **0.70·H** (bottom 30%) |
| R3 Bottom nav | M3 `NavigationBar`, 80 dp plus the gesture inset, **exactly 3** destinations (each ≥ 120 dp wide at 360 dp) |
| R4 Target size | All targets ≥ **48×48 dp**; primary targets ≥ **56 dp** tall; ≥ **8 dp** edge-to-edge gap between adjacent targets |
| R5 Edges | No primary control within **16 dp** of the left or right edge (thumb arc and the system back gesture). The date strip uses 16 dp content padding. |
| R6 No hidden actions | No top-app-bar `actions`, no overflow or `DropdownMenu` in any top bar, no top FAB. Confirmations use `ModalBottomSheet` with buttons at the bottom. A source-scan test enforces this. |
| R7 Detail | Opens full-screen with a **bottom** action bar (Back, 56 dp). The system back gesture also works. |
| R8 Refresh | Pull-to-refresh on every list, plus Retry in the bottom-zone stale banner |
| R9 Live distinction (A2) | Not colour alone: a 4 dp leading accent bar, a filled "live" dot, and a bold period/clock. Non-text contrast ≥ **3:1** in both themes. |
| R10 Type & contrast | Scores ≥ 20 sp with tabular figures; body ≥ 14 sp; text contrast ≥ **4.5:1**. R1 and R2 must still hold at font scale **2.0**. |
| R11 Theme | Fixed light and dark palettes; dynamic colour is off in v1 so both themes look deliberate (A10) |

### Network surface (A9)
- **Allowlist.** A Ktor plugin allows only `api-web.nhle.com`, plus `assets.nhle.com` for the logo URLs embedded in the payloads (pending B2). Anything else throws.
- **Shared client.** Coil uses the same client (`coil-network-ktor3`), so logos go through the same allowlist.
- **No silent extras.** OkHttp's HTTP cache is off, and there's no other HTTP stack.

### Widgets later (D5)
A future Glance widget in `:app` would read the same cache-first repositories through `AppContainer` and refresh from the existing WorkManager scheduler, with no change to domain or data.

---

## 3. Risk register

| ID | Risk | L | I | Mitigation | Stage |
|---|---|---|---|---|---|
| R1 | **Host SDK too old for the current stack**: Compose 1.12 needs compileSdk 37, AGP 9.4 defaults to build-tools 36.0.0; host has android-35 and BT 34/35 | Certain | High | B1: install `platforms;android-37` and `build-tools;36.0.0`. Set `android.builder.sdkDownload=false` so a missing package fails loudly instead of silently changing the host. Doctor script asserts the packages. | 0 |
| R2 | Undocumented API drift | High | High | Adapter boundary and nullable DTOs (§2), `Unknown(raw)` enums, per-game isolation, mutated-fixture tests, dated fixture folders. Drift shows as stale data, never a crash. | 1, 2 |
| R3 | `standings/now` returns 307 | Certain | Med | Always call `/v1/standings/{date}`. Ktor redirect-following stays on as a backstop. The allowlist covers the same-host redirect. A MockEngine test covers the 307 path. | 2 |
| R4 | Payload size (~94 KB per date) and mobile data use | High | Med | One endpoint per date, ±1-day prefetch only, gzip negotiated, polling only while foregrounded and live, LRU caps. Stage 2 records the actual transfer size. | 2, 4 |
| R5 | **Notifications under Doze, standby buckets and the 15-minute periodic floor**: WorkManager-only goal alerts will be minutes to tens of minutes late when the device is idle | High | Med | Game start: one-time work with `initialDelay` set to the start time. During a favourite's live game: self-rechaining one-time work every ~3 min with network constraint, stopping at FINAL. Dedup ledger. In-app copy says alerts are best-effort. B3 decides whether to offer a battery-optimisation exemption. Latency is measured on the Pixel 9. | 8 |
| R6 | Toolchain reproducibility | Med | High | `flake.lock` pins the JDK. The wrapper is pinned with `distributionSha256Sum`. The version catalog pins everything else. The SDK is host state, not pinned by Nix: the README lists the exact packages and the doctor script verifies them. | 0 |
| R7 | NixOS and prebuilt dynamic binaries: AGP's Maven aapt2; Robolectric native-graphics `.so` (Roborazzi) | Med | High | The Stage 0 smoke run exercises both (`assembleDebug` plus one native-graphics Compose test). Fallbacks: `android.aapt2FromMavenOverride` pointing to the SDK's build-tools aapt2, or targeted `LD_LIBRARY_PATH` in the devShell. nix-ld is a human-only host change (nixos-rebuild is excluded for agents). | 0 |
| R8 | No device or emulator on the build host | Certain | Med | Robolectric, Compose UI tests, Roborazzi and reach and contrast assertions run on the JVM. Device-only behaviour (Doze, real polling, gesture nav, notification delivery, install-over-upgrade) goes into explicit per-stage Pixel 9 checklists signed off by the human. | all |
| R9 | Game-detail endpoints (`/v1/gamecenter/{id}/landing`, `/boxscore`, `/right-rail`) **not probed** | Med | Med | Stage 7 entry: capture fixtures for one live and one final game. Map A6 sections to their sources. If a section has no source, it's hidden and raised to the human. | 7 |
| R10 | Logos come from `assets.nhle.com` (A9 scope); bundling them instead would bring trademark issues and F-Droid's NonFreeAssets flag | Med | Med | B2. Fallback: an abbreviation badge with no image. | 4 |
| R11 | Forgejo won't fork an empty repo, and D6 forbids pushing upstream | Med | Med | B5: the human creates the initial upstream commit (LICENSE plus a stub README), then forks to `openclaw`. | 0 |
| R12 | NHL rate limiting or blocking | Low | High | Honest User-Agent, request caps, backoff with jitter, honour 429 / `Retry-After`, no parallel bursts | 2 |
| R13 | Time zones, DST and which day a game belongs to | Med | Med | kotlinx-datetime with an injected `Clock` and `TimeZone`. Games group by the feed's game date. Start times shown local. Tests at DST transitions and midnight. | 1, 4 |
| R14 | Library compileSdk churn (Compose 1.13 alpha already needs 37.1) | Med | Med | Pin exact versions. AGP's `checkAarMetadata` is the tripwire. Rule: step down one minor and record why. | 0 |
| R15 | **G2 gap**: `testDebugUnitTest` exists only in `:app`, so JVM-module tests wouldn't run under the named gate | Certain | High | `:app:testDebugUnitTest` depends on `:domain:test` and `:data:test`. The Stage 0 exit checks this with `--dry-run`. | 0 |
| R16 | Season edges: off-season, preseason, playoffs (wildcard not meaningful), international or special-event teams outside the 32 | Med | Low | Unknown teams render as abbreviation only. Empty or off-season dates show "No games · Next: <date>". Playoff bracket is out of scope. | 1, 4, 6 |
| R17 | F-Droid scanner findings (AGP dependency-info blob, proprietary dependencies) | Med | Med | `dependenciesInfo { includeInApk = false; includeInBundle = false }`. A banned-coordinate check (`com.google.android.gms`, `com.google.firebase`, `com.google.android.play`, known analytics SDKs) on the runtime classpaths runs from Stage 0. | 0, 10 |

---

## 4. Toolchain decision

**Approach.** A Nix flake that only provides a `devShell`, plus an in-repo Gradle wrapper. There's no `shellHook` that runs anything, nothing launches a nested shell, and direnv is optional.

**Env vars set by the devShell:**

| Var | Value |
|---|---|
| `JAVA_HOME` | `${pkgs.jdk21.home}`, which evaluates to `/nix/store/<hash>-openjdk-21.0.x/lib/openjdk` with the hash fixed by `flake.lock`. JDK 21 because Robolectric needs it for SDK ≥ 35; AGP's minimum is 17. |
| `ANDROID_HOME` | `/var/lib/hermes/android-sdk` |
| `ANDROID_SDK_ROOT` | `/var/lib/hermes/android-sdk` (must match `ANDROID_HOME`; AGP errors if they differ) |
| `PATH` (prepended) | `$JAVA_HOME/bin` (from `packages = [ jdk21 ]`) and `/var/lib/hermes/android-sdk/cmdline-tools/latest/bin` (sdkmanager, used for B1) |

`GRADLE_USER_HOME` stays unset, so the existing `~/.gradle` cache is reused. The flake's nixpkgs input is `nixos-26.05`, locked.

**`gradle.properties`:**
- `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`
- `kotlin.daemon.jvmargs=-Xmx2g`
- `org.gradle.parallel=true`
- `org.gradle.caching=true`
- `org.gradle.configuration-cache=true`
- `org.gradle.java.installations.auto-download=false`
- `android.builder.sdkDownload=false`
- `android.useAndroidX=true`

No Gradle `jvmToolchain()` is declared. Bytecode target 17 is set explicitly, and the JDK comes only from `JAVA_HOME`.

**Wrapper bootstrap (once, Stage 0):**
`nix shell nixpkgs#gradle --command gradle wrapper --gradle-version 9.8.2 --distribution-type bin --gradle-distribution-sha256-sum <sha256 from services.gradle.org>`

After that, only `./gradlew` is used. `gradle-wrapper.jar` is committed and its checksum validated.

**Flake gotchas:**
- `flake.nix` and `flake.lock` must be tracked by git before `nix develop` can see them.
- If flakes aren't enabled on the host, the README fallback is the same command with `--extra-experimental-features 'nix-command flakes'`.

**The single documented build command (G1). It goes in the README in Stage 0:**
```
nix develop --command ./gradlew :app:assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 5. Version pins

Versions were read from Maven and Google Maven metadata on 2026-10-25.

**Policy:** use the newest minor that has been out for at least two weeks, at its latest patch. Patch releases are accepted right away. Where the newest minor is only days old, the previous minor is pinned (marked †).

| Item | Pin | Reason |
|---|---|---|
| AGP (`com.android.application`, `com.android.lint`) | 9.4.1 | Current stable; supports API 37; Compose 1.12 needs ≥ 9.2; built-in Kotlin |
| Gradle (wrapper) | 9.8.2 | Current; AGP 9.4 needs ≥ 9.6.0 |
| Kotlin (KGP, `plugin.compose`, `plugin.serialization`) | 2.4.21 | Current stable patch; one Kotlin version everywhere, which AGP's built-in Kotlin also uses |
| JDK (Gradle runtime) / bytecode target | 21 / 17 | Robolectric needs JDK 21 for SDK ≥ 35; 17 is the safe Android bytecode level |
| compileSdk | 37 | Required by Compose 1.12 stable |
| targetSdk | 36 | Matches the only verification device (Pixel 9, Android 16). Lint `OldTargetApi` is disabled with this reason given. See non-blocking question N1. |
| minSdk | 31 | A10: Android 12+ |
| build-tools | 36.0.0 | AGP 9.4 default |
| Compose BOM | 2026.09.00 | Latest stable BOM (Compose 1.12.x); Material 3 comes from the BOM |
| activity-compose | 1.13.0 | Stable, ≥ 2 weeks old |
| lifecycle (runtime-compose, viewmodel-compose) | 2.10.0 † | 2.11.0 shipped 2026-10-21; `collectAsStateWithLifecycle` |
| navigation-compose | 2.10.2 | Type-safe routes, the common choice for 3 tabs plus a detail screen |
| core-ktx | 1.19.1 | Stable |
| work-runtime-ktx / work-testing | 2.12.0 | A7 with no GMS; `TestDriver` for JVM tests |
| datastore-preferences-core | 1.2.1 † | KMP, no Android types; 1.3.0 shipped 2026-10-21 |
| Ktor (core, okhttp engine in `:app`, mock in tests) | 3.6.0 | KMP HTTP; redirects and plugins for the allowlist |
| kotlinx-serialization-json | 1.11.0 † | KMP JSON for the adapter; 1.12.0 shipped 2026-10-19 |
| kotlinx-coroutines (core, test) | 1.11.0 | Stable |
| kotlinx-datetime | 0.8.0 | KMP dates and zones (no `java.time` in domain/data) |
| okio | 3.18.2 | KMP file I/O for the cache and DataStore paths |
| Coil 3 (compose, svg, network-ktor3) | 3.6.3 | Open-source, KMP; SVG logos through the shared Ktor client and allowlist |
| Robolectric | 4.17 | JVM UI tests; supports SDK 36/37 |
| Roborazzi (plugin, compose, junit-rule) | 1.76.0 † | JVM screenshot tests for light/dark; 1.77.0 is days old |
| JUnit | 4.13.2 | Required by Robolectric and compose ui-test-junit4 |
| Turbine | 1.2.1 | Flow assertions |
| Spotless plugin / ktlint | 8.10.4 / 1.8.0 | Formatter gate (G3). detekt left out on purpose (N6). |
| Android Lint | via AGP | `abortOnError`, `warningsAsErrors`, `checkDependencies`; applied to the JVM modules via `com.android.lint` |

If any pin fails `checkAarMetadata` or fails to compile together with the others, step down one minor and record the reason in `libs.versions.toml`.

---

## 5a. R4 audit boundaries (recorded, not implied)

The reach audit is a geometry check over the Compose semantics tree. It answers "how much of this target can ever be
under a thumb", which is what R4 needs, and it deliberately does not answer anything else. Two classes of defect are
outside it by construction rather than by oversight:

- **Z-order.** A target painted over by a later sibling, or one whose taps an untagged overlay consumes, still
  measures its full size and passes. Catching that needs hit-testing, not measurement, and belongs with the device
  pass.
- **Right-to-left.** Compose's RTL `horizontalScroll` reports `reverseScrolling = false` while its content moves right
  as the value grows - probed under Robolectric, not assumed. So a mirrored layout is measured in the wrong direction.
  The audit honours `reverseScrolling` when a caller sets it and assumes LTR otherwise, which matches the English-only
  v1. Localisation must revisit this before any RTL build ships.

### Collections are reach targets, actions are primary controls (S5.2)

R1 puts a primary control at or below 0.50·H, and R2 tightens that to 0.70·H for frequent ones. That is a rule about
**fixed actions**: the date strip, the favourites toggle and the bottom bar all sit in the bottom zone of the scores
screen for exactly this reason, and the game rows above them are clickable yet carry no primary tag.

A collection of items - the thirty-two team toggles, a list of games - is the other case. Its rows are reached by
scrolling, so applying R1 to each row asks for something structurally impossible: thirty-two controls cannot all start
below the midpoint. The first attempt satisfied the letter of R1 by giving the header a weight and squeezing the list
into the bottom half, which passed every test and showed **five of thirty-two teams**.

The rule, therefore:

- **Fixed actions** (one per screen, pressed where they sit) are `primaryControl`, and R1/R2/edge insets apply.
- **Rows in a scrolling collection** are `reachTarget`: R4 size and gap, and R5 containment, still apply; R1 and R2 do
  not.
- A screen still declares its own primary controls, and the audit fails if the tree's declaration and the screen's
  differ - so changing a row's classification has to be done deliberately, in both places.

This is a judgement about reachability, not an exemption from it: if a collection ever becomes a single action (a
one-tap "confirm all"), that action is primary and belongs in the bottom zone.

## 6. CI recommendation

> ⚠️ **Unverified.** Nobody has checked whether the Forgejo instance's Actions runner can do an Android build (JDK 21, SDK with platform 37 and build-tools 36.0.0, Gradle, about 8 GB RAM). CI is therefore **Stage 11: optional, last, and off the critical path.** No other stage depends on it.

- **Hard prerequisite: a probe job, before any gate job.** A `workflow_dispatch` job on the runner reports:
  - `nix --version` (or `java -version`);
  - whether `$ANDROID_HOME/platforms/android-37` and `build-tools/36.0.0` exist, or whether the SDK can be provisioned in the job;
  - free disk ≥ 15 GB and RAM ≥ 8 GB;
  - outbound access to Maven Central, Google Maven and services.gradle.org.

  If anything fails, Stage 11 stops and becomes a question for the human (options: a custom runner image with JDK 21 and cmdline-tools, or Nix on the runner).
- **Gate job (only after the probe passes).** Runs the same PR-gate command as §1 inside `nix develop`, caching `~/.gradle`. It needs no secrets, which suits fork PRs. It should run only for PRs from the trusted `openclaw` fork, because running arbitrary fork PRs on a self-hosted runner is a host-security risk.
- **Manual equivalent until then.** The executing agent runs the PR-gate command locally and pastes the tail of the output (task summary and test counts) into the PR description. The reviewer checks that against the diff. The human squash-merges (D7). Device checks stay manual either way.

---

## 7. Open questions

### Blocking (each lists the stage it blocks)
- **B1 (Stage 0): SDK additions.** May the executing agent run `sdkmanager "platforms;android-37" "build-tools;36.0.0"` against `/var/lib/hermes/android-sdk`? That's host state outside the repo, plus a download.
  - Option 1, recommended: yes.
  - Option 2: provide the SDK via Nix `androidenv`. More reproducible, but heavier, and it needs licence acceptance in Nix config.
  - Option 3: stay on compileSdk 35 by pinning mid-2025 Compose and AGP 8.x. Not recommended: stale on day one.
- **B5 (Stage 0's first PR): repository bootstrap.** Does `patrick/roux-sports` exist on Forgejo? If it's empty, will you make the single initial upstream commit (LICENSE plus a stub README) so `openclaw` can fork it? Does the executing agent have push access to the `openclaw` fork?
- **B2 (Stage 4): logo host.** Does A9's "only NHL endpoints" include `assets.nhle.com`, the NHL CDN behind the logo URLs in the API payloads? Recommended: yes, allowlisted. The alternatives are bundled logos (trademark issues, F-Droid NonFreeAssets) or abbreviation badges only.
- **B3 (Stage 8): goal-alert latency under A7.** Choose one:
  - (a) Accept best-effort latency with in-app disclosure. Recommended.
  - (b) Same as (a), plus an opt-in prompt for a battery-optimisation exemption. Fine for F-Droid and sideloading.
  - (c) A foreground service during favourite games. This contradicts A7's literal wording and needs a decision change.
- **B4 (Stage 10): release signing and channel.** Who holds the release keystore, and where (never in the repo)? Where is v1 published: Forgejo releases, a self-hosted F-Droid repo, or both?

### Non-blocking (defaults in brackets)
- **N1.** targetSdk 36 (matches the device) or 37 (latest)? [36]
- **N2.** Dynamic colour off, with fixed deliberate palettes? [off in v1]
- **N3.** Day grouping: by the league's game date, with local start times and a "+1d" marker when a game crosses local midnight? [yes]
- **N4.** Show preseason and playoff games, labelled PRE / PO? Playoff bracket stays out of scope. [yes]
- **N5.** Suppress notifications for a game while the app is in the foreground? [yes]
- **N6.** Add detekt on top of ktlint and Lint? [no in v1]
- **N7.** Commit raw NHL JSON fixtures (~94 KB each) to the GPL repo, or trimmed versions? [raw, plus mutated copies]
- **N8.** User-Agent string. [`RouxSports/<version> (+https://git.montycasa.net/patrick/roux-sports)`]
- **N9.** Name of the third tab. [Teams: favourites, notification settings, about]
- **N10.** English only in v1, with strings externalised? [yes]
- **N11.** Submit to the main F-Droid repo in the v1 timeline or after? [after]

STEP blocks follow in turn 2.
# Roux Sports v1: plan part 2 (changes from your answers, STEP blocks, summary)

## Changes to §1–§5 from B1–B5

**§1 Stages**
- **Stage 0**: adds the one-time upstream bootstrap (S0.1). The SDK now comes from the flake. The Stage 0 exit gate changes:
  - The first `nix develop` builds the SDK. It runs as a background command with no timeout, budgeted at up to 90 minutes.
  - `scripts/check-toolchain.sh` must pass inside the devShell.
  - After you revoke Write, a test push to upstream must be rejected.
- **Stage 8 exit**: also requires the best-effort disclosure text (tested), and scheduling must sit behind `GameWatchScheduler`.
- **Stage 10**: releases go to Forgejo only. Signing reads a gitignored `keystore.properties`, and you hold the keystore.

**§2 Architecture**
- New `domain/notify/GameWatchScheduler` interface. WorkManager is the v1 implementation, and a UnifiedPush distributor could implement the same interface later without touching the decider, the worker logic or the UI.
- Notifications are delivered at most once: the dedup ledger is written before posting.
- The `assets.nhle.com` allowlisting is confirmed.
- Favourites are keyed by `TeamKey`. S1.6 checks that the key matches across the score and standings feeds: numeric id if both feeds carry it, otherwise the three-letter team code.

**§3 Risks**
- **R1 retired.** It's replaced by R18 and R19:
  - **R18**: the SDK is read-only in the Nix store. Anything that tries to install or write fails loudly. `android.builder.sdkDownload=false` stays, and no script calls `sdkmanager`.
  - **R19**: the first SDK build is slow and takes store disk. The budget is in the Stage 0 gate and the README warns about it.
- **R5 decided**: option (a), best-effort with in-app disclosure.
- **R6 improved**: `flake.lock` now pins the SDK too.
- **R7 mostly resolved for aapt2**: `GRADLE_OPTS` overrides it with the Nix-patched aapt2. The Robolectric native-graphics risk is still open; S0.9 tests it.
- **R10 closed.**
- **R11** is handled by S0.1.

**§4 Toolchain.** The nixpkgs import uses `config = { android_sdk.accept_license = true; allowUnfree = true; }` and `androidSdk = (pkgs.androidenv.composeAndroidPackages { platformVersions = [ "37" "36" "35" ]; buildToolsVersions = [ "36.0.0" ]; includeEmulator = false; includeSystemImages = false; includeNDK = false; }).androidsdk`.

| Var | Value |
|---|---|
| `JAVA_HOME` | `${pkgs.jdk21.home}` |
| `ANDROID_HOME` | `${androidSdk}/libexec/android-sdk` → `/nix/store/<hash>-androidsdk/libexec/android-sdk` |
| `ANDROID_SDK_ROOT` | same as `ANDROID_HOME` |
| `GRADLE_OPTS` | `-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2` |
| `PATH` (prepended) | jdk21 `bin`, `$ANDROID_HOME/build-tools/36.0.0` (aapt2 and apksigner for the audit scripts). `cmdline-tools` is not on PATH. |

- `/var/lib/hermes/android-sdk` is never referenced.
- The G1 command is unchanged: `nix develop --command ./gradlew :app:assembleDebug`.

**§5 Pins.** One row added: **Android SDK** from nixpkgs `androidenv` (pinned by `flake.lock`): platforms 37/36/35, build-tools 36.0.0. This is the exact set you evaluated.

**Conventions used in the STEP blocks**
- **Path shorthands:**
  - `dom/` = `domain/src/main/kotlin/net/montycasa/roux/domain/`
  - `domT/` = `domain/src/test/kotlin/net/montycasa/roux/domain/`
  - `dat/` = `data/src/main/kotlin/net/montycasa/roux/data/`
  - `datT/` = `data/src/test/kotlin/net/montycasa/roux/data/`
  - `fx/<cap>/` = `data/src/test/resources/fixtures/<capture-date>/`
  - `appM/` = `app/src/main/kotlin/net/montycasa/roux/`
  - `appT/` = `app/src/test/kotlin/net/montycasa/roux/`
  - `MANIFEST` = `app/src/main/AndroidManifest.xml`
- **File counts exclude:**
  - the step's acceptance-test file(s);
  - generated artifacts (lock file, wrapper, fixtures, screenshot baselines);
  - the step's own `res/values*/` files. N10 (strings externalised) uses per-feature `strings_<feature>.xml` files so string resources don't force steps to run one after another.
- **PR gate** = the §1 command. Every step must also pass it.
- **Device tests**: every Robolectric test runs at the Pixel 9 size (`w411dp-h923dp-420dpi`), plus `w360dp-h780dp` where reach is asserted.
- **Tier rule as applied:**
  - a hard trigger → Sonnet;
  - otherwise Q3 = Y → Haiku;
  - otherwise two production files with behaviour, or Q2 = N → DeepSeek;
  - otherwise (config, docs, scripts, tests only, or one production file) → Free.

---

### Stage 0: Toolchain and skeleton

STEP S0.1: Upstream bootstrap commit (one-time exception to D6)
- Files: `LICENSE` (canonical GPL-3.0 text), `README.md` (stub: name, one-line purpose, "GPL-3.0-or-later")
- Depends on: none. You grant `openclaw` temporary Write on `patrick/roux-sports` first.
- Acceptance test (file + assertion):
  - `LICENSE`: `sha256sum LICENSE` equals the sha256 of https://www.gnu.org/licenses/gpl-3.0.txt fetched at step time.
  - `git ls-remote git.montycasa.net:patrick/roux-sports refs/heads/main` equals local `git rev-parse HEAD`.
  - The commit subject is `chore: initial commit`.
  - After you revoke Write, `git push --dry-run git.montycasa.net:patrick/roux-sports HEAD:refs/heads/probe` exits non-zero.
- Risk class: auth/permissions
- Privacy class: public-repo (the push uses an SSH key, so no free model touches this step)
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: auth
- Tier: Sonnet
- Effort: low
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go, independent review. You confirm Write is revoked and create the `openclaw` fork.
- Reason: The temporary Write is a permissions boundary (hard trigger), and a push to upstream `main` isn't cleanly revertible (Q4 N).

STEP S0.2: Nix devShell with SDK from androidenv
- Files: `flake.nix`, `.gitignore` (`build/`, `.gradle/`, `local.properties`, `keystore.properties`, `result`). `flake.lock` is generated and must be `git add`ed before `nix develop`.
- Depends on: S0.1
- Acceptance test (file + assertion): `scripts/check-toolchain.sh`, written first. `nix develop --command scripts/check-toolchain.sh` exits 0. The script asserts:
  - the Java major version is 21;
  - `$ANDROID_HOME` equals `$ANDROID_SDK_ROOT` and starts with `/nix/store/`;
  - `$ANDROID_HOME/platforms/android-37/android.jar` exists;
  - `$ANDROID_HOME/build-tools/36.0.0/aapt2 version` runs;
  - `[ ! -w "$ANDROID_HOME" ]`;
  - `GRADLE_OPTS` contains `aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2`;
  - the script itself contains no `sdkmanager` call.

  The first run is a background command with no timeout and a 90-minute budget.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Config file plus script with a runnable check (Q1, Q2) and an easy revert (Q4).

STEP S0.3: Gradle wrapper, settings, properties
- Files:
  - `settings.gradle.kts`: rootProject name; `google()` and `mavenCentral()` repositories; `FAIL_ON_PROJECT_REPOS`.
  - `gradle.properties`: the §4 values.
  - Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, via `nix shell nixpkgs#gradle --command gradle wrapper --gradle-version 9.8.2 --distribution-type bin --gradle-distribution-sha256-sum <sha>`.
- Depends on: S0.2
- Acceptance test (file + assertion):
  - `gradle/wrapper/gradle-wrapper.properties` has `distributionSha256Sum` equal to the published sha for gradle-9.8.2-bin.
  - `nix develop --command ./gradlew --version` shows `Gradle 9.8.2` and `JVM: 21.`.
  - `./gradlew help --configuration-cache` run twice: the second run prints "Reusing configuration cache".
  - `grep -q 'android.builder.sdkDownload=false' gradle.properties`.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Mechanical config with an exact check (Q1, Q2, Q4).

STEP S0.4: Version catalog and root build (Spotless + ktlint)
- Files: `gradle/libs.versions.toml` (every §5 pin), `build.gradle.kts` (plugins declared with `apply false`; Spotless with ktlint 1.8.0 on `*.kt` and `*.kts`)
- Depends on: S0.3
- Acceptance test (file + assertion):
  - `./gradlew buildEnvironment` contains `com.android.tools.build:gradle:9.4.1` and `kotlin-gradle-plugin:2.4.21`.
  - `./gradlew spotlessCheck` exits 0.
  - `libs.versions.toml` contains `composeBom = "2026.09.00"`.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Declarative pins and an explicit check (Q1, Q2, Q4).

STEP S0.5: `:domain` module and KMP-boundary test (G4)
- Files: `domain/build.gradle.kts` (kotlin-jvm, `com.android.lint` with `warningsAsErrors`, jvmTarget 17, `systemProperty("roux.rootDir", rootDir)` on test), `settings.gradle.kts` (one include line)
- Depends on: S0.4
- Acceptance test (file + assertion): `domT/KmpBoundaryTest.kt`:
  - `scanSource("import android.os.Bundle")` → 1 violation;
  - `"val b = android.os.Bundle()"` → 1;
  - `"import java.time.Instant"` → 1;
  - `"import androidx.datastore.core.DataStore"` → 0;
  - `scanTree(domain/src/main, data/src/main)` is empty (missing directories count as empty).
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One module, and the G4 assertion is fully specified (Q1, Q2).

STEP S0.6: `:data` module and adapter-isolation test (G5)
- Files: `data/build.gradle.kts` (kotlin-jvm, serialization, lint; `api(project(":domain"))`; Ktor core, serialization-json, okio, datastore-preferences-core; test: ktor-client-mock, okio-fakefilesystem, coroutines-test, turbine, junit), `settings.gradle.kts` (one include line)
- Depends on: S0.5
- Acceptance test (file + assertion): `datT/AdapterIsolationTest.kt`:
  - a sample file at `data/repo/X.kt` importing `net.montycasa.roux.data.nhl.dto.ScoreDto` → flagged;
  - the same import at `data/nhl/Y.kt` → allowed;
  - the real tree → 0 violations.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One module with an explicit scan assertion (Q1, Q2).

STEP S0.7: `:app` module skeleton with the full dependency set
- Files:
  - `app/build.gradle.kts`:
    - Identity and SDK: applicationId and namespace `net.montycasa.roux`; compileSdk 37, minSdk 31, targetSdk 36.
    - Plugins: Compose and Roborazzi.
    - Dependencies: every app runtime and test dependency from the catalog.
    - Test config: `isIncludeAndroidResources = true`; `testDebugUnitTest` depends on `:domain:test` and `:data:test`.
  - `MANIFEST` (application only, label `@string/app_name`)
  - `settings.gradle.kts` (include line)
  - `app/src/main/res/values/strings.xml`
- Depends on: S0.6
- Acceptance test (file + assertion):
  - `nix develop --command ./gradlew :app:assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.
  - `aapt2 dump badging` on it shows `name='net.montycasa.roux'`, `sdkVersion:'31'`, `targetSdkVersion:'36'`, `compileSdkVersion='37'`.
  - `./gradlew testDebugUnitTest --dry-run` lists `:domain:test` and `:data:test`.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One module of build config; the R15 wiring is tested explicitly (Q1, Q2).

STEP S0.8: Blank Compose launcher activity
- Files: `appM/MainActivity.kt` (`enableEdgeToEdge`; shows `stringResource(R.string.app_name)`), `MANIFEST` (launcher activity), `res/values/themes.xml` (`@android:style/Theme.DeviceDefault.DayNight` with no title)
- Depends on: S0.7
- Acceptance test (file + assertion): `appT/MainActivitySmokeTest.kt` (Robolectric): `onNodeWithText("Roux Sports").assertIsDisplayed()`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Trivial bounded code with a JVM UI assertion (Q1, Q2, Q4).

STEP S0.9: Native-graphics and Roborazzi smoke (R7)
- Files: `gradle.properties` (`roborazzi.test.verify=true`). Only if native libraries fail to load: the minimal `LD_LIBRARY_PATH` entries added to `flake.nix`, with the reason recorded.
- Depends on: S0.8
- Acceptance test (file + assertion): `appT/RenderSmokeTest.kt`, `@GraphicsMode(NATIVE)`:
  - `onRoot().captureRoboImage("src/test/screenshots/render-smoke.png")`;
  - `./gradlew :app:recordRoborazziDebug` creates the PNG;
  - `./gradlew testDebugUnitTest` then passes in verify mode.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Config plus one test that surfaces the NixOS native-library risk early (Q1, Q2).

STEP S0.10: Lint gate, F-Droid dependency-info off, banned-dependency check
- Files: `app/build.gradle.kts`:
  - `lint { abortOnError; warningsAsErrors; checkDependencies; disable += "OldTargetApi" }` with a comment giving the reason;
  - `dependenciesInfo { includeInApk = false; includeInBundle = false }`;
  - a `verifyNoProprietaryDeps` task wired into `preBuild`.
- Depends on: S0.7
- Acceptance test (file + assertion):
  - `./gradlew lint` exits 0.
  - `./gradlew :app:verifyNoProprietaryDeps` exits 0.
  - With `-Proux.verifyDeps.probe=com.google.firebase:firebase-common:21.0.0` it exits non-zero, and the output names `com.google.firebase`.
  - `./gradlew :app:assembleDebug --dry-run` lists `verifyNoProprietaryDeps`.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Single build file, with both positive and negative runnable checks (Q1, Q2).

STEP S0.11: README (G1 command) and per-stage device checklist
- Files:
  - `README.md`: the G1 line verbatim, the PR gate, a warning that the first `nix develop` builds the SDK and is slow, "SDK comes from the flake; never run sdkmanager", and the fork-PR flow.
  - `docs/device-checklist.md`: Pixel 9 sections for Stages 0 and 4–10.
- Depends on: S0.7
- Acceptance test (file + assertion):
  - `grep -Fxq 'nix develop --command ./gradlew :app:assembleDebug' README.md`
  - `grep -qi 'first.*nix develop.*slow' README.md`
  - `grep -c '^## Stage ' docs/device-checklist.md` ≥ 8
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Docs with grep-checkable content (Q1, Q2, Q4).

### Stage 1: Domain model and NHL adapter

STEP S1.1: Capture base fixtures
- Files: `scripts/capture-fixtures.sh` (`base` mode; `curl --compressed -D`; records transfer sizes to `SIZES.txt`), `datT/Fixtures.kt` (classpath loader, `CURRENT = "<cap>"`). Generated in `fx/<cap>/`: `score-gameday.json`, `score-emptyday.json` (an off-season date), `schedule-gameday.json`, `standings.json` (dated form), `standings-now.headers`, `standings-season.json`.
- Depends on: S0.6
- Acceptance test (file + assertion), run from `fx/<cap>/`:
  - `jq -e '[.gameWeek[].numberOfGames]|add>0' score-gameday.json`
  - `jq -e '(.games // [])|length==0' score-emptyday.json`
  - `head -1 standings-now.headers | grep -q ' 307'`
  - `grep -qi '^location: .*/v1/standings/20' standings-now.headers`
  - every `.json` passes `jq -e .` and is larger than 1 KB
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Script with jq assertions; GET only (Q1, Q2, Q4).

STEP S1.2: Game domain model
- Files: `dom/model/Game.kt` (`GameId`, `Game`, `GameState` sealed with `Unknown(raw)`, `FinalKind`, `PeriodInfo`, `ScoreDay(date, games, dateIndex, droppedCount)`, `DateIndexEntry`), `dom/model/Team.kt` (`TeamKey`, `TeamRef` with abbrev and logo/darkLogo URLs)
- Depends on: S0.5
- Acceptance test (file + assertion): `domT/model/GameStateTest.kt`:
  - `fromFeed("LIVE") == Live(critical=false)`; `"CRIT"` → `Live(critical=true)`
  - `"FUT"` → `Scheduled`; `"PRE"` → `Pre`
  - `"FINAL"` and `"OFF"` → `Final`
  - `"WAT"` → `Unknown("WAT")`
  - `FinalKind.from("OT") == OT`, `"SO"` → `SO`, `"REG"` → `REG`
  - `game.isLive` is true only for `Live`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure data types with table tests (Q1, Q2, Q4).

STEP S1.3: Score adapter (DTOs and `parseScore`)
- Files: `dat/nhl/dto/ScoreDto.kt` (internal; all fields nullable), `dat/nhl/NhlAdapter.kt` (Json config, `ParseResult`, `parseScore`)
- Depends on: S1.1, S1.2
- Acceptance test (file + assertion): `datT/nhl/ScoreAdapterTest.kt`, fixtures `fx/<cap>/score-gameday.json` and `score-emptyday.json`:
  - the result is `Success`;
  - `games.size == EXPECTED_GAMES` (taken from jq at capture time);
  - every team abbrev matches `[A-Z]{3}`;
  - `dateIndex` is non-empty with `sum(numberOfGames) > 0`;
  - `droppedCount == 0`;
  - the empty-day fixture → `Success` with `games.isEmpty()`.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: The G5 boundary maps across modules, and every later screen depends on its parsing invariants (Q3).

STEP S1.4: Score adapter drift hardening
- Files: `dat/nhl/NhlAdapter.kt`
- Depends on: S1.3
- Acceptance test (file + assertion): `datT/nhl/ScoreAdapterDriftTest.kt`, mutating `fx/<cap>/score-gameday.json` in-test:
  - remove `gameState` from game 0 → that game is kept as `Unknown`;
  - remove `homeTeam` from game 1 → `droppedCount == 1` and the rest are intact;
  - an extra unknown field → identical result;
  - `gameWeek` removed → `dateIndex` empty, games intact;
  - `period` given as a string → degrades without throwing;
  - truncated bytes → `Failure`;
  - no test throws.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: The "never throws, drop per game" rule is a hidden invariant (Q3).

STEP S1.5: Standings and season domain model
- Files: `dom/model/Standings.kt` (`StandingRow` with GP/W/L/OTL/PTS and league/conference/division/wildcard sequence numbers; `Conference` and `Division` with `Unknown`; `Season` with start/end dates)
- Depends on: S0.5
- Acceptance test (file + assertion): `domT/model/StandingsModelTest.kt`:
  - `row(w=10, l=3, otl=2).record == "10-3-2"`
  - `otl == null` → `"10-3-–"`
  - `Division.fromFeed("Zzz") == Unknown("Zzz")`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure types (Q1, Q2, Q4).

STEP S1.6: Standings and seasons adapter
- Files: `dat/nhl/dto/StandingsDto.kt`, `dat/nhl/NhlAdapter.kt` (`parseStandings`, `parseSeasons`)
- Depends on: S1.4, S1.5
- Acceptance test (file + assertion): `datT/nhl/StandingsAdapterTest.kt`, fixtures `fx/<cap>/standings.json`, `standings-season.json`, `score-gameday.json`:
  - 32 rows; 16 per conference; 8 per division;
  - every row has a division sequence and a wildcard sequence;
  - every `TeamKey` in the score fixture is in the standings key set (this proves the favourites key works across both feeds);
  - removing `otLosses` from row 0 keeps the row with `otl == null`;
  - truncated input → `Failure`;
  - the latest season has non-null start and end.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Matching team keys across feeds is a hidden invariant that favourites depend on (Q3).

### Stage 2: Data layer

STEP S2.1: PollPolicy
- Files: `dom/policy/PollPolicy.kt`
- Depends on: S1.2
- Acceptance test (file + assertion): `domT/policy/PollPolicyTest.kt`:
  - any visible game live on today → `25.seconds`
  - a game in `Pre`, or starting within 9 minutes → `60.seconds`
  - today, next start 3 hours away → `5.minutes`
  - a past date with every game final → `null`
  - `backoff(1..6)` with zero jitter → 25, 50, 100, 200, 300, 300 s
  - seeded jitter stays within ±10%
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: A pure function with a table test (Q1, Q2, Q4).

STEP S2.2: FreshnessPolicy and `Loadable`
- Files: `dom/policy/FreshnessPolicy.kt`, `dom/Loadable.kt`
- Depends on: S1.2
- Acceptance test (file + assertion): `domT/policy/FreshnessPolicyTest.kt`:
  - today with a live game: age 20 s → fresh, 30 s → stale
  - a past all-final day → fresh at any age
  - future dates: 6 h
  - standings: 10 min when live, 1 h otherwise
  - seasons: 24 h
  - detail: 30 s when live, forever when final
  - `Loadable.from(cached, fetchFailed=true)` has freshness `Stale(since=fetchedAt)`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure policy with a table test (Q1, Q2, Q4).

STEP S2.3: HTTP client factory (allowlist, UA, redirects)
- Files: `dat/net/NhlHttpClientFactory.kt`
- Depends on: S0.6
- Acceptance test (file + assertion): `datT/net/NhlHttpClientTest.kt` (MockEngine):
  - a request to `https://example.com` throws `HostNotAllowedException`, and `requestHistory` stays empty
  - `api-web.nhle.com` and `assets.nhle.com` pass
  - 307 to `/v1/standings/2026-10-25` → 2 requests, final status 200
  - `User-Agent == "RouxSports/<ver> (+https://git.montycasa.net/patrick/roux-sports)"`
  - 429 with `Retry-After: 120` → `RateLimited(120.seconds)`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One file; every A9 and R3 behaviour is asserted (Q1, Q2).

STEP S2.4: NhlApi paths
- Files: `dat/net/NhlApi.kt` (score, standings by date, seasons, and provisional gamecenter landing/boxscore/right-rail paths, confirmed in S7.1)
- Depends on: S2.3
- Acceptance test (file + assertion): `datT/net/NhlApiTest.kt`:
  - `standings(2026-10-25)` requests exactly `/v1/standings/2026-10-25`
  - no code path requests `/v1/standings/now`
  - a 5xx → `HttpError(503)`
  - an `IOException` → `NetworkError`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Bounded single file (Q1, Q2, Q4).

STEP S2.5: ResponseCache (raw bytes, LRU, atomic)
- Files: `dat/cache/ResponseCache.kt`
- Depends on: S0.6
- Acceptance test (file + assertion): `datT/cache/ResponseCacheTest.kt` (okio `FakeFileSystem`):
  - put/get round-trips bytes and `fetchedAt`
  - a 31st score entry evicts the oldest; caps are 3 for standings and 20 for detail
  - a corrupt envelope → a miss, with no throw
  - a failure injected before `atomicMove` leaves the previous entry readable
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One file; atomicity and eviction are tested explicitly (Q1, Q2).

STEP S2.6: Preferences store (favourites, alert prefs, dedup ledger)
- Files: `dom/repo/PreferencesRepository.kt`, `dat/prefs/DataStorePreferencesRepository.kt` (file `roux.preferences_pb`, `prefs_version = 1`, `ReplaceFileCorruptionHandler`)
- Depends on: S0.6, S1.2
- Acceptance test (file + assertion): `datT/prefs/DataStorePreferencesRepositoryTest.kt` (temp directory):
  - set favourites {TOR, MTL}, cancel the scope, open a new instance on the same path → reads {TOR, MTL}
  - `favouritesOnly` defaults to true
  - per-team `start` and `goals` flags persist
  - the ledger persists
  - a garbage file → defaults, with no throw
- Risk class: data loss
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / N / N
- Hard trigger: data-loss
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go; independent review
- Reason: The on-disk format underpins G6 and is costly to change after release (hard trigger: data-loss; Q4 N).

STEP S2.7: ScoresRepository
- Files: `dom/repo/ScoresRepository.kt`, `dat/repo/ScoresRepositoryImpl.kt`
- Depends on: S1.4, S2.2, S2.4, S2.5
- Acceptance test (file + assertion): `datT/repo/ScoresRepositoryImplTest.kt` (MockEngine serving `fx/<cap>/score-gameday.json`, `FakeFileSystem`, fixed clock):
  - fresh cache → 0 requests
  - network failure with cache → `Loadable(data, Stale(since))`
  - failure with no cache → `Missing` plus error
  - a parse `Failure` is handled like a network failure
  - a success writes the cache
  - `prefetchAdjacent(d)` requests only d−1 and d+1, and only if they aren't fresh
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Combines API, cache, adapter and policy across modules (Q3).

STEP S2.8: StandingsRepository
- Files: `dom/repo/StandingsRepository.kt`, `dat/repo/StandingsRepositoryImpl.kt`
- Depends on: S1.6, S2.2, S2.4, S2.5
- Acceptance test (file + assertion): `datT/repo/StandingsRepositoryImplTest.kt` (fixtures `fx/<cap>/standings.json` and `standings-season.json`):
  - clock 2026-10-26T02:00Z → requests `/v1/standings/2026-10-25` (league date in America/New_York)
  - `/now` is never in the request history
  - a repeat call within the TTL → 0 requests
  - failure → `Stale` with the cached rows
  - seasons are fetched at most once per 24 h
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Cross-module, with the league-date and 307 invariants (Q3).

### Stage 3: App shell and one-handed design system

STEP S3.1: Fixed light/dark theme
- Files: `appM/ui/theme/Color.kt`, `appM/ui/theme/Theme.kt` (no dynamic colour)
- Depends on: S0.10
- Acceptance test (file + assertion): `appT/ui/theme/ThemeContrastTest.kt`, for both schemes:
  - contrast(onSurface, surface) ≥ 4.5
  - contrast(onSurfaceVariant, surface) ≥ 4.5
  - contrast(LiveAccent, surface) ≥ 3.0
  - `rouxColorScheme(dark = true) === RouxDarkColors`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Constants with numeric assertions (Q1, Q2, Q4).

STEP S3.2: Reach rules as code and their test assertions
- Files: `appM/ui/reach/Reach.kt` (the R1–R5 constants; `Modifier.primaryControl(tag, frequent)` as a semantics property), `appT/ui/reach/ReachAssertions.kt` (test helper `assertPrimaryControlsReachable()`)
- Depends on: S0.9
- Acceptance test (file + assertion): `appT/ui/reach/ReachAssertionsTest.kt`:
  - a top-aligned primary control → `AssertionError` naming its tag
  - bottom-aligned → passes
  - a frequent control at 0.6·H → fails R2
  - a 40 dp target → fails R4
  - 8 dp from the left edge → fails R5
  - targets 4 dp apart → fails the 8 dp gap rule
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One production file plus a test helper; every rule has a negative case (Q1, Q2).

STEP S3.3: No-hidden-actions guard (R6)
- Files: test only
- Depends on: S0.9
- Acceptance test (file + assertion): `appT/ui/NoHiddenActionsTest.kt`:
  - the scanner flags `TopAppBar(title = {}, actions = {})`, `DropdownMenu(` and `AlertDialog(`
  - it passes `CenterAlignedTopAppBar(title = {…})`
  - the real `app/src/main` tree → 0 violations
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: A test-only guard (Q1, Q2, Q4).

STEP S3.4: Navigation shell (3 bottom destinations)
- Files: `appM/ui/nav/RouxNavHost.kt` (Scores, Standings, Teams, plus placeholder screens; `NavigationBar`), `appM/MainActivity.kt` (RouxTheme + RouxNavHost); `res/values/strings_nav.xml`
- Depends on: S0.8, S3.1, S3.2
- Acceptance test (file + assertion): `appT/ui/nav/ShellTest.kt`:
  - nav items "Scores", "Standings", "Teams" exist; "Scores" is selected at launch
  - `assertPrimaryControlsReachable()` passes at both sizes and at font scale 1.0 and 2.0
  - items are ≥ 48 dp
  - tapping "Standings" shows its placeholder
  - Roborazzi `shell-light.png` and `shell-dark.png` verify
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: Two production files with behaviour (Q2); no hidden invariant.

STEP S3.5: AppContainer (manual dependency injection)
- Files: `appM/RouxApp.kt` (Application + AppContainer; engine and clock injectable; OkHttp engine; `ResponseCache(filesDir/nhl-cache)`; a single DataStore at `filesDir/datastore/roux.preferences_pb`), `MANIFEST` (`android:name=".RouxApp"`, INTERNET)
- Depends on: S0.8, S2.3, S2.5, S2.6
- Acceptance test (file + assertion): `appT/AppContainerTest.kt`:
  - `container` is the same instance across calls
  - `container.preferences` is the same instance twice (the single-DataStore invariant)
  - `httpClient.get("https://example.com")` throws `HostNotAllowedException`
  - the cache directory is under `filesDir`
  - requested permissions == [INTERNET]
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Only one DataStore per file is allowed, and the wiring spans all three modules (Q3).

### Stage 4: Scores screen

STEP S4.1: DateStrip policy
- Files: `dom/policy/DateStrip.kt`
- Depends on: S1.2, S1.5
- Acceptance test (file + assertion): `domT/policy/DateStripTest.kt`:
  - index {Oct 24: 5, Oct 25: 0, Oct 26: 9} → Oct 25 has `hasGames = false`
  - `nextGameDate(Oct 25) == Oct 26`
  - items are clamped to the season start and end
  - the default selection is the local date
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure function with tests (Q1, Q2, Q4).

STEP S4.2: Favourites filter and ordering
- Files: `dom/policy/FavoritesOrdering.kt`
- Depends on: S1.2, S1.5
- Acceptance test (file + assertion): `domT/policy/FavoritesOrderingTest.kt`:
  - favourites-only with {TOR} → only TOR games
  - empty favourites → all games plus `hint == PickTeams`
  - no favourite games that day → `NoFavouriteGames`
  - `pin(rows, {TOR, MTL})` puts TOR and MTL first and keeps feed order for the rest
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure function with tests (Q1, Q2, Q4).

STEP S4.3: ScoresViewModel (polling and lifecycle)
- Files: `appM/ui/scores/ScoresViewModel.kt`, `appM/ui/scores/ScoresUiState.kt`
- Depends on: S2.1, S2.7, S4.1, S4.2
- Acceptance test (file + assertion): `appT/ui/scores/ScoresViewModelTest.kt` (`runTest`, Turbine, fake repo):
  - one live game → refresh at t = 0, 25 and 50 s
  - a past all-final date → 0 refreshes in 120 s
  - collector cancelled → no refresh after the 5 s grace period
  - resubscribe while stale → immediate refresh
  - `onPullToRefresh` forces a fetch
  - a failure keeps the data and marks it `Stale`
  - `favouritesOnly` defaults to true and toggles
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Implements A3's timing and lifecycle rules on top of domain policies and the repository (Q3).

STEP S4.4: GameRow composable
- Files: `appM/ui/scores/GameRow.kt`; `res/values/strings_game_row.xml`
- Depends on: S1.2, S3.1, S3.2
- Acceptance test (file + assertion): `appT/ui/scores/GameRowTest.kt` (Locale US, TZ America/Toronto):
  - scheduled shows "7:00 PM"
  - live shows "2nd" and "12:34", plus a `liveIndicator` node with contentDescription "Live"; non-live rows have none
  - finals show "FINAL/OT" and "FINAL/SO"
  - a null logo shows an abbrev badge
  - rows are ≥ 56 dp tall
  - Roborazzi verifies each state in light and dark
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One composable with explicit assertions (Q1, Q2).

STEP S4.5: DateStripBar composable
- Files: `appM/ui/scores/DateStripBar.kt`; `res/values/strings_date_strip.xml`
- Depends on: S3.2, S4.1
- Acceptance test (file + assertion): `appT/ui/scores/DateStripBarTest.kt`:
  - chips are ≥ 56×56 dp
  - an empty date's contentDescription contains "no games"
  - tapping Oct 26 → `onSelect(2026-10-26)`
  - the first chip's left edge is ≥ 16 dp
  - the selected chip has `selected` semantics
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One composable (Q1, Q2, Q4).

STEP S4.6: ScoresScreen and StaleBanner
- Files: `appM/ui/scores/ScoresScreen.kt` (list, pull-to-refresh, empty date, favourites toggle, `onGameClick`), `appM/ui/common/StaleBanner.kt`; `res/values/strings_scores.xml`, `strings_common.xml`
- Depends on: S4.3, S4.4, S4.5
- Acceptance test (file + assertion): `appT/ui/scores/ScoresScreenTest.kt`:
  - an empty date shows "No games on Sat, Oct 25" and a "Next games: Tue, Oct 28" button
  - `Stale` shows "Updated 19:42" with a Retry primary control in the bottom 30%
  - `Missing` shows an error with Retry
  - swiping down calls `onRefresh`
  - tapping a row → `onGameClick(id)`
  - reach passes at both sizes and at font scale 2.0
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: Two production files with behaviour (Q2); no hidden invariant.

STEP S4.7: Wire Scores and Coil into the app
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/RouxApp.kt` (`ScoresRepositoryImpl`; Coil `ImageLoader` on the shared Ktor client via `SingletonImageLoader.Factory`)
- Depends on: S3.4, S3.5, S4.6
- Acceptance test (file + assertion): `appT/ui/scores/ScoresIntegrationTest.kt` (MockEngine serving `fx/<cap>/score-gameday.json`):
  - rows show the fixture abbrevs
  - MockEngine throwing `IOException` with a pre-seeded cache → rows plus stale banner and no error screen (A11 cold start)
  - logo requests go to host `assets.nhle.com` through MockEngine
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: End-to-end wiring across all modules, with the A11 and A9 invariants (Q3).

### Stage 5: Favourites

STEP S5.1: TeamsViewModel (favourites)
- Files: `appM/ui/teams/TeamsViewModel.kt`
- Depends on: S2.6, S2.8
- Acceptance test (file + assertion): `appT/ui/teams/TeamsViewModelTest.kt`:
  - 32 teams sorted by name, favourites first
  - `toggle(TOR)` → prefs favourites == {TOR}; toggling again removes it
  - standings `Missing` → retry state, with no crash
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One file against interfaces (Q1, Q2).

STEP S5.2: TeamsScreen (picker)
- Files: `appM/ui/teams/TeamsScreen.kt`; `res/values/strings_teams.xml`
- Depends on: S3.2, S5.1
- Acceptance test (file + assertion): `appT/ui/teams/TeamsScreenTest.kt`:
  - 32 rows
  - toggles are ≥ 48 dp, with stateDescription "Favourite" or "Not favourite"
  - tapping a toggle → `onToggle(TeamKey)`
  - reach passes
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One composable (Q1, Q2, Q4).

STEP S5.3: Wire Teams tab; persistence integration (G6)
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/RouxApp.kt`
- Depends on: S4.7, S5.2
- Acceptance test (file + assertion): `appT/ui/teams/FavoritesPersistenceTest.kt`:
  - pick TOR in the UI → close the container (scope cancelled) → new container on the same `filesDir` → Teams shows TOR as a favourite
  - Scores, with `fx/<cap>/score-gameday.json`, shows only TOR games by default
  - "All games" shows every fixture game
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: The DataStore single-instance and close/reopen rules plus the favourites-only default span modules (Q3).

### Stage 6: Standings

STEP S6.1: StandingsGrouping (including wildcard)
- Files: `dom/policy/StandingsGrouping.kt`
- Depends on: S1.5, S4.2
- Acceptance test (file + assertion): `domT/policy/StandingsGroupingTest.kt`:
  - League view: 32 rows by league sequence
  - Conference view: 2×16; Division view: 4×8
  - Wildcard view: per conference, two division blocks of 3, then a wildcard block in wildcard-sequence order with the cut line after WC2
  - a pinned favourites section appears only when the filter is on
  - an unknown division goes into an "Other" group
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure function using the feed's sequence numbers (Q1, Q2).

STEP S6.2: StandingsViewModel and StandingsScreen
- Files: `appM/ui/standings/StandingsViewModel.kt`, `appM/ui/standings/StandingsScreen.kt`; `res/values/strings_standings.xml`
- Depends on: S2.8, S4.6, S6.1
- Acceptance test (file + assertion): `appT/ui/standings/StandingsViewModelTest.kt` and `StandingsScreenTest.kt`:
  - the League / Conference / Division / Wild Card switcher is a frequent primary control
  - headers GP W L OTL PTS are shown
  - League view has 32 rows
  - the pinned section is visible when the filter is on
  - the stale banner is reused
  - reach passes at both sizes
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: Two production files with behaviour (Q2).

STEP S6.3: Wire Standings
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/RouxApp.kt`
- Depends on: S5.3, S6.2
- Acceptance test (file + assertion): `appT/ui/standings/StandingsIntegrationTest.kt` (MockEngine with `fx/<cap>/standings.json` and `standings-season.json`):
  - the tab shows 32 rows
  - the request history has `/v1/standings/<date>` and no `/v1/standings/now`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Cross-module wiring with the R3 invariant (Q3).

### Stage 6b: Design parity with the signed-off Scores design (v4b)

**Why this stage exists.** The Scores design was signed off on 2026-10-25 and recorded in
`/var/lib/hermes/workspace/roux-sports-design/README.md` ("decided, ready to implement"), but no step in this plan ever
carried it: nothing in `docs/` referenced the design file. Stages 3–6 therefore implemented the palette and the reach
rules while rendering a two-line matchup row with a status footer. The result is the design's colours inside Material3
structure: correct brand, wrong screen. This stage is the missing link, and it runs **before Stage 9**, so the screenshot
matrix captures the real design rather than the interim screen.

**The contract is the file, not this prose.** `final/scores-screen.html` is authoritative: seven frames at 411 × 923 and
360 × 780, light and dark, with a 1.0×/2.0× type toolbar. The measurements below were read out of its CSS. Where this
plan and the file disagree, the file wins and this plan gets corrected.

Reference measurements (from `final/scores-screen.html`):

| Element | Value |
|---|---|
| Card | one per game, `grid-template-columns: minmax(0,1fr) 5.75rem`, radius 16 dp, **76 dp tall**, **8 dp between cards** |
| Card inner | 1 dp transparent border (border-box) + 8 dp vertical padding + two 28 dp rows 2 dp apart = 76 dp |
| Crest | 34 × 34 dp (32 dp when the **scroll container** is ≤ 380 dp — the design queries `.listwrap`, the full-width viewport, not the card) |
| Club name | 15 sp / 600 (14 sp narrow), letter-spacing -.005 em, ellipsised, full name not abbreviation |
| Score | 22 sp / 500 (21 sp narrow), **tabular figures**, right-aligned, 30 dp slot (28 dp narrow), letter-spacing -.02 em; ink2 by default, ink at 700 when live, signature for a final winner, ink3 for the dash |
| Rail | 1 dp left rule, **92 dp wide (82 dp narrow)**; a stack **centred vertically only** and flush left 12 dp in from the rail's edge (7 dp from its right), never horizontally centred: pill, clock, shots for a live game; pill then shots for a final; the start time alone for an upcoming one |
| Live | filled orange **pill carrying only `dot + LIVE`** (9 sp / 800, uppercase, 0.09 em letter-spacing, 1.5 dp border in its own fill so it matches the outlined pill's size); the clock sits **below** the pill in signature at 12 sp / 800 tabular; live scores are ink at **weight 700** (no leading bar — see R9 note) |
| Final | 1.5 dp stroke pill in **signature**: `FINAL`, `FINAL · OT`, `FINAL · SO`; then shots. Winner's name and score in signature, the loser's score in **ink2** |
| Upcoming | no pill and **no numerals** — but an **en dash** at ink3 weight 400 in the score slot, so the column reads "not started" rather than broken; muted names; start time alone in the rail |
| Header | 64 dp row, 48 dp navigation handle, `.brandmark` **20 dp image box** (18 dp visible ink with the supplied asset), `● N LIVE` pill, 28 dp high |
| Date chip | 56 × 56; weekday/month small, day 19 sp; today's chip reads only "Today"; calendar tile pinned right |
| No star toggle | favourites become the "My Teams" pseudo-league in the drawer |
| Header | 64 dp row above the list (it does not scroll): 48 dp menu, wordmark in a **20 dp box** (18 dp of visible ink in the supplied asset), 28 dp live pill, 10 dp column gaps, 16 dp side padding |
| Drawer | **240 dp dark panel in both themes** (`#08090B`, not a theme surface), 20/16/16/24 dp padding, wordmark 108 dp wide, 56 dp rows with 10 dp radius, 8 dp row gaps, 1 dp `#1B1D22` rules; the flexible spacer sits **directly after the brand block**, so the menu and footer anchor to the bottom |
| Drawer rows | My Teams carries the favourite count and selects the favourites-only view; the league row shows name plus sport, selected = `#15171C` background with an orange sport label; footer carries its own top border, so no extra rule above it |

The card's **8 dp gap is the same 8 dp as `Reach.MIN_TARGET_GAP_DP`**: a card is clickable, so it is a touch target, and the
design happens to satisfy the rule exactly. Shrinking the gap to fit more cards would be an accessibility regression
dressed as a design decision. Two earlier versions of this table said 72 dp and 2 dp, taken from the README's prose rather
than the file; both were wrong and both were implemented before the render was measured. Measure first.

**R9 is amended by the design, deliberately.** The brief demands a 4 dp leading accent bar on live games; the signed-off
design removes it (`README.md` line 54) because the rail capsule already carries a dot and a bold clock. "Never colour
alone" survives — fill + dot + bold clock are three cues. Tests that assert the bar must be re-pointed at the capsule,
not deleted.

**R10 tension is accepted, not hidden.** Rail micro-labels run 9–12 sp, under the brief's 14 sp body floor. They are
labels rather than body text, and the file was drawn that way; they must still meet non-text contrast ≥ 3:1 in both themes.
Any *body* copy stays ≥ 14 sp.

STEP S6b.1: Shots on goal in the domain model
- Files: `dom/model/Game.kt`, `dat/net/NhlAdapter.kt`
- Depends on: S1.3
- Acceptance test: `datT/nhl/ScoreAdapterTest.kt` — the captured `score-gameday.json` yields per-team `sog` (e.g. PHI 32,
  ANA 26); a payload without `sog` yields `null`, never `0`, so the rail can omit the line rather than claim zero shots.
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: Free. Effort: low.

STEP S6b.2: The game card, the rail, and the type scale
- Files: `appM/ui/scores/GameRow.kt`, `appM/ui/theme/Type.kt` (new), `appM/ui/scores/ScoresScreen.kt`, strings
- Depends on: S6b.1, S3.2
- Acceptance test: `appT/ui/scores/GameRowTest.kt` — card height **76 dp** at 1.0× type (1 dp border + 16 dp padding +
  58 dp of rows); crest 34 dp wide and 32 dp at a ≤ 380 dp card; rail 92 dp wide and 82 dp narrow; score uses tabular
  figures; live renders capsule + dot + bold clock; final renders the ruled `FINAL` capsule and paints the winner's name
  and score in the signature colour; upcoming renders no capsule and no score numerals; `sog` renders only when known;
  a missing logo falls back to the abbreviation badge without changing the row height; and the list gap equals
  `Reach.MIN_TARGET_GAP_DP`.
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: DeepSeek. Effort: medium.

STEP S6b.3: The header
- Files: `appM/ui/scores/ScoresScreen.kt`, `appM/ui/nav/RouxNavHost.kt`
- Depends on: S6b.2
- Acceptance test: `appT/ui/scores/ScoresHeaderTest.kt` — the wordmark is text-or-image with an accessible name, the live
  pill reads "N LIVE" for N live games and disappears at zero, and the drawer handle is classified as navigation (not a
  primary control), per the design's own deviation note. R1/R2 still pass for every actual control.
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: DeepSeek. Effort: low.

STEP S6b.4: The drawer and My Teams
- Files: `appM/ui/drawer/LeagueDrawer.kt` (new), `appM/RouxApp.kt`, strings
- Depends on: S6b.3
- Acceptance test: `appT/ui/drawer/LeagueDrawerTest.kt` — 240 dp dark brand panel in both themes, 56 dp rows;
  My Teams sits above the leagues, carries the current favourite count and selects the existing favourites-only state;
  NHL is the selected league. Both rows close the drawer, and selection persists through `PreferencesRepository`.
  While NHL is the only supported league, assert the absence of the edit pencil and its hint, Settings, and non-NHL
  leagues. Editing/reordering is deferred until multiple leagues exist. The final CSS overrides bottom padding to 24 dp.
  `appT/ui/nav/ShellTest.kt` exercises header opening and both selections through the real preference store.
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: DeepSeek. Effort: medium.

STEP S6b.5: The date strip and the calendar
- Files: `appM/ui/scores/DateStripBar.kt`, `appM/ui/calendar/MonthSheet.kt` (new), strings
- Depends on: S6b.3, S6b.4
- Acceptance test: `appT/ui/scores/DateStripTest.kt` — chips are 56 × 56 with the day at 19 sp, today's chip reads only
  "Today", the calendar tile is pinned at the right end and opens a month sheet whose cells meet the touch-target rule,
  and **the star favourites toggle is gone** (the design removed it; favourites live in the drawer).
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: DeepSeek. Effort: medium.

STEP S6b.6: Parity proof
- Files: test only; baselines in `appT/screenshots/design-parity/`
- Depends on: S6b.2–S6b.5
- Acceptance test: `appT/ui/DesignParityTest.kt` — Roborazzi records the Scores screen at 411 × 923 and 360 × 780, light
  and dark, and the four PNGs are compared by eye against the corresponding frames in `final/scores-screen.html`, with the
  sign-off recorded in `docs/device-checklist.md`. This is the step that would have caught the original drift.
- Risk class: none. Privacy class: public-repo. Q1/Q2/Q3/Q4/Q5: Y/Y/N/Y/N. Tier: Free. Effort: low.



### Stage 7: Game detail

STEP S7.1: Capture gamecenter fixtures and map A6 sources
- Files: `scripts/capture-fixtures.sh` (add a `gamecenter <finalId> <liveId>` mode), `docs/a6-sources.md`. Generated: `fx/<cap>/gc-{final,live}-{landing,boxscore,right-rail}.json`.
- Depends on: S1.1
- Acceptance test (file + assertion):
  - every gc file passes `jq -e .` and is larger than 1 KB
  - `docs/a6-sources.md` has one row for each of shots, power play, goalies, three stars and period scoring, giving the endpoint and JSON path or "NO SOURCE"
  - any "NO SOURCE" stops the step for you to decide
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Script plus doc with jq checks (Q1, Q2, Q4).

STEP S7.2: GameDetail domain model
- Files: `dom/model/GameDetail.kt`
- Depends on: S1.2
- Acceptance test (file + assertion): `domT/model/GameDetailTest.kt`:
  - `sections()` returns only non-null sections, in the order PeriodScoring, Shots, PowerPlay, Goalies, ThreeStars
  - period labels are 1st/2nd/3rd/OT/SO
  - `PowerPlay(1, 4).label == "1/4"`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Pure types (Q1, Q2, Q4).

STEP S7.3: Detail adapter, degrading one section at a time
- Files: `dat/nhl/dto/GameDetailDto.kt`, `dat/nhl/NhlAdapter.kt` (`parseGameDetail`)
- Depends on: S1.6, S7.1, S7.2
- Acceptance test (file + assertion): `datT/nhl/GameDetailAdapterTest.kt`, fixtures `fx/<cap>/gc-final-*.json` and `gc-live-*.json`:
  - final → all 5 sections non-null
  - removing each section's source node (paths from `docs/a6-sources.md`) → only that section is null, and the result is still `Success`
  - live → `Success` with period scoring present
  - boxscore bytes absent → `Success` with only the landing-derived sections
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Merges several endpoints, with the A6 degradation invariant (Q3).

STEP S7.4: GameDetailRepository
- Files: `dom/repo/GameDetailRepository.kt`, `dat/repo/GameDetailRepositoryImpl.kt`
- Depends on: S2.2, S2.4, S2.5, S7.3
- Acceptance test (file + assertion): `datT/repo/GameDetailRepositoryImplTest.kt` (MockEngine serving the gc fixtures):
  - a final game, once cached → 0 further requests
  - a live game refetches after 30 s
  - boxscore 500 with landing OK → partial detail marked `Stale`
  - everything fails but a cache exists → `Stale` cached data
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Partial-failure handling across modules (Q3).

STEP S7.5: GameDetailViewModel and GameDetailScreen
- Files: `appM/ui/detail/GameDetailViewModel.kt`, `appM/ui/detail/GameDetailScreen.kt` (bottom Back bar); `res/values/strings_detail.xml`
- Depends on: S3.2, S4.6, S7.4
- Acceptance test (file + assertion): `appT/ui/detail/GameDetailViewModelTest.kt` and `GameDetailScreenTest.kt`:
  - live: refresh every 30 s while subscribed, stopping when unsubscribed; final: never polls
  - for each of 5 cases with one null section → that section's tag is absent and the others present
  - Back is a primary control in the bottom 30%
  - reach passes at both sizes
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: Two production files with behaviour (Q2).

STEP S7.6: Wire the detail route
- Files: `appM/ui/nav/RouxNavHost.kt`, `appM/RouxApp.kt`
- Depends on: S6.3, S7.5
- Acceptance test (file + assertion): `appT/ui/detail/DetailNavigationTest.kt`:
  - tapping the first fixture game shows the detail with both abbrevs
  - the Back button returns to Scores with the same date selected
  - the system back gesture does the same
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: low
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Navigation state across screens (Q3).

### Stage 8: Notifications

STEP S8.1: NotificationDecider (pure, with dedup)
- Files: `dom/policy/NotificationDecider.kt`
- Depends on: S1.2, S2.6
- Acceptance test (file + assertion): `domT/policy/NotificationDeciderTest.kt`:
  - a favourite with the start pref going Scheduled → Live → exactly one `GameStart`
  - goals 1 → 2 → one `Goal`
  - the same input twice → 0 events the second time
  - not a favourite, or pref off → 0
  - a goal count that goes down → 0 events, ledger updated
  - final + 1 day → ledger entry pruned
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: The dedup and score-correction rules are hidden invariants (Q3).

STEP S8.2: GameWatchScheduler interface and WorkManager implementation
- Files:
  - `dom/notify/GameWatchScheduler.kt`: `reconcile(today, favourites, prefs)` and `cancelAll()`. The KDoc says a UnifiedPush distributor can implement this later.
  - `appM/notify/WorkManagerGameWatchScheduler.kt`: unique `watch-<gameId>` work with `initialDelay` = start − now; unique periodic `daily-reconcile` every 12 h.
- Depends on: S1.2, S2.6
- Acceptance test (file + assertion): `appT/notify/WorkManagerGameWatchSchedulerTest.kt` (`WorkManagerTestInitHelper`):
  - a favourite game 2 h ahead → exactly one ENQUEUED `watch-<id>` with delay 2 h ± 1 s
  - reconcile twice → still one
  - prefs off → CANCELLED
  - not a favourite → none
  - `daily-reconcile` is enqueued once
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: An interface across modules plus unique-work invariants (Q3).

STEP S8.3: Notifier and channels
- Files: `appM/notify/Notifier.kt`; `res/values/strings_notify.xml`
- Depends on: S8.1
- Acceptance test (file + assertion): `appT/notify/NotifierTest.kt` (Robolectric `ShadowNotificationManager`):
  - channels `game_start` and `goals` exist
  - a `Goal` event → one notification on `goals` titled "TOR 2 – 1 MTL", with content intent data `roux://game/<id>`
  - notifications disabled → `NotPosted`, with no throw
  - the same (game, event) reuses the notification id
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One file with explicit assertions (Q1, Q2).

STEP S8.4: GameWatchWorker
- Files: `appM/notify/GameWatchWorker.kt`
- Depends on: S2.7, S8.1, S8.2, S8.3
- Acceptance test (file + assertion): `appT/notify/GameWatchWorkerTest.kt` (`TestListenableWorkerBuilder`; fake repo serving a mutated `fx/<cap>/score-gameday.json`):
  - a new goal → `posted == 1`, with the ledger persisted before posting (at most once)
  - a rerun → 0 posts
  - still live → a follow-up unique work with a 180 s delay
  - final → no follow-up
  - a network failure → `Result.retry()` and 0 posts
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: The ledger-before-post order and re-chaining are hidden invariants (Q3).

STEP S8.5: TeamsViewModel alert preferences
- Files: `appM/ui/teams/TeamsViewModel.kt`
- Depends on: S5.1, S8.2
- Acceptance test (file + assertion): `appT/ui/teams/TeamsViewModelNotificationsTest.kt`:
  - `setGoals(TOR, true)` persists and calls `scheduler.reconcile` once
  - the first alert enabled without permission → `RequestNotificationPermission` effect
  - `onPermissionResult(false)` → toggle back to off, with an explanation state
  - un-favouriting TOR clears its alert prefs and reconciles
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One file against fakes (Q1, Q2).

STEP S8.6: Alert toggles, POST_NOTIFICATIONS flow and disclosure
- Files: `appM/ui/teams/TeamsScreen.kt` (per-favourite Start/Goals switches; `RequestPermission` launcher; disclosure text), `MANIFEST` (POST_NOTIFICATIONS); `res/values/strings_teams.xml`
- Depends on: S5.2, S8.5
- Acceptance test (file + assertion): `appT/ui/teams/TeamsScreenNotificationsTest.kt`:
  - "Alerts are best-effort: Android battery saving can delay them by several minutes or more." is visible whenever alert toggles are shown
  - permission denied (Robolectric `denyPermissions`) → the switch turns off and an inline explanation appears, with an "Open settings" primary control in the bottom zone
  - granted → the switch stays on
  - switches are ≥ 48 dp
  - requested permissions include POST_NOTIFICATIONS
- Risk class: auth/permissions
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: auth
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go; independent review. You approve the disclosure wording.
- Reason: Runtime-permission handling is a permissions boundary (hard trigger).

STEP S8.7: Wire the scheduler and the notification deep link
- Files: `appM/RouxApp.kt` (provides the scheduler and Notifier; reconciles at app start and when prefs change), `appM/ui/nav/RouxNavHost.kt` (deep link `roux://game/{id}`)
- Depends on: S7.6, S8.4, S8.6
- Acceptance test (file + assertion): `appT/notify/NotificationsIntegrationTest.kt`:
  - favourites {TOR} with start and goals prefs, serving `fx/<cap>/score-gameday.json` → after `onCreate`, `watch-<TOR game id>` exists
  - all prefs off → cancelled
  - an intent with `roux://game/<id>` opens that game's detail
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / Y / N
- Hard trigger: none
- Tier: Haiku
- Effort: medium
- Route: Claude subscription
- Reviewer: Sonnet via Claude subscription; second review
- Reason: Reconciliation triggers plus the deep link span every layer (Q3).

### Stage 9: Design and accessibility audit

STEP S9.1: Reach audit across all screens
- Files: test only (any failure spawns one fix step per screen file)
- Depends on: S8.7
- Acceptance test (file + assertion): `appT/ui/ReachAuditTest.kt`. `assertPrimaryControlsReachable()` passes for every combination of:
  - screens: Scores (data, empty, stale), Standings (×4 views), Teams (with alerts), Detail (full, partial)
  - sizes: `w411dp-h923dp-420dpi` and `w360dp-h780dp`
  - font scale: 1.0 and 2.0
  - theme: light and dark
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: medium
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: A test-only matrix built on the existing helper (Q1, Q2, Q4).

STEP S9.2: Screenshot matrix (light and dark)
- Files: test only. Generated baselines in `app/src/test/screenshots/matrix/`.
- Depends on: S8.7
- Acceptance test (file + assertion): `appT/ui/ScreenshotMatrixTest.kt`:
  - `recordRoborazziDebug` followed by `testDebugUnitTest` verify passes for every screen in both themes
  - you review the PNGs and sign off in `docs/device-checklist.md`
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Test only (Q1, Q2, Q4).

STEP S9.3: TalkBack semantics
- Files: `appM/ui/scores/GameRow.kt` (merged description). Fixes needed elsewhere spawn follow-up steps.
- Depends on: S8.7
- Acceptance test (file + assertion): `appT/ui/AccessibilityLabelsTest.kt`:
  - every clickable node on every screen has non-empty text or a contentDescription
  - a game row reads "Toronto 3, Montreal 2, live, 2nd period, 12:34"
  - a date chip reads "Saturday October 25, 9 games"
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / N / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: medium
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: The fix surface may cover several screens (Q2 N).

STEP S9.4: One-thumb walkthrough sign-off
- Files: `docs/device-checklist.md` (Stage 9 results recorded)
- Depends on: S9.1, S9.2, S9.3
- Acceptance test (file + assertion): `docs/device-checklist.md` has every Stage 9 item ticked by you on the Pixel 9: right hand, left hand, light, dark, maximum font size, gesture navigation.
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: N / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Only records your manual results (docs); Q1 N because the check is manual.

### Stage 10: Release readiness

STEP S10.1: Release build type, R8 and signing config
- Files: `app/build.gradle.kts` (release: minify and shrink resources; `signingConfig` read from a gitignored `keystore.properties` if present; versionCode 1, versionName 1.0.0), `app/proguard-rules.pro`
- Depends on: S0.10, S9.4
- Acceptance test (file + assertion): `scripts/check-release-signing.sh`:
  - with no `keystore.properties`, `assembleRelease` exits 0 and produces `app-release-unsigned.apk`
  - with a throwaway keystore made by `keytool` in `mktemp -d` and a temporary properties file removed by `trap`, `apksigner verify --print-certs` succeeds
  - `git check-ignore -q keystore.properties`
  - `minifyReleaseWithR8` reports no missing-class errors
- Risk class: deployment
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: deployment
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go; independent review. You confirm the signing approach.
- Reason: The signing and release config is the deployment boundary (hard trigger).

STEP S10.2: Release APK audit script
- Files: `scripts/check-release-apk.sh`
- Depends on: S10.1
- Acceptance test (file + assertion):
  - on the release APK it exits 0. It checks that `aapt2 dump permissions` is a subset of the allowlist (INTERNET, POST_NOTIFICATIONS, plus the WorkManager-merged set recorded from S8.7), that `unzip -Z1` has no `firebase`, `play-services` or `com/google/android/gms` entries, and that the badging package and version match
  - `--self-test` given a READ_CONTACTS permission list exits 1
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: A script with positive and negative checks (Q1, Q2, Q4).

STEP S10.3: Network-host source audit (A9)
- Files: test only
- Depends on: S8.7
- Acceptance test (file + assertion): `domT/NetworkHostAuditTest.kt`:
  - URL hosts found anywhere under `*/src/main` are a subset of {api-web.nhle.com, assets.nhle.com, api.nhle.com}; `api.nhle.com` was authorized on 2026-11-03 for league-relative team statistics.
  - `git.montycasa.net` is allowed only in `appM/ui/teams/AboutSection.kt`
  - a sample containing `https://example.com` is flagged
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: A test-only scan (Q1, Q2, Q4).

STEP S10.4: About section (licence, source link, NHL disclaimer)
- Files: `appM/ui/teams/AboutSection.kt`, `appM/ui/teams/TeamsScreen.kt`; `res/values/strings_teams.xml`
- Depends on: S8.6
- Acceptance test (file + assertion): `appT/ui/teams/AboutSectionTest.kt`:
  - shows versionName "1.0.0" and "GPL-3.0-or-later"
  - "Source code" is a primary control that fires `ACTION_VIEW` to `https://git.montycasa.net/patrick/roux-sports` (`nextStartedActivity`), with no HTTP request
  - shows "Not affiliated with or endorsed by the NHL. Team names and logos are trademarks of the NHL and its teams."
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: DeepSeek
- Effort: low
- Route: OpenCode Go
- Reviewer: a `*-free` model via OpenCode Zen; routine
- Reason: Two production files with behaviour (Q2).

STEP S10.5: F-Droid-format store metadata
- Files: `fastlane/metadata/android/en-US/{title.txt, short_description.txt, full_description.txt, changelogs/1.txt}`
- Depends on: S10.1
- Acceptance test (file + assertion): `scripts/check-metadata.sh`:
  - title ≤ 50 characters, short description ≤ 80, full description ≤ 4000
  - `changelogs/<versionCode>.txt` exists
  - the full description contains "No ads" and "No tracking"
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Docs with a length check (Q1, Q2, Q4).

STEP S10.6: Release docs
- Files: `README.md` (release section: Forgejo releases only, `keystore.properties` format and that it's gitignored, `scripts/build-release.sh`, checking the sha256 and `apksigner`), `CHANGELOG.md`
- Depends on: S10.1
- Acceptance test (file + assertion):
  - `grep -q 'keystore.properties' README.md`
  - `grep -qi 'Forgejo release' README.md`
  - `grep -q '^## \[1.0.0\]' CHANGELOG.md`
- Risk class: docs
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: Docs (Q1, Q2, Q4).

STEP S10.7: Release build script and v1.0.0 publication
- Files: `scripts/build-release.sh`. It runs the PR gate, `assembleRelease`, `check-release-apk.sh` and `apksigner verify`, then writes `app/build/dist/roux-sports-1.0.0.apk` and its `.sha256`.
- Depends on: S10.1, S10.2, S10.3, S10.4, S10.5, S10.6
- Acceptance test (file + assertion):
  - with no `keystore.properties`, it exits non-zero with "keystore.properties not found"
  - with a throwaway keystore it completes, and the `.sha256` matches the APK
  - you then run it with the real keystore, tag `v1.0.0` through the fork-PR flow and upload to Forgejo releases
- Risk class: deployment
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / N / N
- Hard trigger: deployment
- Tier: Sonnet
- Effort: low
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go; independent review. You perform the publication.
- Reason: Publishing a signed release is deployment (hard trigger) and can't be cleanly reverted (Q4 N).

### Stage 11: CI (optional, off the critical path)

STEP S11.1: Forgejo runner probe
- Files: `.forgejo/workflows/runner-probe.yml` (`workflow_dispatch`)
- Depends on: S0.11
- Acceptance test (file + assertion): a dispatched run is green and logs `PROBE_OK` after it:
  - runs `nix develop --command scripts/check-toolchain.sh` (this builds the SDK on the runner);
  - checks `df` ≥ 15 GB and RAM ≥ 8 GB;
  - reaches Maven Central, Google Maven and services.gradle.org.

  Any failed check logs `PROBE_FAIL:<check>` and exits 1.
- Risk class: none
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / N / Y / N
- Hard trigger: none
- Tier: Free
- Effort: low
- Route: OpenCode Zen (free only)
- Reviewer: a different `*-free` model via OpenCode Zen; routine
- Reason: One YAML file with an explicit pass/fail marker (Q1, Q2, Q4).

STEP S11.2: PR-gate workflow, restricted to the trusted fork
- Files: `.forgejo/workflows/pr-gate.yml` (on `pull_request` to `main`; `if: github.event.pull_request.head.repo.full_name == 'openclaw/roux-sports'`; runs the PR gate in `nix develop`; caches `~/.gradle`)
- Depends on: S11.1 (PROBE_OK)
- Acceptance test (file + assertion):
  - an `openclaw` PR shows `pr-gate` green
  - `grep -q "head.repo.full_name == 'openclaw/roux-sports'" .forgejo/workflows/pr-gate.yml`
  - `! grep -q 'secrets\.' .forgejo/workflows/pr-gate.yml`
- Risk class: auth/permissions
- Privacy class: public-repo
- Q1 / Q2 / Q3 / Q4 / Q5: Y / Y / Y / N / N
- Hard trigger: auth
- Tier: Sonnet
- Effort: medium
- Route: Claude subscription
- Reviewer: deepseek-v4.1-flash via OpenCode Go; independent review. You approve the runner trust guard.
- Reason: Deciding which code runs on a self-hosted runner is a permissions boundary (hard trigger).

---

## Closing summary

### 1. Step order, dependency graph and parallelism

**Recommended order:**
- Stage 0: S0.1 → S0.2 → S0.3 → S0.4 → S0.5 → S0.6 → S0.7 → {S0.8 → S0.9, S0.10, S0.11}
- Stages 1–2: S1.1 · S1.2 · S1.5 · S2.1 · S2.2 · S2.3 · S2.5 · S2.6 → S1.3 → S1.4 → S1.6 · S2.4 → S2.7 · S2.8
- Stage 3 (can start right after Stage 0): S3.1 · S3.2 · S3.3 → S3.4 · S3.5
- Stage 4: S4.1 · S4.2 · S4.4 · S4.5 → S4.3 → S4.6 → S4.7
- Stages 5–8: S5.1 → S5.2 → S5.3 → S6.1 → S6.2 → S6.3 → S7.1 … S7.6 → S8.1 … S8.7
- Stages 9–10: S9.1 · S9.2 · S9.3 → S9.4 → S10.1 → S10.2 · S10.3 · S10.4 · S10.5 · S10.6 → S10.7
- Stage 11: S11.1 → S11.2, at any point after S0.11; it never blocks anything.

**Critical path:** S0.1–S0.7 → S1.1 → S1.3 → S1.4 → S2.7 → S4.3 → S4.6 → S4.7 → S5.3 → S6.3 → S7.6 → S8.7 → S9.x → S9.4 → S10.1 → S10.7.

**Chokepoint files that force serialisation:**
- `settings.gradle.kts`: S0.3, S0.5, S0.6, S0.7
- `gradle.properties`: S0.3, S0.9
- `app/build.gradle.kts`: S0.7, S0.10, S10.1
- `MANIFEST`: S0.7, S0.8, S3.5, S8.6
- `dat/nhl/NhlAdapter.kt`: S1.3 → S1.4 → S1.6 → S7.3
- **`appM/ui/nav/RouxNavHost.kt` and `appM/RouxApp.kt`** (the main bottleneck): S3.4/S3.5 → S4.7 → S5.3 → S6.3 → S7.6 → S8.7. These two files fix the order of all the wiring steps.
- `appM/MainActivity.kt`: S0.8, S3.4
- `appM/ui/teams/TeamsViewModel.kt`: S5.1 → S8.5
- `appM/ui/teams/TeamsScreen.kt` (and `strings_teams.xml`): S5.2 → S8.6 → S10.4
- `appM/ui/scores/GameRow.kt`: S4.4 → S9.3
- `scripts/capture-fixtures.sh`: S1.1 → S7.1
- `README.md`: S0.1 → S0.11 → S10.6
- `docs/device-checklist.md`: S0.11 → S9.4

**What can run in parallel, judged by file overlap:**
- **After S0.6:** domain-only steps S1.2, S1.5, S2.1, S2.2, S4.1, S4.2, S6.1, S7.2, S8.1 touch separate files and can run together, subject only to their type dependencies. S2.3 and S2.5 are separate data files and can run alongside them. S1.1 and S7.1 share only the capture script.
- **S2.7 and S2.8**: separate repository files; they can run in parallel once their inputs are done.
- **Stage 3 vs Stages 1–2:** S3.1, S3.2 and S3.3 touch only app UI files and can run alongside the data stages.
- **UI lanes:** S4.4 ∥ S4.5. S5.1/S5.2 ∥ S6.1/S6.2 ∥ S7.3–S7.5 ∥ S8.2–S8.4 have no shared files. Their wiring steps still merge one at a time through the RouxNavHost/RouxApp bottleneck.
- **Stage 9:** S9.1 ∥ S9.2 (separate test files). S9.3 runs alongside them but owns `GameRow.kt`.
- **Stage 10:** S10.2 ∥ S10.3 ∥ S10.5 ∥ S10.6. S10.4 is held to its turn by `TeamsScreen.kt`.

### 2. Steps that need your confirmation before starting
- **S0.1**: grant temporary Write first. Afterwards, revoke it and create the `openclaw` fork.
- **S0.2**: confirm the nixpkgs revision you evaluated, so `flake.lock` pins the same one. Also accept the first-run SDK build time and disk use.
- **S7.1**: timing, because a live game must be in progress to capture the live fixtures.
- **S8.6**: approve the disclosure wording.
- **S9.2 and S9.4**: you review the screenshots and do the one-thumb sign-off.
- **S10.1**: confirm the signing approach.
- **S10.7**: you run it with the real keystore, tag the release and publish.
- **S11.1**: enable Actions and the runner, then dispatch the probe.
- **S11.2**: approve the runner trust guard.

You also sign off each stage's Pixel 9 checklist.

### 3. Steps that couldn't be split cleanly
- **S0.5, S0.6, S0.7**: a module's build file and its `settings.gradle.kts` include line must land together. Gradle won't accept including a project directory that doesn't exist, and a module that isn't included is invisible.
- **S0.2 and S0.3**: `flake.lock` and the wrapper files are generated together with their source files and can't be committed separately.
- **S1.1 and S7.1**: many fixture files, but they're one artifact set produced by one script run.
- **Wiring steps** (S4.7, S5.3, S6.3, S7.6, S8.7): a route without its container dependency (or the reverse) doesn't compile or test meaningfully. Each is two files, so within the rule, but they're forced into sequence.
- **S8.6**: the permission request needs both the manifest declaration and the UI launcher to be testable.
- **S9.1 and S9.3**: audits whose fixes can't be known in advance. Each failure spawns a fix step limited to one screen file, under the same tier rules.
- **S10.5**: four small text files in one metadata directory, treated as one unit.
- **S0.9**: conditionally touches `flake.nix` if Robolectric native graphics can't load its libraries on NixOS (R7).

### 4. Steps per tier
| Tier | Steps |
|---|---|
| Free | 40 |
| DeepSeek | 6 |
| Haiku | 17 |
| Sonnet | 6 |
| Opus | 0 |
| **Total** | **69** |
