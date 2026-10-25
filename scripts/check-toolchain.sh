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

# (g) AGP prefers sdk.dir from local.properties over the environment, so an
# environment agreeing with itself proves nothing about what Gradle will use.
# If local.properties pins a different SDK, this check must fail.
script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")" || fail "could not resolve the script directory"
project_root="$(dirname -- "$script_dir")"
local_properties="$project_root/local.properties"
if [ -f "$local_properties" ]; then
  # Java properties accept '=', ':' or whitespace as the key/value separator.
  sdk_dir_line="$(grep -E '^[[:space:]]*sdk\.dir([[:space:]]*[=:][[:space:]]*|[[:space:]]+|$)' "$local_properties" | tail -n 1 || true)"
  if [ -n "$sdk_dir_line" ]; then
    sdk_dir_value="$(printf '%s\n' "$sdk_dir_line" | sed -E 's/^[[:space:]]*sdk\.dir[[:space:]]*[=:]?[[:space:]]*//')"
    sdk_dir_value="${sdk_dir_value%"${sdk_dir_value##*[![:space:]]}"}"
    [ -n "$sdk_dir_value" ] || fail "local.properties has an empty sdk.dir but ANDROID_HOME is '$ANDROID_HOME'"
    # AGP resolves a relative sdk.dir against the Gradle project root.
    case "$sdk_dir_value" in
      /*) sdk_dir_path="$sdk_dir_value" ;;
      *) sdk_dir_path="$project_root/$sdk_dir_value" ;;
    esac
    sdk_dir_real="$(realpath -m -- "$sdk_dir_path")" || fail "could not resolve sdk.dir '$sdk_dir_value' from $local_properties"
    android_home_real="$(realpath -m -- "$ANDROID_HOME")" || fail "could not resolve ANDROID_HOME '$ANDROID_HOME'"
    [ "$sdk_dir_real" = "$android_home_real" ] || fail "local.properties sdk.dir ('$sdk_dir_value' -> '$sdk_dir_real') does not match ANDROID_HOME ('$ANDROID_HOME' -> '$android_home_real')"
  fi
fi

# (h) This script must never invoke the legacy SDK package manager.
forbidden_tool=sdk''manager
if grep -qF -- "$forbidden_tool" "$0"; then
  fail "check-toolchain.sh must not reference $forbidden_tool"
fi

printf 'TOOLCHAIN OK\n'
