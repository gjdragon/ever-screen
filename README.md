# EverScreen

A tiny, focused Android app that does one thing: keeps your screen awake for a
duration you choose — even while you're using another app.

Unlike the built-in "stay awake while charging" developer option, EverScreen
works on battery, on demand, for exactly as long as you tell it to.

## Features

- **Two ways to set the duration**
  - **Duration mode** — pick a length from 1 minute to 3 hours via quick presets
    (15m / 30m / 1h / 2h) or a slider.
  - **Until time mode** — pick a clock time and EverScreen keeps the screen on
    until then (rolls over to tomorrow if the time you pick has already passed
    today).
- **Works in the background.** EverScreen runs as a foreground service holding
  a wake lock, so the screen stays on even after you switch to another app —
  not just while EverScreen itself is on screen.
- **Live countdown**, shown both in the app and in the persistent notification
  Android requires for foreground services, with a one-tap **Stop** action
  right in the notification.
- No ads, no accounts, no permissions beyond what's needed to do the job.

## How it works

Android normally lets an *Activity* keep the screen on only while that
specific screen is visible (`FLAG_KEEP_SCREEN_ON`). EverScreen instead starts
a foreground `Service` that acquires a `PowerManager` CPU wake lock (`PARTIAL_WAKE_LOCK`)
and displays an invisible, transparent overlay window (`TYPE_APPLICATION_OVERLAY`)
with `FLAG_KEEP_SCREEN_ON`. This keeps the display awake across all apps and the home
screen on modern Android versions. Android requires a visible notification for any
running foreground service — that's the notification you'll see while EverScreen is active,
with a countdown and a Stop button.

## Requirements

- Android Studio (Koala or newer recommended)
- Android SDK 34 (compileSdk / targetSdk), minSdk 24 (Android 7.0+)
- Kotlin 1.9.24

## Building

```bash
git clone https://github.com/<your-username>/EverScreen.git
cd EverScreen
```

Open the folder in Android Studio, let it sync, then Run. On first launch
(Android 13+) it will ask for notification permission — this is needed to
show the required foreground-service notification and its countdown/Stop
action.

## Permissions used

| Permission | Why |
|---|---|
| `WAKE_LOCK` | Holds a partial wake lock to keep CPU active for countdown timer |
| `SYSTEM_ALERT_WINDOW` | Displays a transparent overlay window with `FLAG_KEEP_SCREEN_ON` to keep screen awake across all apps |
| `FOREGROUND_SERVICE` | Runs the keep-awake service in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required by Android 14+ to declare the foreground service's purpose |
| `POST_NOTIFICATIONS` | Shows the required foreground-service status notification (Android 13+) |

## Icon

The launcher icon is a bold "sun" glyph on the app's purple brand color,
symbolizing the screen staying bright. It's a full adaptive icon (separate
background/foreground/monochrome layers for Android 8+, so it displays
correctly as a circle, squircle, or rounded square depending on the launcher)
with legacy baked PNGs for older versions. A 512×512 store-listing version
lives in `docs/play_store_icon_512.png`.

## License

MIT — see [LICENSE](LICENSE).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
