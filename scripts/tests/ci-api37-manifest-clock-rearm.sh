#!/usr/bin/env bash
# Disposable Android17/API37 proof: system-broadcast-driven manifest receiver
# produces a real pending RTC alarm from a fixture that was NOT pre-scheduled.
set -euo pipefail
CLASS='com.gree1d.reappzuku.core.Api37ManifestClockRearmInstrumentationTest'
original_zone="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
original_auto="$(adb shell settings get global auto_time_zone | tr -d '\r')"
test -n "$original_zone"
instrument_pid=''
restore() {
  local result=$?
  trap - EXIT
  if [ -n "$instrument_pid" ]; then
    kill "$instrument_pid" >/dev/null 2>&1 || true
    wait "$instrument_pid" >/dev/null 2>&1 || true
  fi
  if ! adb shell cmd alarm set-timezone "$original_zone" >>/tmp/api37-real-clock-rearm-restore.txt 2>&1; then
    echo 'CRITICAL: failed to restore original emulator timezone' >&2
    result=1
  fi
  if [ "$original_auto" = null ]; then
    adb shell settings delete global auto_time_zone >>/tmp/api37-real-clock-rearm-restore.txt 2>&1 || result=1
  else
    adb shell settings put global auto_time_zone "$original_auto" >>/tmp/api37-real-clock-rearm-restore.txt 2>&1 || result=1
  fi
  if [ "$(adb shell getprop persist.sys.timezone | tr -d '\r')" != "$original_zone" ]; then
    echo 'CRITICAL: timezone restore mismatch' >&2
    result=1
  fi
  exit "$result"
}
trap restore EXIT

# This step runs on a fresh disposable emulator only. No clock wall-time
# changes, root, Shizuku, user package, or privileged mutation.
adb shell settings put global auto_time_zone 0
adb shell cmd alarm set-timezone Etc/UTC
test "$(adb shell getprop persist.sys.timezone | tr -d '\r')" = 'Etc/UTC'
INSTRUMENTATION="$(adb shell pm list instrumentation | tr -d '\r' |
  sed -n 's/^instrumentation:\([^ ]*\) (target=com.gree1d.reappzuku)$/\1/p' |
  head -n 1)"
test -n "$INSTRUMENTATION"
adb logcat -c
adb shell am instrument -w -r \
  -e ci_manifest_clock_rearm verify \
  -e ci_expected_timezone Pacific/Honolulu \
  -e class "$CLASS" "$INSTRUMENTATION" \
  >/tmp/api37-real-clock-rearm-test.txt 2>&1 &
instrument_pid=$!
ready=0
for attempt in $(seq 1 50); do
  adb logcat -d -s ReAppzukuClockRearm:I '*:S' \
    >/tmp/api37-real-clock-rearm-logcat.txt 2>&1 || true
  if grep -Fq 'READY: fixture stored, no RTC alarm, observer registered' \
      /tmp/api37-real-clock-rearm-logcat.txt; then
    ready=1
    break
  fi
  if ! kill -0 "$instrument_pid" 2>/dev/null; then break; fi
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  cat /tmp/api37-real-clock-rearm-test.txt
  echo 'Fixture not ready; real system change withheld' >&2
  exit 1
fi
adb shell cmd alarm set-timezone Pacific/Honolulu
wait "$instrument_pid"
instrument_pid=''
cat /tmp/api37-real-clock-rearm-test.txt
grep -Fq 'OK (1 test)' /tmp/api37-real-clock-rearm-test.txt
! grep -Fq 'FAILURES!!!' /tmp/api37-real-clock-rearm-test.txt
adb logcat -d -s ReAppzukuClockRearm:I '*:S' \
  >/tmp/api37-real-clock-rearm-logcat.txt
grep -Fq 'ALARM_ARMED: real timezone broadcast caused production RTC rearm' \
  /tmp/api37-real-clock-rearm-logcat.txt
test "$(adb shell getprop persist.sys.timezone | tr -d '\r')" = 'Pacific/Honolulu'
echo 'API37_MANIFEST_TIMEZONE_RTC_REARM_PASS'
