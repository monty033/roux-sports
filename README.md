# Roux Sports

An ad-free, open-source NHL scores app for Android, in development.

The goal is NHL scores, live game state, and standings. No ads, no trackers, no
telemetry.

Kotlin and Jetpack Compose. GPL-3.0-or-later.

Source: [github.com/monty033/roux-sports](https://github.com/monty033/roux-sports) — a public mirror. Development
happens on a self-hosted Forgejo instance that is not reachable from the internet; the mirror is what outside
readers can follow.

**Current state: Stage 10 of 11 — release readiness.** Scores, standings, teams, team detail (schedule, players,
league-relative team stats), player detail, game detail and team alerts are all built and on `main`. What remains is
the release itself: the About screen, and publishing a signed 1.0.0.

## Building

You need [Nix](https://nixos.org/) with flakes enabled, on **x86_64-linux**. The flake
declares that system only and says so; macOS, Windows and ARM Linux are not supported and
would need their own `androidenv` composition. Everything else — JDK, Android SDK, build
tools — is pinned by the flake and realised into your local Nix store. Nothing is installed
system-wide and no Android tooling needs to exist on your machine first.

The single build command:

```
nix develop --command ./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

The first `nix develop` is slow: it fetches the pinned JDK and Android SDK packages into
your Nix store, which takes a while and a lot of disk. How long depends on your network and
what you already have cached, so treat the first run as a one-off setup step rather than
something that has hung. Later runs reuse the store path and start quickly.

If your Nix does not have flakes enabled, pass the flag to the Nix invocation rather than
changing your global config:

```
nix --extra-experimental-features 'nix-command flakes' develop --command ./gradlew :app:assembleDebug
```

### The SDK comes from the flake — never run `sdkmanager`

`ANDROID_HOME` points into the Nix store and is read-only. Do not run `sdkmanager`, do not
accept licences, do not create `local.properties`, and do not let an IDE write one. Adding a
platform or tool means editing the flake, where the change is reviewable; mutating the SDK by
hand produces a machine that builds differently from everyone else's and from CI.

There is no `sdkmanager` binary available in the dev shell on purpose.

## The gate

Every change must pass this before review:

```
nix develop --command ./gradlew spotlessCheck lint testDebugUnitTest :app:assembleDebug :domain:check :data:check
```

Beyond compiling, it enforces:

- **Formatting** — Spotless with ktlint.
- **Lint** — warnings are errors, dependencies are checked too, and the F-Droid
  dependency-info blob is switched off.
- **No proprietary code** — `verifyNoProprietaryDeps` fails the build if a resolved runtime
  dependency belongs to a banned group, or if a runtime artifact's origin cannot be
  identified at all. It is a denylist, and worth understanding as one: known ad, tracker and
  telemetry SDK groups are refused by name, and anything whose provenance cannot be
  determined is refused outright, but a dependency from a group nobody has listed will pass.
  It raises the cost of adding such an SDK rather than making it impossible.
- **Module boundaries** — `:domain` and `:data` are plain Kotlin/JVM modules with no Android
  types, asserted structurally rather than by convention, so they can move to Kotlin
  Multiplatform later. That is a credible path rather than a guarantee — the guard keeps
  Android platform jars off the compile classpaths, which is necessary for such a move but
  not sufficient on its own. The guards are wired into each module's `check`
  task, which is why `:domain:check` and `:data:check` are named in the gate above:
  `testDebugUnitTest` runs those modules' tests but not their `check` tasks, so without them
  the boundary assertion would never run.
- **Tests at real device size** — Robolectric tests run at Pixel 9 dimensions, and the render
  smoke compares against a committed screenshot baseline.

## Screenshot baselines

`ScreenshotMatrixTest` photographs every screen state in `AuditStates` in both themes, one golden per
(state, theme), under `app/src/test/screenshots/matrix/`. The same state list feeds the contrast and type-size
audit, so a screen added to the audit is covered by both without further edits.

A changed baseline is a claim that the UI changed, so baseline changes need diff images reviewed before
re-recording: open the changed PNG, confirm the difference is intended, then record. Re-recording a whole
directory to make a run go green defeats the purpose, because it discards the evidence the baseline exists to
keep.

Record with `./gradlew :app:recordRoborazziDebug --tests "*ScreenshotMatrixTest"`, then re-run
`./gradlew :app:testDebugUnitTest --tests "*ScreenshotMatrixTest"` without it to verify.

## Contributing

Work happens on the `openclaw` fork and reaches upstream through a pull request. Upstream
`main` is protected; direct pushes are refused by the server, not merely discouraged.

The flow:

1. Branch from current upstream `main` in the fork.
2. Make the change, and run the gate above.
3. Push the branch to the fork.
4. Open a pull request against `patrick/roux-sports:main`.
5. Wait for review. Merging is the maintainer's call, never the author's.

Commits should explain *why* a change is what it is. A message that restates the diff has
no value once the diff is visible; the reasoning is the part that is lost otherwise.

## Device testing

See [docs/device-testing.md](docs/device-testing.md) for getting an APK onto a physical
device, and [docs/device-checklist.md](docs/device-checklist.md) for what to check on one
at each stage.

## Release

Releases are published on Forgejo releases and nowhere else.

To build a release, use `scripts/build-release.sh` (arrives with the release). It runs the gate, `assembleRelease`, `check-release-apk.sh`, `apksigner verify`, and writes `app/build/dist/roux-sports-1.0.0.apk` plus its `.sha256`.

Signing uses a `keystore.properties` file (gitignored, never committed) in the project root:

```
storeFile=...
storePassword=...
keyAlias=...
keyPassword=...
```

Verify a download with:

```
sha256sum -c roux-sports-1.0.0.apk.sha256
apksigner verify --print-certs roux-sports-1.0.0.apk
```

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
