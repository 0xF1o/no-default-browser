# Android Browser Picker - Minimal App

Build a tiny Android app that intercepts web URL intents and shows a list of installed browsers to pick from — so the user never needs to set a default browser.

## Goals
- Create a Docker-based build environment for Android apps (using modern SDK/API 34, no Java installed natively)
- Build an Android app that intercepts `http://`, `https://`, `http://` intents
- Show a scrollable list of all installed apps that can handle web URLs (browsers)
- When user taps a browser, forward the URL to it
- Produce a usable APK in the project's `apk/` directory

## Checklist
- [x] Write `Dockerfile.build` with Android SDK 34 + Gradle build environment (multi-stage)
- [x] Write `build.sh` — shell script to build APK via Docker (uses docker cp to extract APK)
- [x] Write `settings.gradle.kts` for modern Gradle (AGP 8.5.2, Kotlin 1.9.25)
- [x] Write `app/build.gradle.kts` — minimal Kotlin/AGP project config
- [x] Write `AndroidManifest.xml` — intent filter for http/https ACTION_VIEW URLs, launcher activity
- [x] Write `MainActivity.kt` — fetch installed browsers from PackageManager, display list, forward intent
- [x] Write `BrowserAdapter.kt` — simple RecyclerView adapter with icon + name
- [x] Write `res/layout/activity_main.xml` — clean vertical list layout
- [x] Write `res/values/strings.xml` and `res/values/themes.xml` — minimal resources
- [x] Write `res/values-night/themes.xml` — dark theme support
- [x] Run `./build.sh` and verify APK is produced in `apk/` — 1.9MB APK at `apk/app-release-unsigned.apk`
- [x] Quick smoke-test: APK is valid zip, contains correct package structure and intent-filter resources

## Verification
- Commands: `bash build.sh` (via Docker), then `adb install apk/debug-app-debug.apk` on device
- Working directory: `D:/work/pg/no-default-browser/`
- Required preserved artifacts: Docker image `android-browser-picker-builder`, `apk/debug-app-debug.apk`, `apk/release-app-release-unsigned.apk`

## Notes
- Use Android Gradle Plugin 8.5.2, Kotlin 1.9.25, compileSdk/targetSdk = 34, minSdk = 24
- No remember-last-choice, no prefs — pure picker every time
- App name: "Pick Browser", package: com.example.browserpicker
- Intercepts ACTION_VIEW for http and https schemes
- Docker volume mount on Windows Git Bash had path translation issues — solved by using docker build + docker cp approach
- Single-task launch mode, taskAffinity cleared to ensure fresh task on each URL
- Auto-launches if only one browser is installed (no picker needed)

## Build Artifacts
- Docker image: `android-browser-picker-builder`
- Debug APK: `apk/debug-app-debug.apk` (5.4MB, no R8 minification, includes debug info)
- Release APK: `apk/release-app-release-unsigned.apk` (2.0MB, R8 minified, proguarded)
- Build command: `bash build.sh` (builds both variants)

## Final Verification
- Exact command: `bash build.sh`
- Working directory: `D:/work/pg/no-default-browser/`
- Required preserved artifacts: Docker image `android-browser-picker-builder`, `apk/debug-app-debug.apk`, `apk/release-app-release-unsigned.apk`
- Result: Both APKs produced (debug 5.4MB + release 2.0MB), valid zip/APK format, manifest compiled with http/https intent-filters