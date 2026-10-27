You are an independent reviewer. Read-only. Do not modify files, commit or push.

REPOSITORY: /var/lib/hermes/workspace/roux-sports/.worktrees/s3-2-reach (Kotlin/JVM Android app, Compose)
FROZEN DIFF: .review-s32/frozen5.diff - review ONLY this. sha256 643a171595251a1ac99c1c945d7ab6e7f451f2a0ef477c915c3261c9edd4d183
Commit 8f343f2, base upstream/main 0ee7585. PR #28.
ROUND 5. Round 4 raised one P2: a count guard catches losing every primary tag, not losing one.

THE FIX. The audit no longer counts primaries. It takes the screen's declaration and compares by
identity:

    assertPrimaryControlsReachable("scores.dateStrip", "scores.toggle")

An empty invocation states that the screen has no primary controls, which is what the untagged,
slider and reachTarget-only fixtures now say. A count is not evidence: the same-size-wrong-name case
passed under a size comparison, so a second test exists specifically for it. All 25 reach cases now
state their declaration instead of relying on a bare call.

MUTATIONS: declaration comparison disabled -> three tests fail (tag-drop guard, partial omission,
empty audit); comparison by size only -> the wrong-name test fails.

The underlying policy is unchanged from round 3: raw rects for R1/R2/edge insets, ancestor-clipped
visible rects for size and gap, zero-area targets skipped, reachTarget for gesture controls that expose
no semantics action.

QUESTIONS.
1. Is declaration-by-identity now sufficient, or is there still a way for a real primary action to
   escape R1/R2 while the audit passes?
2. Does requiring an explicit declaration create a burden that will cause screens to declare a convenient
   set rather than a true one - and is there a cheaper guard?
3. Is anything in the audit over-fitted to these tests?
4. Would you merge as it stands?

OUTPUT FORMAT, exact: first line VERDICT: APPROVE or VERDICT: REQUEST_CHANGES, then findings as
"- **P0|P1|P2** - path:line." with a concrete failure scenario, or "No findings." plus at most three
sentences of residual risk. End with one sentence on whether you verified the frozen hash.
