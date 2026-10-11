#!/usr/bin/env bash
# Read-only, disposable emulator AppOps output inventory. No appops SET/RESET.
set -euo pipefail
package=com.gree1d.reappzuku
target_user="$(adb shell am get-current-user 2>/dev/null | tr -d '\r' || true)"
uid_line="$(adb shell pm list packages -U "$package" | tr -d '\r' || true)"
uid="$(printf '%s\n' "$uid_line" | sed -nE "s/^package:com\\.gree1d\\.reappzuku uid:([0-9]+)$/\\1/p" | head -n 1)"
if [[ ! "$uid" =~ ^[0-9]+$ ]]; then
  echo 'READONLY_APPOPS_IDENTITY_UNKNOWN: no unique package UID'
  exit 0
fi
if [[ ! "$target_user" =~ ^[0-9]+$ ]]; then
  echo 'READONLY_APPOPS_USER_UNKNOWN: not asserting current-user match'
  exit 0
fi
echo "READONLY_APPOPS_PACKAGE=$package UID=$uid USER=$target_user"
for operation in WAKE_LOCK RUN_IN_BACKGROUND; do
  for target in "$package" "$uid"; do
    label=PACKAGE
    [ "$target" = "$uid" ] && label=UID
    result=''
    if result="$(adb shell cmd appops get --user "$target_user" "$target" "$operation" 2>&1 | tr -d '\r')"; then
      if [ "${#result}" -gt 4096 ]; then
        echo "READONLY_APPOPS_${label}_${operation}=OUTPUT_TOO_LARGE_UNKNOWN"
      else
        echo "READONLY_APPOPS_${label}_${operation}_BEGIN"
        printf '%s\n' "$result"
        echo "READONLY_APPOPS_${label}_${operation}_END"
      fi
    else
      echo "READONLY_APPOPS_${label}_${operation}=UNAVAILABLE_UNKNOWN"
    fi
  done
done
echo READONLY_APPOPS_DIAGNOSTIC_COMPLETE
