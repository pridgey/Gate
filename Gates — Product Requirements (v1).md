# Gates — Product Requirements (v1)

Oct 31, 2026 · @Taylor

## Summary

Gates is an Android home-screen launcher that puts friction only where people ask for it. Every app is either open as usual or behind one of three gates: **Pause** (pick a time, it opens), **Ask first** (name a reason, it opens with that reason's time) or **Vault** (fingerprint every time). Apps can have their own daily limit and share a group limit. Apps that run out of time wait **Upstairs** until the daily reset, which defaults to 6:00 am.

The promise: as fast as a normal launcher for everything you didn't gate, and one tap (never a hold or a puzzle) for everything you did. **Still mode** is an optional calmer layer for people who want it.

## Problem and evidence

People open a handful of apps on autopilot many times a day, and the tools that help are separate apps bolted on after the fact. The launcher already sits in front of every app launch, so it is the natural place for a gate.

- Adults receive about 146 notifications a day, roughly one every 10 minutes ([Workplace Insight, musicMagpie data](https://workplaceinsight.net/people-receive-a-phone-notification-every-ten-minutes-on-average/)).
- In a 2023 study, over half of teens received 237 or more notifications a day and checked their phones more than 100 times a day ([Common Sense Media](https://www.commonsensemedia.org/press-releases/teens-are-bombarded-with-hundreds-of-notifications-a-day)).
- Standalone pause apps such as one sec and ScreenZen show demand for a deliberate pause before opening distracting apps, but each needs separate setup and an accessibility permission.
- A second, separate need: banking, trading and smart-lock apps open with a single tap on an unlocked phone, including a pocket tap or a phone handed to a child.

## Goals and non-goals

**Goals**

- Opening a gated app takes one tap after the app icon, never a hold, a puzzle or required reading.
- Ungated apps open exactly as fast as on a standard launcher.
- Every gate, limit and group can be seen and changed from one place, the Gates app, or by long-pressing an icon.
- Users are never locked out of their own apps; going over a limit is possible and visible.
- Money and lock apps are protected by the system fingerprint prompt with no extra layer.

**Non-goals**

- Parental control or management of someone else's phone.
- Judging how people use apps: no streaks, scores or shaming copy.
- Reading content inside other apps. Gates only knows which app is open and for how long.
- Replacing Android's Digital Wellbeing or Private Space.

## Target users

| Who | What they want from Gates | Gates they'll use most |
| --- | --- | --- |
| The autopilot opener | Fewer reflexive opens of feeds and video without deleting the apps | Pause, Ask first, limits |
| The goal setter | A clear daily budget for a set of apps | Limits and groups, Upstairs |
| The careful owner | Money, password and lock apps that a pocket tap or a borrowed phone can't open | Vault |
| The overwhelmed | A calmer phone overall, especially at night | Still mode |

One person often fits several rows, so all gates live in one product rather than separate modes.

## Core concepts

**Gate types.** Each app has exactly one gate type.

| Gate | What happens on open | Icon badge | Time-limited |
| --- | --- | --- | --- |
| None | Opens immediately | None | Optional |
| Pause | A sheet offers time choices; tapping one opens the app for that long | Clock | Yes |
| Ask first | A sheet offers reasons, each with a time; tapping one opens the app; the reason is quoted back when time is up | ? | Yes |
| Vault | The system fingerprint prompt appears; success opens the app | Lock | No |

**Limits and groups.** An app can have its own daily limit and can belong to one group with a shared daily limit. The remaining time is whichever is smaller. The icon label shows that remaining time ("12m left").

**Upstairs.** A shelf above home with two sections: apps that are out of time (until the daily reset) and apps the user chose to put there. Upstairs apps keep their gates and still appear in All apps and search, tagged.

**Daily reset.** Limits refill once a day at the reset time, 6:00 am by default and user-adjustable.

**The Gates app.** A real app on the home screen that holds all gate settings. It has its own Pause gate, so the first gate a new user meets is a live example.

**Still mode.** An optional setting that adds a paced breath to gates and calmer visuals at night. Off by default.

## Key flows

&#91;embedded content: lifecycle of a gated open · Pause, Ask first and Vault\]

Apps with no gate open directly. Pause and Ask first lead to a timed session that ends in Close or More time. When the app's own or group limit is used up, it moves Upstairs until the reset; Vault apps open after the fingerprint with no timer.

Other flows in the prototype: long-press an icon to set its gate, open Upstairs from the bar at the top of home, and set up apps and groups in the Gates app.

## Functional requirements

P0 ships in the first release; P1 follows soon after; P2 is later.

| ID | Requirement | Priority |
| --- | --- | --- |
| HOME-1 | Home is a standard icon grid with dock, All apps (swipe up) and search. Swipe down on home opens notifications, as on other launchers. | P0 |
| HOME-2 | Gated icons show a badge for their gate type; time-limited icons show remaining time under the name. | P0 |
| HOME-3 | Long-press on any icon offers: Gate, Daily limit, Group, Move Upstairs, App info. | P0 |
| GATE-1 | Pause gate: a bottom sheet with 2–4 time choices plus Cancel. One tap on a time opens the app. No press-and-hold anywhere. | P0 |
| GATE-2 | Time choices never exceed the remaining time; the last chip reads "All Nm" when it equals what's left. | P0 |
| GATE-3 | Ask first gate: reasons with times, editable per app; free-text reason with a time picker; typed reasons are offered as one-tap options next time. | P0 |
| GATE-4 | Vault gate: tapping the icon shows the system biometric prompt (fingerprint, falling back to PIN). No vault tile or intermediate screen. | P0 |
| GATE-5 | Gates apply identically from home, All apps, search, Upstairs and shortcuts. | P0 |
| TIME-1 | During a timed session a timer shows remaining time (see Timer pill and dynamic island). Tapping it ends the session early. | P0 |
| TIME-2 | At zero, a sheet offers Close (default) and More time (drawn from what's left today). Ask first quotes the user's reason. | P0 |
| TIME-3 | When an app's own or group limit hits zero, it moves Upstairs until the reset. Opening it shows an out-of-time sheet with OK and Borrow from tomorrow. | P0 |
| UP-1 | An "Upstairs · N" bar at the top of home opens Upstairs with a tap. Hidden when Upstairs is empty. | P0 |
| UP-2 | Upstairs apps appear in All apps and search with a tag (UPSTAIRS, OUT OF TIME). | P0 |
| APP-1 | The Gates app lists gated apps grouped by gate type, plus a count of ungated apps and an Add a gate action. | P0 |
| APP-2 | Per-app screen: gate type, reasons (Ask first), daily limit, group, and "move Upstairs when out of time" toggle. | P0 |
| APP-3 | Groups screen: create, rename and delete groups; each shows used / total minutes today. | P0 |
| APP-4 | The Gates app is installed with its own Pause gate and a one-line explanation on first open. Users can remove it. | P0 |
| SET-1 | Settings: daily reset time, Still mode, timer display, optional gating of opens from notifications and recents. | P0 |
| STILL-1 | Still mode, as specified in its own section. | P1 |
| WEEK-1 | A weekly summary in the Gates app: time per gated app, opens cancelled at the gate, borrowing. No scores or streaks. | P1 |
| LEND-1 | Lending mode: hide chosen apps and all Vault apps while someone else uses the phone. | P2 |

## Timer pill and dynamic island

The session timer appears in the best place the phone offers, and never over full-screen content.

| Priority | Where the timer shows | When |
| --- | --- | --- |
| 1 | Inside a dynamic island, such as the one from the Essentials app | An island app is installed and Gates is connected to it |
| 2 | The system status-bar chip (Android Live Updates) | The phone supports Live Updates and the user allows it |
| 3 | Gates' own overhead pill, top centre, under the status bar | Neither of the above |

Requirements:

- **TIMER-1** The timer is hidden whenever the foreground app is in full-screen or immersive mode (video, games, camera, reading modes). Time keeps counting; the time's-up sheet still appears at zero.
- **TIMER-2** In the island or chip, the collapsed state shows the app name and remaining time ("Video · 4:12"); the expanded state adds End now and +5 min.
- **TIMER-3** The overhead pill is small, draggable left or right along the top edge, and never covers the status bar's own icons.
- **TIMER-4** In the last minute the timer changes state (an accent fill) without sound or vibration.
- **TIMER-5** Settings → Timer display: Automatic (default), Island, Status bar chip, Overhead pill, Off.

On Android 16, Live Updates puts an always-visible chip in the status bar for apps that post an ongoing progress-style notification ([Android Authority](https://www.androidauthority.com/android-16-qpr1-live-updates-3573399)). Whether Google allows a countdown of this kind there, and exactly how the Essentials island picks up an app's timer, still need confirming (see Open questions).

## Daily reset time

Limits refill at **6:00 am** by default, not midnight. A midnight reset rewards staying up: anyone awake at 12:01 gets a fresh allowance at the worst hour to have one.

- **RESET-1** Default reset time is 6:00 am local time.
- **RESET-2** Settings → Limits reset at: any time in 15-minute steps. Changing it applies from the next reset, never mid-day, so it can't be used to refill today.
- **RESET-3** Every "back at" message uses the actual reset time ("Back at 6:00 am"), and "tomorrow" means the next reset, not the next calendar day.
- **RESET-4** Borrowed minutes come out of the allowance that starts at the next reset.
- **RESET-5** On a time-zone change, the reset follows local time; a day is never skipped or doubled.
- **RESET-6** Onboarding asks one question: "When does your day usually start?" (6:00 am preselected) and uses the answer.

## Still mode

Still mode is an optional layer for people who want a calmer phone. It is off by default, offered once during onboarding and never required to open anything.

- **STILL-1** Toggle in Settings and in the Gates app. Optional schedule: always, or only between set times (for example 9:00 pm to the reset time).
- **STILL-2 Calmer home.** Warm, low-contrast palette; the clock and app names in a quieter type; badges and time-left labels softened. Layout and icon positions do not change.
- **STILL-3 Breath on gates.** Pause and Ask first sheets open with one paced breath (about 4 seconds in, 6 out) shown as a slowly growing ring. The time choices are tappable throughout; the breath never blocks or delays a choice.
- **STILL-4 Soft exit.** At time's up, the screen fades to grayscale over a few seconds before the sheet appears. Requires the optional accessibility permission; without it, the sheet appears as normal.
- **STILL-5** Vault gates are unchanged in Still mode: the fingerprint prompt appears immediately.
- **STILL-6** No sounds, haptics or streaks are added by Still mode.

## Platform constraints, permissions and privacy

A launcher sees launches it starts itself, so Gates covers home, All apps, search, Upstairs and shortcuts out of the box. Opens from notifications, recents or other apps bypass any launcher; covering those needs an accessibility service, as standalone pause apps already use.

| Permission | Needed for | Required? |
| --- | --- | --- |
| Default home app | Being the launcher | Yes |
| Usage access | Time used per app, limits, Upstairs | Yes, for limits |
| Biometric prompt (no special permission) | Vault | No |
| Post notifications / Live Updates | Status-bar timer chip | Optional |
| Accessibility service | Gating opens from notifications and recents; detecting full-screen; Still mode grayscale | Optional, explained before asking |
| Island app integration | Timer in a dynamic island | Optional |

Privacy: all usage data, reasons typed into Ask first and settings stay on the device. No account is required, and there are no analytics on app names or reasons. Backup, if added, is opt-in.

## Success metrics

Measured on-device and shown to the user in the weekly summary; any aggregate reporting is opt-in and carries no app names. Targets are open until the beta sets baselines.

| Metric | What it tells us | Target |
| --- | --- | --- |
| Day-30 retention as default launcher | The launcher is liveable day to day | \[TARGET\] |
| Share of gate sheets that end in Cancel | The pause is changing decisions | \[TARGET\] |
| Share of timed sessions closed at or before time's up | Users honor the time they chose | \[TARGET\] |
| Minutes in gated apps, week 4 vs week 1 | The core outcome | \[TARGET\] |
| Users who disable all gates within 14 days | Friction feels like nagging | Below \[TARGET\] |
| Users with at least one Vault app | Reach beyond screen-time users | \[TARGET\] |

## Risks and open questions

**Risks**

- **Back doors.** Without the accessibility service, opening a gated app from a notification skips the gate. Mitigation: say so plainly in setup and offer the upgrade.
- **Nagging.** Friction that feels like scolding gets the launcher uninstalled. Mitigation: opt-in per app, neutral copy, Cancel and Close always one tap.
- **Platform overlap.** Google could ship similar features in Digital Wellbeing or the Pixel launcher. Mitigation: Gates' edge is that all gates, limits and Upstairs live in the home screen itself.
- **Accessibility-permission trust.** Users are wary of this permission, and Play Store policy scrutinizes its use. Mitigation: keep it optional and document exactly what it reads.

**Open questions**

- [ ] How does the Essentials app's dynamic island receive another app's timer: a public API, ongoing notifications, or a media-style session?
- [ ] Are countdown timers allowed as Android Live Updates, and on which phones is the chip enabled?
- [ ] Should More time at time's up be capped per session (for example twice) or only by the daily limit?
- [ ] Should Borrow from tomorrow have a ceiling?
- [ ] Can a group contain a Vault app, or are Vault apps never time-limited?
- [ ] Does Upstairs need its own gate, or is reaching it a tap with no friction?

## Milestones

Dates are open; each release ships when its exit criteria are met.

| Release | Scope | Exit criteria |
| --- | --- | --- |
| 1. Prototype | Clickable flows for Pause, Ask first, Vault, Upstairs, the Gates app | 5–8 user sessions; no one needs help to open a gated app |
| 2. Private beta | All P0 requirements; overhead pill timer; 6:00 am reset with setting | Daily use as default launcher for 2 weeks without reverting |
| 3. Public beta | Still mode, weekly summary, status-bar chip and island timers, optional accessibility service | Baselines set for every success metric |
| 4. 1.0 | Polish, Play Store listing, accessibility review | Day-30 retention at or above target |
| Later | Lending mode | Demand confirmed in beta feedback |
