# Unscroll

Android app that helps people stop doomscrolling. It detects when Instagram, TikTok, or Facebook is in the foreground, shows a live ticking session timer as a floating overlay, logs every session, lets the user block apps, and shows a dashboard of time invested.

## Current state
Last updated after M1. Keep this section in sync when a milestone lands.

- **Done**: M0 (project setup, CI) and M1 (permissions onboarding). See ROADMAP.md.
- **Build**: AGP 8.13, Kotlin 2.2, Gradle 8.14 wrapper, compileSdk/targetSdk 36, KSP for Hilt and Room. Versions live in `gradle/libs.versions.toml`. CI (`.github/workflows/ci.yml`) runs `./gradlew lint test assembleDebug` on every PR and on pushes to `main`.
- **App shell**: `MainActivity` (edge-to-edge) → `UnscrollRoot`, which uses `AppViewModel`/`AppGate` to pick onboarding or the main app. The main app (`UnscrollApp`) is a bottom bar with Dashboard, Apps and Settings, all still placeholders.
- **Onboarding** (`ui/onboarding`, `domain/onboarding`): Welcome → Usage Access → Overlay → Notifications → Battery (with OEM hints). Navigation rules are pure Kotlin in `OnboardingFlow`. The current step is kept in `SavedStateHandle`.
- **Permissions**:
  - `PermissionRepository` (`data/permission`) exposes a `StateFlow<PermissionState>` plus per-permission flows. `MainActivity.onResume` calls `refresh()`.
  - Usage Access and Overlay are required; Notifications and battery optimization are optional.
  - Declared in the manifest: `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`.
- **Storage**: one preferences DataStore (`user_preferences`, provided by `DataStoreModule`) holding the onboarding-completed flag. Room is a dependency, but there is no database yet.
- **Not built yet**: tracking service, sessions/Room, overlay, blocking, real dashboard.
- **Tests**: JVM unit tests only (`app/src/test`): pure domain logic, repositories with fakes or a temp-file DataStore, and ViewModels via `MainDispatcherRule`. Shared fakes are in `testing/Fakes.kt`.
- **Cloud sessions**: the Claude Code cloud environment can't reach `dl.google.com`/`maven.google.com`, so Android builds can't run there. CI is the source of truth for `lint test assembleDebug`.

## Principles
- **Privacy first**: all data stays on-device. No analytics SDKs, no network calls, no accounts in v1. Never read or store screen content, only package names and timestamps (plus scroll counts later).
- **Friction over hard blocks**: hard blocks get bypassed. Prefer pause screens, cooldowns, and nudges.
- **Battery**: only poll or tick while a tracked app is in the foreground and the screen is on.
- **Play Store policy safe**: every sensitive permission must have a clear in-app explanation screen before the system prompt.

## Tech stack
- Kotlin, Jetpack Compose (Material 3), min SDK 26, target latest stable SDK
- Architecture: MVVM + Repository, single module to start (`app`)
- Room (sessions, settings), DataStore (preferences), Hilt for DI
- Coroutines + Flow, no RxJava
- WorkManager for periodic maintenance (e.g., closing orphaned sessions)
- Tests: JUnit5/JUnit4, Turbine for Flow, Room in-memory tests

## Package layout (`com.unscroll.app`)
```
data/        Room entities, DAOs, repositories, DataStore
domain/      models, use cases (pure Kotlin, unit-testable)
service/     TrackingService (foreground), AppDetector, SessionManager
overlay/     OverlayTimerManager (WindowManager overlay), BlockActivity
ui/          Compose screens, ViewModels, theme
  onboarding/  dashboard/  apps/  settings/  block/
util/        time formatting, permission helpers
```

## Tracked apps (package names)
- Instagram: `com.instagram.android`
- TikTok: `com.zhiliaoapp.musically` and `com.ss.android.ugc.trill`
- Facebook: `com.facebook.katana`
Keep this in one `TrackedApps` file so users can later add any installed app. Use `<queries>` in the manifest for package visibility (Android 11+). Do NOT use QUERY_ALL_PACKAGES.

## Permissions
- `PACKAGE_USAGE_STATS` (special access, user grants in Settings)
- `SYSTEM_ALERT_WINDOW` (overlay timer + block screen)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED` (restart tracking after reboot)
- Later, optional: AccessibilityService (scroll counting, more reliable foreground detection). Must be opt-in with its own explanation screen.

## Core data model
- `SessionEntity(id, packageName, startTime, endTime, scrollCount)`
- `AppLimitEntity(packageName, dailyLimitMinutes, blocked, blockMode, scheduleJson, cooldownUntil)`
- `DailyStat` is computed from sessions via queries, not stored
Sessions are logged from day one because Android only keeps short usage history.

## Rules for working in this repo
- Make small, focused PRs: one milestone (see ROADMAP.md) per branch/PR.
- Always add unit tests for domain logic and DAO tests for new queries.
- No hardcoded user-facing strings: use `strings.xml`.
- Support dark mode and edge-to-edge.
- Handle OEM battery killers (Xiaomi, Samsung, Huawei, OnePlus): onboarding must guide users to disable battery optimization.
- The service must recover from being killed: on restart, close any open session using the last known timestamp.
- Don't add libraries without saying why in the PR description.
- Run `./gradlew lint test assembleDebug` before finishing a task and report results.
- You cannot run an emulator in the cloud environment, so note anything that needs manual device testing in the PR description under "Manual test steps".

## Out of scope for now
iOS, accounts/cloud sync, social/accountability features, monetization. Revisit after the Android MVP is validated.
