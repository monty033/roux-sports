# Device testing: delivering APKs to the phone

There is no emulator and no `adb` on the build host. Every on-device check happens by
delivering an APK over Tailscale to Patrick's phone, who installs it with Taildrop.

This is the only sanctioned delivery route. Do not stand up an HTTP server, a file host, or a
cloud bucket as the primary path.

## Devices

| Thing | Value |
|---|---|
| Phone (Tailscale device name) | `pixel-9` — Pixel 9, Android 16, the reference device |
| Relay host | `local-proxy`, reached as `root@local-proxy` — the tailnet policy denies every other user |
| Staging directory on the build host | `/var/lib/hermes/workspace/apk-share/` |

The build host's own Taildrop send is not the route. The send is relayed from `local-proxy` as
root, and that is the path verified end to end. A refusal when sending from the build host is
expected and is not a reason to invent another channel — an HTTP server or a cloud bucket is the
fallback below, not a substitute for fixing the relay.

## Procedure

Set the round once, then use `$APK` in every command below. Shell-safe names matter: writing
`roux-R<n>.apk` literally makes the shell read `<n>` as a redirection and the copy-paste fails.

```sh
ROUND=R1                                  # bump every round
APK="roux-$ROUND.apk"
STAGE="/var/lib/hermes/workspace/apk-share/$APK"
```

1. **Build from a tree that contains every commit under test.** Record the base SHA and the
   SHAs of the commits included. An APK built before a fix reached the tree is a stale artifact:
   the phone installs it, the old behaviour is tested, and the fix is reported as missing.

2. **Name the artifact after the round** — `$APK`, giving `roux-R1.apk`, `roux-R2.apk`, and so
   on, so the phone's Downloads list stays legible and a later round is never confused with an
   earlier one.

3. **Record the artifact's identity** before it leaves the host:

   ```sh
   LOCAL_SHA=$(sha256sum "$STAGE" | cut -d' ' -f1) || { echo "cannot read $STAGE"; exit 1; }
   echo "local  $LOCAL_SHA"
   ```

4. **Copy to the relay, then compare the full digest and fail on mismatch** — not a prefix, and
   not merely printed. `scp` does not work against this relay: its login shell is `fish`, so the
   connection closes as soon as `scp` starts. Pipe the file through a remote `bash` instead. An
   `scp`-style transfer that fails partway still leaves a stale file at the destination, so the
   copy is checked before the hash is trusted, and the comparison must be able to stop the
   procedure:

   ```sh
   ssh root@local-proxy "bash -c 'cat > /tmp/$APK'" < "$STAGE" \
     || { echo "transfer to relay FAILED"; exit 1; }
   REMOTE_SHA=$(ssh root@local-proxy "bash -c 'sha256sum /tmp/$APK'" | cut -d' ' -f1)
   [ -n "$REMOTE_SHA" ] || { echo "could not hash relay copy"; exit 1; }
   [ "$LOCAL_SHA" = "$REMOTE_SHA" ] || {
     echo "DIGEST MISMATCH local=$LOCAL_SHA remote=$REMOTE_SHA"; exit 1; }
   echo "digest match"
   ```

   The relay's login shell is `fish`, which rejects `$?`. Wrap every remote command in
   `bash -c '...'`, and expand `$APK` on the local side as shown, not inside the remote quotes.

5. **Send from the relay and require success:**

   ```sh
   ssh root@local-proxy "bash -c 'tailscale file cp /tmp/$APK pixel-9:'" \
     || { echo "TAILDROP SEND FAILED - do not report as delivered"; exit 1; }
   ```

6. **Delete the relay copy and prove it is gone.** The check must keep the exit status meaningful:
   `rm -f` succeeding does not mean the file is gone, and `ls … || echo gone` turns a permissions
   error into a false all-clear.

   ```sh
   ssh root@local-proxy "bash -c 'rm -f /tmp/$APK && test ! -e /tmp/$APK'" \
     || { echo "RELAY CLEANUP FAILED - /tmp/$APK may still exist"; exit 1; }
   echo "relay copy removed"
   ```

7. **Tell Patrick the round name and what to check.** He accepts the file in the Tailscale app
   and installs it. Name the checks from the sign-off list below, plus any check the current
   stage added. A delivery without named checks is a delivery nobody can sign off.

## Signing, and "App not installed"

Android refuses to replace an installed app whose signing certificate differs, and gives the
user no reason. Debug APKs are signed with the building host's `~/.android/debug.keystore`, so
the same source built on two different hosts produces two identities that cannot update each
other in place.

Before blaming the build, compare certificates:

```sh
bash "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$STAGE" \
  | grep 'SHA-256 digest'
```

against the certificate of the build already on the phone. If they differ, rebuild from the
host that produced the installed build rather than asking Patrick to uninstall — uninstalling
costs his stored data.

Call out a deliberate signing-identity change *before* delivery, never after he reports the
install failure.

Note that `apksigner` and friends are `#!/bin/bash` scripts that exec `java` from `PATH`; inside
the devShell both are present. Run them as `bash "$ANDROID_HOME/build-tools/36.0.0/apksigner"`
if invoking directly.

## Fallback

If `local-proxy` is unreachable, a temporary HTTP server on the tailnet is the documented
fallback — not the first resort. Reaching for it before checking the relay costs a round trip
and a correction.

## Sign-off checks

Every round runs these, whatever the stage. A stage that adds behaviour adds its own checks on
top; these are the floor.

1. **It installs over the previous build** — no uninstall prompt, no "App not installed".
2. **It launches and reaches the primary screen** without a crash or an empty frame.
3. **The screen the round touched renders the expected data**, not a placeholder or a spinner
   that never resolves.
4. **Rotation and large font** — turn the device sideways, then set the system font to its
   largest setting; nothing clips, overlaps, or becomes unreachable.
5. **Back navigation behaves** — the system back gesture leaves the app from the top level and
   never traps the user on a screen with no way out.
6. **One-handed reach** — the controls the round added can be touched with the right thumb while
   holding the phone normally, without shifting grip.
7. **No crash in the log** — for anything touching live data, a crash the UI hides still counts
   as a failed round.

Report each as pass or fail with a one-line note. "Looks fine" is not a sign-off.

## What this route cannot prove

Roborazzi/Robolectric screenshots come from the JVM and are not a substitute for the device
round. Thumb reach in the actual hand, gesture-navigation conflicts, scroll feel, real polling
against a live game, Doze behaviour, and notification delivery are device-only, and they belong
on the stage checklist as explicit items.

## Status of this procedure

Steps 3, 4 and 6 were executed against `root@local-proxy` with a throwaway file: the transfer,
the digest comparison, and the cleanup all succeed, and a deliberately mismatched digest is
rejected rather than reported as a match. Step 5 (the actual Taildrop send to `pixel-9`) is the
only step not exercised here, because it puts a file on Patrick's phone and is done as part of a
real round.

Two things were learned by running it rather than writing it down: `scp` cannot reach this relay
because the login shell is `fish`, and the relay rejects any user other than `root`. Both are
captured above so the next person does not rediscover them.
