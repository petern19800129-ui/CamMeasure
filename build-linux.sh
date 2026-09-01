#!/usr/bin/env bash
set -euo pipefail

# Helper for Linux machines that already have Android SDK installed.
# If 'gradle' is installed, this builds directly. Otherwise open the project in Android Studio.
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle command not found. Open this folder in Android Studio and use Build > Build APK(s)."
  exit 2
fi

gradle :app:assembleDebug

echo "APK: app/build/outputs/apk/debug/app-debug.apk"
