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
#   nfl-base    the same idea for ESPN's NFL feed: scoreboards, teams, standings,
#               two teams' roster/schedule/statistics, one finished game, and one
#               athlete per position group. Runs any time.
#   nfl-week    the week scoreboards for the current week, the next week and week 1, so the
#               census can check that a week spans several game days. Runs any time.
#   nfl-live    a game that is actually being played. Refuses unless the scoreboard
#               shows one, and rolls back if the summary it gets has neither drives
#               nor a situation - which is what a pre-game payload looks like, and
#               the exact mistake the NHL game page made.
set -euo pipefail

# These are the dates the committed fixtures were captured from.
GAME_DAY=2026-10-24
EMPTY_DAY=2026-07-15
FUTURE_DAY=2026-10-26
API=https://api-web.nhle.com/v1

usage() {
    printf 'Usage:\n  %s base\n  %s gamecenter [--final GAME_ID] [--live GAME_ID]\n  %s nfl-base\n  %s nfl-week\n  %s nfl-live [--event EVENT_ID]\n' "$0" "$0" "$0" "$0" "$0" >&2
    printf '\nWithout --final/--live the ids are chosen from the slate: the final one from\n' >&2
    printf '%s, the live one from score/now.\n' "$GAME_DAY" >&2
    printf '\nWithout --event the NFL live game is the first in-progress event on the\n' >&2
    printf 'scoreboard. nfl-live refuses to run when no game is being played.\n' >&2
}

MODE=${1:-}
case $MODE in
    base | gamecenter | nfl-base | nfl-week | nfl-live) shift ;;
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
        --event)
            if [[ $# -lt 2 || -z ${2:-} ]]; then
                printf 'ERROR: --event needs an ESPN event id\n' >&2
                usage
                exit 2
            fi
            LIVE_ID=$2
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
        if [[ $MODE == gamecenter || $MODE == nfl-base || $MODE == nfl-week || $MODE == nfl-live ]]; then
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
        # NFL fixtures, same contract: each pins the thing that makes the artifact worth
        # keeping. A Sunday scoreboard with three games is not the Sunday sample. A live
        # summary with no drives and no situation is a pre-game skeleton that happens to
        # have been fetched during a game - the rollback exists so it cannot replace a
        # corpus that had real live data in it.
        nfl-summary-live.json) shape='((.drives.previous // [] | length) > 0) or (.situation != null)' ;;
        # A week spans several game days. A one-day scoreboard saved under a week name must fail here.
        nfl-scoreboard-week-*.json) shape='(.events | type == "array") and ((.events | length) > 0) and (([.events[].date[0:10]] | unique | length) >= 2)' ;;
        nfl-scoreboard-sunday-*.json) shape='(.events | type == "array") and ((.events | length) >= 8)' ;;
        nfl-scoreboard-emptyday.json) shape='(.events | type == "array") and ((.events | length) == 0)' ;;
        nfl-scoreboard-*.json) shape='(.events | type == "array") and ((.leagues | length) > 0)' ;;
        nfl-teams.json) shape='([.sports[0].leagues[0].teams[]] | length) == 32' ;;
        nfl-standings.json) shape='(.children | length) == 2' ;;
        nfl-roster-*.json) shape='(.athletes | type == "array") and ((.athletes | length) > 0)' ;;
        nfl-schedule-*.json) shape='(.events | type == "array") and ((.events | length) > 0)' ;;
        nfl-stats-*.json) shape='.team | type == "object"' ;;
        nfl-summary-final.json) shape='(.boxscore.teams | length) == 2' ;;
        nfl-athlete-*.json) shape='(.athlete.id != null) and ((.athlete.headshot.href | type) == "string")' ;;
        # A player with no games returns only `filters`: no `seasonTypes`, no columns. That is the
        # shape the player page must survive, so it is captured rather than treated as an error.
        nfl-gamelog-empty.json) shape='(.filters | type == "array") and (.seasonTypes == null)' ;;
        nfl-gamelog-*.json) shape='(.seasonTypes | type == "array") and ((.seasonTypes | length) > 0)' ;;
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

if [[ $MODE == nfl-week ]]; then
    NFL_SITE=${NFL_SITE_BASE:-https://site.api.espn.com/apis}
    NFL_DATA="$NFL_SITE/site/v2/sports/football/nfl"
    ROOT="$REPO_ROOT/data/src/test/resources/fx-nfl"
    mkdir -p -- "$ROOT"
    # Glob order is lexical, and labels are YYYY-MM-DD, so the last match is the newest corpus.
    NFL_LABEL=${NFL_CAPTURE_LABEL:-}
    if [[ -z $NFL_LABEL ]]; then
        for dir in "$ROOT"/*/; do
            name=$(basename -- "$dir")
            if [[ $name =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then NFL_LABEL=$name; fi
        done
    fi
    NFL_LABEL=${NFL_LABEL:-$(date -u +%Y-%m-%d)}
    if [[ ! $NFL_LABEL =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
        printf 'ERROR: NFL capture label must be YYYY-MM-DD, got %s\n' "$NFL_LABEL" >&2
        exit 1
    fi
    TARGET="$ROOT/$NFL_LABEL"
    rm -rf -- "$STAGING"
    STAGING=$(mktemp -d "$ROOT/.capture-$NFL_LABEL.XXXXXX")
    CAPTURE="$STAGING/capture"
    mkdir -- "$CAPTURE"
    {
        printf 'NFL capture label: %s\n' "$NFL_LABEL"
        printf 'Source: %s - ESPN public site API, unofficial, no key. Authorized 2026-11-18; docs/nfl-sources.md records what it carries.\n' "$NFL_DATA"
        printf 'transfer_bytes: curl size_download (body before decompression). stored_bytes: bytes on disk after decompression.\n\n'
    } > "$CAPTURE/SIZES.txt"

    # Weeks are named by the ESPN week number, so a later capture of the same week replaces the file.
    WEEKS=(11 12 1)
    WEEK_FILES=()
    for week in "${WEEKS[@]}"; do
        name=nfl-scoreboard-week-$week.json
        WEEK_FILES+=("$name")
        capture_body "$name" "$NFL_DATA/scoreboard?seasontype=2&week=$week"
    done

    if [[ -e $TARGET ]]; then
        cp -a -- "$TARGET" "$STAGING/previous"
    fi
    mkdir -p -- "$TARGET"
    for name in "${WEEK_FILES[@]}"; do
        cp -p -- "$CAPTURE/$name" "$TARGET/$name"
    done
    # Replace only the week lines, leaving the base corpus entries and the header intact.
    if [[ -e $TARGET/SIZES.txt ]]; then
        grep -vE '^nfl-scoreboard-week-' -- "$TARGET/SIZES.txt" > "$STAGING/SIZES.txt" || true
    fi
    grep -E '^nfl-scoreboard-week-' -- "$CAPTURE/SIZES.txt" >> "$STAGING/SIZES.txt"
    mv -- "$STAGING/SIZES.txt" "$TARGET/SIZES.txt"
    complete=1
    printf 'Captured %s week scoreboards in %s\n' "${#WEEK_FILES[@]}" "$TARGET"
    exit 0
fi

if [[ $MODE == nfl-base || $MODE == nfl-live ]]; then
    NFL_SITE=${NFL_SITE_BASE:-https://site.api.espn.com/apis}
    NFL_DATA="$NFL_SITE/site/v2/sports/football/nfl"
    NFL_COMMON="$NFL_SITE/common/v3/sports/football/nfl"
    ROOT="$REPO_ROOT/data/src/test/resources/fx-nfl"
    mkdir -p -- "$ROOT"
    # One corpus per label. Without an explicit label the newest corpus is continued, so a live
    # capture taken days after the base capture lands beside it instead of starting a corpus that
    # holds only the live half.
    # `grep` exits 1 when the corpus is new and there is nothing to match, which under `pipefail`
    # and `set -e` would end the run before it captured anything.
    NFL_LABEL=${NFL_CAPTURE_LABEL:-$(ls -1 -- "$ROOT" 2> /dev/null | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' | sort | tail -1 || true)}
    NFL_LABEL=${NFL_LABEL:-$(date -u +%Y-%m-%d)}
    if [[ ! $NFL_LABEL =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
        printf 'ERROR: NFL capture label must be YYYY-MM-DD, got %s\n' "$NFL_LABEL" >&2
        exit 1
    fi
    TARGET="$ROOT/$NFL_LABEL"
    # The staging directory made for the NHL corpus is unused here. Leaving it behind litters the
    # fixture tree with an empty .capture-* directory after every NFL run.
    rm -rf -- "$STAGING"
    STAGING=$(mktemp -d "$ROOT/.capture-$NFL_LABEL.XXXXXX")
    CAPTURE="$STAGING/capture"
    mkdir -- "$CAPTURE"
    {
        printf 'NFL capture label: %s\n' "$NFL_LABEL"
        printf 'Source: %s - ESPN public site API, unofficial, no key. Authorized 2026-11-18; docs/nfl-sources.md records what it carries.\n' "$NFL_DATA"
        printf 'transfer_bytes: curl size_download (body before decompression). stored_bytes: bytes on disk after decompression.\n\n'
    } > "$CAPTURE/SIZES.txt"

    # A day's scoreboard, cached so the search for sample days costs one request per day, once.
    nfl_day() {
        local day=$1 file="$STAGING/day-${1//-/}.json"
        if [[ ! -e $file ]]; then
            curl --compressed --silent --show-error --connect-timeout 20 --max-time 60 \
                --output "$file" "$NFL_DATA/scoreboard?dates=${day//-/}" || return 1
        fi
        jq -r '(.events // []) | length' "$file" 2> /dev/null
    }

    # Two Sundays with a full slate, most recent first. Derived rather than pinned: a pinned
    # Sunday silently becomes an off-season day a few months later, and the capture would keep
    # succeeding against a stale sample.
    find_sundays() {
        local found=() back day count
        for ((back = 0; back < 60 && ${#found[@]} < 2; back++)); do
            day=$(date -u -d "$(date -u +%Y-%m-%d) - $back days" +%Y-%m-%d)
            [[ $(date -u -d "$day" +%u) == 7 ]] || continue
            count=$(nfl_day "$day") || return 1
            [[ $count -ge 8 ]] && found+=("$day")
        done
        [[ ${#found[@]} == 2 ]] || return 1
        printf '%s %s' "${found[0]}" "${found[1]}"
    }

    # A day with no games at all: an empty slate is a distinct state, not a failure.
    find_empty_day() {
        local back day count
        for ((back = 1; back <= 21; back++)); do
            day=$(date -u -d "$(date -u +%Y-%m-%d) - $back days" +%Y-%m-%d)
            count=$(nfl_day "$day") || return 1
            [[ $count == 0 ]] && { printf '%s' "$day"; return 0; }
        done
        return 1
    }

    # The athlete set is chosen by position group, because a quarterback's game log and a
    # kicker's are not the same table and a parser tested on one is untested on the other.
    nfl_athletes() {
        jq -r --arg want "$2" \
            '[.athletes[]?.items[]?] | map(select(.position.abbreviation as $p | ($want | split(",")) | index($p))) | .[0:4][] | .id // empty' \
            "$1"
    }

    # The first name at a position is often a player with no games played, whose game log is an
    # empty payload. Probe before capturing so the corpus holds a real table for each position
    # group, and keep one empty sample on purpose.
    nfl_athlete_with_games() {
        local roster=$1 want=$2 candidate
        for candidate in $(nfl_athletes "$roster" "$want"); do
            if curl -q --compressed --silent --show-error --connect-timeout 20 --max-time 60 \
                "$NFL_COMMON/athletes/$candidate/gamelog" 2> /dev/null |
                jq -e '(.seasonTypes | length) > 0' > /dev/null 2>&1; then
                printf '%s' "$candidate"
                return 0
            fi
        done
        return 1
    }

    # One player with no games at all, for the page that has to survive an empty log.
    nfl_athlete_without_games() {
        local roster=$1 candidate
        # Defense first, then everyone: the first twelve names on a roster are the offensive
        # starters, and a starter has a game log. The empty-log case lives further down the depth chart.
        for candidate in $(jq -r '[ (.athletes[]? | select(.position == "defense") | .items[]?.id), (.athletes[]? | .items[]?.id) ] | .[0:24][]' "$roster"); do
            if curl -q --compressed --silent --show-error --connect-timeout 20 --max-time 60 \
                "$NFL_COMMON/athletes/$candidate/gamelog" 2> /dev/null |
                jq -e '(.seasonTypes | length) > 0' > /dev/null 2>&1; then
                continue
            fi
            printf '%s' "$candidate"
            return 0
        done
        return 1
    }

    if [[ $MODE == nfl-base ]]; then
        if ! SUNDAYS=$(find_sundays) || [[ -z $SUNDAYS ]]; then
            printf 'ERROR: found fewer than two Sundays with eight or more games\n' >&2
            exit 1
        fi
        read -r SUNDAY_A SUNDAY_B <<< "$SUNDAYS"
        if ! EMPTY_DAY_NFL=$(find_empty_day); then
            printf 'ERROR: found no day in the last three weeks with zero games\n' >&2
            exit 1
        fi
        printf 'Sampling Sundays %s and %s; empty day %s\n' "$SUNDAY_A" "$SUNDAY_B" "$EMPTY_DAY_NFL"
        {
            printf 'Sample days: Sunday A %s; Sunday B %s; empty day %s\n' "$SUNDAY_A" "$SUNDAY_B" "$EMPTY_DAY_NFL"
        } >> "$CAPTURE/SIZES.txt"

        capture_body nfl-scoreboard-today.json "$NFL_DATA/scoreboard"
        capture_body nfl-scoreboard-sunday-a.json "$NFL_DATA/scoreboard?dates=${SUNDAY_A//-/}"
        capture_body nfl-scoreboard-sunday-b.json "$NFL_DATA/scoreboard?dates=${SUNDAY_B//-/}"
        capture_body nfl-scoreboard-emptyday.json "$NFL_DATA/scoreboard?dates=${EMPTY_DAY_NFL//-/}"
        capture_body nfl-teams.json "$NFL_DATA/teams"
        capture_body nfl-standings.json "$NFL_SITE/v2/sports/football/nfl/standings"

        FINAL_EVENT=$(jq -r '[.events[] | select(.status.type.state == "post")][0].id // empty' \
            "$CAPTURE/nfl-scoreboard-sunday-a.json")
        if [[ -z $FINAL_EVENT ]]; then
            printf 'ERROR: no finished game on %s to sample\n' "$SUNDAY_A" >&2
            exit 1
        fi
        abbr_of() {
            jq -r --arg id "$FINAL_EVENT" --arg side "$1" \
                '.events[] | select(.id == $id) | .competitions[0].competitors[] | select(.homeAway == $side) | .team.abbreviation' \
                "$CAPTURE/nfl-scoreboard-sunday-a.json"
        }
        AWAY=$(abbr_of away)
        HOME=$(abbr_of home)
        if [[ -z $AWAY || -z $HOME ]]; then
            printf 'ERROR: could not read both teams from event %s\n' "$FINAL_EVENT" >&2
            exit 1
        fi
        for side in away home; do
            abbr=$([[ $side == away ]] && printf '%s' "$AWAY" || printf '%s' "$HOME")
            capture_body "nfl-roster-$side.json" "$NFL_DATA/teams/$abbr/roster"
            capture_body "nfl-schedule-$side.json" "$NFL_DATA/teams/$abbr/schedule"
            capture_body "nfl-stats-$side.json" "$NFL_DATA/teams/$abbr/statistics"
        done
        capture_body nfl-summary-final.json "$NFL_DATA/summary?event=$FINAL_EVENT"

        for group in QB WR DE,OLB PK; do
            token=${group%%,*}
            if [[ $token == PK ]]; then token=K; fi
            if ! athlete=$(nfl_athlete_with_games "$CAPTURE/nfl-roster-home.json" "$group"); then
                printf 'ERROR: no %s on the %s roster has a game log; the sample would be incomplete\n' "$group" "$HOME" >&2
                exit 1
            fi
            capture_body "nfl-athlete-$token.json" "$NFL_COMMON/athletes/$athlete"
            capture_body "nfl-gamelog-$token.json" "$NFL_COMMON/athletes/$athlete/gamelog"
        done
        if empty_athlete=$(nfl_athlete_without_games "$CAPTURE/nfl-roster-home.json"); then
            capture_body nfl-gamelog-empty.json "$NFL_COMMON/athletes/$empty_athlete/gamelog"
        else
            printf 'WARNING: every probed player has a game log, so no empty-log sample was taken\n' >&2
        fi

        if [[ -e $TARGET ]]; then
            mv -- "$TARGET" "$STAGING/previous"
        fi
        mv -- "$CAPTURE" "$TARGET"
        complete=1
        printf 'Captured %s NFL fixtures in %s\n' "$(find "$TARGET" -name '*.json' | wc -l)" "$TARGET"
        printf '  final event %s: %s at %s\n' "$FINAL_EVENT" "$AWAY" "$HOME"
        exit 0
    fi

    # nfl-live: the whole point is a game in progress. Without one this mode refuses, because a
    # corpus that never sampled live would let every parser test pass against a pre-game skeleton.
    capture_body nfl-scoreboard-live.json "$NFL_DATA/scoreboard"
    if [[ -z $LIVE_ID ]]; then
        LIVE_ID=$(jq -r '[.events[] | select(.status.type.state == "in")][0].id // empty' \
            "$CAPTURE/nfl-scoreboard-live.json")
    fi
    if [[ -z $LIVE_ID ]]; then
        printf 'ERROR: no NFL game is in progress, so there is no live sample to take.\n' >&2
        printf 'That sample is the reason this mode exists. Run it during a game.\n' >&2
        exit 1
    fi
    state=$(jq -r --arg id "$LIVE_ID" '.events[] | select(.id == $id) | .status.type.state' \
        "$CAPTURE/nfl-scoreboard-live.json")
    if [[ $state != in ]]; then
        printf 'ERROR: event %s is not in progress (state %s), so it is not a live sample.\n' \
            "$LIVE_ID" "${state:-unknown}" >&2
        exit 1
    fi
    printf 'Sampling live event %s\n' "$LIVE_ID"
    capture_body nfl-summary-live.json "$NFL_DATA/summary?event=$LIVE_ID"

    if [[ -e $TARGET ]]; then
        cp -a -- "$TARGET" "$STAGING/previous"
    fi
    mkdir -p -- "$TARGET"
    for name in nfl-scoreboard-live.json nfl-summary-live.json; do
        cp -p -- "$CAPTURE/$name" "$TARGET/$name"
    done
    # Replace only the live lines, leaving the base corpus entries and the header intact.
    if [[ -e $TARGET/SIZES.txt ]]; then
        grep -vE '^nfl-(scoreboard|summary)-live\.json' -- "$TARGET/SIZES.txt" > "$STAGING/SIZES.txt" || true
    fi
    grep -E '^nfl-(scoreboard|summary)-live\.json' -- "$CAPTURE/SIZES.txt" >> "$STAGING/SIZES.txt"
    mv -- "$STAGING/SIZES.txt" "$TARGET/SIZES.txt"
    complete=1
    printf 'Captured the live NFL sample in %s\n' "$TARGET"
    printf '  %s: drives=%s situation=%s\n' "$LIVE_ID" \
        "$(jq -r '(.drives.previous // []) | length' "$TARGET/nfl-summary-live.json")" \
        "$(jq -r 'if .situation then "present" else "absent" end' "$TARGET/nfl-summary-live.json")"
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
