You are an independent reviewer. Read-only. Do not modify files, commit or push.

REPOSITORY: /var/lib/hermes/workspace/roux-sports/.worktrees/s3-2-reach (Kotlin/JVM Android app, Compose)
FROZEN DIFF: .review-s32/frozen4.diff - review ONLY this. sha256 16a4ad771474b4b75665981d32da350b1d17f85e48365451e181f24ea15d04a9. Verify before starting.
Commit 1cbbc52, base upstream/main 0ee7585. PR #28.
ROUND 4. Round 3 raised three P2s; all three fixed, and all three of my first fixtures for them were
shown by mutation NOT to fail, then rebuilt.

1. Gap pass now filters to visible targets (a zero-area target reported a 0 dp gap against the edge).
2. Visible rect now comes from boundsInRoot, which is already clipped by every ancestor - not
   position+size clipped to the window, which let a 30 dp viewport expose a 56 dp control as 56 dp.
3. Modifier.reachTarget(tag) declares a non-primary target, for draggable/pointerInput controls that
   expose neither OnClick nor SetProgress; it is measured for R4 but not subjected to R1/R2/R5.

FIXTURE HONESTY, since it is the recurring failure mode here: the "clipped ancestor" case originally
used Modifier.size inside a 30 dp parent, which CONSTRAINS rather than clips, so raw and visible agreed
and the mutation was a no-op (now requiredSize). The zero-area case sat 767 dp from its neighbour so no
gap was possible (now a pure rectangle test). The collection condition had a redundant clause making its
own mutation unobservable (simplified).

MUTATIONS: gap pass without the filter; visible from position+size; declared targets not collected.
Each lands on its own test. Gate green, 339 tests.

QUESTIONS.
1. Are the rule-to-rectangle assignments now correct for every rule?
2. Is Modifier.reachTarget's escape hatch adequate for the gesture targets Compose cannot expose, and is
   it documented well enough that a screen author would actually use it?
3. Does declaring a target but forgetting to tag it as primary create a silent R1/R2 hole in practice -
   and is the minPrimary guard enough to catch that?
4. Do any remaining tests pass for the wrong reason?
5. Would you merge as it stands?

OUTPUT FORMAT, exact: first line VERDICT: APPROVE or VERDICT: REQUEST_CHANGES, then findings as
"- **P0|P1|P2** - path:line." with a concrete failure scenario, or "No findings." plus at most three
sentences of residual risk. End with one sentence on whether you verified the frozen hash.
