#!/usr/bin/env bash
# Disposable-emulator-only, test-package AppOps conflict proof.
# Never use this on physical hardware, the product or a user-managed package.
set -euo pipefail

sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
qemu="$(adb shell getprop ro.kernel.qemu | tr -d '\r')"
user="$(adb shell am get-current-user | tr -d '\r')"
[[ "$sdk" == 24 || "$sdk" == 37 ]] || {
  echo APP_OPS_PROBE_REFUSED_UNEXPECTED_SDK >&2
  exit 1
}
[[ "$qemu" == 1 && "$user" == 0 ]] || {
  echo APP_OPS_PROBE_REFUSED_NON_EMULATOR_OR_USER >&2
  exit 1
}
fixture='com.gree1d.reappzuku.test'
if [[ "$sdk" == 37 ]]; then
  fixture='com.reappzuku.securityprobe'
fi
product='com.gree1d.reappzuku'
operation='WAKE_LOCK'

# Both packages must exist, and the fixture must be separate from the product.
adb shell pm path "$fixture" | tr -d '\r' | grep -Eq '^package:/.*\.apk$'
adb shell pm path "$product" | tr -d '\r' | grep -Eq '^package:/.*\.apk$'
[[ "$fixture" != "$product" ]]

uid_line="$(adb shell pm list packages -U "$fixture" 2>/dev/null | tr -d '\r' || true)"
uids="$(printf '%s\n' "$uid_line" |
  sed -nE 's/^package:[a-zA-Z0-9_.]+ uid:([0-9]+)$/\1/p' | sort -u)"
if [[ ! "$uids" =~ ^[0-9]+$ ]]; then
  dump="$(adb shell dumpsys package "$fixture" | tr -d '\r')"
  uids="$(printf '%s\n' "$dump" |
    sed -nE 's/^[[:space:]]*userId=([0-9]+)([[:space:]].*)?$/\1/p' | sort -u)"
fi
[[ "$uids" =~ ^[0-9]+$ ]] || {
  echo APP_OPS_PROBE_REFUSED_UNVERIFIED_TEST_UID >&2
  exit 1
}
uid="$uids"

if [[ "$sdk" == 37 ]]; then
  # A UID-scoped change would affect every package sharing this UID.
  # Require exactly one fixture package in the entire emulator listing.
  all_packages="$(adb shell pm list packages -U | tr -d '\r')"
  same_uid="$(printf '%s\n' "$all_packages" | grep -E " uid:$uid$" || true)"
  [[ "$same_uid" == "package:$fixture uid:$uid" ]] || {
    echo APP_OPS_PROBE_REFUSED_SHARED_OR_UNKNOWN_UID >&2
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

# Preserve the complete existing package AND UID output as a diagnostic.
baseline_all="$(get_all)"
baseline_package="$(get_package)"
baseline_uid="$(get_uid || true)"
echo APP_OPS_FIXTURE_BASELINE_ALL_BEGIN
printf '%s\n' "$baseline_all"
echo APP_OPS_FIXTURE_BASELINE_ALL_END
echo APP_OPS_FIXTURE_BASELINE_PACKAGE_BEGIN
printf '%s\n' "$baseline_package"
echo APP_OPS_FIXTURE_BASELINE_PACKAGE_END
echo APP_OPS_FIXTURE_BASELINE_UID_BEGIN
printf '%s\n' "$baseline_uid"
echo APP_OPS_FIXTURE_BASELINE_UID_END

if [[ "$sdk" == 24 ]]; then
  # Android 7 in this emulator does not have a reliable numeric UID get.
  # No AppOps set or reset is permitted on this API.
  echo API24_UID_OVERRIDE_PROBE_READ_ONLY_UNSUPPORTED
  exit 0
fi

# Android 17 installs dozens of unrelated UID-mode IGNORE rows. They must
# remain untouched. Only WAKE_LOCK may be initially absent on both layers.
[[ "$baseline_package" == $'No operations.\nDefault mode: allow' ]] || {
  echo APP_OPS_PROBE_REFUSED_EXISTING_PACKAGE_WAKE_LOCK >&2
  exit 1
}
[[ "$baseline_uid" == $'No operations.\nDefault mode: allow' ]] || {
  echo APP_OPS_PROBE_REFUSED_EXISTING_UID_WAKE_LOCK >&2
  exit 1
}

armed=0
cleanup() {
  local status=$?
  trap - EXIT
  if (( armed )); then
    local restored=1
    # WAKE_LOCK has platform default ALLOW. Restoring UID ALLOW removes
    # the temporary UID entry; PACKAGE MODE IS NEVER CHANGED.
    # No package-wide reset: it would erase unrelated preexisting UID ops.
    adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" allow \
      >/tmp/reappzuku-appops-target-restore.txt 2>&1 || restored=0
    if (( restored == 0 )); then
      echo APP_OPS_FIXTURE_TARGETED_RESTORE_FAILED >&2
      cat /tmp/reappzuku-appops-target-restore.txt >&2
      status=1
    else
      local after_all after_package after_uid
      after_all="$(get_all)" || status=1
      after_package="$(get_package)" || status=1
      after_uid="$(get_uid)" || status=1
      if [[ "$after_all" != "$baseline_all" ||
            "$after_package" != "$baseline_package" ||
            "$after_uid" != "$baseline_uid" ]]; then
        echo APP_OPS_FIXTURE_RESTORE_MISMATCH >&2
        printf 'after_all=%s\nafter_package=%s\nafter_uid=%s\n' \
          "$after_all" "$after_package" "$after_uid" >&2
        status=1
      else
        echo API37_APP_OPS_FIXTURE_ORIGINAL_STATE_RESTORED
      fi
    fi
  fi
  exit "$status"
}
trap cleanup EXIT
# Arm before attempting the first change, even if the command partially fails.
armed=1

# Package WAKE_LOCK remains implicit DEFAULT=ALLOW; change ONLY UID scope.
adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" ignore
conflict="$(get_package)"
echo API37_UID_CONFLICT_RAW_BEGIN
printf '%s\n' "$conflict"
echo API37_UID_CONFLICT_RAW_END
# Require an explicit UID override and only the IMPLICIT package fallback.
grep -Eq '(^|[[:space:]])Uid mode: WAKE_LOCK: ignore([;[:space:]]|$)' <<< "$conflict"
grep -Fq 'Default mode: allow' <<< "$conflict"
! grep -Eq '^WAKE_LOCK: ' <<< "$conflict"
echo API37_UID_IGNORE_OVERRIDES_IMPLICIT_PACKAGE_ALLOW_OBSERVED

# UID ALLOW and AppOps DEFAULT are distinct requested modes.
adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" allow
allow_output="$(get_package)"
echo API37_UID_ALLOW_RAW_BEGIN
printf '%s\n' "$allow_output"
echo API37_UID_ALLOW_RAW_END
! grep -Fq 'Uid mode: WAKE_LOCK: ignore' <<< "$allow_output"

adb shell cmd appops set --user "$user" --uid "$fixture" "$operation" default
default_output="$(get_package)"
echo API37_UID_DEFAULT_RAW_BEGIN
printf '%s\n' "$default_output"
echo API37_UID_DEFAULT_RAW_END
! grep -Fq 'Uid mode: WAKE_LOCK: ignore' <<< "$default_output"
echo API37_UID_ALLOW_DEFAULT_TRANSITIONS_OBSERVED