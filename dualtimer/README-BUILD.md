# Dual Timer v1.2.1 — Build Kit

This is the complete Android source for Dual Timer v1.2.1.

## Included features

- Two independent countdown timers.
- Four editable presets per timer.
- Start button green.
- Reset button red.
- Compact portrait layout with both timers visible.
- Screen stays awake while the app is open.
- Alarm tone and vibration when a timer finishes.
- Configurable home-screen preset timer widget.
- Widget is designed as 1×1.
- Each widget can use Timer 1 or Timer 2 and Preset 1–4.
- Tapping the widget starts/restarts the countdown without opening the app.

## Build requirements

- Linux/macOS/Windows with Java 17.
- Android SDK platform 36.
- Android build-tools 36.0.0.
- Gradle 8.13 or newer.
- ANDROID_HOME set to your Android SDK directory.

Example on Linux:

    export ANDROID_HOME="$HOME/Android/Sdk"
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

If platform 36 is not installed:

    sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"

## Build on Linux/macOS

    chmod +x build.sh
    ./build.sh

The APK will be created as:

    DualTimer-v1.2.1-debug.apk

## Manual Gradle build

    gradle :app:assembleDebug

Output:

    app/build/outputs/apk/debug/app-debug.apk

## Project details

- Package: com.pete.dualtimer
- Min SDK: 24
- Target SDK: 36
- Compile SDK: 36
- Java: 17
- App version: 1.2.1
