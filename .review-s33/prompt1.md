You are an independent reviewer. Read-only. Do not modify files, commit or push.

REPOSITORY: /var/lib/hermes/workspace/roux-sports/.worktrees/s3-3-hidden-actions (Kotlin/JVM Android app, Compose)
FROZEN DIFF: .review-s33/frozen1.diff - review ONLY this. sha256 32b6550621ebf9aae571042f4a65e19113acef5bdcaa2bcecda8b7da2b5ca598
Commit e88875f, base upstream/main bf628b6. New branch, no PR number yet.
STAGE S3.3 of the approved plan: "No-hidden-actions guard (R6)". Test-only.

WHAT R6 SAYS: no top-app-bar actions, no overflow or DropdownMenu in any top bar, no top FAB, and
confirmations use ModalBottomSheet with buttons at the bottom instead of a dialog. A source-scan test
enforces it.

THE IMPLEMENTATION. findHiddenActions(source) blanks comments and string literals while preserving line
counts, then flags (a) any top bar call whose OWN balanced argument list contains `actions =`, and
(b) DropdownMenu( and AlertDialog( anywhere. A test walks app/src/main and requires zero violations.

MUTATIONS, each landing on its own test:
- actions check disabled -> the two top-bar tests fail;
- DropdownMenu ban removed -> the overflow test fails;
- blanking removed -> the prose test fails;
- arguments read to end-of-file instead of the matching paren -> the nested-argument test fails;
- a compilable DropdownMenu planted in app/src/main -> the real-tree test fails and names the file.

Two fixtures were rebuilt after their mutations passed: the unbalanced-paren decoy was placed before the
bar (so an unbounded read found nothing), and the planted violation used a call that did not compile.

Gate green, 353 tests, 9 new. Note the plan assigned this step to an OpenCode Zen free model; I used the
same Codex reviewer as the rest of the stage for consistency. Say if that is disqualifying.

QUESTIONS.
1. Does the scan miss a way a real app could hide an action (a bar built through a wrapper composable, an
   actions slot passed by name from elsewhere, a menu opened by a raw pointerInput)?
2. Are comment/string blanking and paren matching correct enough that a false negative is unlikely and a
   false positive impossible?
3. Is the real-tree test load-bearing, or would it pass in a tree where the scan is broken?
4. Would you merge as it stands?

OUTPUT FORMAT, exact: first line VERDICT: APPROVE or VERDICT: REQUEST_CHANGES, then findings as
"- **P0|P1|P2** - path:line." with a concrete failure scenario, or "No findings." plus at most three
sentences of residual risk. End with one sentence on whether you verified the frozen hash.
