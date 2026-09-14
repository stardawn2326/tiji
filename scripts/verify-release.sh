#!/usr/bin/env bash
set -euo pipefail

# This script is intentionally fail-closed. It is the release harness for a
# signed RC, not a convenience wrapper for debug APKs.

fail_signing() {
  echo "RELEASE_ACCEPTANCE=FAIL" >&2
  echo "RELEASE_SIGNING_GATE=FAIL" >&2
  echo "verify-release: $*" >&2
  exit 1
}

fail_provenance() {
  echo "RELEASE_ACCEPTANCE=FAIL" >&2
  echo "ARTIFACT_PROVENANCE_GATE=FAIL" >&2
  echo "verify-release: $*" >&2
  exit 1
}

resolve_android_tool() {
  local override_var="$1"
  local command_name="$2"
  local override="${!override_var:-}"
  local found=""
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

  if [[ -n "$override" ]]; then
    printf '%s\n' "$override"
    return 0
  fi

  found="$(command -v "$command_name" 2>/dev/null || true)"
  if [[ -n "$found" ]]; then
    printf '%s\n' "$found"
    return 0
  fi

  if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
    find "$sdk_root/build-tools" -maxdepth 2 -type f \
      \( -name "$command_name" -o -name "$command_name.exe" \) \
      -print 2>/dev/null | sort -V | tail -n1
  fi
}

normalize() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]'
}

require_known() {
  local field="$1"
  local value="$2"
  if [[ -z "$value" || "$value" == "unknown" ]]; then
    fail_provenance "$field could not be read from the release artifact"
  fi
}

require_numeric() {
  local field="$1"
  local value="$2"
  require_known "$field" "$value"
  [[ "$value" =~ ^[0-9]+$ ]] || fail_provenance "$field is not numeric: $value"
}

read_badging() {
  local aapt_cmd="$1"
  local apk="$2"
  local output=""

  if ! output="$("$aapt_cmd" dump badging "$apk" 2>&1)"; then
    fail_provenance "metadata tool failed: $aapt_cmd dump badging $apk"
  fi
  [[ -n "$output" ]] || fail_provenance "metadata tool returned no output for $apk"
  printf '%s\n' "$output"
}

extract_badging_field() {
  local pattern="$1"
  local badging="$2"
  printf '%s\n' "$badging" | sed -nE "$pattern" | head -n1
}

validate_provenance_fields() {
  local application_id="$1"
  local version_code="$2"
  local version_name="$3"
  local min_sdk="$4"
  local target_sdk="$5"
  local compile_sdk="$6"
  local apk_sha256="$7"
  local signer_sha256="$8"
  local source_sha="$9"
  local apk_size="${10}"
  local java_version="${11}"
  local gradle_version="${12}"

  require_known "applicationId" "$application_id"
  [[ "$application_id" == "com.tiji.mistakes" ]] ||
    fail_provenance "unexpected applicationId: $application_id"
  require_numeric "versionCode" "$version_code"
  require_known "versionName" "$version_name"
  require_numeric "minSdk" "$min_sdk"
  require_numeric "targetSdk" "$target_sdk"
  require_numeric "compileSdk" "$compile_sdk"
  require_known "APK SHA256" "$apk_sha256"
  [[ "$apk_sha256" =~ ^[[:xdigit:]]{64}$ ]] ||
    fail_provenance "APK SHA256 is not a 64-character hex digest"
  require_known "signer SHA256" "$signer_sha256"
  [[ "$(normalize "$signer_sha256")" =~ ^[[:xdigit:]]{64}$ ]] ||
    fail_provenance "signer SHA256 is not a 64-character hex digest"
  require_known "source SHA" "$source_sha"
  [[ "$source_sha" =~ ^[[:xdigit:]]{40}$ ]] ||
    fail_provenance "source SHA is not a 40-character Git commit"
  require_numeric "APK size" "$apk_size"
  [[ "$apk_size" -gt 0 ]] || fail_provenance "APK size is zero"
  require_known "JDK version" "$java_version"
  require_known "Gradle version" "$gradle_version"
}

main() {
  local required_signing_vars=(
    TIJI_SIGNING_STORE_FILE
    TIJI_SIGNING_STORE_PASSWORD
    TIJI_SIGNING_KEY_ALIAS
    TIJI_SIGNING_KEY_PASSWORD
  )
  local missing_signing_vars=()
  local name

  for name in "${required_signing_vars[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      missing_signing_vars+=("$name")
    fi
  done
  if (( ${#missing_signing_vars[@]} > 0 )); then
    fail_signing "missing formal release signing variables: ${missing_signing_vars[*]}"
  fi

  [[ -f "$TIJI_SIGNING_STORE_FILE" ]] ||
    fail_signing "keystore not found: $TIJI_SIGNING_STORE_FILE"
  [[ -n "${TIJI_PREVIOUS_SIGNER_SHA256:-}" ]] ||
    fail_signing "TIJI_PREVIOUS_SIGNER_SHA256 is required for signer continuity"

  local gradle_cmd="${GRADLE_CMD:-./gradlew}"
  local apksigner_cmd="$(resolve_android_tool APKSIGNER apksigner)"
  [[ -n "$apksigner_cmd" ]] ||
    fail_signing "apksigner is not available; set APKSIGNER"

  local build_command="$gradle_cmd :app:assembleRelease --no-daemon --console=plain -PTIJI_REQUIRE_RELEASE_SIGNING=true"
  echo "build command: $build_command"
  if ! $gradle_cmd :app:assembleRelease --no-daemon --console=plain \
      -PTIJI_REQUIRE_RELEASE_SIGNING=true; then
    fail_signing "formal release build failed"
  fi

  local release_dir="app/build/outputs/apk/release"
  local release_apks=()
  mapfile -t release_apks < <(find "$release_dir" -maxdepth 1 -type f \
    -name 'tiji-v*-release.apk' -print | sort)
  (( ${#release_apks[@]} == 1 )) ||
    fail_provenance "expected one release APK, found ${#release_apks[@]}"
  local apk="${release_apks[0]}"

  local verify_output=""
  if ! verify_output="$("$apksigner_cmd" verify --verbose --print-certs "$apk" 2>&1)"; then
    fail_signing "apksigner verification failed"
  fi
  printf '%s\n' "$verify_output"
  local signer_sha256
  signer_sha256="$(printf '%s\n' "$verify_output" | awk -F': ' \
    '/Signer #1 certificate SHA-256 digest:/ {print $2; exit}')"
  [[ -n "$signer_sha256" ]] || fail_signing "could not read signer SHA-256"
  if [[ "$(normalize "$signer_sha256")" != \
      "$(normalize "$TIJI_PREVIOUS_SIGNER_SHA256")" ]]; then
    fail_signing "signer continuity mismatch: previous=$TIJI_PREVIOUS_SIGNER_SHA256 current=$signer_sha256"
  fi

  local aapt_cmd="$(resolve_android_tool AAPT aapt)"
  if [[ -z "$aapt_cmd" ]]; then
    aapt_cmd="$(resolve_android_tool AAPT2 aapt2)"
  fi
  [[ -n "$aapt_cmd" ]] ||
    fail_provenance "aapt or aapt2 is required for artifact provenance; set AAPT or AAPT2"

  local badging
  badging="$(read_badging "$aapt_cmd" "$apk")"
  local application_id
  local version_code
  local version_name
  local min_sdk
  local target_sdk
  local compile_sdk
  application_id="$(extract_badging_field "s/^package: name='([^']+)'.*/\\1/p" "$badging")"
  version_code="$(extract_badging_field "s/^package:.*versionCode='([^']+)'.*/\\1/p" "$badging")"
  version_name="$(extract_badging_field "s/^package:.*versionName='([^']+)'.*/\\1/p" "$badging")"
  min_sdk="$(extract_badging_field "s/^sdkVersion:'([^']+)'.*/\\1/p" "$badging")"
  target_sdk="$(extract_badging_field "s/^targetSdkVersion:'([^']+)'.*/\\1/p" "$badging")"
  compile_sdk="$(extract_badging_field "s/^package:.*compileSdkVersion='([^']+)'.*/\\1/p" "$badging")"
  if [[ -z "$compile_sdk" ]]; then
    compile_sdk="$(sed -nE 's/^[[:space:]]*compileSdk[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' \
      app/build.gradle.kts | head -n1)"
  fi

  local source_sha
  source_sha="$(git rev-parse HEAD 2>/dev/null || true)"
  local apk_sha256
  apk_sha256="$(sha256sum "$apk" | awk '{print $1}')"
  local apk_size
  apk_size="$(wc -c < "$apk" | tr -d '[:space:]')"
  local java_cmd="${JAVA_CMD:-java}"
  local java_version_output
  if ! java_version_output="$("$java_cmd" -version 2>&1)"; then
    fail_provenance "JDK is not available: $java_cmd"
  fi
  local java_version="$(printf '%s\n' "$java_version_output" | head -n1)"
  local gradle_version_output
  if ! gradle_version_output="$($gradle_cmd --version --no-daemon --console=plain 2>&1)"; then
    fail_provenance "Gradle version could not be read"
  fi
  local gradle_version
  gradle_version="$(printf '%s\n' "$gradle_version_output" | sed -nE 's/^Gradle (.+)/Gradle \1/p' | head -n1)"

  validate_provenance_fields \
    "$application_id" "$version_code" "$version_name" "$min_sdk" \
    "$target_sdk" "$compile_sdk" "$apk_sha256" "$signer_sha256" \
    "$source_sha" "$apk_size" "$java_version" "$gradle_version"

  cat <<EOF
source SHA: $source_sha
applicationId: $application_id
versionCode: $version_code
versionName: $version_name
APK filename: $apk
APK size: $apk_size
APK SHA256: $apk_sha256
signer SHA256: $signer_sha256
minSdk: $min_sdk
targetSdk: $target_sdk
compileSdk: $compile_sdk
JDK version: $java_version
Gradle version: $gradle_version
metadata tool: $aapt_cmd
build command: $build_command
RELEASE_SIGNING_GATE=PASS
ARTIFACT_PROVENANCE_GATE=PASS
EOF
}

# Allows the self-check script to source the parser/validation helpers without
# running a real signed build.
if [[ "${VERIFY_RELEASE_SOURCE_ONLY:-0}" != "1" ]]; then
  main "$@"
fi
