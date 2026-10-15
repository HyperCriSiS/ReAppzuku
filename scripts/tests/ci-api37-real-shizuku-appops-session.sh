#!/usr/bin/env bash
# Explicit manual/CI-only Android 17 real-Shizuku read-only AppOps session fault gate.
# Runs exclusively on a disposable AOSP emulator; no AppOps set/reset, journal or scheduler.
set -euo pipefail

PRODUCT=com.gree1d.reappzuku
TEST=com.gree1d.reappzuku.core.RealShizukuPinnedAppOpsSessionInstrumentationTest
SHIZUKU_PKG=moe.shizuku.privileged.api
APK=/tmp/shizuku-v13.6.0.r1086.2650830c-release.apk
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 37
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test -n "$(adb shell pm path "$PRODUCT")"
test -n "$(adb shell pm path com.reappzuku.securityprobe)"

# The exact official Shizuku APK/digest were already used for a successful API37
# UserService test in this repo (run 34316502181). Never accept unpinned installers.
curl --fail --location --http1.1 --proto '=https' --tlsv1.2 --retry 3 \
  'https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk' \
  -o "$APK"
echo '6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f  /tmp/shizuku-v13.6.0.r1086.2650830c-release.apk' | sha256sum -c -
adb install -r "$APK" > /tmp/rz-shizuku-install.txt

SHIZUKU_APK_PATH="$(adb shell pm path "$SHIZUKU_PKG" | tr -d '\r' | sed -n '1s/^package://p')"
test -n "$SHIZUKU_APK_PATH"
SHIZUKU_LIB="${SHIZUKU_APK_PATH%/*}/lib/x86_64/libshizuku.so"
adb shell test -x "$SHIZUKU_LIB"
adb shell "$SHIZUKU_LIB" > /tmp/rz-shizuku-start.txt

has_daemon=false
for attempt in $(seq 1 80); do
  if [ -n "$(adb shell pidof shizuku_server 2>/dev/null | tr -d '\r' || true)" ]; then
    has_daemon=true
    break
  fi
  sleep 0.25
done
test "$has_daemon" = true

# Grant via the genuine Android/Shizuku UI rather than fabricating a granted
# callback in the app. All permissions and settings belong only to this emulator.
adb shell am start -W -n "$SHIZUKU_PKG/moe.shizuku.manager.MainActivity" \
  > /tmp/rz-shizuku-manager-start.txt
for attempt in $(seq 1 40); do
  if [ -n "$(adb shell pidof "$SHIZUKU_PKG" 2>/dev/null | tr -d '\r' || true)" ]; then break; fi
  sleep 0.25
done
sleep 2
adb shell pm revoke "$PRODUCT" moe.shizuku.manager.permission.API_V23 2>/dev/null || true
adb shell am force-stop "$PRODUCT" || true
adb shell am start -W -n "$PRODUCT/.ui.MainActivity" > /tmp/rz-shizuku-app-start.txt

dialog=false
for attempt in $(seq 1 120); do
  if adb shell dumpsys activity activities 2>/dev/null \
    | grep -Fq 'moe.shizuku.manager.authorization.RequestPermissionActivity'; then
    dialog=true
    break
  fi
  sleep 0.25
done
test "$dialog" = true
adb shell uiautomator dump /sdcard/rz-shizuku-appops.xml >/dev/null
adb shell cat /sdcard/rz-shizuku-appops.xml > /tmp/rz-shizuku-permission.xml
python3 - <<'PY' > /tmp/rz-shizuku-allow-coordinates
import re
from pathlib import Path
xml = Path('/tmp/rz-shizuku-permission.xml').read_text(encoding='utf-8')
match = re.search(r'resource-id="android:id/button1"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if not match:
    raise SystemExit('Genuine Shizuku always-allow permission button not found')
x1, y1, x2, y2 = map(int, match.groups())
print((x1+x2)//2, (y1+y2)//2)
PY
read -r x y < /tmp/rz-shizuku-allow-coordinates
adb shell input tap "$x" "$y"

granted=false
for attempt in $(seq 1 120); do
  if adb shell dumpsys package "$PRODUCT" \
    | grep -A20 'runtime permissions:' \
    | grep -Fq 'moe.shizuku.manager.permission.API_V23: granted=true'; then
    granted=true
    break
  fi
  sleep 0.25
done
test "$granted" = true
adb shell am force-stop "$PRODUCT" || true
adb logcat -c

# Instrumentation is intentionally concurrent with the host daemon death/restart,
# so cached READY cannot disguise a replaced UserService session.
(timeout 200 adb shell am instrument -w -r \
  -e realShizukuPinnedAppOpsProbe true -e class "$TEST" \
  "$PRODUCT.test/androidx.test.runner.AndroidJUnitRunner" \
  >/tmp/rz-shizuku-appops-instrumentation.txt 2>&1) &
probe_pid=$!
cleanup_probe() {
  if kill -0 "$probe_pid" 2>/dev/null; then
    kill "$probe_pid" 2>/dev/null || true
  fi
}
trap cleanup_probe EXIT
wait_marker() {
  local marker="$1" max_iterations="$2"
  for attempt in $(seq 1 "$max_iterations"); do
    if adb logcat -d -s RZShizukuAppOps:I '*:S' | grep -Fq "$marker"; then
      echo "$marker"
      return 0
    fi
    sleep 0.5
  done
  cat /tmp/rz-shizuku-appops-instrumentation.txt || true
  echo "Missing mandatory real Shizuku marker: $marker" >&2
  adb shell pidof shizuku_server || true
  adb logcat -d -t 200 | grep -Ei 'Shizuku|RZShizukuAppOps|UserService' | tail -55 || true
  return 1
}
wait_marker RZ_SHIZUKU_APPOPS_PIN_READY 150
client_pid_before="$(adb shell pidof "$PRODUCT" | tr -d '\r')"
test -n "$client_pid_before"
test "${client_pid_before#* }" = "$client_pid_before"

initial="$(adb shell pidof shizuku_server | tr -d '\r')"
test -n "$initial"
test "${initial#* }" = "$initial"
adb shell kill -9 "$initial"
wait_marker RZ_SHIZUKU_APPOPS_PIN_INVALIDATED 80
adb shell "$SHIZUKU_LIB" > /tmp/rz-shizuku-restart.txt
replacement=''
for attempt in $(seq 1 100); do
  replacement="$(adb shell pidof shizuku_server 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$replacement" ] && [ "$replacement" != "$initial" ]; then break; fi
  sleep 0.25
done
test -n "$replacement"
test "${replacement#* }" = "$replacement"
test "$replacement" != "$initial"
echo 'RZ_SHIZUKU_ACTUAL_SERVER_PID_CHANGED'

# Android 17 may retain a running replacement shell daemon without delivering
# the manager-mediated Binder to an already-running target process. Resume the
# *official manager UI* so it can propagate the replacement Binder; never inject
# a Binder or bypass production UserService authorization.
adb shell am start -W -n "$SHIZUKU_PKG/moe.shizuku.manager.MainActivity" \
  > /tmp/rz-shizuku-manager-restart.txt
# A replacement server PID does not guarantee that Android 17 has delivered
# its Binder to the existing client. Both outcomes must be explicit in the
# instrumented test: fresh read-only pin OR still-unavailable/fail-closed.
completion=''
for attempt in $(seq 1 150); do
  markers="$(adb logcat -d -s RZShizukuAppOps:I '*:S')"
  if grep -Fq 'RZ_SHIZUKU_APPOPS_NEW_PIN_READONLY_ONLY' <<<"$markers"; then
    completion=NEW_PIN_READONLY_ONLY
    break
  fi
  if grep -Fq 'RZ_SHIZUKU_APPOPS_RECONNECT_UNAVAILABLE_FAIL_CLOSED' <<<"$markers"; then
    completion=RECONNECT_UNAVAILABLE_FAIL_CLOSED
    break
  fi
  sleep 0.5
done
test -n "$completion"
echo "RZ_SHIZUKU_APPOPS_RECONNECT_RESULT=$completion"
wait "$probe_pid"
trap - EXIT
cat /tmp/rz-shizuku-appops-instrumentation.txt
grep -Fq 'OK (1 test)' /tmp/rz-shizuku-appops-instrumentation.txt
! grep -Fq 'INSTRUMENTATION_STATUS_CODE: -4' /tmp/rz-shizuku-appops-instrumentation.txt
! grep -Fq 'FAILURES!!!' /tmp/rz-shizuku-appops-instrumentation.txt
client_pid_after="$(adb shell pidof "$PRODUCT" | tr -d '\r')"
test "$client_pid_before" = "$client_pid_after"
echo 'RZ_SHIZUKU_APPOPS_MAIN_PID_PRESERVED'
adb logcat -d -s RZShizukuAppOps:I '*:S' > /tmp/rz-shizuku-appops-markers.txt
grep -E 'RZ_SHIZUKU_APPOPS_PROVIDER_(LIVE_BEFORE|LIVE_AFTER|REREQUEST)' /tmp/rz-shizuku-appops-markers.txt || true
for marker in PIN_READY PIN_INVALIDATED; do
  grep -Fq "RZ_SHIZUKU_APPOPS_$marker" /tmp/rz-shizuku-appops-markers.txt
done
grep -Fq "RZ_SHIZUKU_APPOPS_$completion" /tmp/rz-shizuku-appops-markers.txt
echo "API37_REAL_SHIZUKU_PIN_INVALIDATION_PASS ($completion)"
