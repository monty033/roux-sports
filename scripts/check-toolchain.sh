#!/usr/bin/env bash
# Self-check for the Roux Sports Android toolchain devShell.
# Run this inside `nix develop`; it exits non-zero on the first failed check.
set -euo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

# (a) The active JDK must be major version 21.
if ! java_output="$(java -version 2>&1)"; then
  fail "java -version failed; is JDK 21 on PATH?"
fi
java_major="$(printf '%s\n' "$java_output" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
[ "$java_major" = "21" ] || fail "expected Java major version 21, got '${java_major:-unknown}'"

# (b) Both SDK variables must be set, agree, and point into the Nix store.
[ -n "${ANDROID_HOME:-}" ] || fail "ANDROID_HOME is not set"
[ -n "${ANDROID_SDK_ROOT:-}" ] || fail "ANDROID_SDK_ROOT is not set"
[ "$ANDROID_HOME" = "$ANDROID_SDK_ROOT" ] || fail "ANDROID_HOME ('$ANDROID_HOME') does not equal ANDROID_SDK_ROOT ('$ANDROID_SDK_ROOT')"
case "$ANDROID_HOME" in
  /nix/store/*) ;;
  *) fail "ANDROID_HOME is not under /nix/store: $ANDROID_HOME" ;;
esac

# (c) The API 37 platform must be installed. nixos-26.05 publishes API 37 as
# revision "37.0", so the SDK directory may be android-37 or android-37.0.
platform_jar="$ANDROID_HOME/platforms/android-37/android.jar"
if [ ! -f "$platform_jar" ]; then
  platform_jar="$ANDROID_HOME/platforms/android-37.0/android.jar"
fi
[ -f "$platform_jar" ] || fail "platform 37 android.jar not found (checked platforms/android-37 and platforms/android-37.0 under $ANDROID_HOME)"

# (d) The pinned build-tools aapt2 must run.
aapt2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
[ -x "$aapt2" ] || fail "aapt2 is missing or not executable: $aapt2"
"$aapt2" version >/dev/null 2>&1 || fail "aapt2 version failed: $aapt2"

# (e) The SDK must be an immutable store path, never a writable SDK copy.
[ ! -w "$ANDROID_HOME" ] || fail "Android SDK is writable, expected an immutable /nix/store path: $ANDROID_HOME"

# (f) Gradle must resolve aapt2 from the SDK, not from Maven.
aapt2_override="aapt2FromMavenOverride=$ANDROID_HOME/build-tools/36.0.0/aapt2"
case "${GRADLE_OPTS:-}" in
  *"$aapt2_override"*) ;;
  *) fail "GRADLE_OPTS does not contain $aapt2_override" ;;
esac

# (g) local.properties must not exist in this workflow: the SDK comes from the
# devShell. Any semantics-sensitive parse of a Java properties file can fail
# open, and a file that must never exist here only needs a presence test.
script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")" || fail "could not resolve the script directory"
project_root="$(dirname -- "$script_dir")"
local_properties="$project_root/local.properties"
if [ -f "$local_properties" ]; then
  fail "local.properties exists at '$local_properties': the Android SDK for this project comes from the Nix devShell; local.properties is not part of the workflow. Delete the file. If a tool created it, the SDK is still supplied by ANDROID_HOME."
fi

# (h) This script must never invoke the legacy SDK package manager.
forbidden_tool=sdk''manager
if grep -qF -- "$forbidden_tool" "$0"; then
  fail "check-toolchain.sh must not reference $forbidden_tool"
fi

printf 'TOOLCHAIN OK\n'
