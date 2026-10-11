#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
source scripts/tests/lib/ci-readonly-provenance.sh
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export READONLY_FIXTURE_COUNT="$tmp/count"

fixture_command() {
  local n
  n="$(cat "$READONLY_FIXTURE_COUNT")"
  n="${n:-0}"
  printf '%s\n' "$((n+1))" >"$READONLY_FIXTURE_COUNT"
  if ((n==0)); then printf '%s' "$READONLY_FIRST"; else printf '%s' "$READONLY_SECOND"; fi
}

check_pair() {
  local name="$1" max="$2" kind="$3" op="$4" first="$5" second="$6" expected="$7"
  export READONLY_FIRST="$first" READONLY_SECOND="$second"
  printf 0 >"$READONLY_FIXTURE_COUNT"
  local result
  result="$(readonly_capture_pair "$name" "$max" "$kind" "$op" fixture_command)"
  grep -Fqx "$name=$expected" <<< "$result" || {
    printf 'Unexpected pair result: %s\n' "$result" >&2
    return 1
  }
  if [[ "$expected" == RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY ]]; then
    grep -Fqx "${name}_BEGIN" <<< "$result"
    grep -Fqx "${name}_END" <<< "$result"
  else
    ! grep -Fq "${name}_BEGIN" <<< "$result"
  fi
  [[ "$(cat "$READONLY_FIXTURE_COUNT")" == 2 ]]
}
APP=READONLY_APPOPS_PACKAGE_WAKE_LOCK
IDLE=READONLY_DEVICEIDLE_INVENTORY
check_pair "$APP" 4096 appops WAKE_LOCK $'No operations.\nDefault mode: allow' \
  $'No operations.\nDefault mode: allow' RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY
check_pair "$APP" 4096 appops WAKE_LOCK 'Uid mode: WAKE_LOCK: ignore' \
  'Uid mode: WAKE_LOCK: ignore' RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY
check_pair "$APP" 4096 appops WAKE_LOCK 'WAKE_LOCK: allow' 'WAKE_LOCK: ignore' \
  CHANGED_DURING_READ_UNKNOWN
check_pair "$APP" 4096 appops WAKE_LOCK $'No operations.\nWarning: permission denied' \
  $'No operations.\nWarning: permission denied' UNRECOGNIZED_OUTPUT_UNKNOWN
check_pair "$APP" 4096 appops WAKE_LOCK 'Error: unsupported operation' \
  'Error: unsupported operation' UNRECOGNIZED_OUTPUT_UNKNOWN
check_pair "$APP" 4096 appops WAKE_LOCK '' '' EMPTY_UNKNOWN
check_pair "$APP" 10 appops WAKE_LOCK 'WAKE_LOCK: allow' 'WAKE_LOCK: allow' \
  OUTPUT_TOO_LARGE_UNKNOWN
check_pair "$IDLE" 8192 deviceidle '' \
  $'system-excidle,com.android.backup,1000\nsystem,com.android.phone,1001' \
  $'system-excidle,com.android.backup,1000\nsystem,com.android.phone,1001' \
  RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY
check_pair "$IDLE" 8192 deviceidle '' \
  'system,com.android.phone,1001' 'user,com.android.phone,1001' \
  CHANGED_DURING_READ_UNKNOWN
for bad in \
  $'system,com.android.phone,1001\nWarning: unknown' \
  $'system,com.android.phone,1001\nuser,com.android.phone,1002' \
  $'system,com.android.phone,1001\nsystem,com.android.phone,1001' \
  'system,com.android.phone,9999999999999999' \
  'system,com.android.phone,0'; do
  check_pair "$IDLE" 8192 deviceidle '' "$bad" "$bad" UNRECOGNIZED_OUTPUT_UNKNOWN
done

# End-to-end fake ADB proves no mutation commands and no raw evidence after
# installed UID drift. It also exercises the API24 dump-only UID fallback.
mkdir -p "$tmp/bin"
cat > "$tmp/bin/adb" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  "shell getprop ro.build.version.sdk")
    if [[ "${MOCK_SCENARIO:-}" == api24 || "${MOCK_SCENARIO:-}" == api24blank ]]; then echo 24; else echo 37; fi ;;
  "shell getprop ro.kernel.qemu")
    if [[ "${MOCK_SCENARIO:-}" == physical ]]; then echo 0; else echo 1; fi ;;
  "shell am get-current-user") echo 0 ;;
  "shell pm list packages -U com.gree1d.reappzuku")
    if [[ "${MOCK_SCENARIO:-}" == api24 ]]; then
      echo package:com.gree1d.reappzuku
    elif [[ "${MOCK_SCENARIO:-}" == api24blank ]]; then
      :
    else
      n="$(cat "$MOCK_UID_READS")"
      n="${n:-0}"
      printf '%s\n' "$((n+1))" >"$MOCK_UID_READS"
      # pm filter also matches the AndroidTest package. Only the exact
      # product package row may establish its UID.
      echo 'package:com.gree1d.reappzuku.test uid:10421'
      if [[ "${MOCK_SCENARIO:-}" == drift && "$n" == 1 ]]; then
        echo 'package:com.gree1d.reappzuku uid:10343'
      else
        echo 'package:com.gree1d.reappzuku uid:10342'
      fi
      if [[ "${MOCK_SCENARIO:-}" == duplicate ]]; then
        echo 'package:com.gree1d.reappzuku uid:10345'
      fi
    fi ;;
  "shell pm path com.gree1d.reappzuku") echo 'package:/data/app/com.gree1d.reappzuku/base.apk' ;;
  "shell dumpsys package com.gree1d.reappzuku") echo 'userId=10342' ;;
  "shell cmd deviceidle whitelist")
    if [[ "${MOCK_SCENARIO:-}" == warning ]]; then
      echo 'Permission denied'
    else
      echo 'system,com.android.phone,1001'
      echo 'user,com.example.other,10300'
    fi ;;
  "shell cmd appops get --user 0 "*)
    operation="${*: -1}"
    if [[ "${MOCK_SCENARIO:-}" == unavailable ]]; then
      echo 'shell backend unavailable' >&2
      exit 1
    fi
    if [[ "$operation" == WAKE_LOCK || "$operation" == RUN_IN_BACKGROUND ]]; then
      printf 'No operations.\nDefault mode: allow\n'
    else
      exit 2
    fi ;;
  *)
    printf 'MOCK_REJECTED_COMMAND: %s\n' "$*" >&2
    exit 99 ;;
esac
MOCK
chmod +x "$tmp/bin/adb"
export MOCK_UID_READS="$tmp/uid_count"
probe="scripts/tests/ci-appops-readonly-observation.sh"
run_probe() {
  local scenario="$1"
  printf 0 >"$MOCK_UID_READS"
  MOCK_SCENARIO="$scenario" PATH="$tmp/bin:$PATH" bash "$probe"
}
out="$(run_probe stable)"
grep -Fqx 'READONLY_APPOPS_DIAGNOSTIC_COMPLETE' <<< "$out"
grep -Fqx 'READONLY_DEVICEIDLE_INVENTORY=RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY' <<< "$out"
grep -Fqx 'READONLY_APPOPS_PACKAGE_WAKE_LOCK=RAW_DOUBLE_READ_MATCH_DIAGNOSTIC_ONLY' <<< "$out"
out="$(run_probe drift)"
grep -Fqx 'READONLY_APPOPS_IDENTITY_CHANGED_DURING_READ_UNKNOWN' <<< "$out"
! grep -Fq '_BEGIN' <<< "$out"
out="$(run_probe warning)"
grep -Fqx 'READONLY_DEVICEIDLE_INVENTORY=UNRECOGNIZED_OUTPUT_UNKNOWN' <<< "$out"
out="$(run_probe unavailable)"
grep -Fqx 'READONLY_APPOPS_PACKAGE_WAKE_LOCK=UNAVAILABLE_UNKNOWN' <<< "$out"
out="$(run_probe physical)"
grep -Fqx 'READONLY_APPOPS_ENVIRONMENT_UNKNOWN' <<< "$out"
! grep -Fq '_BEGIN' <<< "$out"
out="$(run_probe api24)"
grep -Fqx 'READONLY_APPOPS_DIAGNOSTIC_COMPLETE' <<< "$out"
grep -Fqx 'READONLY_APPOPS_PACKAGE=com.gree1d.reappzuku UID=10342 USER=0' <<< "$out"
out="$(run_probe api24blank)"
grep -Fqx 'READONLY_APPOPS_DIAGNOSTIC_COMPLETE' <<< "$out"
grep -Fqx 'READONLY_APPOPS_PACKAGE=com.gree1d.reappzuku UID=10342 USER=0' <<< "$out"
out="$(run_probe duplicate)"
grep -Fqx 'READONLY_APPOPS_IDENTITY_UNKNOWN' <<< "$out"
! grep -Fq '_BEGIN' <<< "$out"
echo READONLY_SHELL_PROVENANCE_FIXTURE_PASS
