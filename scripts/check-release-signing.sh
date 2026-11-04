#!/usr/bin/env bash
# Acceptance test for the release signing and shrinking setup (plan S10.1). It proves, in order:
#   1. with no keystore.properties, assembleRelease succeeds and produces app-release-unsigned.apk;
#   2. with a throwaway keystore and a temporary keystore.properties, assembleRelease succeeds and
#      apksigner verifies the APK and prints a signer certificate;
#   3. keystore.properties is git-ignored, so the signing secrets cannot be committed;
#   4. R8 ran (minifyReleaseWithR8, forced to re-execute) and reported no "Missing class" errors.
# Run it inside `nix develop`. keytool comes from the JDK on PATH; apksigner is taken from PATH, otherwise from
# the newest $ANDROID_HOME/build-tools/*/ directory. It works from any cwd. It refuses to run when a real
# keystore.properties already exists, and only ever creates a throwaway keystore inside a mktemp -d directory;
# the trap removes that directory and the temporary keystore.properties on every exit, including failure.
# Nothing here prints the throwaway password.
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
work_dir=""
created_properties=0

cleanup() {
  if [ "$created_properties" = 1 ]; then
    rm -f -- "$properties_file"
  fi
  if [ -n "$work_dir" ]; then
    rm -rf -- "$work_dir"
  fi
}
trap cleanup EXIT

# A keystore.properties that already exists is somebody's real signing config: never overwrite or delete it.
[ ! -e "$properties_file" ] || fail "keystore.properties already exists at '$properties_file'; move it away so this script can test the unsigned path, then restore it afterwards"

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

keytool="$(resolve_tool keytool)"
apksigner="$(resolve_tool apksigner)"

work_dir="$(mktemp -d)"

run_gradle() {
  # Output goes to a log so a failure can show its tail without flooding the transcript.
  local log="$1"
  shift
  if ! ./gradlew "$@" --console=plain >"$log" 2>&1; then
    tail -n 40 -- "$log" >&2
    return 1
  fi
}

# (1) No keystore.properties: the build must still work and be unsigned.
rm -rf -- "$release_dir"
run_gradle "$work_dir/unsigned.log" :app:assembleRelease || fail "assembleRelease failed without keystore.properties"
[ -f "$release_dir/app-release-unsigned.apk" ] || fail "assembleRelease without keystore.properties did not produce $release_dir/app-release-unsigned.apk"
pass "(1) no keystore.properties: assembleRelease exits 0 and produces app-release-unsigned.apk"

# (2) A throwaway keystore: the build must sign, and apksigner must accept the result.
# The password is random, travels to keytool and apksigner through the environment, and is only ever written to
# the temporary keystore.properties that the trap removes.
store_password="$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
export CHECK_RELEASE_SIGNING_PASSWORD="$store_password"
"$keytool" -genkeypair -keystore "$work_dir/throwaway.jks" -storetype PKCS12 \
  -storepass:env CHECK_RELEASE_SIGNING_PASSWORD -keypass:env CHECK_RELEASE_SIGNING_PASSWORD \
  -alias throwaway -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=Roux throwaway release check" >/dev/null 2>&1 || fail "keytool could not create the throwaway keystore"
created_properties=1
(
  umask 077
  {
    printf 'storeFile=%s\n' "$work_dir/throwaway.jks"
    printf 'storePassword=%s\n' "$store_password"
    printf 'keyAlias=throwaway\n'
    printf 'keyPassword=%s\n' "$store_password"
  } >"$properties_file"
)
rm -rf -- "$release_dir"
run_gradle "$work_dir/signed.log" :app:assembleRelease || fail "assembleRelease failed with a throwaway keystore.properties"
signed_apk="$release_dir/app-release.apk"
[ -f "$signed_apk" ] || fail "assembleRelease with keystore.properties did not produce $signed_apk"
signer_output="$("$apksigner" verify --print-certs "$signed_apk" 2>&1)" || fail "apksigner verify rejected $signed_apk"
printf '%s\n' "$signer_output" | grep -q 'Signer #1 certificate DN' || fail "apksigner verify printed no signer for $signed_apk"
pass "(2) throwaway keystore: assembleRelease exits 0 and apksigner verifies the APK"
printf '%s\n' "$signer_output" | grep -E 'Signer #1 certificate (DN|SHA-256)' | sed 's/^/      /'

# Remove the temporary secrets before anything else runs.
rm -f -- "$properties_file"
created_properties=0
unset CHECK_RELEASE_SIGNING_PASSWORD store_password

# (3) The properties file can never be committed.
git check-ignore -q keystore.properties || fail "keystore.properties is not git-ignored"
pass "(3) git check-ignore: keystore.properties is ignored"

# (4) R8 must really run - rerun that one task so an up-to-date or cached result cannot hide a problem - and it
# must not report missing classes, which would mean a library's optional dependency needs a -dontwarn or keep rule.
r8_log="$work_dir/r8.log"
run_gradle "$r8_log" :app:minifyReleaseWithR8 --rerun --no-build-cache || fail "minifyReleaseWithR8 failed"
grep -q 'Task :app:minifyReleaseWithR8$' "$r8_log" || fail "minifyReleaseWithR8 did not execute (it was up to date, cached or skipped)"
if grep -q 'Missing class' "$r8_log"; then
  grep 'Missing class' "$r8_log" >&2
  fail "R8 reported missing classes"
fi
pass "(4) minifyReleaseWithR8 executed and reported no Missing class errors"

printf 'RELEASE SIGNING OK\n'
