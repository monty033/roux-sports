#!/usr/bin/env bash
# To bump fixtures, edit Fixtures.CURRENT in Fixtures.kt first, then run this script.
#
# These fixtures are a point-in-time snapshot, not a stable artifact: a recapture
# will produce a diff. Responses carry volatile metadata (HTTP date, etag, cf-ray,
# standingsDateTimeUtc), and any fixture containing games that have not yet been
# played can also carry changing data, because their state advances. Fixtures about
# dates already in the past are effectively fixed. So do not recapture casually, and
# read the diff rather than assuming it is noise. No LIVE sample exists yet -
# capturing one needs a game actually in progress, so take it when one is on.
set -euo pipefail

# These are the dates the committed fixtures were captured from.
GAME_DAY=2026-10-24
EMPTY_DAY=2026-07-15
FUTURE_DAY=2026-10-26
API=https://api-web.nhle.com/v1

if [[ $# -ne 1 || ${1:-} != base ]]; then
    printf 'Usage: %s base\n' "$0" >&2
    exit 2
fi

REPO_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
FIXTURES_FILE="$REPO_ROOT/data/src/test/kotlin/net/montycasa/roux/data/Fixtures.kt"
if ! CAPTURE_LABEL=$(sed -nE 's/^[[:space:]]*const val CURRENT = "([0-9]{4}-[0-9]{2}-[0-9]{2})"[[:space:]]*$/\1/p' "$FIXTURES_FILE") ||
    [[ ! $CAPTURE_LABEL =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    printf 'ERROR: cannot find a single const val CURRENT = "YYYY-MM-DD" in %s\n' "$FIXTURES_FILE" >&2
    exit 1
fi
ROOT="$REPO_ROOT/data/src/test/resources/fx"
TARGET="$ROOT/$CAPTURE_LABEL"
mkdir -p -- "$ROOT"
STAGING=$(mktemp -d "$ROOT/.capture-$CAPTURE_LABEL.XXXXXX")
CAPTURE="$STAGING/capture"
complete=0

cleanup() {
    if [[ $complete -ne 1 ]]; then
        printf 'Capture failed; removing staging directory: %s\n' "$STAGING" >&2
        if [[ -d "$STAGING/previous" ]]; then
            if [[ -e "$TARGET" ]] && ! mv -- "$TARGET" "$CAPTURE"; then
                printf 'ERROR: could not roll back replacement; previous capture preserved at %s/previous\n' "$STAGING" >&2
                return 1
            fi
            if ! mv -- "$STAGING/previous" "$TARGET"; then
                printf 'ERROR: could not restore previous capture; preserved at %s/previous\n' "$STAGING" >&2
                return 1
            fi
        fi
    fi
    rm -rf -- "$STAGING"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

mkdir -- "$CAPTURE"
{
    printf 'Capture label (Fixtures.CURRENT): %s\n' "$CAPTURE_LABEL"
    printf 'Pinned request dates: game day %s; empty day %s; future day %s\n' "$GAME_DAY" "$EMPTY_DAY" "$FUTURE_DAY"
    printf 'transfer_bytes: curl size_download (body before decompression), or size_header for the headers artifact.\n'
    printf 'stored_bytes: bytes on disk after decompression. SIZES.txt is generated locally, not transferred.\n\n'
} > "$CAPTURE/SIZES.txt"

validate_body() {
    local name=$1 file=$2 shape
    case "$name" in
        # Each score fixture encodes the contract its tests rely on, not merely "this is
        # an array". A recapture that broke a contract would otherwise replace a good
        # corpus with one that fails later, at a point where nobody remembers the cause.
        # Ordered before the score-* glob so they actually apply.
        score-gameday.json) shape='(.games | type == "array") and ((.games | length) > 0) and (.gameWeek | type == "array") and (([.gameWeek[].numberOfGames] | map(. // 0) | add // 0) > 0)' ;;
        # No `// []` fallback here on purpose: a response that omits `games` entirely is a
        # different failure from an off-season day with zero games, and treating a missing
        # field as an empty list would let that response replace the capture.
        score-emptyday.json) shape='(.games | type == "array") and ((.games | length) == 0) and (.gameWeek | type == "array")' ;;
        # This fixture exists to sample upcoming games, so it must stay non-empty and
        # entirely FUT. A pinned date eventually arrives, and without this invariant a
        # recapture would quietly replace the future-state sample with live or final
        # states. If this fails, re-pin FUTURE_DAY to a genuinely future date.
        score-futuredate.json) shape='(.games | type == "array") and ((.games | length) > 0) and ([.games[].gameState] | unique == ["FUT"]) and (.gameWeek | type == "array")' ;;
        score-*.json) shape='(.games | type == "array") and (.gameWeek | type == "array")' ;;
        schedule-gameday.json) shape='.gameWeek | type == "array"' ;;
        standings.json) shape='.standings | type == "array"' ;;
        standings-season.json) shape='.seasons | type == "array"' ;;
        *) printf 'ERROR: no shape check defined for %s\n' "$name" >&2; return 1 ;;
    esac
    if ! jq -e "$shape" "$file" > /dev/null; then
        printf 'ERROR: %s failed JSON shape check: %s\n' "$name" "$shape" >&2
        return 1
    fi
}

capture_body() {
    local name=$1 url=$2
    shift 2
    local metadata status transferred effective stored
    metadata=$(curl --compressed --silent --show-error \
        --connect-timeout 20 --max-time 120 "$@" \
        --output "$CAPTURE/$name" \
        --write-out '%{http_code} %{size_download} %{url_effective}' "$url")
    read -r status transferred effective <<< "$metadata"
    if [[ $status != 200 ]]; then
        printf 'ERROR: %s returned HTTP %s (expected 200)\n' "$url" "$status" >&2
        return 1
    fi
    if [[ $name == standings.json && ! $effective =~ ^https://api-web\.nhle\.com/v1/standings/[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
        printf 'ERROR: standings did not resolve to a dated URL: %s\n' "$effective" >&2
        return 1
    fi
    validate_body "$name" "$CAPTURE/$name"
    stored=$(wc -c < "$CAPTURE/$name")
    printf '%s transfer_bytes=%s stored_bytes=%s url=%s\n' \
        "$name" "$transferred" "$stored" "$effective" >> "$CAPTURE/SIZES.txt"
    printf '%s: HTTP %s; transferred %s bytes; stored %s bytes; %s\n' \
        "$name" "$status" "$transferred" "$stored" "$effective"
}

capture_body score-gameday.json "$API/score/$GAME_DAY"
capture_body score-emptyday.json "$API/score/$EMPTY_DAY"
capture_body score-futuredate.json "$API/score/$FUTURE_DAY"
# No LIVE sample exists yet: capture one when a game is actually in progress.
capture_body schedule-gameday.json "$API/schedule/$GAME_DAY"

# Deliberately omit -L: preserve the raw 307 headers and Location as received.
metadata=$(curl --compressed --silent --show-error \
    --connect-timeout 20 --max-time 120 \
    -D "$CAPTURE/standings-now.headers" --output /dev/null \
    --write-out '%{http_code} %{size_header}' "$API/standings/now")
read -r status transferred <<< "$metadata"
if [[ $status != 307 ]]; then
    printf 'ERROR: %s/standings/now returned HTTP %s (expected 307)\n' "$API" "$status" >&2
    exit 1
fi
if ! grep -qi '^location: .*/v1/standings/20' "$CAPTURE/standings-now.headers"; then
    printf 'ERROR: standings redirect has no dated Location header\n' >&2
    exit 1
fi
stored=$(wc -c < "$CAPTURE/standings-now.headers")
printf 'standings-now.headers transfer_bytes=%s stored_bytes=%s url=%s/standings/now\n' \
    "$transferred" "$stored" "$API" >> "$CAPTURE/SIZES.txt"
printf 'standings-now.headers: HTTP %s; transferred %s bytes; stored %s bytes\n' \
    "$status" "$transferred" "$stored"

capture_body standings.json "$API/standings/now" -L --max-redirs 3 --proto-redir =https
capture_body standings-season.json "$API/standings-season"

# Keep the old capture available for rollback until the validated replacement is installed.
if [[ -e "$TARGET" ]]; then
    mv -- "$TARGET" "$STAGING/previous"
fi
mv -- "$CAPTURE" "$TARGET"
complete=1
printf 'Captured 7 base fixtures in %s\n' "$TARGET"
