#!/usr/bin/env bash
#
# Metadata gate for the store listing (plan S10.5). It proves:
#   1. title.txt is at most 50 characters;
#   2. short_description.txt is at most 80;
#   3. full_description.txt is at most 4000, and states "No ads" and "No tracking";
#   4. a changelog exists for the versionCode in app/build.gradle.kts.
#
# Character counts are of the text itself, not of the file: a trailing newline is not a character the store
# counts, and counting it would turn a 50-character title into a failure. It works from any cwd and exits
# non-zero on the first failed check.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METADATA="$ROOT/fastlane/metadata/android/en-US"

TITLE_MAX=50
SHORT_MAX=80
FULL_MAX=4000

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

# The characters a store listing would show, with no trailing newline.
text_of() {
  printf '%s' "$(cat "$1")"
}

require_file() {
  [ -f "$1" ] || fail "missing $1"
}

require_file "$METADATA/title.txt"
require_file "$METADATA/short_description.txt"
require_file "$METADATA/full_description.txt"

title_chars=$(text_of "$METADATA/title.txt" | wc -m | tr -d ' ')
printf 'title: %s characters\n' "$title_chars"
[ "$title_chars" -le "$TITLE_MAX" ] || fail "title is $title_chars characters, limit is $TITLE_MAX"
pass "title is within $TITLE_MAX characters"

short_chars=$(text_of "$METADATA/short_description.txt" | wc -m | tr -d ' ')
printf 'short description: %s characters\n' "$short_chars"
[ "$short_chars" -le "$SHORT_MAX" ] || fail "short description is $short_chars characters, limit is $SHORT_MAX"
pass "short description is within $SHORT_MAX characters"

full_chars=$(text_of "$METADATA/full_description.txt" | wc -m | tr -d ' ')
printf 'full description: %s characters\n' "$full_chars"
[ "$full_chars" -le "$FULL_MAX" ] || fail "full description is $full_chars characters, limit is $FULL_MAX"
pass "full description is within $FULL_MAX characters"

grep -q 'No ads' "$METADATA/full_description.txt" || fail "full description does not say 'No ads'"
pass "full description says 'No ads'"

grep -q 'No tracking' "$METADATA/full_description.txt" || fail "full description does not say 'No tracking'"
pass "full description says 'No tracking'"

# The changelog file name follows the shipped versionCode, so it cannot drift from the build.
version_code=$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9]\+\).*/\1/p' "$ROOT/app/build.gradle.kts")
[ -n "$version_code" ] || fail "could not read versionCode from app/build.gradle.kts"
changelog="$METADATA/changelogs/$version_code.txt"
[ -f "$changelog" ] || fail "missing changelog for versionCode $version_code at $changelog"
[ -s "$changelog" ] || fail "changelog for versionCode $version_code is empty"
pass "changelog for versionCode $version_code exists and is not empty"

printf 'METADATA OK\n'
