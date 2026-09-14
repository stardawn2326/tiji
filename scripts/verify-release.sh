#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "RELEASE_ACCEPTANCE=FAIL" >&2
  echo "RELEASE_SIGNING_GATE=FAIL" >&2
  echo "verify-release: $*" >&2
  exit 1
}

required_signing_vars=(
  TIJI_SIGNING_STORE_FILE
  TIJI_SIGNING_STORE_PASSWORD
  TIJI_SIGNING_KEY_ALIAS
  TIJI_SIGNING_KEY_PASSWORD
)
missing_signing_vars=()
for name in "${required_signing_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing_signing_vars+=("$name")
  fi
done
if (( ${#missing_signing_vars[@]} > 0 )); then
  fail "missing formal release signing variables: ${missing_signing_vars[*]}"
fi

[[ -f "$TIJI_SIGNING_STORE_FILE" ]] || fail "keystore not found: $TIJI_SIGNING_STORE_FILE"
[[ -n "${TIJI_PREVIOUS_SIGNER_SHA256:-}" ]] || fail "TIJI_PREVIOUS_SIGNER_SHA256 is required for signer continuity"

gradle_cmd="${GRADLE_CMD:-./gradlew}"
apksigner_cmd="${APKSIGNER:-}"
if [[ -z "$apksigner_cmd" ]]; then
  apksigner_cmd="$(command -v apksigner || true)"
fi
[[ -n "$apksigner_cmd" ]] || fail "apksigner is not available; set APKSIGNER"

build_command="$gradle_cmd :app:assembleRelease --no-daemon --console=plain -PTIJI_REQUIRE_RELEASE_SIGNING=true"
echo "build command: $build_command"
$gradle_cmd :app:assembleRelease --no-daemon --console=plain -PTIJI_REQUIRE_RELEASE_SIGNING=true

mapfile -t release_apks < <(find app/build/outputs/apk/release -maxdepth 1 -type f -name 'tiji-v*-release.apk' -print | sort)
(( ${#release_apks[@]} == 1 )) || fail "expected one release APK, found ${#release_apks[@]}"
apk="${release_apks[0]}"

verify_output="$($apksigner_cmd verify --verbose --print-certs "$apk")" || fail "apksigner verification failed"
printf '%s\n' "$verify_output"
signer_sha256="$(printf '%s\n' "$verify_output" | awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}')"
[[ -n "$signer_sha256" ]] || fail "could not read signer SHA-256"

normalize() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'; }
if [[ "$(normalize "$signer_sha256")" != "$(normalize "$TIJI_PREVIOUS_SIGNER_SHA256")" ]]; then
  fail "signer continuity mismatch: previous=$TIJI_PREVIOUS_SIGNER_SHA256 current=$signer_sha256"
fi

aapt_cmd="${AAPT:-$(command -v aapt || true)}"
badging=""
if [[ -n "$aapt_cmd" ]]; then
  badging="$($aapt_cmd dump badging "$apk")"
fi
source_sha="$(git rev-parse HEAD)"
apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
apk_size="$(wc -c < "$apk" | tr -d '[:space:]')"
version_code="$(printf '%s\n' "$badging" | sed -nE 's/.*versionCode='"'"'([^'"'"']+)'"'"'.*/\1/p' | head -n1)"
version_name="$(printf '%s\n' "$badging" | sed -nE 's/.*versionName='"'"'([^'"'"']+)'"'"'.*/\1/p' | head -n1)"
min_sdk="$(printf '%s\n' "$badging" | sed -nE 's/.*sdkVersion:'"'"'([^'"'"']+)'"'"'.*/\1/p' | head -n1)"
target_sdk="$(printf '%s\n' "$badging" | sed -nE 's/.*targetSdkVersion:'"'"'([^'"'"']+)'"'"'.*/\1/p' | head -n1)"
compile_sdk="$(sed -nE 's/^[[:space:]]*compileSdk = ([0-9]+).*/\1/p' app/build.gradle.kts | head -n1)"

cat <<EOF
source SHA: $source_sha
versionCode: ${version_code:-unknown}
versionName: ${version_name:-unknown}
APK filename: $apk
APK size: $apk_size
APK SHA256: $apk_sha256
signer SHA256: $signer_sha256
minSdk: ${min_sdk:-unknown}
targetSdk: ${target_sdk:-unknown}
compileSdk: ${compile_sdk:-unknown}
build command: $build_command
RELEASE_SIGNING_GATE=PASS
ARTIFACT_PROVENANCE_GATE=PASS
EOF
