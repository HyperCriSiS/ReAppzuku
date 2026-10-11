#!/usr/bin/env bash
# Disposable-emulator, read-only AppOps and DeviceIdle observations.
# Stable raw text is diagnostic ONLY: no AppOps set/reset or restore authority.
set -euo pipefail
export LC_ALL=C
source "$(dirname "${BASH_SOURCE[0]}")/lib/ci-readonly-provenance.sh"

package=com.gree1d.reappzuku
sdk="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
qemu="$(adb shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' || true)"
if [[ "$qemu" != 1 || ( "$sdk" != 24 && "$sdk" != 37 ) ]]; then
  echo READONLY_APPOPS_ENVIRONMENT_UNKNOWN
  exit 0
fi

read_user() {
  local value
  value="$(adb shell am get-current-user 2>/dev/null | tr -d '\r')" || return 1
  [[ "$value" =~ ^[0-9]+$ ]] || return 1
  printf '%s' "$value"
}

# No first-match UID guess, including on older pm output. A second UID
# observation after data capture detects the identity changes observable here.
read_uid() {
  local listing dump candidates uid
  listing="$(adb shell pm list packages -U "$package" 2>/dev/null | tr -d '\r')" || return 1
  if [[ "$listing" =~ ^package:com\.gree1d\.reappzuku[[:space:]]uid:([0-9]+)$ ]]; then
    uid="${BASH_REMATCH[1]}"
  elif [[ "$listing" == "package:$package" ]]; then
    # Old Android can omit -U. The fallback accepts one unique numeric value,
    # not arbitrary command output or an inferred package name.
    dump="$(adb shell dumpsys package "$package" 2>/dev/null | tr -d '\r')" || return 1
    candidates="$(printf '%s\n' "$dump" |
      sed -nE 's/^[[:space:]]*userId=([0-9]+)[[:space:]]*$/\1/p' | sort -u)"
    [[ "$candidates" =~ ^[0-9]+$ ]] || return 1
    uid="$candidates"
  else
    return 1
  fi
  [[ "$uid" =~ ^[0-9]+$ ]] || return 1
  ((${#uid}<=10)) || return 1
  ((10#$uid>0 && 10#$uid<=2147483647)) || return 1
  printf '%s' "$uid"
}

user="$(read_user || true)"
uid="$(read_uid || true)"
if [[ "$user" != 0 || ! "$uid" =~ ^[0-9]+$ ]]; then
  echo READONLY_APPOPS_IDENTITY_OR_USER_UNKNOWN
  exit 0
fi

# Buffer all observations until the independent postflight UID/user check:
# a changed identity cannot leak earlier raw records as "stable" evidence.
observations="$(
  for operation in WAKE_LOCK RUN_IN_BACKGROUND; do
    readonly_capture_pair "READONLY_APPOPS_PACKAGE_${operation}" 4096 appops "$operation" \
      adb shell cmd appops get --user "$user" "$package" "$operation"
    readonly_capture_pair "READONLY_APPOPS_UID_${operation}" 4096 appops "$operation" \
      adb shell cmd appops get --user "$user" "$uid" "$operation"
  done
  readonly_capture_pair READONLY_DEVICEIDLE_INVENTORY 8192 deviceidle '' \
    adb shell cmd deviceidle whitelist
)"
final_user="$(read_user || true)"
final_uid="$(read_uid || true)"
if [[ "$final_user" != "$user" || "$final_uid" != "$uid" ]]; then
  echo READONLY_APPOPS_IDENTITY_CHANGED_DURING_READ_UNKNOWN
  exit 0
fi
echo "READONLY_APPOPS_PACKAGE=$package UID=$uid USER=$user"
printf '%s\n' "$observations"
echo READONLY_APPOPS_DIAGNOSTIC_COMPLETE
