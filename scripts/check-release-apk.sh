#!/usr/bin/env bash
# Audit of the built release APK (plan S10.2). It proves:
#   1. every permission the APK requests is in the recorded allowlist below;
#   2. no entry in the APK belongs to Firebase or Google Play services;
#   3. the badging package is net.montycasa.roux, versionName 1.0.0 and versionCode 1.
# Usage: check-release-apk.sh [APK]    APK defaults to the single APK in app/build/outputs/apk/release.
#        check-release-apk.sh --self-test    needs no APK; feeds the permission check a list containing
#                                            android.permission.READ_CONTACTS and exits 1 because it is rejected.
# Run it inside `nix develop`. aapt2 is taken from PATH, otherwise from the newest $ANDROID_HOME/build-tools/*/
# directory. APK entries are listed with `unzip -Z1`, or with the JDK's `jar tf` when unzip is not installed
# (the devShell has no unzip). It works from any cwd and exits non-zero on the first failed check.
set -euo pipefail

EXPECTED_PACKAGE=net.montycasa.roux
EXPECTED_VERSION_NAME=1.0.0
EXPECTED_VERSION_CODE=1

# The permission allowlist, recorded from `aapt2 dump permissions` on the release APK built from this tree.
# Two are the app's own (manifest): INTERNET for the NHL endpoints and logos, POST_NOTIFICATIONS for alerts.
# The rest are merged in by WorkManager (work-runtime 2.12.0) and exist so periodic work survives idle and reboot:
#   WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE.
# <package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is androidx.core's own signature-level permission, declared
# and used by this package alone; it is allowed for this package only (see allowed_permission below).
ALLOWED_PERMISSIONS=(
  android.permission.INTERNET
  android.permission.POST_NOTIFICATIONS
  android.permission.WAKE_LOCK
  android.permission.ACCESS_NETWORK_STATE
  android.permission.RECEIVE_BOOT_COMPLETED
  android.permission.FOREGROUND_SERVICE
)

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

allowed_permission() {
  local candidate="$1" allowed
  for allowed in "${ALLOWED_PERMISSIONS[@]}"; do
    [ "$candidate" != "$allowed" ] || return 0
  done
  [ "$candidate" = "$EXPECTED_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" ]
}

# Reads one permission name per line on stdin; prints each one outside the allowlist and returns 1 if any.
check_permissions() {
  local name rejected=0
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    if ! allowed_permission "$name"; then
      printf '  not allowed: %s\n' "$name" >&2
      rejected=1
    fi
  done
  return "$rejected"
}

if [ "${1:-}" = "--self-test" ]; then
  # A clean list must pass, or a checker that rejects everything would "pass" this self-test.
  printf '%s\n' "${ALLOWED_PERMISSIONS[@]}" | check_permissions || fail "self-test: the allowlist itself was rejected"
  printf 'self-test: allowlist accepted; now offering android.permission.READ_CONTACTS\n'
  if printf '%s\n' "${ALLOWED_PERMISSIONS[@]}" android.permission.READ_CONTACTS | check_permissions; then
    printf 'SELF-TEST BROKEN: android.permission.READ_CONTACTS was accepted\n' >&2
    exit 2
  fi
  fail "permissions include android.permission.READ_CONTACTS (self-test: this rejection is the expected result)"
fi

script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")" || fail "could not resolve the script directory"
project_root="$(dirname -- "$script_dir")"

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

if [ "$#" -ge 1 ]; then
  apk="$1"
else
  shopt -s nullglob
  candidates=("$project_root"/app/build/outputs/apk/release/*.apk)
  shopt -u nullglob
  [ "${#candidates[@]}" -eq 1 ] || fail "expected exactly one APK in $project_root/app/build/outputs/apk/release, found ${#candidates[@]}; run :app:assembleRelease or pass the APK path"
  apk="${candidates[0]}"
fi
[ -f "$apk" ] || fail "APK not found: $apk"
printf 'APK: %s\n' "$apk"

aapt2="$(resolve_tool aapt2)"

list_entries() {
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$1"
  else
    jar tf "$1"
  fi
}

# (1) Permissions.
permissions_output="$("$aapt2" dump permissions "$apk")" || fail "aapt2 dump permissions failed for $apk"
permissions="$(printf '%s\n' "$permissions_output" | sed -n "s/^uses-permission: name='\\([^']*\\)'.*/\\1/p")"
printf 'permissions requested:\n'
printf '%s\n' "$permissions" | sed 's/^/  /'
printf '%s\n' "$permissions" | check_permissions || fail "the APK requests permissions outside the allowlist"
pass "permissions are a subset of the allowlist"

# (2) No Firebase or Google Play services anywhere in the package.
entries="$(list_entries "$apk")" || fail "could not list the entries of $apk"
proprietary="$(printf '%s\n' "$entries" | grep -iE 'firebase|play-services|com/google/android/gms' || true)"
if [ -n "$proprietary" ]; then
  printf '%s\n' "$proprietary" >&2
  fail "the APK contains Firebase or Google Play services entries"
fi
pass "no firebase, play-services or com/google/android/gms entries"

# (3) Badging.
badging="$("$aapt2" dump badging "$apk")" || fail "aapt2 dump badging failed for $apk"
package_line="$(printf '%s\n' "$badging" | grep -m1 '^package:' || true)"
[ -n "$package_line" ] || fail "aapt2 dump badging printed no package line"
package_name="$(printf '%s\n' "$package_line" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
version_code="$(printf '%s\n' "$package_line" | sed -n "s/.*versionCode='\\([^']*\\)'.*/\\1/p")"
version_name="$(printf '%s\n' "$package_line" | sed -n "s/.*versionName='\\([^']*\\)'.*/\\1/p")"
printf 'badging: package=%s versionName=%s versionCode=%s\n' "$package_name" "$version_name" "$version_code"
[ "$package_name" = "$EXPECTED_PACKAGE" ] || fail "badging package is '$package_name', expected $EXPECTED_PACKAGE"
[ "$version_name" = "$EXPECTED_VERSION_NAME" ] || fail "badging versionName is '$version_name', expected $EXPECTED_VERSION_NAME"
[ "$version_code" = "$EXPECTED_VERSION_CODE" ] || fail "badging versionCode is '$version_code', expected $EXPECTED_VERSION_CODE"
pass "badging package, versionName and versionCode"

printf 'RELEASE APK OK\n'
