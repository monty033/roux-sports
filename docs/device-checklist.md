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

- [ ] Build the APK with `nix develop --command ./gradlew :app:assembleDebug`.
- [ ] Install it on the device.
- [ ] The app appears in the launcher with its icon and the name **Roux Sports**. The icon is
      a deliberate placeholder at this stage.
- [ ] Launching it shows a blank screen with the app name and does not crash.
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

- [ ] One-thumb walkthrough: reach every primary action — switching tabs, changing date,
      opening a game, going back — without moving your grip or using a second hand.
- [ ] Repeat the walkthrough at system font scale 2.0. Text grows without clipping, overlap,
      or truncation that loses meaning. This is the check that breaks layouts most often.
- [ ] Light and dark themes: every screen, both themes, no unreadable text and no invisible
      icon.
- [ ] Contrast: small secondary text — dates, timestamps, "final" labels — is legible in
      daylight on the device at normal brightness.
- [ ] TalkBack: turning it on, every control announces something meaningful. An icon button
      that reads as "button" is a defect.
- [ ] TalkBack: the scores list is navigable and a game row announces its teams, score and
      state as a coherent unit.
- [ ] Nothing depends on colour alone to convey state — a live game must be distinguishable
      without seeing red versus grey.
- [ ] Dynamic type and display size changes do not break the navigation shell.

---

## Stage 10 — Release readiness

The release build differs from debug in ways that only show up on a device: R8 has shrunk and
obfuscated the code, and signing has changed.

- [ ] Install the release APK **over** the debug build without uninstalling. Existing
      favourites and settings survive. This checks that signing and storage agree; a failure
      here means users lose their data on upgrade.
- [ ] Cold start time is acceptable and the app does not show a blank window for a noticeable
      period.
- [ ] Run through the smoke checklist: scores load, a game opens, standings render, favourites
      persist, a notification arrives.
- [ ] No debug-only UI, banner, or log output appears in the release build.
- [ ] The app requests only the permissions it needs. Anything unexpected is a defect to fix,
      not a note to keep.
- [ ] Airplane mode behaves as in Stage 4 — R8 has been known to break reflection-based
      serialisation that worked in debug.
- [ ] Tag the release only after every line above passes.
