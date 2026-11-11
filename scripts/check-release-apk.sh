#!/usr/bin/env bash
# Audit of the built release APK (plan S10.2). It proves:
#   1. every permission the APK requests is in the recorded allowlist below;
#   2. no entry in the APK belongs to Firebase or Google Play services;
#   3. the badging package is net.montycasa.roux, versionName 1.0.0 and versionCode 1.
# Usage: check-release-apk.sh [APK]    APK defaults to the single APK in app/build/outputs/apk/release.
#        check-release-apk.sh --self-test    needs no APK; feeds the permission check a list containing
#                                            android.permission.READ_CONTACTS and exits 1 because it is rejected.
#        check-release-apk.sh --backup [APK]    audits only the backup policy (Stage E1, preferences_only): the manifest
#                                            has android:allowBackup="true" and android:dataExtractionRules pointing at
#                                            xml/data_extraction_rules, and that compiled file has exactly the sections
#                                            cloud-backup and device-transfer, each holding exactly
#                                            <include domain="file" path="datastore/"/> and
#                                            <exclude domain="file" path="nhl-cache/"/>. Anything missing, malformed,
#                                            broader or contradictory fails. Build the APK first with
#                                            `./gradlew :app:assembleRelease` (the default APK path above is the
#                                            unsigned release APK). The other checks do not run in this mode.
# This is a static audit of the built APK; it does not prove that a device restore behaves as intended.
# Run it inside `nix develop`. aapt2 is taken from PATH, otherwise from the newest $ANDROID_HOME/build-tools/*/
# directory. APK entries are listed with `unzip -Z1`, or with the JDK's `jar tf` when unzip is not installed
# (the devShell has no unzip). It works from any cwd and exits non-zero on the first failed check.
set -euo pipefail

EXPECTED_PACKAGE=net.montycasa.roux
EXPECTED_VERSION_NAME=1.0.0
EXPECTED_VERSION_CODE=1

# The permission allowlist, recorded from `aapt2 dump permissions` on the release APK built from this tree.
# Two are the app's own (manifest): INTERNET for the NHL endpoints and logos, POST_NOTIFICATIONS for alerts.
# The rest are merged in by WorkManager (work-runtime 2.12.0) and exist so periodic work survives idle and reboot:
#   WAKE_LOCK, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE.
# <package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is androidx.core's own signature-level permission, declared
# and used by this package alone; it is allowed for this package only (see allowed_permission below).
ALLOWED_PERMISSIONS=(
  android.permission.INTERNET
  android.permission.POST_NOTIFICATIONS
  android.permission.WAKE_LOCK
  android.permission.ACCESS_NETWORK_STATE
  android.permission.RECEIVE_BOOT_COMPLETED
  android.permission.FOREGROUND_SERVICE
)

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

allowed_permission() {
  local candidate="$1" allowed
  for allowed in "${ALLOWED_PERMISSIONS[@]}"; do
    [ "$candidate" != "$allowed" ] || return 0
  done
  [ "$candidate" = "$EXPECTED_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" ]
}

# Reads one permission name per line on stdin; prints each one outside the allowlist and returns 1 if any.
check_permissions() {
  local name rejected=0
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    if ! allowed_permission "$name"; then
      printf '  not allowed: %s\n' "$name" >&2
      rejected=1
    fi
  done
  return "$rejected"
}

if [ "${1:-}" = "--self-test" ]; then
  # A clean list must pass, or a checker that rejects everything would "pass" this self-test.
  printf '%s\n' "${ALLOWED_PERMISSIONS[@]}" | check_permissions || fail "self-test: the allowlist itself was rejected"
  printf 'self-test: allowlist accepted; now offering android.permission.READ_CONTACTS\n'
  if printf '%s\n' "${ALLOWED_PERMISSIONS[@]}" android.permission.READ_CONTACTS | check_permissions; then
    printf 'SELF-TEST BROKEN: android.permission.READ_CONTACTS was accepted\n' >&2
    exit 2
  fi
  fail "permissions include android.permission.READ_CONTACTS (self-test: this rejection is the expected result)"
fi

script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")" || fail "could not resolve the script directory"
project_root="$(dirname -- "$script_dir")"

resolve_tool() {
  local name="$1" found
  if found="$(command -v "$name")"; then
    printf '%s\n' "$found"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ]; then
    found="$(ls -d -- "$ANDROID_HOME"/build-tools/*/"$name" 2>/dev/null | sort -V | tail -n 1 || true)"
    if [ -n "$found" ] && [ -x "$found" ]; then
      printf '%s\n' "$found"
      return
    fi
  fi
  fail "$name not found on PATH or under \$ANDROID_HOME/build-tools; run this inside nix develop"
}

mode=release
if [ "${1:-}" = "--backup" ]; then
  mode=backup
  shift
fi

if [ "$#" -ge 1 ]; then
  apk="$1"
else
  shopt -s nullglob
  candidates=("$project_root"/app/build/outputs/apk/release/*.apk)
  shopt -u nullglob
  [ "${#candidates[@]}" -eq 1 ] || fail "expected exactly one APK in $project_root/app/build/outputs/apk/release, found ${#candidates[@]}; run :app:assembleRelease or pass the APK path"
  apk="${candidates[0]}"
fi
[ -f "$apk" ] || fail "APK not found: $apk"
printf 'APK: %s\n' "$apk"

aapt2="$(resolve_tool aapt2)"

# Backup policy (Stage E1). The compiled manifest names the rules by resource id, so the id is resolved through
# `aapt2 dump resources` to the xml resource name and to the file inside the APK that `dump xmltree` can read.
EXPECTED_RULES_RESOURCE=xml/data_extraction_rules

# Reads `aapt2 dump xmltree` of AndroidManifest.xml on stdin; prints the backup-related application attributes and
# "application" for each <application> element. A custom backup agent is rejected because it can bypass these rules.
manifest_backup_attributes() {
  awk '
    /^ *E: / { in_app = ($2 == "application"); if (in_app) print "application"; next }
    in_app && /^ *A: http:\/\/schemas\.android\.com\/apk\/res\/android:allowBackup\(0x[0-9a-f]+\)=/ {
      sub(/^[^=]*=/, ""); print "allowBackup=" $0; next }
    in_app && /^ *A: http:\/\/schemas\.android\.com\/apk\/res\/android:dataExtractionRules\(0x[0-9a-f]+\)=/ {
      sub(/^[^=]*=/, ""); print "dataExtractionRules=" $0; next }
    in_app && index($0, "android:backupAgent(") > 0 {
      sub(/^[^=]*=/, ""); print "backupAgent=" $0; next }
  '
}

# Reads `aapt2 dump xmltree` of the rules file on stdin and prints one line per fact:
#   ROOT                          the document element is <data-extraction-rules>
#   SECTION|name                  a child of the root (cloud-backup or device-transfer expected)
#   RULE|section|tag|domain|path  an include/exclude inside a section
#   BAD|reason                    anything outside that shape (unknown element or attribute, wrong nesting)
parse_rules() {
  awk '
    function flush() {
      if (rule_tag != "") print "RULE|" rule_section "|" rule_tag "|" rule_domain "|" rule_path
      rule_tag = ""; rule_domain = ""; rule_path = ""
    }
    /^ *E: / {
      flush()
      match($0, /^ */); indent = RLENGTH; name = $2
      while (depth > 0 && indents[depth] >= indent) depth--
      depth++; indents[depth] = indent; names[depth] = name; kind = ""
      if (depth == 1) {
        if (name == "data-extraction-rules") print "ROOT"; else print "BAD|unexpected root element " name
      } else if (depth == 2) {
        print "SECTION|" name; section = name
        if (name != "cloud-backup" && name != "device-transfer") print "BAD|unexpected section " name
      } else if (depth == 3 && (name == "include" || name == "exclude")) {
        rule_tag = name; rule_section = section; kind = "rule"
      } else {
        print "BAD|unexpected element " name " in " section
      }
      next
    }
    /^ *A: / {
      line = $0; sub(/^ *A: /, "", line)
      attr = line; sub(/=.*/, "", attr)
      value = line; sub(/^[^=]*="/, "", value); sub(/" \(Raw: ".*"\)$/, "", value)
      if (kind == "rule" && attr == "domain") rule_domain = value
      else if (kind == "rule" && attr == "path") rule_path = value
      else print "BAD|unexpected attribute " attr " on " names[depth] " in " section
      next
    }
    END { flush() }
  '
}

check_backup_policy() {
  local apk="$1" manifest_dump facts attr_lines application_count=0 allow_backup="" rules_ref="" backup_agent="" line
  manifest_dump="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")" || fail "aapt2 dump xmltree failed for AndroidManifest.xml in $apk"
  attr_lines="$(printf '%s\n' "$manifest_dump" | manifest_backup_attributes)"
  while IFS= read -r line; do
    case "$line" in
      application) application_count=$((application_count + 1)) ;;
      allowBackup=*) allow_backup="${line#allowBackup=}" ;;
      dataExtractionRules=*) rules_ref="${line#dataExtractionRules=}" ;;
      backupAgent=*) backup_agent="${line#backupAgent=}" ;;
    esac
  done <<<"$attr_lines"
  [ -z "$backup_agent" ] || fail "manifest declares custom android:backupAgent (${backup_agent}); custom agent behavior is not audited"
  [ "$application_count" -eq 1 ] || fail "manifest has $application_count <application> elements, expected 1"
  [ "$allow_backup" = "true" ] || fail "manifest does not enable allowBackup (android:allowBackup='${allow_backup:-unset}', expected true)"
  [ -n "$rules_ref" ] || fail "manifest has no android:dataExtractionRules"
  pass "manifest enables allowBackup"

  # Resolve the rules reference (@0x7fXXXXXX) to a resource name and the file that holds it.
  case "$rules_ref" in
    @0x*) ;;
    *) fail "android:dataExtractionRules is '$rules_ref', expected a resource reference" ;;
  esac
  local resources rules_id="${rules_ref#@}" rules_name rules_files rules_file
  resources="$("$aapt2" dump resources "$apk")" || fail "aapt2 dump resources failed for $apk"
  rules_name="$(printf '%s\n' "$resources" | awk -v id="$rules_id" '$1 == "resource" && $2 == id { print $3; exit }')"
  [ "$rules_name" = "$EXPECTED_RULES_RESOURCE" ] \
    || fail "android:dataExtractionRules does not reference $EXPECTED_RULES_RESOURCE (resolves to '${rules_name:-nothing}')"
  rules_files="$(printf '%s\n' "$resources" | awk -v id="$rules_id" '
    $1 == "resource" { current = ($2 == id); next }
    /^ *type / { current = 0; next }
    current && /\(file\) / { for (i = 1; i <= NF; i++) if ($i == "(file)") print $(i + 1) }
  ')"
  [ -n "$rules_files" ] || fail "no file found in $apk for $EXPECTED_RULES_RESOURCE"
  pass "android:dataExtractionRules references $EXPECTED_RULES_RESOURCE"

  while IFS= read -r rules_file; do
    printf 'rules file: %s\n' "$rules_file"
    local dump
    dump="$("$aapt2" dump xmltree --file "$rules_file" "$apk")" || fail "aapt2 dump xmltree failed for $rules_file in $apk"
    facts="$(printf '%s\n' "$dump" | parse_rules)"
    check_rules_facts "$facts" || fail "$rules_file does not match the preferences_only policy"
  done <<<"$rules_files"
  pass "data extraction rules include only file datastore/ and exclude file nhl-cache/ for cloud-backup and device-transfer"
}

# Reads parse_rules output as $1. Reports every violation on stderr and returns 1 if there is any.
check_rules_facts() {
  local facts="$1" bad=0 line section kind rule_section tag domain path included excluded count
  printf '%s\n' "$facts" | grep -qx 'ROOT' || { printf '  rules: no <data-extraction-rules> root element\n' >&2; bad=1; }
  while IFS= read -r line; do
    case "$line" in
      BAD\|*) printf '  rules: %s\n' "${line#BAD|}" >&2; bad=1 ;;
    esac
  done <<<"$facts"
  for section in cloud-backup device-transfer; do
    count="$(printf '%s\n' "$facts" | grep -cx "SECTION|$section" || true)"
    if [ "$count" -eq 0 ]; then
      printf '  %s: section missing\n' "$section" >&2; bad=1; continue
    elif [ "$count" -gt 1 ]; then
      printf '  %s: section appears %s times\n' "$section" "$count" >&2; bad=1
    fi
    included=0 excluded=0
    while IFS='|' read -r kind rule_section tag domain path; do
      [ "$kind" = RULE ] && [ "$rule_section" = "$section" ] || continue
      if [ "$tag" = include ] && [ "$domain" = file ] && [ "$path" = "datastore/" ]; then
        included=$((included + 1))
      elif [ "$tag" = exclude ] && [ "$domain" = file ] && [ "$path" = "nhl-cache/" ]; then
        excluded=$((excluded + 1))
      else
        printf '  %s: unexpected %s %s %s\n' "$section" "$tag" "$domain" "$path" >&2; bad=1
      fi
    done <<<"$facts"
    [ "$included" -ge 1 ] || { printf '  %s: missing include file datastore/\n' "$section" >&2; bad=1; }
    [ "$excluded" -ge 1 ] || { printf '  %s: missing exclude file nhl-cache/\n' "$section" >&2; bad=1; }
    [ "$included" -le 1 ] && [ "$excluded" -le 1 ] || { printf '  %s: duplicate rule\n' "$section" >&2; bad=1; }
  done
  return "$bad"
}

if [ "$mode" = backup ]; then
  check_backup_policy "$apk"
  printf 'BACKUP POLICY OK\n'
  exit 0
fi

list_entries() {
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$1"
  else
    jar tf "$1"
  fi
}

# (1) Permissions.
permissions_output="$("$aapt2" dump permissions "$apk")" || fail "aapt2 dump permissions failed for $apk"
permissions="$(printf '%s\n' "$permissions_output" | sed -n "s/^uses-permission: name='\\([^']*\\)'.*/\\1/p")"
printf 'permissions requested:\n'
printf '%s\n' "$permissions" | sed 's/^/  /'
printf '%s\n' "$permissions" | check_permissions || fail "the APK requests permissions outside the allowlist"
pass "permissions are a subset of the allowlist"

# (2) No Firebase or Google Play services anywhere in the package.
entries="$(list_entries "$apk")" || fail "could not list the entries of $apk"
proprietary="$(printf '%s\n' "$entries" | grep -iE 'firebase|play-services|com/google/android/gms' || true)"
if [ -n "$proprietary" ]; then
  printf '%s\n' "$proprietary" >&2
  fail "the APK contains Firebase or Google Play services entries"
fi
pass "no firebase, play-services or com/google/android/gms entries"

# (3) Badging.
badging="$("$aapt2" dump badging "$apk")" || fail "aapt2 dump badging failed for $apk"
package_line="$(printf '%s\n' "$badging" | grep -m1 '^package:' || true)"
[ -n "$package_line" ] || fail "aapt2 dump badging printed no package line"
package_name="$(printf '%s\n' "$package_line" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
version_code="$(printf '%s\n' "$package_line" | sed -n "s/.*versionCode='\\([^']*\\)'.*/\\1/p")"
version_name="$(printf '%s\n' "$package_line" | sed -n "s/.*versionName='\\([^']*\\)'.*/\\1/p")"
printf 'badging: package=%s versionName=%s versionCode=%s\n' "$package_name" "$version_name" "$version_code"
[ "$package_name" = "$EXPECTED_PACKAGE" ] || fail "badging package is '$package_name', expected $EXPECTED_PACKAGE"
[ "$version_name" = "$EXPECTED_VERSION_NAME" ] || fail "badging versionName is '$version_name', expected $EXPECTED_VERSION_NAME"
[ "$version_code" = "$EXPECTED_VERSION_CODE" ] || fail "badging versionCode is '$version_code', expected $EXPECTED_VERSION_CODE"
pass "badging package, versionName and versionCode"

printf 'RELEASE APK OK\n'
