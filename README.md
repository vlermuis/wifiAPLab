# WiFi AP Lab

An Android test harness for exercising a phone as a Wi‑Fi access point and recording local events.

## What it does

- Starts and stops Android `LocalOnlyHotspot` (API 26+).
- Requests the relevant Wi‑Fi permissions for Android 13+ and older releases.
- Saves timestamped events in `files/wifi-ap-events.log` inside the app sandbox.
- Records app start, permission results, AP lifecycle callbacks, failures, and Wi‑Fi/AP broadcasts.
- Provides a test panel for SSID, password, band, channel, hidden SSID, and client-limit intent.

## Important Android limitation

Stock third-party Android apps cannot generally force all Soft AP parameters. The LocalOnlyHotspot API lets the operating system choose the final SSID, passphrase, band, channel, security, and client policy. The advanced fields are therefore recorded as test intent and displayed as such; they are not silently presented as applied settings. A device/OEM system app or rooted test build is needed for full Soft AP configuration and client/MAC association callbacks.

## Build locally

Open the folder in Android Studio with an Android SDK containing API 35 and JDK 17, then run the `app` configuration. The project uses Android Gradle Plugin 8.5.2 and Kotlin 2.0.21.

From a machine with Gradle 8.7 installed:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub

Push this folder to a GitHub repository. The included `.github/workflows/android.yml` automatically installs JDK 17, Gradle 8.7, and Android API 35, then builds and uploads the debug APK as a workflow artifact. It runs on pushes to `main` or `master`, pull requests, and manual workflow dispatch.
