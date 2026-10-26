# No Default Browser

Tiny Android app that picks a browser per link, so you never have to set a default browser.
Tap a link in any app, choose "No Default Browser", then choose which browser opens it — or
copy the link instead. The link itself is truncated to two lines; tap it to expand, long-press to copy.

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
| APK | 2.0 MB release + 5.4 MB debug, release unsigned | 21 KB, signed |
| toolchain | ~2 GB image, re-downloaded on every build (`--no-cache`) | 471 MB image, cached layers |
| build after a source change | minutes | ~2 s |

`entrypoint.sh` is the entire build. If you want to run it natively, `apt install openjdk-17-jdk`
and point `ANDROID_JAR` at any `android.jar`.

## Layout

```
AndroidManifest.xml                  app + intent filters
src/com/flo/nodefaultbrowser/        MainActivity + BrowserAdapter, plain Java, no dependencies
res/                                 strings, themes (light/dark), 2 layouts, launcher + copy icons
build.sh                             host entry point (docker)
entrypoint.sh                        the 5-step build, runs inside the image
Dockerfile                           JDK + android.jar + build-tools
```

## Troubleshooting

If the list comes up empty the app shows what `PackageManager` answered for each of the eight
probes (scheme × {no type, `text/html`} × {no category, `BROWSABLE`}), which is what makes a
device quirk diagnosable instead of a dead end.

The reason the probes are a cross product: browsers declare their web filters differently.
Older builds declare `<data android:scheme="http"/>` only, while current Chrome/Brave/Edge/Firefox
pair the scheme with a MIME type (`<data android:scheme="https" android:mimeType="text/html"/>`) —
on Android 16+ a type-less `VIEW` intent then matches *nothing*, not even from a shell:

```
$ adb shell cmd package query-activities -a android.intent.action.VIEW -c ...BROWSABLE -d https://example.com/x
1 activities found:  com.flo.nodefaultbrowser/.MainActivity      # only ourselves
$ ... -t text/html -d https://example.com/x
4 activities found:  chrome, brave, emmx, firefox
```

Same reason `launch()` tries the plain intent first and the typed one second.

## Requirements

Android 7.0 (API 24) or newer, at least one browser installed.

## License

MIT
