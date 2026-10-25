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
root, and that is the path whose transfer, digest comparison and cleanup have been verified — the
final hand-off to the phone has not (see Status of this procedure). A refusal when sending from
the build host is expected and is not a reason to invent another channel — an HTTP server or a
cloud bucket is the fallback below, not a substitute for fixing the relay.

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

3. **Stage the built APK at the round path.** The build writes somewhere else entirely
   (`app/build/outputs/apk/debug/app-debug.apk`); everything below reads `$STAGE`, so copy it
   deliberately and refuse to continue if the build did not produce one:

   ```sh
   BUILT=app/build/outputs/apk/debug/app-debug.apk
   mkdir -p /var/lib/hermes/workspace/apk-share
   cp "$BUILT" "$STAGE" || { echo "no APK at $BUILT - did the build succeed?"; exit 1; }
   test -s "$STAGE" || { echo "staged APK is empty"; exit 1; }
   ```

4. **Record the artifact's identity** before it leaves the host. Capture the output and check the
   exit status *before* parsing: piping straight into `cut` would report `cut`'s status and could
   turn a failed `sha256sum` into a silent empty string.

   ```sh
   LOCAL_RAW=$(sha256sum "$STAGE") || { echo "cannot hash $STAGE"; exit 1; }
   LOCAL_SHA=${LOCAL_RAW%% *}
   [ -n "$LOCAL_SHA" ] || { echo "empty local digest"; exit 1; }
   echo "local  $LOCAL_SHA"
   ```

5. **Copy to the relay, then compare the full digest and fail on mismatch** — not a prefix, and
   not merely printed. `scp` does not work against this relay: its login shell is `fish`, so the
   connection closes as soon as `scp` starts. Pipe the file through a remote `bash` instead. A
   transfer that fails partway still leaves a stale file at the destination, so the copy is
   checked before the hash is trusted, and the comparison must be able to stop the procedure:

   ```sh
   ssh root@local-proxy "bash -c 'cat > /tmp/$APK'" < "$STAGE" \
     || { echo "transfer to relay FAILED"; exit 1; }
   REMOTE_RAW=$(ssh root@local-proxy "bash -c 'sha256sum /tmp/$APK'") \
     || { echo "ssh failed while hashing the relay copy"; exit 1; }
   REMOTE_SHA=${REMOTE_RAW%% *}
   [ -n "$REMOTE_SHA" ] || { echo "empty remote digest"; exit 1; }
   [ "$LOCAL_SHA" = "$REMOTE_SHA" ] || {
     echo "DIGEST MISMATCH local=$LOCAL_SHA remote=$REMOTE_SHA"; exit 1; }
   echo "digest match"
   ```

   The relay's login shell is `fish`, which rejects `$?`. Wrap every remote command in
   `bash -c '...'`, and expand `$APK` on the local side as shown, not inside the remote quotes.

6. **Send from the relay and require success:**

   ```sh
   ssh root@local-proxy "bash -c 'tailscale file cp /tmp/$APK pixel-9:'" \
     || { echo "TAILDROP SEND FAILED - do not report as delivered"; exit 1; }
   ```

7. **Delete the relay copy and prove it is gone.** The check must keep the exit status meaningful:
   `rm -f` succeeding does not mean the file is gone, and `ls … || echo gone` turns a permissions
   error into a false all-clear.

   ```sh
   ssh root@local-proxy "bash -c 'rm -f /tmp/$APK && test ! -e /tmp/$APK'" \
     || { echo "RELAY CLEANUP FAILED - /tmp/$APK may still exist"; exit 1; }
   echo "relay copy removed"
   ```

8. **Tell Patrick the round name and what to check.** Taildrop does not ask him to accept anything:
   the file arrives as a notification on the phone, and opening it puts the APK in Downloads,
   where he installs it. Name the checks from the sign-off list below, plus any check the current
   stage added. A delivery without named checks is a delivery nobody can sign off.

## Signing, and "App not installed"

Android refuses to replace an installed app whose signing certificate differs, and gives the
user no reason. Debug APKs are signed with the building host's `~/.android/debug.keystore`, so
the same source built on two different hosts produces two identities that cannot update each
other in place.

Before blaming the build, compare certificates:

```sh
CERT_OUT=$(bash "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$STAGE") \
  || { echo "apksigner could not verify $STAGE"; exit 1; }
printf '%s\n' "$CERT_OUT" | grep 'SHA-256 digest'
```

against the certificate of the build already on the phone. If they differ, rebuild from the
host that produced the installed build rather than asking Patrick to uninstall — uninstalling
costs his stored data.

Check `apksigner`'s own exit status before filtering its output, as above: piping it straight into
`grep` reports `grep`'s status, so a verifier failure would look like a success whenever the
pattern happened to match earlier output.

Call out a deliberate signing-identity change *before* delivery, never after he reports the
install failure.

Note that `apksigner` and friends are `#!/bin/bash` scripts that exec `java` from `PATH`; inside
the devShell both are present. Run them as `bash "$ANDROID_HOME/build-tools/36.0.0/apksigner"`
if invoking directly.

## Fallback

If `local-proxy` is unreachable, serve the staged APK from the build host over the tailnet. This
is the fallback, not the first resort — reaching for it before checking the relay costs a round
trip and a correction.

Bind to the tailnet address only, never to `0.0.0.0`, so the file is not offered to the whole
LAN:

```sh
TSIP=$(tailscale ip -4)                 # 100.x.y.z, this host
nix shell nixpkgs#python3 --command python3 -m http.server 8099 \
  --bind "$TSIP" --directory /var/lib/hermes/workspace/apk-share
```

Print the URL to open — a phone browser cannot expand a shell variable, so hand Patrick the
literal address rather than a template:

```sh
printf 'http://hermes:8099/%s\n' "$APK"
printf 'http://%s:8099/%s\n' "$TSIP" "$APK"
```

Give him the first line, or the second if MagicDNS does not resolve on the device. When the
round is over, stop the server with Ctrl-C and delete the staged file with `rm -f "$STAGE"` — a
fallback server left running is how a build artifact ends up on an open port for a week.

Verified on this host: the server starts, serves the directory with HTTP 200, and `tailscale ip
-4` reports the tailnet address. If `8099` is taken, pick another port and say which one.

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
7. **No crash the UI hides** — for anything touching live data, the app must not visibly close,
   freeze, or bounce back to the launcher. The build host has no `adb`, so the check the phone
   operator can actually perform is visual. If a machine with `adb` is at hand (a laptop, not the
   build host), confirm with `adb logcat -d | grep -i 'FATAL EXCEPTION'` after the round and say
   whether you did. Otherwise record "no visible crash" and state plainly that the log was not
   inspected — do not imply it was.

Report each as pass or fail with a one-line note. "Looks fine" is not a sign-off.

## What this route cannot prove

Roborazzi/Robolectric screenshots come from the JVM and are not a substitute for the device
round. Thumb reach in the actual hand, gesture-navigation conflicts, scroll feel, real polling
against a live game, Doze behaviour, and notification delivery are device-only, and they belong
on the stage checklist as explicit items.

## Status of this procedure

Verified against the real relay with throwaway files: staging a build output, hashing it, the
transfer to `root@local-proxy`, the digest comparison, and the cleanup. Corrupting the relay copy
after transfer makes the comparison fail rather than pass, which is the case that matters. The
fallback server was started and answered HTTP 200 from the tailnet address.

Not verified: the Taildrop send itself, because it puts a file on Patrick's phone and belongs to a
real round. Everything up to that point is exercised.

Two things were learned by running it rather than writing it down: `scp` cannot reach this relay
because the login shell is `fish`, and the relay rejects any user other than `root`. Both are
captured above so the next person does not rediscover them.
