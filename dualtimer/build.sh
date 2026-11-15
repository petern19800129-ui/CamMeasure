#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ERROR: ANDROID_HOME is not set."
  echo "Example:"
  echo '  export ANDROID_HOME="$HOME/Android/Sdk"'
  exit 1
fi

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "ERROR: ANDROID_HOME does not exist: $ANDROID_HOME"
  exit 1
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/cmdline-tools/bin:$PATH"

if command -v sdkmanager >/dev/null 2>&1; then
  if [[ ! -d "$ANDROID_HOME/platforms/android-36" ]]; then
    echo "Installing Android SDK platform 36..."
    yes | sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" >/dev/null
  fi
fi

if [[ -x ./gradlew ]]; then
  GRADLE=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=(gradle)
else
  echo "ERROR: Gradle is not installed and no Gradle wrapper is included."
  echo "Install Gradle 8.13+ or generate a wrapper with:"
  echo "  gradle wrapper --gradle-version 8.13"
  exit 1
fi

echo "Building Dual Timer v1.2.1..."
"${GRADLE[@]}" :app:assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="DualTimer-v1.2.1-debug.apk"

if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK was not created."
  exit 1
fi

cp -f "$APK" "$OUT"
echo
echo "Build complete:"
echo "  $(pwd)/$OUT"
