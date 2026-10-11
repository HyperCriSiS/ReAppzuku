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
  local listing row listed_package uid='' hits=0 dump candidates package_path
  listing="$(adb shell pm list packages -U "$package" 2>/dev/null | tr -d '\r')" || listing=''
  # pm filtering also returns the instrumentation package. Validate every row,
  # and require no duplicate/conflicting records for the exact target name.
  if [[ -n "$listing" ]]; then
    while IFS= read -r row || [[ -n "$row" ]]; do
      if [[ "$row" =~ ^package:([A-Za-z0-9_.]+)[[:space:]]uid:([0-9]+)$ ]]; then
        listed_package="${BASH_REMATCH[1]}"
        if [[ "$listed_package" == "$package" ]]; then
          ((hits+=1))
          uid="${BASH_REMATCH[2]}"
        fi
      elif [[ "$row" =~ ^package:([A-Za-z0-9_.]+)$ ]]; then
        listed_package="${BASH_REMATCH[1]}"
        if [[ "$listed_package" == "$package" ]]; then ((hits+=1)); fi
      else
        return 1
      fi
    done <<< "$listing"
  fi
  ((hits<=1)) || return 1
  if [[ -z "$uid" ]]; then
    # API24 can omit the UID or have no -U output at all. Require independent
    # installed APK path and one unambiguous numeric dumpsys userId.
    package_path="$(adb shell pm path "$package" 2>/dev/null | tr -d '\r')" || return 1
    [[ "$package_path" =~ ^package:/.*\.apk$ ]] || return 1
    dump="$(adb shell dumpsys package "$package" 2>/dev/null | tr -d '\r')" || return 1
    candidates="$(printf '%s\n' "$dump" |
      sed -nE 's/^[[:space:]]*userId=([0-9]+)[[:space:]]*$/\1/p' | sort -u)"
    [[ "$candidates" =~ ^[0-9]+$ ]] || return 1
    uid="$candidates"
  fi
  [[ "$uid" =~ ^[0-9]+$ ]] || return 1
  ((${#uid}<=10)) || return 1
  ((10#$uid>0 && 10#$uid<=2147483647)) || return 1
  printf '%s' "$uid"
}

user="$(read_user || true)"
uid="$(read_uid || true)"
if [[ ! "$uid" =~ ^[0-9]+$ ]]; then
  echo READONLY_APPOPS_IDENTITY_UNKNOWN
  exit 0
fi
if [[ "$user" != 0 ]]; then
  echo READONLY_APPOPS_USER_UNKNOWN
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
