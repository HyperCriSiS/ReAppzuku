#!/usr/bin/env bash
# Disposable-emulator-only AppOps conflict probe.
# NEVER point this at the product, a user-installed package or physical device.
set -euo pipefail

fixture='com.gree1d.reappzuku.test'
product='com.gree1d.reappzuku'
operation='WAKE_LOCK'
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
user="$(adb shell am get-current-user | tr -d '\r')"
[[ "$sdk" == 24 || "$sdk" == 37 ]] || {
  echo 'APP_OPS_PROBE_REFUSED_UNEXPECTED_SDK' >&2
  exit 1
}
[[ "$user" == 0 ]] || {
  echo 'APP_OPS_PROBE_REFUSED_NONZERO_USER' >&2
  exit 1
}
# Verify the disposable test APK was installed independently of the real app.
adb shell pm path "$fixture" | tr -d '\r' | grep -Eq '^package:/.*\.apk$'
adb shell pm path "$product" | tr -d '\r' | grep -Eq '^package:/.*\.apk$'
[[ "$fixture" != "$product" ]]
uid_dump="$(adb shell dumpsys package "$fixture" | tr -d '\r')"
uids="$(printf '%s\n' "$uid_dump" |
  sed -nE 's/^[[:space:]]*userId=([0-9]+)[[:space:]]*$/\1/p' | sort -u)"
[[ "$uids" =~ ^[0-9]+$ ]] || {
  echo 'APP_OPS_PROBE_REFUSED_UNVERIFIED_TEST_UID' >&2
  exit 1
}
uid="$uids"
echo "APP_OPS_FIXTURE=$fixture UID=$uid SDK=$sdk"

get_all() {
  adb shell cmd appops get --user "$user" "$fixture" 2>&1 | tr -d '\r'
}
get_package() {
  adb shell cmd appops get --user "$user" "$fixture" "$operation" 2>&1 | tr -d '\r'
}
get_uid() {
  adb shell cmd appops get --user "$user" "$uid" "$operation" 2>&1 | tr -d '\r'
}
# Never reset a fixture that has even one unrelated recorded operation.
baseline_all="$(get_all)"
baseline_package="$(get_package)"
baseline_uid="$(get_uid)"
echo "APP_OPS_FIXTURE_BASELINE_ALL_BEGIN"
printf '%s\n' "$baseline_all"
echo "APP_OPS_FIXTURE_BASELINE_ALL_END"
echo "APP_OPS_FIXTURE_BASELINE_PACKAGE_BEGIN"
printf '%s\n' "$baseline_package"
echo "APP_OPS_FIXTURE_BASELINE_PACKAGE_END"
echo "APP_OPS_FIXTURE_BASELINE_UID_BEGIN"
printf '%s\n' "$baseline_uid"
echo "APP_OPS_FIXTURE_BASELINE_UID_END"

# Android 7 may not accept numeric UID queries or the --uid set variant.
# This is a capability finding, NOT positive UID evidence.
if [[ "$sdk" == 24 ]]; then
  echo 'API24_UID_OVERRIDE_PROBE_READ_ONLY_UNSUPPORTED'
  exit 0
fi
# A fresh instrumentation package must have no explicitly recorded AppOps.
# Any other initial condition is an unexpected fixture contamination: fail.
[[ "$baseline_all" == 'No operations.' ]] || {
  echo 'APP_OPS_FIXTURE_REFUSED_NONEMPTY_BASELINE' >&2
  exit 1
}
[[ "$baseline_package" == *'No operations.'* ]] || {
  echo 'APP_OPS_FIXTURE_REFUSED_PACKAGE_BASELINE' >&2
  exit 1
}
[[ "$baseline_uid" == *'No operations.'* ]] || {
  echo 'APP_OPS_FIXTURE_REFUSED_UID_BASELINE' >&2
  exit 1
}
armed=0
cleanup() {
  local status=$?
  trap - EXIT
  if (( armed )); then
    if ! adb shell cmd appops reset --user "$user" "$fixture" > /tmp/appops-fixture-reset.log 2>&1; then
      echo 'APP_OPS_FIXTURE_RESET_FAILED' >&2
      cat /tmp/appops-fixture-reset.log >&2
      status=1
    else
      local after_all after_pkg after_uid
      after_all="$(get_all)" || status=1
      after_pkg="$(get_package)" || status=1
      after_uid="$(get_uid)" || status=1
      if [[ "$after_all" != "$baseline_all" ||
            "$after_pkg" != "$baseline_package" ||
            "$after_uid" != "$baseline_uid" ]]; then
        echo 'APP_OPS_FIXTURE_RESTORE_MISMATCH' >&2
        printf 'after_all=%s\nafter_pkg=%s\nafter_uid=%s\n' \
          "$after_all" "$after_pkg" "$after_uid" >&2
        status=1
      else
        echo 'API37_APP_OPS_FIXTURE_ORIGINAL_STATE_RESTORED'
      fi
    fi
  fi
  exit "$status"
}
trap cleanup EXIT
# Arm cleanup before the first attempt, even if the shell command reports an
# error after partially applying a mode.
armed=1

adb shell cmd appops set --user "$user" "$fixture" "$operation" allow
adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" ignore
conflict="$(get_package)"
echo 'API37_UID_CONFLICT_RAW_BEGIN'
printf '%s\n' "$conflict"
echo 'API37_UID_CONFLICT_RAW_END'
# Assert actual *separate* package and UID rows, not an implicit default hint.
grep -Eq '(^|[[:space:]])Uid mode: WAKE_LOCK: ignore([;[:space:]]|$)' <<< "$conflict"
grep -Eq '^WAKE_LOCK: allow([;[:space:]]|$)' <<< "$conflict"
echo 'API37_UID_IGNORE_OVERRIDES_PACKAGE_ALLOW_OBSERVED'

# Distinguish explicitly requested changes without claiming that DEFAULT is a
# persistently recorded row on every Android build.
adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" allow
allow_output="$(get_package)"
echo 'API37_UID_ALLOW_RAW_BEGIN'
printf '%s\n' "$allow_output"
echo 'API37_UID_ALLOW_RAW_END'
! grep -Fq 'Uid mode: WAKE_LOCK: ignore' <<< "$allow_output"
adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" default
default_output="$(get_package)"
echo 'API37_UID_DEFAULT_RAW_BEGIN'
printf '%s\n' "$default_output"
echo 'API37_UID_DEFAULT_RAW_END'
! grep -Fq 'Uid mode: WAKE_LOCK: ignore' <<< "$default_output"
echo 'API37_UID_ALLOW_DEFAULT_TRANSITIONS_OBSERVED'
