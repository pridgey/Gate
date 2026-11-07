# Gates

An Android app that puts friction only where you ask for it: pick an app, give it a gate,
and Gates steps in whenever that app opens. It works alongside whatever launcher you use.
See [the PRD](<Gates — Product Requirements (v1).md>) (written when Gates was a launcher;
the home screen parts no longer apply).

**Status: rough first draft.** Kotlin + Jetpack Compose, minSdk 30.

## Get the APK

Every push to `main` publishes a GitHub Release (`v0.1.<run number>`) with the APK
attached; see the Releases page. Gates updates itself from these releases (Gates → Help →
Updates, plus a quiet check every 6 hours).

Pushes to other branches build the APK as a run
artifact only (Actions tab → run → Artifacts).

Build locally (needs JDK 17 and the Android SDK):

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then open Gates and allow **Turn on gates** (an accessibility service). Gates can't step
in without it.

## What's in this draft

- Gates: Pause, Ask first (reasons, typed reasons saved), Vault (system biometric prompt),
  shown over the app as it opens, from anywhere (home, notifications, recents, other apps)
- Add a gate → pick an app: gate, daily limit, shared limit, hide when lending, app info
- Daily limits and shared group limits from usage stats; out-of-time sheet with Borrow from tomorrow
- Going back to the home screen ends a session (after a 5-second grace, so a trip through
  recents back into the same app doesn't)
- Session timer: ongoing notification (requests the Android 16 Live Update chip),
  overhead pill (hidden in full-screen apps), time's-up sheet with Close / More time
- Daily reset at 6:00 am by default; changes apply from the next reset
- The Gates screen: setup checklist, gated apps by type, weekly summary, groups, settings,
  first-run question
- Still mode: warm palette, breath ring on gate sheets, optional evening schedule,
  soft exit at time's up (true grayscale after
  `adb shell pm grant dev.pridgey.gates android.permission.WRITE_SECURE_SETTINGS`,
  otherwise a gray fade)
- Accessibility service (required): shows gates, ends sessions on home, detects full
  screen, enforces Lending mode
- Lending mode: blocks Vault apps and chosen apps and locks Gates; ending it needs your fingerprint

## Dynamic island (Essentials)

Gates is an [Essentials](https://github.com/sameerasw/essentials) island provider
(`app/src/main/java/dev/pridgey/gates/island/GatesIslandService.kt`), using the provider SDK
vendored in `island-sdk/` (MIT). To turn it on: Essentials → Island → Island apps → Gates timer
→ Allow. While a session runs the island shows the time left; it peeks in the last minute and
at time's up, long-press ends the session, and the expanded view has End now and +5 min.

Without pairing, Essentials still shows Gates' timer notification as a plain timer.

Release builds are signed with a key kept out of the repo: CI reads it from the
`SIGNING_*` Actions secrets, and local builds use `~/.android/gates-signing/` if present.
Keep a backup of that folder; without it, installed copies can't be updated.

Gates → Diagnostics → **Timer & island diagnostics** logs pairing, connects, publishes and
taps (`[island]` lines), and can test other timer styles. Share the report or
`adb logcat -s GatesDiag`.

## Design

Calm and minimal: Manrope (OFL, `licenses/`), a near-neutral palette with one sage accent,
text-first home. The shared components live in `app/src/main/java/dev/pridgey/gates/ui/Kit.kt`.

Screenshots of the main screens are rendered on the JVM with Paparazzi:

```sh
./gradlew recordPaparazziDebug -Pscreens   # writes app/src/test/snapshots/images/
```

## Notes

- `QUERY_ALL_PACKAGES` and the diagnostics notification listener are for the investigation
  and would need review before a Play Store release.
- Full-screen detection is a heuristic (status bar window missing); check the `a11y` lines
  in the diagnostics log on your phone.
