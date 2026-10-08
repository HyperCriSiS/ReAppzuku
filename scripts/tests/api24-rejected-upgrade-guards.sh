#!/usr/bin/env bash
# API-24-only disposable-emulator negative update tests.
# No uninstall, data clear, downgrade override, privileged shell or release keys.
set -euo pipefail

[ "$#" -eq 3 ] || { echo 'Usage: <older-debug.apk> <installed-debug.apk> <installed-versionCode>' >&2; exit 2; }
older_apk="$1"
installed_apk="$2"
expected_version="$3"
package="com.gree1d.reappzuku"
[ -s "$older_apk" ] && [ -s "$installed_apk" ] || exit 2
[[ "$expected_version" =~ ^[1-9][0-9]*$ ]] || exit 2

SDK_ROOT="$(printenv ANDROID_SDK_ROOT || printenv ANDROID_HOME || printf '/usr/local/lib/android/sdk')"
APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt | sort -V | tail -n 1)"
[ -x "$APKSIGNER" ] && [ -x "$AAPT" ] || { echo 'Android APK tooling unavailable' >&2; exit 1; }
command -v adb >/dev/null
command -v keytool >/dev/null
command -v openssl >/dev/null

umask 077
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

single_signer() {
  local verified values normalized
  verified="$("$APKSIGNER" verify --verbose --print-certs "$1")" || return 1
  values="$(printf '%s\n' "$verified" |
    sed -nE 's/^.*certificate SHA-256 digest:[[:space:]]*([[:xdigit:]:[:space:]]+)$/\1/p')"
  [ "$(printf '%s\n' "$values" | grep -c .)" -eq 1 ] || return 1
  normalized="$(printf '%s' "$values" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
  [[ "$normalized" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s\n' "$normalized"
}

version_from_apk() {
  local line
  line="$("$AAPT" dump badging "$1" | sed -n '1p')" || return 1
  printf '%s\n' "$line" | sed -nE "s/^package: name='$package' versionCode='([0-9]+)'.*/\1/p"
}

baseline_signer="$(single_signer "$installed_apk")" || { echo 'Cannot verify installed candidate signer' >&2; exit 1; }
test "$(version_from_apk "$installed_apk")" = "$expected_version" ||
  { echo 'Candidate is not the expected installed version' >&2; exit 1; }
older_version="$(version_from_apk "$older_apk")"
[[ "$older_version" =~ ^[0-9]+$ ]] && [ "$older_version" -lt "$expected_version" ] ||
  { echo 'Older fixture is not a valid downgrade' >&2; exit 1; }
test "$(single_signer "$older_apk")" = "$baseline_signer" ||
  { echo 'Downgrade fixture unexpectedly has a different signing key' >&2; exit 1; }

verify_installed_and_data() {
  local stage="$1" runner
  adb shell dumpsys package "$package" > "$tmp/installed-$stage.txt"
  grep -Fq "versionCode=$expected_version" "$tmp/installed-$stage.txt" ||
    { echo "Installed version changed after $stage" >&2; exit 1; }
  runner="$(adb shell pm list instrumentation | tr -d '\r' |
    sed -n 's/^instrumentation:\([^ ]*\) (target=com.gree1d.reappzuku)$/\1/p' | sed -n '1p')"
  [ -n "$runner" ] || { echo 'Instrumentation runner disappeared' >&2; exit 1; }
  adb shell am instrument -w -r \
    -e class com.gree1d.reappzuku.core.EmulatorUpgradePersistenceTest#verifyAfterUpgrade \
    -e upgrade_smoke_stage verify "$runner" > "$tmp/verify-$stage.txt"
  cat "$tmp/verify-$stage.txt"
  grep -Fq 'OK (1 test)' "$tmp/verify-$stage.txt"
  ! grep -Fq 'FAILURES!!!' "$tmp/verify-$stage.txt"
}

expect_rejection() {
  local name="$1" expected_error="$2" apk="$3" result
  # Exit code and exact Android PackageManager error are BOTH required.
  if result="$(adb install -r -t "$apk" 2>&1)"; then
    echo "UNEXPECTED_INSTALL_SUCCESS: $name" >&2
    exit 1
  fi
  printf 'Rejected %s: %s\n' "$name" "$result"
  printf '%s\n' "$result" | grep -Fq "$expected_error" ||
    { echo "UNEXPECTED_INSTALL_ERROR: $name, expected $expected_error" >&2; exit 1; }
}

# Resign only the CI candidate APK with a brand-new, short-lived fake debug key.
# The production signing key is never available to this job.
password="$(openssl rand -hex 20)"
keytool -genkeypair -noprompt \
  -alias alternate -keyalg RSA -keysize 2048 -validity 2 \
  -storetype PKCS12 -keystore "$tmp/alternate.jks" \
  -storepass "$password" -keypass "$password" \
  -dname 'CN=ReAppzuku disposable emulator negative-update fixture' \
  >/dev/null 2>&1
"$APKSIGNER" sign \
  --ks "$tmp/alternate.jks" --ks-key-alias alternate \
  --ks-pass "pass:$password" --key-pass "pass:$password" \
  --out "$tmp/wrong-signer.apk" "$installed_apk"
test "$(version_from_apk "$tmp/wrong-signer.apk")" = "$expected_version"
alternate_signer="$(single_signer "$tmp/wrong-signer.apk")" ||
  { echo 'Alternate signer is invalid or ambiguous' >&2; exit 1; }
test "$alternate_signer" != "$baseline_signer" ||
  { echo 'Alternate signing identity unexpectedly matches installed identity' >&2; exit 1; }

expect_rejection 'mismatched signing certificate' 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' "$tmp/wrong-signer.apk"
verify_installed_and_data 'wrong-signer'
echo 'API24_WRONG_SIGNER_REJECTED_DATA_PASS'

expect_rejection 'versionCode downgrade' 'INSTALL_FAILED_VERSION_DOWNGRADE' "$older_apk"
verify_installed_and_data 'downgrade'
echo 'API24_DOWNGRADE_REJECTED_DATA_PASS'
