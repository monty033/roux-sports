#!/usr/bin/env bash
# To bump fixtures, edit Fixtures.CURRENT in Fixtures.kt first, then run this script.
#
# These fixtures are a point-in-time snapshot, not a stable artifact: a recapture
# will produce a diff. Responses carry volatile metadata (HTTP date, etag, cf-ray,
# standingsDateTimeUtc), and any fixture containing games that have not yet been
# played can also carry changing data, because their state advances. Fixtures about
# dates already in the past are effectively fixed. So do not recapture casually, and
# read the diff rather than assuming it is noise.
#
# Two modes:
#   base        the slate, schedule and standings corpus. Runs any time.
#   gamecenter  one finished game and one game in progress, from the three
#               gamecenter endpoints. The LIVE half needs a game actually being
#               played, so this mode refuses to run rather than capture a corpus
#               that never sampled LIVE - a parser test written against a half
#               corpus would pass and mean nothing.
set -euo pipefail

# These are the dates the committed fixtures were captured from.
GAME_DAY=2026-10-24
EMPTY_DAY=2026-07-15
FUTURE_DAY=2026-10-26
API=https://api-web.nhle.com/v1

usage() {
    printf 'Usage:\n  %s base\n  %s gamecenter [--final GAME_ID] [--live GAME_ID]\n' "$0" "$0" >&2
    printf '\nWithout --final/--live the ids are chosen from the slate: the final one from\n' >&2
    printf '%s, the live one from score/now.\n' "$GAME_DAY" >&2
}

MODE=${1:-}
case $MODE in
    base | gamecenter) shift ;;
    *)
        usage
        exit 2
        ;;
esac

FINAL_ID=''
LIVE_ID=''
while [[ $# -gt 0 ]]; do
    case $1 in
        --final | --live)
            if [[ $# -lt 2 || ! ${2:-} =~ ^[0-9]{10}$ ]]; then
                printf 'ERROR: %s needs a 10-digit game id\n' "$1" >&2
                usage
                exit 2
            fi
            if [[ $1 == --final ]]; then FINAL_ID=$2; else LIVE_ID=$2; fi
            shift 2
            ;;
        *)
            printf 'ERROR: unexpected argument %s\n' "$1" >&2
            usage
            exit 2
            ;;
    esac
done

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
        if [[ $MODE == gamecenter ]]; then
            # The target holds the base corpus too, so it is restored whole from the copy taken
            # before the first file was installed rather than file by file.
            if [[ -d "$STAGING/previous" ]]; then
                rm -rf -- "$TARGET"
                if ! mv -- "$STAGING/previous" "$TARGET"; then
                    printf 'ERROR: could not restore previous capture; preserved at %s/previous\n' "$STAGING" >&2
                    return 1
                fi
            fi
        elif [[ -d "$STAGING/previous" ]]; then
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
        # The gamecenter fixtures exist to sample three specific states, so each check pins the
        # thing that makes the artifact worth keeping. A finished game must carry the three
        # sections a fan reads after the horn; a live one must actually be live. If a check
        # fails, the capture is not the sample it claims to be and must not replace the corpus.
        gc-final-landing.json) shape='(.gameState | . == "OFF" or . == "FINAL") and (.summary.scoring | type == "array") and ((.summary.scoring | length) > 0) and (.summary.threeStars | type == "array") and ((.summary.threeStars | length) == 3)' ;;
        gc-final-boxscore.json) shape='(.gameState | . == "OFF" or . == "FINAL") and ((.playerByGameStats.awayTeam.goalies | length) > 0) and ((.playerByGameStats.homeTeam.goalies | length) > 0) and (.periodDescriptor.number >= 1)' ;;
        # linescore is an object with byPeriod and totals, not the array a reader might expect;
        # pinning the shape keeps a future recapture from silently switching the container.
        gc-final-right-rail.json) shape='(.linescore.totals | type == "object") and ((.teamGameStats | length) > 0) and (([.teamGameStats[] | select(.category == "powerPlay")] | length) == 1)' ;;
        gc-live-landing.json) shape='(.gameState | . == "LIVE" or . == "CRIT") and (.clock | type == "object")' ;;
        # Deliberately loose: which player sections exist mid-game is exactly what the first
        # live capture is meant to discover. These three checks are the parts already certain -
        # the game is live and both teams are named - and tighten once the sample says more.
        gc-live-boxscore.json) shape='(.gameState | . == "LIVE" or . == "CRIT") and (.awayTeam.abbrev | type == "string") and (.homeTeam.abbrev | type == "string")' ;;
        gc-live-right-rail.json) shape='.gameInfo | type == "object"' ;;
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

pick_game() {
    local url=$1 filter=$2 what=$3 body=$STAGING/slate.json id
    if ! curl --compressed --silent --show-error --connect-timeout 20 --max-time 60 \
        --output "$body" "$url"; then
        printf 'ERROR: could not read %s\n' "$url" >&2
        return 1
    fi
    id=$(jq -r "(.games // []) | $filter | .id" "$body" 2> /dev/null | head -1)
    if [[ ! $id =~ ^[0-9]{10}$ ]]; then
        printf 'no %s game in %s\n' "$what" "$url" >&2
        return 1
    fi
    printf '%s' "$id"
}

if [[ $MODE == base ]]; then
    capture_body score-gameday.json "$API/score/$GAME_DAY"
    capture_body score-emptyday.json "$API/score/$EMPTY_DAY"
    capture_body score-futuredate.json "$API/score/$FUTURE_DAY"
    # No LIVE sample in this corpus: gamecenter mode captures it, and only during a game.
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
    exit 0
fi

if [[ -z $FINAL_ID ]]; then
    if ! FINAL_ID=$(pick_game "$API/score/$GAME_DAY" \
        'map(select(.gameState == "OFF" or .gameState == "FINAL")) | .[0]' finished); then
        printf 'ERROR: no finished game on %s to sample\n' "$GAME_DAY" >&2
        exit 1
    fi
fi
if [[ -z $LIVE_ID ]]; then
    if ! LIVE_ID=$(pick_game "$API/score/now" \
        'map(select(.gameState == "LIVE" or .gameState == "CRIT")) | .[0]' in-progress); then
        printf 'ERROR: no game is in progress, so there is no LIVE sample to take.\n' >&2
        printf 'That sample is the reason this mode exists - a corpus without LIVE would let a\n' >&2
        printf 'parser test pass against states it never saw. Run this during a game.\n' >&2
        exit 1
    fi
fi

printf 'Sampling final game %s and live game %s\n' "$FINAL_ID" "$LIVE_ID"
capture_body gc-final-landing.json "$API/gamecenter/$FINAL_ID/landing"
capture_body gc-final-boxscore.json "$API/gamecenter/$FINAL_ID/boxscore"
capture_body gc-final-right-rail.json "$API/gamecenter/$FINAL_ID/right-rail"
capture_body gc-live-landing.json "$API/gamecenter/$LIVE_ID/landing"
capture_body gc-live-boxscore.json "$API/gamecenter/$LIVE_ID/boxscore"
capture_body gc-live-right-rail.json "$API/gamecenter/$LIVE_ID/right-rail"

# The target also holds the base corpus, so snapshot it whole before installing anything.
if [[ -e "$TARGET" ]]; then
    cp -a -- "$TARGET" "$STAGING/previous"
fi
mkdir -p -- "$TARGET"
for name in gc-final-landing.json gc-final-boxscore.json gc-final-right-rail.json \
    gc-live-landing.json gc-live-boxscore.json gc-live-right-rail.json; do
    cp -p -- "$CAPTURE/$name" "$TARGET/$name"
done
# Replace only the gamecenter lines, leaving the base entries and the header intact.
if [[ -e "$TARGET/SIZES.txt" ]]; then
    grep -v '^gc-' -- "$TARGET/SIZES.txt" > "$STAGING/SIZES.txt" || true
fi
grep '^gc-' -- "$CAPTURE/SIZES.txt" >> "$STAGING/SIZES.txt"
mv -- "$STAGING/SIZES.txt" "$TARGET/SIZES.txt"
complete=1

states=$(jq -r '"\(.awayTeam.abbrev)@\(.homeTeam.abbrev) \(.gameState)"' "$TARGET/gc-live-boxscore.json")
printf 'Captured 6 gamecenter fixtures in %s\n' "$TARGET"
printf '  final %s: %s\n' "$FINAL_ID" "$(jq -r '"\(.awayTeam.abbrev)@\(.homeTeam.abbrev) \(.gameState)"' "$TARGET/gc-final-boxscore.json")"
printf '  live  %s: %s\n' "$LIVE_ID" "$states"
