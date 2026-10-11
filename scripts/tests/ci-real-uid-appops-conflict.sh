#!/usr/bin/env bash
# Disposable-emulator-only AppOps conflict probe.
# NEVER point this at the product, a user-installed package or physical device.
set -euo pipefail

fixture='com.gree1d.reappzuku.test'
product='com.gree1d.reappzuku'
operation='WAKE_LOCK'
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$sdk" == 37 ]]; then
  fixture='com.reappzuku.securityprobe'
fi
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
uid_line="$(adb shell pm list packages -U "$fixture" 2>/dev/null | tr -d '\r' || true)"
uids="$(printf '%s\n' "$uid_line" |
  sed -nE 's/^package:[a-zA-Z0-9_.]+ uid:([0-9]+)$/\1/p' | sort -u)"
if [[ ! "$uids" =~ ^[0-9]+$ ]]; then
  # Some SDK images omit -U, and some dumpsys variants append fields to
  # their userId line. Require one unambiguous numeric value either way.
  uid_dump="$(adb shell dumpsys package "$fixture" | tr -d '\r')"
  uids="$(printf '%s\n' "$uid_dump" |
    sed -nE 's/^[[:space:]]*userId=([0-9]+)([[:space:]].*)?$/\1/p' | sort -u)"
fi
[[ "$uids" =~ ^[0-9]+$ ]] || {
  echo "APP_OPS_PROBE_REFUSED_UNVERIFIED_TEST_UID listing=$uid_line" >&2
  exit 1
}
uid="$uids"
if [[ "$sdk" == 37 ]]; then
  # The fixture must not share its UID with the product or any other app:
  # resetting the UID layer must not affect an unrelated package.
  all_packages="$(adb shell pm list packages -U | tr -d '\r')"
  same_uid="$(printf '%s\n' "$all_packages" | grep -E " uid:$uid$" || true)"
  [[ "$same_uid" == "package:$fixture uid:$uid" ]] || {
    echo "APP_OPS_PROBE_REFUSED_SHARED_OR_UNKNOWN_UID=$uid" >&2
    exit 1
  }
fi
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
# Capture ALL existing UID/package modes for exact post-test comparison.
baseline_all="$(get_all)"
baseline_package="$(get_package)"
baseline_uid="$(get_uid || true)"
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
# Android grants/denies many unrelated UID AppOps even to minimal apps.
# Never reset the entire package: require ONLY the target WAKE_LOCK operation
# to be initially unset at BOTH layers before changing that single operation.
[[ "$baseline_package" == 
armed=0
cleanup() {
  local status=$?
  trap - EXIT
  if (( armed )); then
    local cleanup_ok=1
    # Restore ONLY the test-owned operation. A broad package reset would
    # erase unrelated system-provisioned UID permission modes.
    adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" default \
      >/tmp/appops-fixture-reset.log 2>&1 || cleanup_ok=0
    adb shell cmd appops set --user "$user" "$fixture" "$operation" default \
      >>/tmp/appops-fixture-reset.log 2>&1 || cleanup_ok=0
    if (( cleanup_ok == 0 )); then
      echo 'APP_OPS_FIXTURE_TARGETED_RESTORE_FAILED' >&2
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
echo 'API37_UID_ALLOW_DEFAULT_TRANSITIONS_OBSERVED'No operations.\nDefault mode: allow' ]] || {
  echo 'APP_OPS_FIXTURE_REFUSED_EXISTING_PACKAGE_WAKE_LOCK' >&2
  exit 1
}
[[ "$baseline_uid" == 
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
echo 'API37_UID_ALLOW_DEFAULT_TRANSITIONS_OBSERVED'No operations.\nDefault mode: allow' ]] || {
  echo 'APP_OPS_FIXTURE_REFUSED_EXISTING_UID_WAKE_LOCK' >&2
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