#!/usr/bin/env bash
# Parse-check the shell that the release workflow actually hands to the runner.
#
# A step's `run: |` block is a YAML scalar; the runner writes it to a file and runs it. Scripts are
# embedded in those blocks with heredocs, and the publishers live inside them. Editing that text as
# lines is how a continuation line went missing once: the workflow still looked fine, actionlint was
# happy, and every publish run died with "syntax error near unexpected token" only after a
# thirty-minute build. `bash -n` on the produced script catches that whole class of damage instantly,
# including the inner script that a wrapper writes and runs.
#
# Usage: scripts/check-workflow-scripts.sh [workflow.yml ...]
# Exit:  0 when every produced script parses, 1 otherwise.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/.." && pwd)"

if [ "$#" -gt 0 ]; then
  workflows=("$@")
else
  workflows=("$repo"/.forgejo/workflows/*.yml)
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
rc=0

extract_block() {  # file line-number -> the run block, as the runner writes it
  python3 - "$1" "$2" <<'PY'
import sys
path, index = sys.argv[1], int(sys.argv[2])
lines = open(path).read().split('\n')
k = index                      # 0-based line after the "run: |" header
while k < len(lines) and not lines[k].strip():
    k += 1
if k >= len(lines):
    sys.exit(0)
ind = len(lines[k]) - len(lines[k].lstrip())
out = []
for l in lines[k:]:
    if l.strip() and (len(l) - len(l.lstrip())) < ind:
        break
    out.append(l[ind:] if l.strip() else '')
sys.stdout.write('\n'.join(out) + '\n')
PY
}

extract_heredoc() {  # file tag -> the body of that heredoc
  python3 - "$1" "$2" <<'PY'
import sys
path, tag = sys.argv[1], sys.argv[2]
text = open(path).read()
start = None
for quoted in (f"<<'{tag}'", f'<<"{tag}"', f'<<{tag}'):
    if quoted in text:
        start = text.index(quoted)
        break
if start is None:
    sys.exit(1)
start = text.index('\n', start) + 1
end = text.find('\n' + tag + '\n', start)
if end < 0:
    sys.exit(1)
sys.stdout.write(text[start:end + 1])
PY
}

for wf in "${workflows[@]}"; do
  [ -f "$wf" ] || { echo "missing workflow: $wf" >&2; rc=1; continue; }
  name="$(basename "$wf")"

  while read -r line_no; do
    [ -n "$line_no" ] || continue
    step="$tmp/$name.$line_no.sh"
    extract_block "$wf" "$line_no" > "$step"
    [ -s "$step" ] || continue

    if ! err="$(bash -n "$step" 2>&1)"; then
      echo "FAIL $name:$line_no the run block does not parse" >&2
      printf '%s\n' "$err" | sed 's/^/     /' >&2
      rc=1
      continue
    fi

    # A wedged heredoc can swallow the rest of the file and still parse, so every heredoc opened in a
    # block must also be closed by its own tag at the start of a line.
    tags="$(sed -n -E "s/.*<<-?'([A-Za-z_][A-Za-z0-9_]*)'.*/\1/p" "$step" | sort -u)"
    while read -r tag; do
      [ -n "$tag" ] || continue
      grep -qx "$tag" "$step" || { echo "FAIL $name:$line_no heredoc '$tag' is never terminated" >&2; rc=1; }
    done <<<"$tags"

    # Only the heredocs that really hold shell are parsed as shell. Most of them hold JavaScript, which
    # bash would reject for reasons that have nothing to do with this check; the wrapper that writes a
    # script and runs it is the one that must survive `bash -n`.
    while read -r tag; do
      [ -n "$tag" ] || continue
      grep -qE "cat > .*SCRIPT.*<<-?'$tag'" "$step" || continue
      inner="$tmp/$name.$line_no.$tag.sh"
      extract_heredoc "$step" "$tag" > "$inner" 2>/dev/null || continue
      [ -s "$inner" ] || continue
      if ! err="$(bash -n "$inner" 2>&1)"; then
        echo "FAIL $name:$line_no the embedded script '$tag' does not parse" >&2
        printf '%s\n' "$err" | sed 's/^/     /' >&2
        rc=1
      fi
    done <<<"$tags"
  # A step may write `run: |` on its own line or inline after the list dash; both are blocks.
  done < <(grep -nE '^ *(- )?run: \|' "$wf" | cut -d: -f1)
done

[ "$rc" -eq 0 ] && echo "workflow scripts parse"
exit "$rc"
