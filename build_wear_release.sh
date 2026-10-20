#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  echo "Build and sign matching per-ABI Wear + companion release APKs, with checksums."
  echo "Requires ANDROID_HOME, KEYSTORE_PASSWORD, KEY_ALIAS; KEY_PASSWORD defaults to KEYSTORE_PASSWORD."
  echo "Optional KEYSTORE_PATH (default ./simpmusic.jks), WEAR_ABIS (default ARMv7 + ARM64)."
  echo "Optional APPLICATION_ID applies the same custom release package to both apps."
  echo "OUTPUT_DIR defaults to build/release and must be empty. Requires Python 3.11+."
  echo "BUILD_TOOLS_VERSION defaults to 36.0.0."
  exit 0
fi
if [[ $# -gt 0 ]]; then echo "Unknown argument: $1" >&2; exit 2; fi

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD}"
: "${KEY_ALIAS:?Set KEY_ALIAS}"
export KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
KEYSTORE_PATH="${KEYSTORE_PATH:-./simpmusic.jks}"
[[ -s "$KEYSTORE_PATH" ]] || { echo "Signing keystore is missing or empty" >&2; exit 1; }
build_tools="$ANDROID_HOME/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
for tool in zipalign apksigner aapt2; do
  [[ -x "$build_tools/$tool" ]] || { echo "Missing SDK tool: $tool" >&2; exit 1; }
done

build_args=()
if [[ -n "${APPLICATION_ID:-}" ]]; then
  [[ "$APPLICATION_ID" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]] || { echo "Invalid APPLICATION_ID" >&2; exit 1; }
  build_args+=("-Psimpmusic.applicationId=$APPLICATION_ID")
fi
if [[ -n "${WEAR_ABIS:-}" ]]; then build_args+=("-Psimpmusic.wear.abis=$WEAR_ABIS"); fi
bash ./gradlew --no-configuration-cache "${build_args[@]}" :wearApp:clean :companionApp:clean :wearApp:assembleRelease :companionApp:assembleRelease
python3 verify_wear_apk.py wearApp/build/outputs/apk/release/wearApp-release-unsigned.apk --release --aapt2 "$build_tools/aapt2"

python3 package_wear_release.py \
  --wear wearApp/build/outputs/apk/release/wearApp-release-unsigned.apk \
  --companion companionApp/build/outputs/apk/release/companionApp-release-unsigned.apk \
  --build-tools "$build_tools" --keystore "$KEYSTORE_PATH" --alias "$KEY_ALIAS" \
  --output "${OUTPUT_DIR:-build/release}"
