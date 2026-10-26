# Roux Sports

An ad-free, open-source NHL scores app for Android.

NHL scores, live game state, and standings. No ads, no trackers, no telemetry.

Kotlin and Jetpack Compose. GPL-3.0-or-later.

## Building

You need [Nix](https://nixos.org/) with flakes enabled. Everything else — JDK, Android
SDK, build tools — comes from the flake. Nothing is installed on your host.

The single build command:

```
nix develop --command ./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

The first `nix develop` is slow, because it builds the Android SDK from source on your
machine; expect several minutes and a lot of disk. Later runs reuse the store path and
start in seconds.

If your Nix does not have flakes enabled, prefix any command with the flag rather than
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
nix develop --command ./gradlew spotlessCheck lint testDebugUnitTest :app:assembleDebug
```

Beyond compiling, it enforces:

- **Formatting** — Spotless with ktlint.
- **Lint** — warnings are errors, dependencies are checked too, and the F-Droid
  dependency-info blob is switched off.
- **No proprietary code** — `verifyNoProprietaryDeps` fails the build if a resolved runtime
  dependency belongs to a banned group, or if a runtime artifact's origin cannot be
  identified at all. Ads, trackers and telemetry SDKs are refused by name.
- **Module boundaries** — `:domain` and `:data` are plain Kotlin/JVM modules with no Android
  types, asserted structurally rather than by convention, so they can move to Kotlin
  Multiplatform later without a rewrite.
- **Tests at real device size** — Robolectric tests run at Pixel 9 dimensions, and the render
  smoke compares against a committed screenshot baseline.

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

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
