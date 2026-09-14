#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

fail() {
  echo "verify-release-test: $*" >&2
  exit 1
}

run_missing_signing_test() {
  local output=""
  local status=0
  set +e
  output="$(env -u TIJI_SIGNING_STORE_FILE \
    -u TIJI_SIGNING_STORE_PASSWORD \
    -u TIJI_SIGNING_KEY_ALIAS \
    -u TIJI_SIGNING_KEY_PASSWORD \
    ./scripts/verify-release.sh 2>&1)"
  status=$?
  set -e
  (( status != 0 )) || fail "missing signing variables unexpectedly succeeded"
  [[ "$output" == *"RELEASE_SIGNING_GATE=FAIL"* ]] ||
    fail "missing signing variables did not fail the signing gate"
}

run_metadata_tool_failure_test() {
  local temp_dir
  temp_dir="$(mktemp -d)"
  trap 'rm -rf "$temp_dir"' RETURN
  local fake_aapt="$temp_dir/fake-aapt"
  cat > "$fake_aapt" <<'EOF'
#!/usr/bin/env bash
exit 17
EOF
  chmod +x "$fake_aapt"

  local output=""
  local status=0
  set +e
  output="$(
    export VERIFY_RELEASE_SOURCE_ONLY=1
    source ./scripts/verify-release.sh
    read_badging "$fake_aapt" "$temp_dir/tiji.apk"
  2>&1)"
  status=$?
  set -e
  (( status != 0 )) || fail "metadata tool failure unexpectedly succeeded"
  [[ "$output" == *"ARTIFACT_PROVENANCE_GATE=FAIL"* ]] ||
    fail "metadata tool failure did not fail the provenance gate"
}

run_unknown_field_test() {
  local output=""
  local status=0
  set +e
  output="$(
    export VERIFY_RELEASE_SOURCE_ONLY=1
    source ./scripts/verify-release.sh
    require_known "targetSdk" "unknown"
  2>&1)"
  status=$?
  set -e
  (( status != 0 )) || fail "unknown metadata unexpectedly succeeded"
  [[ "$output" == *"ARTIFACT_PROVENANCE_GATE=FAIL"* ]] ||
    fail "unknown metadata did not fail the provenance gate"
}

bash -n scripts/verify-release.sh
bash -n scripts/verify-release-test.sh
run_missing_signing_test
run_metadata_tool_failure_test
run_unknown_field_test
echo "verify-release self-check: PASS"
