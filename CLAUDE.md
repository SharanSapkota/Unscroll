# Unscroll

Android app that helps people stop doomscrolling. It detects when Instagram, TikTok, or Facebook is in the foreground, shows a live ticking session timer as a floating overlay, logs every session, lets the user block apps, and shows a dashboard of time invested.

## Current state
Last updated with M5. Keep this section in sync when a milestone lands.

- **Done**: M0 (project setup, CI), M1 (permissions onboarding), M2 (foreground detection and session logging), M3 (dashboard) and M4 (overlay timer). M5 (limits and blocking) is implemented and awaiting device testing. See ROADMAP.md.
- **Build**: AGP 8.13, Kotlin 2.2, Gradle 8.14 wrapper, compileSdk/targetSdk 36, KSP for Hilt and Room. Versions live in `gradle/libs.versions.toml`. CI (`.github/workflows/ci.yml`) runs `./gradlew lint test assembleDebug` on every PR and on pushes to `main`.
- **App shell**: `MainActivity` (edge-to-edge) → `UnscrollRoot`, which uses `AppViewModel`/`AppGate` to pick onboarding or the main app. The main app (`UnscrollApp`) is a bottom bar with Dashboard, Apps and Settings. `MainActivity.onResume` refreshes permissions and restarts tracking if it is enabled.
- **Onboarding** (`ui/onboarding`, `domain/onboarding`): Welcome → Usage Access → Overlay → Notifications → Battery (with OEM hints). Navigation rules are pure Kotlin in `OnboardingFlow`. The current step is kept in `SavedStateHandle`.
- **Permissions**:
  - `PermissionRepository` (`data/permission`) exposes a `StateFlow<PermissionState>` plus per-permission flows.
  - Usage Access and Overlay are required; Notifications and battery optimization are optional.
- **Tracking** (`service/`):
  - `TrackingService`: specialUse foreground service with a low-importance notification, `START_STICKY`.
  - It runs `SessionManager.run()` (pure Kotlin, injectable `Clock`, 3 s debounce, 5 s heartbeat, orphan recovery on start), `OverlayTimerManager.run()` and `BlockEnforcer.run()`.
  - `SessionManager.currentSession` stays set during the debounce window. `foregroundSession` clears the moment the user leaves; the overlay uses that one.
  - `AppDetector` polls `UsageStatsManager.queryEvents` every ~1 s, only while `ScreenStateMonitor` reports the screen on.
  - `ForegroundTracker` (`domain/tracking`) turns usage events into the foreground package.
  - `TrackingController` turns tracking on and off (Settings switch).
  - `BootReceiver` restarts tracking after a reboot or app update.
  - Tracked packages live in `domain/tracking/TrackedApps`, mirrored in the manifest `<queries>`.
- **Storage**:
  - Room `UnscrollDatabase` (v2): `sessions` (`SessionEntity`/`SessionDao`/`SessionRepository`), plus `app_limits` and `block_overrides` (`BlockingDao`/`LimitRepository`).
  - Migrations are explicit (`data/db/Migrations.kt`, `ALL_MIGRATIONS`); never use destructive fallback. `MigrationTest` builds the old schema by hand and migrates it.
  - The schema is exported to `app/schemas/` by the `androidx.room` Gradle plugin. Don't use the `room.schemaLocation` KSP argument: parallel variants race on the same file.
  - Preferences DataStore (`user_preferences`) holds the onboarding flag, the tracking switch and the session heartbeat (`TrackingPreferences`).
- **Dashboard** (`ui/dashboard`, `domain/insights`):
  - Today's total with a trend against yesterday at the same time of day.
  - A period selector (Today, Week = last 7 days, Month = last 30 days, All time) driving per-app cards (time, opens, average, longest), plus an "All apps" summary.
  - A Canvas hour-of-day heatmap, an "hours invested" card, and a Canvas chart of the last 7 days vs the 7 before.
  - Data flow:
    - SQL in `SessionDao` clips sessions to a range (sessions crossing midnight are split; open sessions run until `now`).
    - `UsageMath` splits sessions into local days and hours (DST-safe).
    - Use cases (`GetPeriodUsageUseCase`, `GetTodayTrendUseCase`, `GetWeekComparisonUseCase`, `GetHoursInvestedUseCase`) sit on `UsageDataSource` (implemented by `UsageRepository`).
    - Conversion constants live in `Equivalents`.
  - `DashboardViewModel` exposes one `StateFlow<DashboardUiState>`. It refreshes on DB changes, every second while a session is open, and every minute otherwise, only while collected.
- **Overlay timer** (`overlay/`, `domain/overlay`):
  - The pill lives in a `TYPE_APPLICATION_OVERLAY` window (`OverlayWindow`: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, wrap content, so touches outside it pass through).
  - Compose runs in a `ComposeView` with its own `OverlayLifecycleOwner` (lifecycle, ViewModelStore and SavedStateRegistry).
  - `OverlayTimerManager` (owned by `TrackingService`) shows it while `foregroundSession` is set, the overlay is enabled, and `Settings.canDrawOverlays` holds (re-checked every 2 s). It hides it on leave, screen off, tracking stop and service destroy.
  - Pure rules: `PillRules` (mm:ss / h:mm:ss, green/yellow/red levels, `shouldShow`) and `PillPositioner` (clamping to screen minus status bar, cutout and nav bar; top-center default).
  - Settings live in `OverlayPreferences` (DataStore): on/off, today's total, thresholds (10/20 min), size, opacity, position per orientation.
  - Settings screen: a live preview and "Reset position". Tap the pill to collapse it to a dot.
- **Limits and blocking** (`domain/blocking`, `data/blocking`, `service/BlockEnforcer`, `ui/apps`, `ui/block`):
  - Per app: daily limit, "Block completely", and a schedule (days plus a start/end time, which may cross midnight).
  - `BlockEvaluator` (pure, injectable `Clock`) returns `Allowed(remainingMillis)` or `Blocked(reason, until)`. Order of precedence: always, then schedule, then daily limit; an extension only lifts the daily limit.
  - `LimitChangePolicy`: stronger changes apply at once; anything weaker becomes a pending change after the cooldown (default 10 min). Pending changes are applied on read by `LimitRepository.getLimit`/`applyDueChanges`.
  - `FrictionPolicy` applies the same rule to the friction settings themselves (`BlockingPreferences`: wait vs typed phrase, cooldown length).
  - `BlockEnforcer` re-evaluates when the limit or extension runs out (at least every 15 s). When blocked, it sends the user home and opens `BlockActivity` in its own task (Back disabled).
  - "I need access" is offered only for the daily limit. It needs the typed phrase or a 30 s wait, grants 5 min, and is logged in `block_overrides`.
  - `BlockSafety` only allows tracked packages, and never Unscroll, launchers, Settings, the dialer or emergency apps.
- **Debug tools**: in debug builds, Settings has "Insert sample data", which seeds 30 days of sessions (`SampleSessionGenerator`/`SampleDataSeeder`).
- **Not built yet**: pause screens and nudges (M6), Accessibility Service (M7).
- **Tests**: JVM unit tests only (`app/src/test`):
  - Pure domain logic, `SessionManager` with a fake clock (virtual time) and fakes.
  - Repositories with fakes or a temp-file DataStore, and ViewModels via `MainDispatcherRule`.
  - Room DAO tests with an in-memory DB under Robolectric (`@Config(application = Application::class)`, SDK 34 via `robolectric.properties`).
  - Shared fakes are in `testing/Fakes.kt`.
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
- `AppLimitEntity(packageName, dailyLimitMinutes?, blockedAlways, scheduleEnabled, scheduleDays bitmask, scheduleStartMinute, scheduleEndMinute, pendingChangeJson?, pendingChangeAppliesAt?)`
- `BlockOverrideEntity(id, packageName, grantedAt, expiresAt, method)` logs every "I need access" extension
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
