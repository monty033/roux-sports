# Device testing: delivering APKs to the phone

There is no emulator and no `adb` on the build host. Every on-device check happens by
delivering an APK over Tailscale to Patrick's phone, who installs it with Taildrop.

This is the only sanctioned delivery route. Do not stand up an HTTP server, a file host, or a
cloud bucket as the primary path.

## Devices

| Thing | Value |
|---|---|
| Phone (Tailscale device name) | `pixel-9` — Pixel 9, Android 16, the reference device |
| Relay host | `local-proxy` (as root) |
| Staging directory on the build host | `/var/lib/hermes/workspace/apk-share/` |

The Hermes host holds no Taildrop file-operator rights, so `tailscale file cp` from here always
fails with `Access denied: file access denied`, and `sudo` prompts for a password. That is the
expected result on this host, not a reason to invent another channel. The file is relayed from
`local-proxy`, which does hold the right.

## Procedure

1. **Build from a tree that contains every commit under test.** Record the base SHA and the
   SHAs of the commits included. An APK built before a fix reached the tree is a stale artifact:
   the phone installs it, the old behaviour is tested, and the fix is reported as missing.

2. **Name the artifact after the round**, so the phone's Downloads list stays legible and a
   later round is never confused with an earlier one:

   ```
   roux-R<n>.apk
   ```

3. **Record the artifact's identity** before it leaves the host:

   ```
   sha256sum /var/lib/hermes/workspace/apk-share/roux-R<n>.apk
   ```

4. **Copy to the relay and compare the full digest there** — not a prefix:

   ```
   scp /var/lib/hermes/workspace/apk-share/roux-R<n>.apk local-proxy:/tmp/roux-R<n>.apk
   ssh local-proxy 'sha256sum /tmp/roux-R<n>.apk'
   ```

   Remote root shells on that host may run `fish`, which rejects `$?`. Wrap remote commands in
   `bash -c '...'`.

5. **Send from the relay** (remote command wrapped for the fish shell):

   ```
   ssh local-proxy "bash -c 'tailscale file cp /tmp/roux-R<n>.apk pixel-9:'"
   ```

   Require a zero exit from that command. A non-zero exit means the file did not send; do not
   report it as delivered.

6. **Delete the relay copy and confirm it is gone:**

   ```
   ssh local-proxy "bash -c 'rm -f /tmp/roux-R<n>.apk; ls /tmp/roux-R<n>.apk 2>/dev/null || echo gone'"
   ```

7. **Tell Patrick the round name and what to check.** He accepts the file in the Tailscale app
   and installs it. Every stage has a checklist in `docs/device-checklist.md`; a delivery
   without a named checklist item is a delivery nobody can sign off.

## Signing, and "App not installed"

Android refuses to replace an installed app whose signing certificate differs, and gives the
user no reason. Debug APKs are signed with the building host's `~/.android/debug.keystore`, so
the same source built on two different hosts produces two identities that cannot update each
other in place.

Before blaming the build, compare certificates:

```
bash "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs roux-R<n>.apk | grep 'SHA-256 digest'
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

## What this route cannot prove

Roborazzi/Robolectric screenshots come from the JVM and are not a substitute for the device
round. Thumb reach in the actual hand, gesture-navigation conflicts, scroll feel, real polling
against a live game, Doze behaviour, and notification delivery are device-only, and they belong
on the stage checklist as explicit items.
