# No Default Browser

Tiny Android app that picks a browser per link, so you never have to set a default browser.
Tap a link in any app, choose "No Default Browser", then choose which browser opens it.

## How it works

The app registers as an `ACTION_VIEW` handler for `http` and `https`. When a link arrives it
queries `PackageManager` for every installed app that can render web pages, shows them as a list,
and forwards the URL to the one you tap (with `setPackage()` so it can't bounce back to the
default). Opened straight from the launcher it just lists the browsers so you can launch one.
If exactly one browser is installed the link is forwarded without asking.

When Android asks you to pick between browsers, choose this app and *Always* — then it becomes the
single entry point for links and you decide per link from then on.

## Build

Needs nothing on the host but Docker.

```bash
./build.sh    # -> apk/no-default-browser.apk (signed, sideloadable)
```

`build.sh` builds (once, then cached) a toolchain image and mounts the repo into it, so
rebuilding after a source change takes about 2 seconds. The signing key is generated on the
first run into `.secrets/` — keep it, otherwise installing an update requires uninstalling
first (which wipes nothing here, the app is stateless).

Install on a device with `adb install -r apk/no-default-browser.apk`, or copy the APK to the
phone and open it with Files ("install from unknown sources"). The old Gradle build used the
package `com.example.browserpicker` — remove that one, it is a different app.

## Why there is no Gradle

An APK is just a zip produced by a handful of command line tools that all live in `build-tools`:

```
aapt2 compile -> aapt2 link -> javac -> d8 -> zip -> zipalign -> apksigner
```

The image contains only those tools plus `android.jar` (~130 MB of downloads) on a JDK base —
no `cmdline-tools`/`sdkmanager`, no Gradle distribution, no Android Gradle Plugin, no Kotlin
compiler, no AndroidX. Result:

| | before (Gradle/AGP) | now |
|---|---|---|
| APK | 2.0 MB release + 5.4 MB debug, release unsigned | 17 KB, signed |
| toolchain | ~2 GB image, re-downloaded on every build (`--no-cache`) | 471 MB image, cached layers |
| build after a source change | minutes | ~2 s |

`build-apk.sh` is the entire build. If you want to run it natively, `apt install openjdk-17-jdk`
and point `ANDROID_JAR` at any `android.jar`.

## Layout

```
AndroidManifest.xml                  app + intent filters
src/com/flo/nodefaultbrowser/        MainActivity, BrowserAdapter (~130 lines of plain Java)
res/                                 strings, themes (light/dark), 2 layouts, launcher icon
build.sh                             host entry point (docker)
build-apk.sh                         the 5-step build, runs inside the image
Dockerfile                           JDK + android.jar + build-tools
```

## Requirements

Android 7.0 (API 24) or newer, at least one browser installed.

## License

MIT
