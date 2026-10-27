You are an independent reviewer. Read-only. Do not modify files, commit or push.

REPOSITORY: /var/lib/hermes/workspace/roux-sports/.worktrees/s3-4-shell (Kotlin/JVM Android app, Compose)
FROZEN DIFF: .review-s34/frozen1.diff - review ONLY this. sha256 6228a320b794aa82c910969c10e4a4f0960180b597438d6ad4a561b5989882de
Commit 9408957, base upstream/main bf628b6. PR #30 (patrick/roux-sports).
STAGE S3.4: navigation shell with three bottom destinations.

WHAT IS THERE. NavigationBar with Scores/Standings/Teams; Scores is the start destination; each shows a
placeholder naming itself and saying the feature is not built yet. The shell's Scaffold owns the insets and
MainActivity stopped padding its content (S0.8's forward note: two padders double the gap or clip the bar's
background).

TWO DECISIONS TO SCRUTINISE.
1. R5 requires primary controls to sit 16 dp inside the screen edges. A stock NavigationBar puts its outer
   items flush. Rather than padding the bar (which insets its background and leaves a strip of page
   showing), the background stays full-bleed and Spacer(16.dp) on each end pushes the items inward. Removing
   the spacers fails the reach audit at all three configurations.
2. Bar items are tagged primaryControl(..., frequent = true), so R2's tighter ceiling applies to navigation.

Also: MainActivitySmokeTest used to assert the app name (the blank title screen); it now asserts the bar
launches with Scores selected. The render-smoke screenshot baseline was re-recorded, and shell-light /
shell-dark were added.

Eight shell tests: destinations exist and Scores starts selected; tapping Standings shows its placeholder;
the audit passes at w411dp, at w360dp-h640dp and at font scale 2.0; every item is at least 48 dp in both
directions; both screenshots.

MUTATIONS: start destination changed -> the selection test and both screenshots fail; edge spacers removed
-> the three audit tests and both screenshots fail; primary tags removed -> the audit tests and the 48 dp
test fail.

QUESTIONS.
1. Is the spacer approach the right way to satisfy R5 without breaking the bar's background, or does it
   distort the item widths / hit areas in a way that matters?
2. Anything wrong with the navigation state handling (popUpTo/saveState/restoreState/launchSingleTop) for
   three peer destinations?
3. Do the tests prove what their names claim, and is any of them passing for the wrong reason?
4. Would you merge as it stands?

OUTPUT FORMAT, exact: first line VERDICT: APPROVE or VERDICT: REQUEST_CHANGES, then findings as
"- **P0|P1|P2** - path:line." with a concrete failure scenario, or "No findings." plus at most three
sentences of residual risk. End with one sentence on whether you verified the frozen hash.
