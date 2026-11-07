# Gates

An Android launcher that puts friction only where you ask for it. See
[the PRD](<Gates — Product Requirements (v1).md>).

**Status: rough first draft.** Kotlin + Jetpack Compose, minSdk 30.

## Get the APK

Every push runs `.github/workflows/build-apk.yml`, which builds a debug APK and
attaches it to the run as an artifact (Actions tab → run → Artifacts).

Build locally (needs JDK 17 and the Android SDK):

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then pick Gates as the default home app.

## What's in this draft

- Home grid, dock, clock; swipe up for All apps + search, swipe down for notifications;
  rearrange home (long-press → Rearrange home, then drag)
- Gates: Pause, Ask first (reasons, typed reasons saved), Vault (system biometric prompt)
- Long-press any icon: gate, daily limit, group, Upstairs, home/dock, hide when lending, app info
- Daily limits and shared group limits from usage stats; "12m left" labels
- Upstairs bar and shelf; out-of-time sheet with Borrow from tomorrow
- Session timer: ongoing notification (requests the Android 16 Live Update chip),
  overhead pill (hidden in full-screen apps), time's-up sheet with Close / More time
- Daily reset at 6:00 am by default; changes apply from the next reset
- The Gates app: gated apps by type, weekly summary, groups, settings, first-run question
- Still mode: warm palette, breath ring on gate sheets, optional evening schedule,
  soft exit at time's up (true grayscale after
  `adb shell pm grant dev.pridgey.gates android.permission.WRITE_SECURE_SETTINGS`,
  otherwise a gray fade)
- Optional accessibility service: gates apps opened from notifications, recents and other
  apps; detects full screen; enforces Lending mode
- Lending mode: hides Vault apps and chosen apps; ending it needs your fingerprint

## Dynamic island (Essentials)

Gates is an [Essentials](https://github.com/sameerasw/essentials) island provider
(`app/src/main/java/dev/pridgey/gates/island/GatesIslandService.kt`), using the provider SDK
vendored in `island-sdk/` (MIT). To turn it on: Essentials → Island → Island apps → Gates timer
→ Allow. While a session runs the island shows the time left; it peeks in the last minute and
at time's up, long-press ends the session, and the expanded view has End now and +5 min.

Without pairing, Essentials still shows Gates' timer notification as a plain timer.

All builds are signed with `keystore/debug.keystore` (a debug key, not a secret) so CI APKs
install over each other and the Essentials pairing survives updates.

Gates → Diagnostics → **Timer & island diagnostics** logs pairing, connects, publishes and
taps (`[island]` lines), and can test other timer styles. Share the report or
`adb logcat -s GatesDiag`.

## Notes

- `QUERY_ALL_PACKAGES` and the diagnostics notification listener are for the investigation
  and would need review before a Play Store release.
- Full-screen detection is a heuristic (status bar window missing); check the `a11y` lines
  in the diagnostics log on your phone.
