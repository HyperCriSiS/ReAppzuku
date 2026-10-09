#!/usr/bin/env bash
set -euo pipefail

# Run only on a disposable debug-APK emulator.
INSTRUMENTATION="$(
  adb shell pm list instrumentation | tr -d '\r' |
    sed -n 's/^instrumentation:\([^ ]*\) (target=com.gree1d.reappzuku)$/\1/p' |
    head -n 1
)"
test -n "$INSTRUMENTATION"
CLASS='com.gree1d.reappzuku.core.Api24Api37PackageHandlerRetryInstrumentationTest'
adb shell am instrument -w -r \
  -e ci_package_handler_retry verify \
  -e class "$CLASS" "$INSTRUMENTATION" \
  | tee /tmp/reappzuku-package-handler-retry.txt
grep -Fq 'OK (1 test)' /tmp/reappzuku-package-handler-retry.txt
! grep -Fq 'FAILURES!!!' /tmp/reappzuku-package-handler-retry.txt
grep -Fq 'INSTRUMENTATION_STATUS: REAPPZUKU_PACKAGE_HANDLER_RETRY_SUCCESS=yes' \
  /tmp/reappzuku-package-handler-retry.txt
echo 'REAPPZUKU_REAL_PACKAGE_HANDLER_RETRY_PASS'
