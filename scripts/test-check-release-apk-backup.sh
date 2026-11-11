#!/usr/bin/env bash
# Focused test for `check-release-apk.sh --backup` (plan Stage E1, policy preferences_only).
# It builds tiny REAL APKs with aapt2 compile/link - the manifest and the data-extraction rules are compiled
# exactly as the app build compiles them, so the checker reads genuine `aapt2 dump xmltree` output - then runs
# the checker on each:
#   - one positive APK (backup on, rules include only datastore/ and exclude nhl-cache/ for cloud and transfer);
#   - negative controls that must each be rejected FOR THE STATED REASON (exit 1 and the reason on stderr), so a
#     checker that rejects everything, or rejects for the wrong reason, still fails this test.
# Run it inside `nix develop`. It needs no built app APK, works from any cwd, and writes only to a mktemp -d.
set -uo pipefail

script_dir="$(dirname -- "$(realpath -- "${BASH_SOURCE[0]}")")"
checker="$script_dir/check-release-apk.sh"
work="$(mktemp -d)"
trap 'rm -rf -- "$work"' EXIT

command -v aapt2 >/dev/null 2>&1 || { echo "FAIL: aapt2 not on PATH; run inside nix develop" >&2; exit 1; }
android_jar="$(ls -d -- "${ANDROID_HOME:?run inside nix develop}"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -n 1)"
[ -f "$android_jar" ] || { echo "FAIL: no android.jar under \$ANDROID_HOME/platforms" >&2; exit 1; }

INC='<include domain="file" path="datastore/" />'
EXC='<exclude domain="file" path="nhl-cache/" />'
GOOD_ATTRS='android:allowBackup="true" android:dataExtractionRules="@xml/data_extraction_rules"'

# build_apk NAME MANIFEST_APPLICATION_ATTRS CLOUD_BODY TRANSFER_BODY [EXTRA_SECTIONS]
# A rules file is written for xml/data_extraction_rules; a CLOUD_BODY of "-" omits the cloud-backup section,
# a TRANSFER_BODY of "-" omits the device-transfer section.
build_apk() {
  local name="$1" attrs="$2" cloud="$3" transfer="$4" extra="${5:-}" dir="$work/$1"
  mkdir -p "$dir/res/xml"
  cat >"$dir/AndroidManifest.xml" <<EOF
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="net.montycasa.roux">
  <application $attrs />
</manifest>
EOF
  {
    echo '<?xml version="1.0" encoding="utf-8"?>'
    echo '<data-extraction-rules>'
    [ "$cloud" = "-" ] || echo "<cloud-backup>$cloud</cloud-backup>"
    [ "$transfer" = "-" ] || echo "<device-transfer>$transfer</device-transfer>"
    echo "$extra"
    echo '</data-extraction-rules>'
  } >"$dir/res/xml/data_extraction_rules.xml"
  # A second, valid-looking file: a manifest pointing at it instead must not satisfy the checker.
  cp "$dir/res/xml/data_extraction_rules.xml" "$dir/res/xml/other_rules.xml"
  (cd "$dir" &&
    aapt2 compile --dir res -o res.zip &&
    aapt2 link -I "$android_jar" --manifest AndroidManifest.xml --min-sdk-version 31 --target-sdk-version 36 \
      -R res.zip -o "$name.apk") >"$dir/build.log" 2>&1 || { cat "$dir/build.log" >&2; echo "FAIL: could not build $name" >&2; exit 1; }
}

failures=0
total=0

run_checker() {
  "$checker" --backup "$work/$1/$1.apk" >"$work/$1.out" 2>"$work/$1.err"
}

expect_pass() {
  local name="$1"
  total=$((total + 1))
  if run_checker "$name" && grep -q 'BACKUP POLICY OK' "$work/$name.out"; then
    echo "ok   - $name accepted"
  else
    echo "FAIL - $name should be accepted" >&2
    sed 's/^/       /' "$work/$name.out" "$work/$name.err" >&2
    failures=$((failures + 1))
  fi
}

expect_reject() {
  local name="$1" reason="$2" status=0
  total=$((total + 1))
  run_checker "$name" || status=$?
  if [ "$status" -eq 1 ] && grep -q "$reason" "$work/$name.err" && ! grep -q 'BACKUP POLICY OK' "$work/$name.out"; then
    echo "ok   - $name rejected ($reason)"
  else
    echo "FAIL - $name should exit 1 with '$reason' on stderr (exit $status)" >&2
    sed 's/^/       /' "$work/$name.out" "$work/$name.err" >&2
    failures=$((failures + 1))
  fi
}

# ---- positive control -------------------------------------------------------------------------------------
build_apk good "$GOOD_ATTRS" "$INC $EXC" "$INC $EXC"
expect_pass good

# ---- negative controls: the rules -------------------------------------------------------------------------
build_apk cloud_missing_cache_exclusion "$GOOD_ATTRS" "$INC" "$INC $EXC"
expect_reject cloud_missing_cache_exclusion 'cloud-backup: missing exclude file nhl-cache/'

build_apk transfer_missing_cache_exclusion "$GOOD_ATTRS" "$INC $EXC" "$INC"
expect_reject transfer_missing_cache_exclusion 'device-transfer: missing exclude file nhl-cache/'

build_apk cloud_missing_prefs_include "$GOOD_ATTRS" "$EXC" "$INC $EXC"
expect_reject cloud_missing_prefs_include 'cloud-backup: missing include file datastore/'

build_apk transfer_missing_prefs_include "$GOOD_ATTRS" "$INC $EXC" "$EXC"
expect_reject transfer_missing_prefs_include 'device-transfer: missing include file datastore/'

build_apk broadened_extra_domain "$GOOD_ATTRS" "$INC $EXC <include domain=\"sharedpref\" path=\".\" />" "$INC $EXC"
expect_reject broadened_extra_domain 'cloud-backup: unexpected include sharedpref'

build_apk broadened_whole_files_dir "$GOOD_ATTRS" "$INC $EXC" '<include domain="file" path="." />'" $EXC"
expect_reject broadened_whole_files_dir 'device-transfer: unexpected include file'

build_apk contradictory_excludes_prefs "$GOOD_ATTRS" "$INC $EXC <exclude domain=\"file\" path=\"datastore/\" />" "$INC $EXC"
expect_reject contradictory_excludes_prefs 'cloud-backup: unexpected exclude file datastore/'

build_apk contradictory_includes_cache "$GOOD_ATTRS" "$INC $EXC" "$INC $EXC <include domain=\"file\" path=\"nhl-cache/\" />"
expect_reject contradictory_includes_cache 'device-transfer: unexpected include file nhl-cache/'

build_apk missing_cloud_section "$GOOD_ATTRS" "-" "$INC $EXC"
expect_reject missing_cloud_section 'cloud-backup: section missing'

build_apk missing_transfer_section "$GOOD_ATTRS" "$INC $EXC" "-"
expect_reject missing_transfer_section 'device-transfer: section missing'

build_apk extra_cross_platform_section "$GOOD_ATTRS" "$INC $EXC" "$INC $EXC" \
  '<cross-platform-transfer platform="ios"><include domain="file" path="datastore/" /></cross-platform-transfer>'
expect_reject extra_cross_platform_section 'unexpected section cross-platform-transfer'

build_apk extra_rule_attribute "$GOOD_ATTRS" '<include domain="file" path="datastore/" requireFlags="clientSideEncryption" /> '"$EXC" "$INC $EXC"
expect_reject extra_rule_attribute 'unexpected attribute requireFlags'

# ---- negative controls: the manifest ----------------------------------------------------------------------
build_apk backup_disabled 'android:allowBackup="false" android:dataExtractionRules="@xml/data_extraction_rules"' "$INC $EXC" "$INC $EXC"
expect_reject backup_disabled 'allowBackup'

build_apk backup_unset 'android:dataExtractionRules="@xml/data_extraction_rules"' "$INC $EXC" "$INC $EXC"
expect_reject backup_unset 'allowBackup'

build_apk custom_backup_agent "${GOOD_ATTRS} android:backupAgent=\".CustomBackupAgent\"" "$INC $EXC" "$INC $EXC"
expect_reject custom_backup_agent 'custom android:backupAgent'

build_apk rules_not_referenced 'android:allowBackup="true"' "$INC $EXC" "$INC $EXC"
expect_reject rules_not_referenced 'dataExtractionRules'

build_apk rules_wrong_resource 'android:allowBackup="true" android:dataExtractionRules="@xml/other_rules"' "$INC $EXC" "$INC $EXC"
expect_reject rules_wrong_resource 'does not reference xml/data_extraction_rules'

# ---- existing behaviour that must survive -----------------------------------------------------------------
total=$((total + 1))
self_status=0
"$checker" --self-test >"$work/self.out" 2>"$work/self.err" || self_status=$?
if [ "$self_status" -eq 1 ] && grep -q 'READ_CONTACTS (self-test: this rejection is the expected result)' "$work/self.err"; then
  echo "ok   - --self-test still exits 1 with its expected rejection"
else
  echo "FAIL - --self-test should exit 1 (got $self_status)" >&2
  failures=$((failures + 1))
fi

echo "$((total - failures))/$total checks passed"
[ "$failures" -eq 0 ]
