#!/usr/bin/env bash
# Release build (plan S10.7): the one command that turns a clean commit and the real keystore into a signed,
# checked, published APK. In order it:
#   1. refuses unless keystore.properties exists at the repository root;
#   2. refuses when a tracked file is modified (the artifact's provenance is the commit SHA);
#   3. prints HEAD and the versionName/versionCode from app/build.gradle.kts;
#   4. runs the README gate;
#   5. runs scripts/check-metadata.sh;
#   6. builds :app:assembleRelease;
#   7. runs scripts/check-release-apk.sh on the signed APK;
#   8. runs apksigner verify --print-certs and prints the signer DN and SHA-256;
#   9. publishes app/build/dist/roux-sports-<versionName>.apk and its .sha256, then proves the pair with
#      `sha256sum -c` run inside that directory;
#  10. prints a summary.
# Run it inside `nix develop`. apksigner is taken from PATH, otherwise from the newest $ANDROID_HOME/build-tools/*/
# directory. It works from any cwd and exits non-zero at the first step that does not print PASS. It only ever
# reads keystore.properties: it never deletes, rewrites or prints it, and never prints a password. Gradle output
# goes to a log in a mktemp -d directory (removed by the trap); only the tail of a failing run is shown.
set -euo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")" || fail "could not resolve the script directory"
project_root="$(dirname -- "$script_dir")"
cd -- "$project_root"

properties_file="$project_root/keystore.properties"
release_dir="$project_root/app/build/outputs/apk/release"
dist_dir="$project_root/app/build/dist"
work_dir=""

cleanup() {
  if [ -n "$work_dir" ]; then
    rm -rf -- "$work_dir"
  fi
}
trap cleanup EXIT

resolve_tool() {
  local name="$1" found
  if found="$(command -v "$name")"; then
    printf '%s\n' "$found"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ]; then
    found="$(ls -d -- "$ANDROID_HOME"/build-tools/*/"$name" 2>/dev/null | sort -V | tail -n 1 || true)"
    if [ -n "$found" ] && [ -x "$found" ]; then
      printf '%s\n' "$found"
      return
    fi
  fi
  fail "$name not found on PATH or under \$ANDROID_HOME/build-tools; run this inside nix develop"
}

run_gradle() {
  # Output goes to a log so a failure can show its tail without flooding the transcript.
  local log="$1"
  shift
  if ! ./gradlew "$@" --console=plain >"$log" 2>&1; then
    tail -n 40 -- "$log" >&2
    return 1
  fi
}

# (1) A release is signed with the real key; without it there is nothing to build.
[ -f "$properties_file" ] || fail "keystore.properties not found at '$properties_file'; create it (storeFile, storePassword, keyAlias, keyPassword) before building a release"
pass "(1) keystore.properties found"

# (2) Tracked files must match HEAD. Untracked files are ignored; staged and unstaged changes both count.
command -v git >/dev/null 2>&1 || fail "git not found on PATH; run this inside nix develop"
dirty="$(git status --porcelain --untracked-files=no)" || fail "git status failed"
if [ -n "$dirty" ]; then
  printf '%s\n' "$dirty" | sed 's/^/  /' >&2
  fail "the working tree has modified tracked files; a release must be built from a commit"
fi
pass "(2) no modified tracked files"

apksigner="$(resolve_tool apksigner)"
work_dir="$(mktemp -d)"

# (3) The transcript records exactly what was built.
head_sha="$(git rev-parse --short HEAD)" || fail "could not read HEAD"
version_name="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]\+\).*/\1/p' app/build.gradle.kts | head -n 1)"
[ -n "$version_name" ] || fail "could not read versionName from app/build.gradle.kts"
[ -n "$version_code" ] || fail "could not read versionCode from app/build.gradle.kts"
printf 'HEAD %s\n' "$head_sha"
printf 'versionName %s\n' "$version_name"
printf 'versionCode %s\n' "$version_code"
pass "(3) HEAD, versionName and versionCode recorded"

# (4) The gate, verbatim from the README.
run_gradle "$work_dir/gate.log" spotlessCheck lint testDebugUnitTest :app:assembleDebug :domain:check :data:check || fail "the gate failed"
pass "(4) gate: spotlessCheck lint testDebugUnitTest :app:assembleDebug :domain:check :data:check"

# (5) The store listing.
scripts/check-metadata.sh || fail "check-metadata.sh failed"
pass "(5) check-metadata.sh"

# (6) Build the signed release. Clear the output directory first so a stale APK can never be picked up.
rm -rf -- "$release_dir"
run_gradle "$work_dir/release.log" :app:assembleRelease || fail "assembleRelease failed"
apk="$release_dir/app-release.apk"
[ -f "$apk" ] || fail "assembleRelease did not produce the signed $apk (is keystore.properties complete?)"
pass "(6) assembleRelease produced $apk"

# (7) The APK audit.
scripts/check-release-apk.sh "$apk" || fail "check-release-apk.sh failed"
pass "(7) check-release-apk.sh"

# (8) Which key signed this build.
signer_output="$("$apksigner" verify --print-certs "$apk" 2>&1)" || fail "apksigner verify rejected $apk"
signer_dn="$(printf '%s\n' "$signer_output" | sed -n 's/^Signer #1 certificate DN: //p')"
signer_sha256="$(printf '%s\n' "$signer_output" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[ -n "$signer_dn" ] || fail "apksigner verify printed no signer DN for $apk"
[ -n "$signer_sha256" ] || fail "apksigner verify printed no signer SHA-256 for $apk"
printf '      Signer #1 certificate DN: %s\n' "$signer_dn"
printf '      Signer #1 certificate SHA-256 digest: %s\n' "$signer_sha256"
pass "(8) apksigner verify --print-certs"

# (9) Publish, then prove the pair with sha256sum -c inside the directory that holds it.
artifact_name="roux-sports-$version_name.apk"
artifact="$dist_dir/$artifact_name"
mkdir -p -- "$dist_dir"
rm -f -- "$artifact" "$artifact.sha256"
cp -- "$apk" "$artifact"
apk_sha256="$(sha256sum -- "$artifact" | cut -d ' ' -f 1)"
[ -n "$apk_sha256" ] || fail "could not hash $artifact"
printf '%s  %s\n' "$apk_sha256" "$artifact_name" >"$artifact.sha256"
(cd -- "$dist_dir" && sha256sum -c "$artifact_name.sha256") || fail "sha256sum -c rejected $artifact.sha256"
pass "(9) published $artifact and $artifact.sha256; sha256sum -c OK"

# (10) Summary.
apk_bytes="$(stat -c %s -- "$artifact")"
printf '\nRELEASE BUILD OK\n'
printf 'artifact: %s\n' "$artifact"
printf 'size:     %s bytes\n' "$apk_bytes"
printf 'sha256:   %s\n' "$apk_sha256"
printf 'signer:   %s\n' "$signer_dn"
printf 'HEAD:     %s\n' "$head_sha"
printf 'The APK is ready to attach to the Forgejo release.\n'
