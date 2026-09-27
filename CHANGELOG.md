# Changelog

All notable changes to EverScreen are documented in this file.

This project does not yet follow a formal versioning policy beyond
straightforward semantic-style version numbers (`MAJOR.MINOR.PATCH`).

## [1.0.0] - 2026-09-27

### Added
- Initial release of EverScreen.
- Foreground service + wake lock that keeps the screen on independent of
  which app is in the foreground, with the required persistent Android
  notification and a one-tap Stop action.
- Live in-app countdown showing remaining time and the exact end time.
- **Duration mode**: quick-pick presets (15m / 30m / 1h / 2h) plus a
  1–180 minute slider.
- **Until time mode**: pick a target clock time via the standard Android
  time picker; automatically rolls over to the next day if that time has
  already passed today.
- Simple segmented control to switch between Duration and Until-time modes.
- Setup controls automatically hide while the timer is running, leaving a
  clean status view with just the countdown and a Stop button.
- Runtime request for notification permission on Android 13+.
- Custom adaptive app icon (sun-on-purple glyph) with legacy fallback icons
  for pre-Android 8 devices.
- MIT license.
