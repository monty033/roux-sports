#!/usr/bin/env bash
# Bump Roux Sports to the next release version (date-based names).
#
#   name = YYYY.MM.DD            first release of a day
#        = YYYY.MM.DD.N          Nth release of the same day, 1 <= N <= 9
#   code = YYYYMMDD * 10 + N
#
# Usage:
#   scripts/bump-version.sh [--dry-run] [--date YYYY-MM-DD]
#
# The date is UTC, because the release runner is UTC and the name must describe the release the
# runner builds. The next counter is read from the tags that already exist, so a second release on
# the same day becomes .1 rather than a re-used name, and the code never repeats.
#
# It also creates the changelog file the release expects, seeded with a TODO line that the release
# preflight refuses, so an unfilled changelog cannot be shipped by accident.
set -euo pipefail

script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")"
project_root="$(dirname -- "$script_dir")"
cd -- "$project_root"

dry_run=0
date_arg=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --dry-run) dry_run=1; shift ;;
    --date) [ "$#" -ge 2 ] || { echo "--date needs YYYY-MM-DD" >&2; exit 2; }; date_arg="$2"; shift 2 ;;
    -h|--help) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

today="${date_arg:-$(date -u +%Y-%m-%d)}"
if ! [[ "$today" =~ ^([0-9]{4})-([0-9]{2})-([0-9]{2})$ ]]; then
  echo "not a date: $today" >&2
  exit 2
fi
dotted="${today//-/.}"

# Highest counter already released on this date. Tags are the record, not a file that can drift.
highest=-1
while read -r tag; do
  [ -n "$tag" ] || continue
  if [ "$tag" = "v$dotted" ]; then
    [ 0 -gt "$highest" ] && highest=0
  elif [[ "$tag" =~ ^v${dotted//./\\.}\.([1-9])$ ]]; then
    n="${BASH_REMATCH[1]}"
    [ "$n" -gt "$highest" ] && highest="$n"
  fi
done < <(git tag -l "v$dotted*" 2>/dev/null || true)

if [ "$highest" -lt 0 ]; then
  counter=0
  name="$dotted"
else
  counter=$((highest + 1))
  [ "$counter" -le 9 ] || { echo "more than 9 releases on $dotted; pick another day" >&2; exit 1; }
  name="$dotted.$counter"
fi
y="${today%%-*}"; rest="${today#*-}"; m="${rest%%-*}"; d="${rest##*-}"
code="$(( (10#$y * 10000 + 10#$m * 100 + 10#$d) * 10 + counter ))"

"$script_dir/check-version.sh" "$name" "$code" >/dev/null
changelog="fastlane/metadata/android/en-US/changelogs/$code.txt"

printf 'version:   %s\nversionCode: %s\nchangelog:  %s\n' "$name" "$code" "$changelog"
if [ "$dry_run" -eq 1 ]; then
  printf 'dry run: nothing written\n'
  exit 0
fi

gradle="app/build.gradle.kts"
[ -f "$gradle" ] || { echo "missing $gradle" >&2; exit 1; }
current_name="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"$/\1/p' "$gradle" | head -1)"
current_code="$(sed -n 's/^[[:space:]]*versionCode = \([0-9]*\).*$/\1/p' "$gradle" | head -1)"
if [ "$current_name" = "$name" ] && [ "$current_code" = "$code" ]; then
  printf 'already at %s (%s)\n' "$name" "$code"
else
  [ "$code" -gt "${current_code:-0}" ] \
    || { echo "refusing to lower versionCode from $current_code to $code" >&2; exit 1; }
  sed -i "s|^\([[:space:]]*\)versionCode = [0-9]*|\1versionCode = $code|" "$gradle"
  sed -i "s|^\([[:space:]]*\)versionName = \".*\"|\1versionName = \"$name\"|" "$gradle"
  printf 'wrote %s and %s into %s\n' "$name" "$code" "$gradle"
fi

mkdir -p "$(dirname -- "$changelog")"
if [ -f "$changelog" ]; then
  printf '%s already exists\n' "$changelog"
else
  printf 'TODO: describe this release.\n' > "$changelog"
  printf 'created %s - replace the TODO line before releasing\n' "$changelog"
fi
