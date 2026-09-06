#!/usr/bin/env bash
set -euo pipefail

# Builds the MineAva Android APK. Works on a machine with JDK 17+ and the
# Android SDK installed (or with ANDROID_HOME set), and in GitHub Actions.
#
# Usage:
#   tools/build_apk.sh              # debug APK
#   BUILD_TYPE=release tools/build_apk.sh
#
# Outputs:
#   app/build/outputs/apk/debug/app-debug.apk
#   app/build/outputs/apk/release/app-release-unsigned.apk

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_TYPE="${BUILD_TYPE:-debug}"

echo "==> MineAva build (${BUILD_TYPE})"

# --- Android SDK detection -------------------------------------------------
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -f "$ROOT/local.properties" ]]; then
    SDK_DIR="$(grep -E '^sdk.dir=' "$ROOT/local.properties" | cut -d= -f2- | tr -d '\r')"
  fi
  SDK_DIR="${SDK_DIR:-${HOME}/Android/Sdk}"
  if [[ -d "$SDK_DIR" ]]; then
    export ANDROID_HOME="$SDK_DIR"
  fi
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
  export ANDROID_HOME="$HOME/Android/Sdk"
fi
echo "==> ANDROID_HOME=${ANDROID_HOME}"

if [[ ! -d "$ANDROID_HOME" ]]; then
  cat <<EOF
ERROR: Android SDK not found at ${ANDROID_HOME}.
Install it (command-line tools + platform-tools + build-tools + platform android-35),
or set ANDROID_HOME/ANDROID_SDK_ROOT. In GitHub Actions the android-debug-build
workflow will install these dependencies automatically.
EOF
  exit 2
fi

# --- JDK detection ---------------------------------------------------------
if command -v java >/dev/null 2>&1; then
  echo "==> JDK: $(java -version 2>&1 | head -1)"
else
  cat <<EOF
ERROR: Java not found on PATH. Install JDK 17+ (e.g. sudo apt install openjdk-17-jdk).
EOF
  exit 2
fi

# --- Gradle selection ------------------------------------------------------
cd "$ROOT"
if [[ -x "$ROOT/gradlew" ]]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
else
  cat <<EOF
ERROR: No Gradle wrapper or Gradle CLI found.
Run `gradle wrapper --gradle-version 8.7` on a machine with Gradle, or use the
GitHub Actions workflow which provisions Gradle automatically.
EOF
  exit 2
fi

echo "==> Static pre-build check"
python3 "$ROOT/tools/static_check.py"

echo "==> Gradle assemble${BUILD_TYPE^}"
"$GRADLE" "assemble${BUILD_TYPE^}"

APK_DIR="app/build/outputs/apk/${BUILD_TYPE}"
echo "==> Built APKs"
find "$APK_DIR" -name '*.apk' -type f -exec ls -lh {} \;
