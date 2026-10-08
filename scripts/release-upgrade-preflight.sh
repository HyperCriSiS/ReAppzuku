#!/usr/bin/env bash
# Read-only, fail-closed device upgrade preflight. Never installs, uninstalls,
# clears data, invokes privileged commands, or uploads a device inventory.
set -euo pipefail

usage() {
  echo 'Usage: bash scripts/release-upgrade-preflight.sh <candidate.apk> <expected-cert-sha256> [adb-serial]' >&2
  exit 2
}
fail() { echo "UPGRADE_PREFLIGHT_BLOCKED: $*" >&2; exit 1; }

[ "$#" -ge 2 ] && [ "$#" -le 3 ] || usage
candidate="$1"
expected="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]\:')"
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || fail 'Expected signing certificate must be a 64-digit SHA-256 fingerprint.'
[ -f "$candidate" ] && [ -r "$candidate" ] || fail 'Candidate APK is missing or unreadable.'

for tool in adb aapt apksigner; do
  command -v "$tool" >/dev/null 2>&1 || fail "Required Android SDK tool not found: $tool"
done

adb_cmd=(adb)
if [ "$#" -eq 3 ]; then
  [ -n "$3" ] || fail 'Explicit device serial must not be empty.'
  adb_cmd+=(-s "$3")
fi
"${adb_cmd[@]}" get-state >/dev/null 2>&1 || fail 'ADB device unavailable or selection is ambiguous.'

identity() {
  local badging pkg version
  badging="$(aapt dump badging "$1")" || return 1
  pkg="$(printf '%s\n' "$badging" | sed -nE "1s/^package: name='([^']+)'.*/\1/p")"
  version="$(printf '%s\n' "$badging" | sed -nE "1s/^package: name='[^']+' versionCode='([0-9]+)'.*/\1/p")"
  [ -n "$pkg" ] && [[ "$version" =~ ^[0-9]{1,10}$ ]] || return 1
  printf '%s %s\n' "$pkg" "$version"
}

certificate() {
  local output digests normalized
  output="$(apksigner verify --verbose --print-certs "$1")" || return 1
  digests="$(printf '%s\n' "$output" | sed -nE 's/^Signer #[0-9]+ certificate SHA-256 digest: *([[:xdigit:]:[:space:]]+)$/\1/p')"
  # Multiple signers and rotation chains need separate, explicit release policy.
  [ "$(printf '%s\n' "$digests" | grep -c .)" -eq 1 ] || return 1
  normalized="$(printf '%s' "$digests" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]\:')"
  [[ "$normalized" =~ ^[0-9a-f]{64}$ ]] || return 1
  printf '%s\n' "$normalized"
}

candidate_identity="$(identity "$candidate")" || fail 'Candidate package identity/versionCode cannot be verified.'
read -r candidate_pkg candidate_version <<< "$candidate_identity"
[ "$candidate_pkg" = 'com.gree1d.reappzuku' ] || fail 'Candidate APK belongs to another package.'
candidate_cert="$(certificate "$candidate")" || fail 'Candidate APK signature cannot be verified as a single signer.'
[ "$candidate_cert" = "$expected" ] || fail 'Candidate certificate differs from the pinned expected identity.'

paths="$("${adb_cmd[@]}" shell pm path "$candidate_pkg" 2>/dev/null)" ||
  fail 'Unable to query installed package.'
[ -n "$paths" ] || fail 'ReAppzuku is not installed; this check requires an existing installation.'
base_path=''
while IFS= read -r line; do
  line="$(printf '%s' "$line" | tr -d '\r')"
  if [[ "$line" == package:*/base.apk ]]; then
    [ -z "$base_path" ] || fail 'Ambiguous installed base APK paths.'
    base_path="$(printf '%s' "$line" | sed 's/^package://')"
  fi
done <<< "$paths"
[ -n "$base_path" ] || fail 'No unambiguous installed base APK was returned by PackageManager.'

umask 077
tmp="$(mktemp -d)" || fail 'Cannot create private temporary directory.'
trap 'rm -rf -- "$tmp"' EXIT
"${adb_cmd[@]}" pull "$base_path" "$tmp/installed.apk" >/dev/null 2>&1 ||
  fail 'Could not read the installed APK; cannot safely confirm its signer.'
[ -s "$tmp/installed.apk" ] || fail 'Installed APK was empty.'

installed_identity="$(identity "$tmp/installed.apk")" ||
  fail 'Installed APK package identity/versionCode cannot be verified.'
read -r installed_pkg installed_version <<< "$installed_identity"
[ "$installed_pkg" = "$candidate_pkg" ] || fail 'Installed package identity does not match candidate.'
installed_cert="$(certificate "$tmp/installed.apk")" ||
  fail 'Installed APK signature cannot be verified as a single signer.'
[ "$installed_cert" = "$expected" ] || fail 'Installed certificate differs from candidate / pinned expected identity.'
[ "$installed_version" -lt "$candidate_version" ] ||
  fail 'Candidate versionCode must be strictly higher than the installed versionCode.'

printf 'UPGRADE_PREFLIGHT_PASS package=%s installedVersionCode=%s candidateVersionCode=%s signingCertSHA256=%s\n' \
  "$candidate_pkg" "$installed_version" "$candidate_version" "$expected"
echo 'No app or device settings were modified. Installation, data preservation and rollback remain untested.'
