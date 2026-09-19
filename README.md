# Floating Subtitles

A native Android app (Kotlin, no WebView) that displays an imported `.srt`/`.vtt`
subtitle file as a movable floating overlay on top of any other app — e.g. a
video player, browser, or streaming app. The app does **not** play video itself;
you play the video wherever you normally do, and this overlay floats on top of
it and advances subtitles on its own clock.

## How it works

- **MainActivity** — one-time setup screen: request the "draw over other apps"
  permission, pick a subtitle file (via the system file picker), and
  start/stop the overlay.
- **SubtitleOverlayService** — a foreground `Service` that adds a small window
  to the screen via `WindowManager`, of type `TYPE_APPLICATION_OVERLAY`. This
  window keeps floating and receiving touch input even while you're inside a
  different app, because the OS composites overlay windows on top of
  everything else. The service must run as a foreground service (with a
  persistent notification) or Android will kill it shortly after you leave
  the app — this is standard Android background-execution policy, not
  something specific to this app.
- **SubtitleParser** — hand-rolled parser for both `.srt` and `.vtt`,
  tolerant of missing SRT index numbers, VTT cue identifiers/settings lines,
  and `,` vs `.` millisecond separators.

## Overlay controls

Tap the small "⋮⋮ subtitles ⋮⋮" bar at the top of the overlay to show/hide the
control row. Drag that same bar to move the overlay anywhere on screen.

- **Play / Pause** — pauses the internal subtitle clock.
- **-5s / +5s** — jump the subtitle position, e.g. after you scrub the video.
- **A- / A+** — shrink/grow subtitle font size.
- **Op- / Op+** — decrease/increase the background opacity behind the text.
- **Sync -0.5s / +0.5s** — nudge subtitle timing without changing position,
  for fine drift correction against the video.
- **✕** — closes the overlay and stops the service.

There's no link between this app and whatever plays the video, so timing is
driven by its own clock starting from when you load the file. Use the sync
and skip controls to line it up with playback, the way you'd manually adjust
subtitles in any external-subtitle video setup.

## Building

This is a standard Gradle/Android Studio project (Kotlin DSL, AGP 8.5.2,
Kotlin 1.9.24, compileSdk/targetSdk 34, minSdk 26):

1. Open the `SubtitleOverlay/` folder in Android Studio (Koala or newer).
2. Let it sync Gradle (needs network access once, to fetch AGP/Kotlin/AndroidX
   dependencies — this environment has no internet, so the project was
   authored but not compiled here).
3. Run on a device/emulator running Android 8.0 (API 26) or newer.

No `gradlew` wrapper jar is bundled (it requires downloading the Gradle
distribution). Android Studio will offer to generate the wrapper on first
open, or run `gradle wrapper` yourself if you have Gradle installed locally.

## Permissions requested at runtime

- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — required for the
  floating overlay; Android routes this through a special Settings screen
  rather than a normal runtime prompt.
- **Notifications** (Android 13+) — needed so the required foreground-service
  notification is visible; without it the overlay still runs, you just won't
  see the "Subtitle overlay is active" notification.

## Known limitations

- Some OEM Android skins (certain Xiaomi/Oppo/Vivo builds) impose extra
  battery-optimization or "display over other apps" restrictions beyond
  stock Android; if the overlay disappears after backgrounding, check the
  device's battery/app-permission settings for this app.
- Subtitle timing is independent of the video, by design (no video player
  integration), so you'll periodically use the skip/sync buttons to keep it
  aligned — same as any standalone subtitle-overlay tool.

## Building from a phone with GitHub Actions

This project includes `.github/workflows/build-apk.yml`. It builds the debug APK in GitHub's cloud using Java 17, Gradle 8.7, and Android SDK 34. See `README_GITHUB_ACTIONS.md` for phone-only instructions. No Android Studio or local Gradle installation is required.
