# CamMeasure MVP

Android AR measuring app that measures **width, height, depth**, or all three dimensions of a box-like object.

## Measurement workflow

- **Width / Height / Depth:** aim the centre crosshair at endpoint 1, tap **MARK POINT**, then aim at endpoint 2 and mark it.
- **Box 3D:** mark one origin corner, then mark the width endpoint, height endpoint, and depth endpoint from that same origin corner.
- Units can be switched between Auto, mm, cm, and m.
- ARCore Depth points are preferred when supported; otherwise the app falls back to detected planes and feature points.

## Requirements

- ARCore-supported Android phone
- Android 7.0 / API 24 or newer
- Google Play Services for AR
- Android Studio with Android SDK 36
- JDK 17

## Build in Android Studio

1. Open the `CamMeasure` folder.
2. Let Gradle sync and install any requested SDK components.
3. Connect the Android phone with USB debugging enabled.
4. Select the phone and press **Run**.
5. For an APK: **Build > Build APK(s)**.

## Accuracy notes

This is an AR measurement tool, not a calibrated metrology instrument. Accuracy depends on lighting, texture, distance, camera motion, ARCore tracking, and depth quality. For best results, move slowly, scan the object from more than one angle, and measure distinct high-contrast edges.

## Privacy disclosure

This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy.

## Build an APK with GitHub Actions

This project includes `.github/workflows/build-apk.yml`.

1. Create a GitHub repository and push this project to it.
2. Open the repository's **Actions** tab.
3. Run **Build CamMeasure APK** (or push to `main`).
4. Download the `CamMeasure-debug-apk` artifact from the completed workflow run.

The workflow installs Java 17, Android API 36/build-tools 36.0.0, Gradle 8.13, builds `:app:assembleDebug`, and uploads the resulting APK.
