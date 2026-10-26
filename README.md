# No Default Browser

Pick a browser for every link on Android, so you never need a default browser.

<img src="docs/screenshot.png" width="280" alt="The app listing Brave, Chrome, Edge and Firefox below a link, with a Copy link row on top">

Tap a link in any app → choose **No Default Browser** → pick the browser that should open it, or
copy the link instead. Choose *Always* once and this app becomes the entry point for every link.

## Build and install

```bash
./build.sh
```

Requires nothing but Docker. The result is `apk/no-default-browser.apk`, signed, so it installs
with `adb install -r` or by copying it to the phone and opening it there.

The signing key is created on the first build in `.secrets/` — keep it, because without it an
update means uninstalling first.

## How it works

- Registers for `http` and `https` links and lists every app that can render a web page.
- Hands the link to the app you tap with `setPackage()`, so it cannot bounce back to a default.
- Browsers declare their web filters in two flavours (scheme only, or scheme + `text/html`), and
  which one they use depends on Android and browser version, so the app probes both.
- No dependencies at all: two Java files against the framework, built by six command line tools
  (`aapt2`, `javac`, `d8`, `zip`, `zipalign`, `apksigner`) in a Docker image. 21 KB APK.

## Requirements

Android 7.0 or newer, and at least one browser installed.

## License

MIT
