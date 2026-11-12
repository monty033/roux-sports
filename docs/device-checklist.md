# Device checklist

What to check on the physical Pixel 9 (Android 16) at each stage. Automated tests cannot
see a thumb, a glare, or a font scale someone actually uses, so these steps are done by a
person and are not optional.

Get the APK onto the device as described in [device-testing.md](device-testing.md). Unless a
stage says otherwise, install the debug build.

Record what you saw, not just whether it passed. "Live score lagged the TV by about 20
seconds" is useful later; "scores fine" is not.

---

## Stage 0 — Toolchain and skeleton

This stage proves the pipeline, not the product. The app is a blank screen showing its own
name, and that is the correct outcome.

- [ ] Install the APK on the device.
- [ ] The app appears in the launcher with its icon and the name **Roux Sports**. The icon is
      a deliberate placeholder at this stage. Launcher rendering is the one thing no test
      here can see.
- [ ] Launching it shows the app name without a blank window or a visible stall first. The
      content itself is covered by the Robolectric smoke test and the render baseline — this
      check is about real-device cold start, not about what is drawn.
- [ ] Rotate the device. Nothing crashes and the text stays on screen.
- [ ] The status bar and navigation bar are readable against the app, in both light and dark
      system themes.
- [ ] Switch the system to dark mode while the app is open. The app follows it rather than
      keeping light colours on a dark window.

If any of these fail, the problem is in the skeleton and it is cheaper to fix now than after
there are screens.

---

## Stage 4 — Scores screen

Do this pass on a night when games are actually being played. A scores screen that has only
ever seen final scores is untested.

- [ ] The screen opens on today's games, or the nearest date with games, without a spinner
      that never resolves.
- [ ] Scrolling back through previous dates loads them and does not jump or duplicate rows.
- [ ] A live game is visibly live — not merely labelled, but distinguishable at a glance from
      a final one.
- [ ] A live game's score updates without you touching the screen. Note roughly how far it
      lags the broadcast.
- [ ] Pull to refresh works, and a manual refresh while a live game is in progress does not
      produce duplicated rows.
- [ ] Pick a date with no games. You get a clear empty state, not a blank area that looks like
      a failure.
- [ ] Turn on aeroplane mode with cached data present. You get a stale banner, and the app
      does not pretend the data is current.
- [ ] Turn on aeroplane mode with no cached data. The failure is legible and offers a retry.
- [ ] The favourites/all toggle changes the list without a full reload.

---

## Stage 5 — Favourites

Favourites that do not survive the app being killed are not favourites.

- [ ] Choose teams in the Teams tab. The picker shows every team and the choice is obvious
      once made.
- [ ] The scores screen now defaults to favourites only.
- [ ] Force-stop the app from system settings, then reopen it. The favourites are still there.
- [ ] Install a newer build over the existing one — do not uninstall first. The favourites are
      still there. This is the case that catches storage that lives in the wrong place.
- [ ] Unfavourite everything. The scores screen shows an empty-favourites hint that explains
      what to do, rather than an empty list with no explanation.
- [ ] Toggle to "all" with no favourites chosen and confirm the app does not treat the empty
      favourites set as an error.

---

## Stage 6 — Standings

- [ ] League, conference, division and wildcard views all render and switch without a reload.
- [ ] Each view shows the right teams — spot-check that a division view contains only that
      division.
- [ ] With the favourites filter on, favourite teams are pinned and identifiable as pinned.
- [ ] Column headers line up with the numbers beneath them at this screen width.
- [ ] Scroll to the bottom of the longest table. Nothing is clipped by the navigation bar.
- [ ] The tie-break or points columns are not truncated to meaninglessness.

---

## Stage 7 — Game detail

Check this twice: once during a live game and once on a final one.

- [ ] Tapping a game opens its detail view and the back gesture returns to the same date and
      scroll position.
- [ ] Live game: the boxscore reflects the current state, and sections update as the game
      progresses.
- [ ] Final game: the boxscore is complete and matches the reported final score.
- [ ] The detail view degrades one section at a time. If a section is missing from the data,
      the rest still renders — nothing is blank, nothing crashes.
- [ ] A game with an unusual state — postponed, suspended, or not yet started — opens without
      crashing and says something sensible.
- [ ] Rotate while on the detail view. State survives.

---

## Stage 8 — Notifications

Notifications are best-effort under Doze; record what actually happened rather than whether
it theoretically should have.

- [ ] On first launch the app asks for notification permission in context, not immediately on
      a cold start with no explanation.
- [ ] Decline the permission. The app still works and does not nag on every launch.
- [ ] Opt in to game-start alerts for a favourite team. Start of a game produces exactly one
      notification.
- [ ] Opt in to goal alerts. A goal produces exactly one notification — not one per polling
      cycle, and not a duplicate after the app is reopened.
- [ ] Record the observed latency between the goal and the notification.
- [ ] Opt out. No further notifications arrive for that team.
- [ ] Leave the phone idle with the screen off for a while during a live game. Note whether
      notifications still arrive and how late — this is the Doze behaviour, and it is the
      honest limit of this design.
- [ ] Tap a notification. It opens the relevant game, not the app's front page.

---

## Stage 9 — Design and accessibility audit

This is a sign-off pass across every screen, not a spot check. Do it with the device in one
hand, because that is how it will be used.

- [x] One-thumb walkthrough: reach every primary action — switching tabs, changing date,
      opening a game, going back — without moving your grip or using a second hand.
- [x] Repeat the walkthrough at system font scale 2.0. Text grows without clipping, overlap,
      or truncation that loses meaning. This is the check that breaks layouts most often.
- [x] Light and dark themes: every screen, both themes, no unreadable text and no invisible
      icon.
- [x] Contrast: small secondary text — dates, timestamps, "final" labels — is legible in
      daylight on the device at normal brightness.
- [ ] TalkBack: turning it on, every control announces something meaningful. An icon button
      that reads as "button" is a defect.
- [ ] TalkBack: the scores list is navigable and a game row announces its teams, score and
      state as a coherent unit.
- [x] Nothing depends on colour alone to convey state — a live game must be distinguishable
      without seeing red versus grey.
- [x] Dynamic type and display size changes do not break the navigation shell.

### Sign-off record

Done on the Pixel 9 (Android 16) by Patrick, one-handed, against builds from this repository's
own tree. Two rounds were needed because the first one found a real defect.

**Round R160, 2026-11-11** — debug build from `aff6c0d` (the merge of PR #136).
Font scale 2.0, light and dark, contrast, colour-independent state, and the dynamic-type plus
display-size item all passed. The display-size half is named because it is half of the item:
a font-scale pass says nothing about layout at a larger display size.
The one-thumb walkthrough failed on its first action: the club page's Schedule / Players / Team
Stats tabs could only be changed by tapping the strip, so a thumb could not swipe between them
the way it can on the player and game pages. That is the defect PR #137 fixed, along with the
64 dp of edge travel that stopped a sideways table scroll from turning the page.

**Round R167, 2026-11-11** — debug build from `c235d33` (the merge of PR #137).
The walkthrough passed: the club page now swipes between its three tabs, the strip follows the
finger, and the players table keeps the last column readable before the page takes over.
Patrick's verdict: "All good".

**TalkBack (the two checks above) is not verified.** No pass had been run with TalkBack enabled
when this stage closed, and neither check can be marked on the strength of the others — TalkBack
reads semantics the visible layout says nothing about. They stay open as a known gap rather than
being ticked or quietly dropped. Treat them as release-blocking only if Stage 10 is to be tagged
without them; that is Patrick's call, recorded when it is made.

---

## Stage 10 — Release readiness

The release build differs from debug in ways that only show up on a device: signing has
changed, and Stage 10 enables R8 minification and shrinking. Both can break things that work
perfectly in debug.

- [x] Upgrade path: install the release APK **over the previous release signed with the same
      release key**, and confirm favourites and settings survive. This is the only in-place
      upgrade Android actually permits, and it is the one real users will perform.
      **Done 2026-11-11** with two throwaway-key release builds from `7fcd692`, `versionCode` 1
      reading 1.0.0 and `versionCode` 2 reading 1.0.1: the second installed over the first without
      uninstalling. Starred teams came back set after the upgrade. The theme, league order and the
      favourites-only toggle are stored the same way, so the same result covers them.
      **What this did not test, and why:** the standings conference and ranking were part of the
      original instruction and could not have survived — they are not persisted at all. They are
      held in the ViewModel's state and default to Eastern and Playoffs on every cold start, which
      makes them an upgrade-independent defect rather than a failed upgrade. `CHANGELOG.md` claims
      they are "remembered between launches"; that claim is untrue of this build and needs either
      the storage or the sentence changed.
- [ ] Do not expect the release APK to install over a debug build. Android refuses to replace
      an app whose signing certificate differs, and a debug build is signed with the build
      host's debug key. An install failure there is a signature mismatch, not data loss —
      see [device-testing.md](device-testing.md). For the first release there is no previous
      release, so there is no upgrade to test; say so in the record rather than skipping
      silently.
- [ ] Cold start time is acceptable and the app does not show a blank window for a noticeable
      period.
- [ ] Run through the smoke checklist: scores load, a game opens, standings render, favourites
      persist, a notification arrives.
- [ ] No debug-only UI or banner appears in the release build. Checking log output needs
      `adb`, which the build host does not have; if no adb-equipped machine is available,
      record logs as uninspected rather than as clean — see
      [device-testing.md](device-testing.md).
- [ ] The app requests only the permissions it needs. Anything unexpected is a defect to fix,
      not a note to keep.
- [ ] With R8 enabled, re-run the Stage 4 aeroplane-mode checks against the release build.
      Minification and shrinking can strip or rename what a serialisation layer looks up
      reflectively, and the symptom is a parse failure that never appears in debug. If the
      release build is not minified, record that and skip the check rather than implying it
      passed.
- [ ] Tag the release only when every **applicable** check above has passed and every
      non-applicable or uninspected one has been explicitly recorded as such. A check that
      could not be performed is a known gap, not a pass; the record should read like one.

## Design-parity screenshots

`DesignParityTest` records the whole shell at the design's own canvas - 411 x 923 and 360 x 780, light and dark, at
mdpi so one PNG pixel is one CSS pixel - into `app/src/test/screenshots/design-parity/`.

- [x] The four baselines have been compared against `roux-sports-design/final/scores-screen.html` and signed off.
      Signed off by Patrick on 2026-10-28 after viewing all four in chat ("Looks good"). Recorded with the date because
      this is a human judgement, not a test: the capture proves the app renders at those sizes, the sign-off says it
      matches the design. If a later change alters the frames, the sign-off is stale and the comparison repeats.
