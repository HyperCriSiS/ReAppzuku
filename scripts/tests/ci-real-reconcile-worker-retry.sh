#!/usr/bin/env bash
set -euo pipefail

# Dedicated disposable API24/API37 debug-emulator gate. No reset/force-stop,
# installation, external privilege, or production policy preference mutation.
PKG='com.gree1d.reappzuku'
CLASS='com.gree1d.reappzuku.core.Api24Api37RealReconcileWorkerRetryInstrumentationTest'
INSTRUMENTATION="$(
  adb shell pm list instrumentation | tr -d '\r' |
    sed -n 's/^instrumentation:\([^ ]*\) (target=com.gree1d.reappzuku)$/\1/p' |
    head -n 1
)"
test -n "$INSTRUMENTATION"

adb shell am instrument -w -r -e ci_real_reconcile_retry_mode verify \
  -e class "$CLASS#startActualWorkerAndObserveFirstRetry" "$INSTRUMENTATION" |
  tee /tmp/reappzuku-real-reconcile-before.txt
grep -Fq 'OK (1 test)' /tmp/reappzuku-real-reconcile-before.txt
! grep -Fq 'FAILURES!!!' /tmp/reappzuku-real-reconcile-before.txt
uuid="$(sed -nE 's/^INSTRUMENTATION_STATUS: REAPPZUKU_REAL_RECONCILE_ID=([0-9a-f-]{36})$/\1/p' /tmp/reappzuku-real-reconcile-before.txt | tail -n 1)"
[[ "$uuid" =~ ^[0-9a-f-]{36}$ ]]

# Start an actual main process and SIGKILL it as its own debug app UID.
adb shell am start -W -n "$PKG/com.gree1d.reappzuku.ui.MainActivity" \
  > /tmp/reappzuku-real-reconcile-launch-before.txt 2>&1
old_pid=''
for attempt in $(seq 1 15); do
  old_pid="$(adb shell pidof -s "$PKG" | tr -d '\r' || true)"
  if [[ "$old_pid" =~ ^[0-9]+$ ]]; then break; fi
  sleep 1
done
[[ "$old_pid" =~ ^[0-9]+$ ]]
echo "REAPPZUKU_REAL_RECONCILE_PRE_KILL_PID=$old_pid"
adb shell "run-as $PKG /system/bin/kill -9 $old_pid"

gone=0
for attempt in $(seq 1 20); do
  current="$(adb shell pidof -s "$PKG" | tr -d '\r' || true)"
  if [ "$current" != "$old_pid" ]; then gone=1; break; fi
  sleep 1
done
test "$gone" -eq 1
echo 'REAPPZUKU_REAL_RECONCILE_PID_TERMINATED'

adb shell am start -W -n "$PKG/com.gree1d.reappzuku.ui.MainActivity" \
  > /tmp/reappzuku-real-reconcile-launch-after.txt 2>&1
new_pid=''
for attempt in $(seq 1 20); do
  new_pid="$(adb shell pidof -s "$PKG" | tr -d '\r' || true)"
  if [[ "$new_pid" =~ ^[0-9]+$ ]] && [ "$new_pid" != "$old_pid" ]; then break; fi
  sleep 1
done
[[ "$new_pid" =~ ^[0-9]+$ ]]
test "$new_pid" != "$old_pid"
echo "REAPPZUKU_REAL_RECONCILE_NEW_PID=$new_pid"

adb shell am instrument -w -r -e ci_real_reconcile_retry_mode verify \
  -e class "$CLASS#verifyActualWorkerRecoveredAfterProcessRestart" \
  -e expected_uuid "$uuid" "$INSTRUMENTATION" |
  tee /tmp/reappzuku-real-reconcile-after.txt
grep -Fq 'OK (1 test)' /tmp/reappzuku-real-reconcile-after.txt
! grep -Fq 'FAILURES!!!' /tmp/reappzuku-real-reconcile-after.txt
grep -Fq 'INSTRUMENTATION_STATUS: REAPPZUKU_REAL_RECONCILE_RETRY_SUCCESS=yes' /tmp/reappzuku-real-reconcile-after.txt
echo 'REAPPZUKU_ACTUAL_RECONCILE_WORKER_RETRIED_AFTER_PROCESS_DEATH'
