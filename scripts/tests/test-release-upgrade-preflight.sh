#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
tool="$repo_root/scripts/release-upgrade-preflight.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/bin"
: > "$tmp/candidate.apk"
printf 'installed APK fixture' > "$tmp/installed.fixture"
cert_a='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
cert_b='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'

cat > "$tmp/bin/aapt" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
last=''
for arg in "$@"; do last="$arg"; done
if [[ "$last" == */installed.apk ]]; then
  printf "package: name='%s' versionCode='%s' versionName='1.8.6'\n" "$MOCK_INSTALLED_PACKAGE" "$MOCK_INSTALLED_VERSION"
else
  printf "package: name='%s' versionCode='%s' versionName='1.8.7'\n" "$MOCK_CANDIDATE_PACKAGE" "$MOCK_CANDIDATE_VERSION"
fi
MOCK
cat > "$tmp/bin/apksigner" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
last=''
for arg in "$@"; do last="$arg"; done
if [[ "$last" == */installed.apk ]]; then
  hash="$MOCK_INSTALLED_CERT"
else
  hash="$MOCK_CANDIDATE_CERT"
fi
echo 'Verifies'
printf 'Signer #1 certificate SHA-256 digest: %s\n' "$hash"
if [ "$MOCK_EXTRA_SIGNER" = 1 ]; then
  printf 'Signer #2 certificate SHA-256 digest: %s\n' "$hash"
fi
MOCK
cat > "$tmp/bin/adb" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [ "$1" = '-s' ]; then shift 2; fi
case "$1" in
  get-state) echo device ;;
  shell)
    [ "$2" = pm ] && [ "$3" = path ] || exit 1
    if [ "$MOCK_INSTALLED" = 0 ]; then exit 0; fi
    if [ "$MOCK_BAD_PATH" = 1 ]; then echo 'package:/data/app/unexpected.apk'
    else echo 'package:/data/app/com.gree1d.reappzuku/base.apk'; fi ;;
  pull)
    [ "$MOCK_PULL_FAIL" = 0 ] || exit 1
    cp "$MOCK_INSTALLED_FIXTURE" "$3" ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "$tmp/bin/"*

export PATH="$tmp/bin:$PATH"
export MOCK_INSTALLED_FIXTURE="$tmp/installed.fixture"
export MOCK_CANDIDATE_PACKAGE='com.gree1d.reappzuku'
export MOCK_INSTALLED_PACKAGE='com.gree1d.reappzuku'
export MOCK_CANDIDATE_VERSION=29 MOCK_INSTALLED_VERSION=28
export MOCK_CANDIDATE_CERT="$cert_a" MOCK_INSTALLED_CERT="$cert_a"
export MOCK_EXTRA_SIGNER=0 MOCK_INSTALLED=1 MOCK_BAD_PATH=0 MOCK_PULL_FAIL=0
pass=0
must_pass() {
  local output
  output="$(bash "$tool" "$tmp/candidate.apk" "$cert_a" 'mock-serial')" || {
    printf 'Expected success, got failure: %s\n' "$output" >&2; exit 1;
  }
  [[ "$output" == UPGRADE_PREFLIGHT_PASS* ]] || { echo 'No pass marker' >&2; exit 1; }
  pass=$((pass+1))
}
must_fail() {
  local name="$1" output
  shift
  if output="$(bash "$tool" "$tmp/candidate.apk" "$cert_a" 2>&1)"; then
    printf 'Expected %s to fail, got: %s\n' "$name" "$output" >&2; exit 1
  fi
  [[ "$output" == *UPGRADE_PREFLIGHT_BLOCKED* ]] || {
    printf 'Missing fail-closed marker for %s: %s\n' "$name" "$output" >&2; exit 1
  }
  pass=$((pass+1))
}
must_pass
MOCK_CANDIDATE_CERT="$cert_b" must_fail 'candidate key mismatch'
MOCK_INSTALLED_CERT="$cert_b" must_fail 'installed key mismatch'
MOCK_CANDIDATE_PACKAGE='com.example.other' must_fail 'foreign candidate'
MOCK_INSTALLED_PACKAGE='com.example.other' must_fail 'foreign installed'
MOCK_INSTALLED_VERSION=29 must_fail 'same versionCode'
MOCK_INSTALLED_VERSION=30 must_fail 'downgrade'
MOCK_INSTALLED=0 must_fail 'uninstalled'
MOCK_BAD_PATH=1 must_fail 'ambiguous base'
MOCK_PULL_FAIL=1 must_fail 'ADB pull denied'
MOCK_EXTRA_SIGNER=1 must_fail 'multiple signers'
if bash "$tool" "$tmp/candidate.apk" 'invalid-fingerprint' >/dev/null 2>&1; then
  echo 'Invalid expected fingerprint accepted' >&2; exit 1
fi
pass=$((pass+1))
[ "$pass" -eq 12 ]
printf 'RELEASE_UPGRADE_PREFLIGHT_TEST_PASS (%d cases)\n' "$pass"
