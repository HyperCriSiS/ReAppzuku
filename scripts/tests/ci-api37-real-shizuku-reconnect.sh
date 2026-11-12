#!/usr/bin/env bash
# Manual, opt-in check on a disposable Android 17 emulator: does a running
# ReAppzuku process get the Binder of a restarted official Shizuku server?
# Nothing is granted and no app setting is changed; the emulator is thrown away.
set -euo pipefail

PRODUCT=com.gree1d.reappzuku
TEST=com.gree1d.reappzuku.core.RealShizukuBinderReconnectInstrumentationTest
SHIZUKU_PKG=moe.shizuku.privileged.api
APK=/tmp/shizuku-v13.6.0.r1086.2650830c-release.apk
LOG_TAG=RZShizukuReconnect

test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 37
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
test -n "$(adb shell pm path "$PRODUCT")"
test -n "$(adb shell pm path "$PRODUCT.test")"

# The official release, pinned by its SHA-256. Never an unpinned installer.
curl --fail --location --http1.1 --proto '=https' --tlsv1.2 --retry 3 \
  'https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk' \
  -o "$APK"
echo "6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f  $APK" | sha256sum -c -
adb install -r "$APK" > /tmp/rz170-shizuku-install.txt

SHIZUKU_APK_PATH="$(adb shell pm path "$SHIZUKU_PKG" | tr -d '\r' | sed -n '1s/^package://p')"
test -n "$SHIZUKU_APK_PATH"
SHIZUKU_LIB="${SHIZUKU_APK_PATH%/*}/lib/x86_64/libshizuku.so"
adb shell test -x "$SHIZUKU_LIB"

server_pid() {
  adb shell pidof shizuku_server 2>/dev/null | tr -d '\r' || true
}

start_server() {
  adb shell "$SHIZUKU_LIB" > "$1"
  for attempt in $(seq 1 100); do
    if [ -n "$(server_pid)" ]; then return 0; fi
    sleep 0.25
  done
  cat "$1" || true
  echo 'The Shizuku server did not start' >&2
  return 1
}

start_server /tmp/rz170-shizuku-start.txt
adb shell am force-stop "$PRODUCT" || true
adb logcat -c

# The test runs while the host ends and restarts the server.
(timeout 420 adb shell am instrument -w -r \
  -e realShizukuReconnectProbe true -e class "$TEST" \
  "$PRODUCT.test/androidx.test.runner.AndroidJUnitRunner" \
  > /tmp/rz170-instrumentation.txt 2>&1) &
probe_pid=$!
cleanup_probe() {
  if kill -0 "$probe_pid" 2>/dev/null; then
    kill "$probe_pid" 2>/dev/null || true
  fi
}
trap cleanup_probe EXIT

wait_marker() {
  local marker="$1" seconds="$2"
  for attempt in $(seq 1 $((seconds * 2))); do
    if adb logcat -d -s "$LOG_TAG:I" '*:S' | grep -Fq "$marker"; then
      adb logcat -d -s "$LOG_TAG:I" '*:S' | grep -F "$marker" | tail -1
      return 0
    fi
    sleep 0.5
  done
  cat /tmp/rz170-instrumentation.txt || true
  echo "Missing marker: $marker" >&2
  echo "server pid: $(server_pid)" >&2
  adb logcat -d -t 300 | grep -Ei 'Shizuku|RZShizukuReconnect' | tail -60 || true
  return 1
}

marker_pid() {
  adb logcat -d -s "$LOG_TAG:I" '*:S' | grep -F "$1" | tail -1 | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p'
}

wait_marker RZ170_BINDER_READY 90
client_before="$(marker_pid RZ170_BINDER_READY)"
test -n "$client_before"

initial="$(server_pid)"
test -n "$initial"
test "${initial#* }" = "$initial"
adb shell kill -9 "$initial"
wait_marker RZ170_BINDER_LOST 60

start_server /tmp/rz170-shizuku-restart.txt
replacement="$(server_pid)"
test -n "$replacement"
test "${replacement#* }" = "$replacement"
test "$replacement" != "$initial"
echo "RZ170_SERVER_PID_CHANGED $initial -> $replacement"

wait_marker RZ170_BINDER_BACK 190
# The same process that lost the Binder got the new one. The pids come from
# the markers: once the test is done Android may end the process, so asking
# for its pid now could find nothing.
client_after="$(marker_pid RZ170_BINDER_BACK)"
test "$client_before" = "$client_after"
echo "RZ170_CLIENT_PID_KEPT $client_before"

wait "$probe_pid"
trap - EXIT
cat /tmp/rz170-instrumentation.txt
grep -Fq 'OK (1 test)' /tmp/rz170-instrumentation.txt
echo 'API37_REAL_SHIZUKU_RECONNECT_PASS'
