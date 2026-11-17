#!/usr/bin/env bash
# The version rule for Roux Sports (plan: date-based version names).
#
#   name = YYYY.MM.DD            first release of a day
#        = YYYY.MM.DD.N          Nth release of the same day, 1 <= N <= 9
#   code = YYYYMMDD * 10 + N
#
# Usage:
#   check-version.sh NAME CODE     validate one pair; prints PASS or FAIL and exits 1 on failure
#   check-version.sh --self-test   run the accept/reject table below, no arguments needed
#
# The date is the UTC date at the moment the version is bumped. The rule is enforced rather than
# trusted: a name that is not a real calendar date, or a code that does not follow from the name,
# cannot ship, because a wrong code silently lowers the install version and strands users.
set -uo pipefail

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

usage() {
  printf 'usage: %s NAME CODE | --self-test\n' "$(basename -- "$0")" >&2
  exit 2
}

# Prints "<name> <code>" for a name, or nothing when the name is malformed.
derive() {
  local name="$1" y m d n rest
  if [[ "$name" =~ ^([0-9]{4})\.([0-9]{2})\.([0-9]{2})(\.([1-9][0-9]?))?$ ]]; then
    y="${BASH_REMATCH[1]}"
    m="${BASH_REMATCH[2]}"
    d="${BASH_REMATCH[3]}"
    n="${BASH_REMATCH[5]:-0}"
  else
    return 0
  fi
  # The month/day shape above still accepts 2026.02.30 and 2026.13.01; require a real date by
  # round-tripping it through date(1) in UTC.
  [ "$(date -u -d "$y-$m-$d" +%Y-%m-%d 2>/dev/null)" = "$y-$m-$d" ] || return 0
  printf '%s %s\n' "$name" "$(( (10#$y * 10000 + 10#$m * 100 + 10#$d) * 10 + 10#$n ))"
}

check_pair() {
  local name="$1" code="$2" derived derived_name derived_code
  [ -n "$name" ] || fail "versionName is empty"
  [ -n "$code" ] || fail "versionCode is empty"
  derived="$(derive "$name")"
  [ -n "$derived" ] || fail "versionName '$name' is not a release version (expected YYYY.MM.DD or YYYY.MM.DD.N)"
  derived_name="${derived% *}"
  derived_code="${derived#* }"
  case "$code" in
    ''|*[!0-9]*) fail "versionCode '$code' is not a number" ;;
  esac
  [ "$code" = "$derived_code" ] \
    || fail "versionCode $code does not follow versionName $derived_name (expected $derived_code)"
  printf 'PASS: version %s code %s\n' "$name" "$code"
}

self_test() {
  local failures=0 case_name name code want
  # accepted pairs
  for case_name in "2026.11.16:202611160" "2026.11.16.1:202611161" "2026.11.16.9:202611169" \
                   "2024.02.29:202402290" "2099.12.31:209912310"; do
    name="${case_name%:*}"; code="${case_name##*:}"
    if ( check_pair "$name" "$code" >/dev/null 2>&1 ); then
      printf 'ok   accepts %s / %s\n' "$name" "$code"
    else
      printf 'FAIL should accept %s / %s\n' "$name" "$code"; failures=$((failures + 1))
    fi
  done
  # rejected pairs: malformed names, impossible dates, codes that do not follow, hyphen form
  for case_name in "1.0.2:102" "2026.11.16-1:202611160" "2026.1.18:202601180" \
                   "2026.11.18.0:202611180" "2026.11.10:20261111" "2026.02.30:202602300" \
                   "2026.13.01:202613010" "2026.11.18:202611181" "2026.11.18.1:202611180" \
                   "2026.11.18:abc" "dev:1" ":1"; do
    name="${case_name%:*}"; code="${case_name##*:}"
    if ( check_pair "$name" "$code" >/dev/null 2>&1 ); then
      printf 'FAIL should reject %s / %s\n' "$name" "$code"; failures=$((failures + 1))
    else
      printf 'ok   rejects %s / %s\n' "$name" "$code"
    fi
  done
  if [ "$failures" -ne 0 ]; then
    printf 'SELF-TEST FAILED: %d case(s)\n' "$failures" >&2
    exit 1
  fi
  printf 'SELF-TEST OK\n'
}

case "${1:-}" in
  --self-test) self_test ;;
  ''|-*) usage ;;
  *) [ "$#" -eq 2 ] || usage; check_pair "$1" "$2" ;;
esac
