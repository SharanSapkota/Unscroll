# Unscroll

Android app that helps people stop doomscrolling. It detects when Instagram, TikTok, or Facebook is in the foreground, shows a live ticking session timer as a floating overlay, logs every session, lets the user block apps, and shows a dashboard of time invested.

## Current state
Last updated with M9. Keep this section in sync when a milestone lands.

- **Done**: M0 (project setup, CI), M1 (permissions onboarding), M2 (foreground detection and session logging), M3 (dashboard), M4 (overlay timer), M5 (limits and blocking), M6 (friction and nudges), M7 (opt-in accessibility scroll counting) and M8 (goals, streaks, weekly report, data export/delete and release docs; the Play release steps themselves are manual, docs/RELEASE.md). M9 (hard swipe limit) is implemented and awaiting device testing. See ROADMAP.md.
- **Build**: AGP 8.13, Kotlin 2.2, Gradle 8.14 wrapper, compileSdk/targetSdk 36, KSP for Hilt and Room. Versions live in `gradle/libs.versions.toml`. CI (`.github/workflows/ci.yml`) runs `./gradlew lint test assembleDebug` on every PR and on pushes to `main`.
- **App shell**: `MainActivity` (edge-to-edge) → `UnscrollRoot`, which uses `AppViewModel`/`AppGate` to pick onboarding or the main app. The main app (`UnscrollApp`) is a bottom bar with Dashboard, Apps and Settings. `MainActivity.onResume` refreshes permissions and the accessibility-service state, and restarts tracking if it is enabled.
- **Onboarding** (`ui/onboarding`, `domain/onboarding`): Welcome → Usage Access → Overlay → Notifications → Battery (with OEM hints). Navigation rules are pure Kotlin in `OnboardingFlow`. The current step is kept in `SavedStateHandle`.
- **Permissions**:
  - `PermissionRepository` (`data/permission`) exposes a `StateFlow<PermissionState>` plus per-permission flows.
  - Usage Access and Overlay are required; Notifications and battery optimization are optional.
- **Tracking** (`service/`):
  - `TrackingService`: specialUse foreground service with a low-importance notification, `START_STICKY`.
  - It runs `SessionManager.run()` (pure Kotlin, injectable `Clock`, 3 s debounce, 5 s heartbeat, orphan recovery on start), `OverlayTimerManager.run()`, `BlockEnforcer.run()`, `FrictionCoordinator.run()` and `SwipeLimitEnforcer.run()`.
  - `SessionManager.currentSession` stays set during the debounce window. `foregroundSession` clears the moment the user leaves; the overlay uses that one.
  - `SessionManager` is the only writer of sessions, `scrollCount` included: `onSwipe(pkg)` adds a swipe to the foreground session and saves it; `swipes` exposes the running count.
  - `AppDetector` polls `UsageStatsManager.queryEvents` every ~1 s, only while `ScreenStateMonitor` reports the screen on.
  - `ForegroundTracker` (`domain/tracking`) turns usage events into the foreground package.
  - `TrackingController` turns tracking on and off (Settings switch).
  - `BootReceiver` restarts tracking after a reboot or app update.
  - Tracked packages live in `domain/tracking/TrackedApps`, mirrored in the manifest `<queries>`.
- **Storage**:
  - Room `UnscrollDatabase` (v6): `sessions` (`SessionEntity`/`SessionDao`/`SessionRepository`), plus `app_limits` (v6 adds the swipe-limit columns, `MIGRATION_5_6`) and `block_overrides` (`BlockingDao`/`LimitRepository`), plus `app_friction` (v4 adds `swipeBreakAfter`; v5 drops the pause columns) and `nudge_log` (`FrictionDao`/`FrictionRepository`). v5 also drops the old `pause_outcomes` table (`MIGRATION_4_5`).
  - Migrations are explicit (`data/db/Migrations.kt`, `ALL_MIGRATIONS`); never use destructive fallback. `MigrationTest` builds the old schema by hand and migrates it.
  - The schema is exported to `app/schemas/` by the `androidx.room` Gradle plugin. Don't use the `room.schemaLocation` KSP argument: parallel variants race on the same file.
  - Preferences DataStore (`user_preferences`) holds the onboarding flag, the tracking switch and the session heartbeat (`TrackingPreferences`), plus the overlay, blocking, quiet-hours, goal and scroll-counting preferences.
- **Dashboard** (`ui/dashboard`, `domain/insights`):
  - Today's total with a trend against yesterday at the same time of day.
  - A period selector (Today, Week = last 7 days, Month = last 30 days, All time) driving per-app cards (time, opens, average, longest), plus an "All apps" summary.
  - A Canvas hour-of-day heatmap, an "hours invested" card, and a Canvas chart of the last 7 days vs the 7 before.
  - Data flow:
    - SQL in `SessionDao` clips sessions to a range (sessions crossing midnight are split; open sessions run until `now`).
    - `UsageMath` splits sessions into local days and hours (DST-safe).
    - Use cases (`GetPeriodUsageUseCase`, `GetTodayTrendUseCase`, `GetWeekComparisonUseCase`, `GetHoursInvestedUseCase`) sit on `UsageDataSource` (implemented by `UsageRepository`).
    - Conversion constants live in `Equivalents`.
  - A "Swipes" card (swipes today; per app: total, per session, per minute, via `GetScrollStatsUseCase` and `SessionDao.appScrollStats`). It only shows once scroll counting was ever on, and only counts sessions since then. A banner asks to re-enable the service when the system switched it off.
  - Daily goal and streaks (`domain/goals`): `GoalPreferences` (global minutes per day, off by default). `GetStreakHistoryUseCase` builds complete days from the first tracked day (max 365) with the linear `UsageMath.totalsByDay`; `StreakRules` (pure) gives the streak ending yesterday and the best, and `withToday` adds today while it is within the goal. A goal card shows today against the goal and both streaks.
  - Weekly report (`GetWeeklyReportUseCase`): the last complete calendar week (locale's first day of week) against the week before: total, trend, daily average, busiest day, top app, opens and goal days. Shown as a "Week of …" card.
  - `DashboardViewModel` exposes one `StateFlow<DashboardUiState>`. The live part refreshes on DB changes, every second while a session is open, and every minute otherwise; goal history and the weekly report reload only on DB changes and once a minute. Only while collected.
- **Overlay timer** (`overlay/`, `domain/overlay`):
  - The pill lives in a `TYPE_APPLICATION_OVERLAY` window (`OverlayWindow`: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, wrap content, so touches outside it pass through).
  - Compose runs in a `ComposeView` with its own `OverlayLifecycleOwner` (lifecycle, ViewModelStore and SavedStateRegistry).
  - `OverlayTimerManager` (owned by `TrackingService`) shows it while `foregroundSession` is set, the overlay is enabled, and `Settings.canDrawOverlays` holds (re-checked every 2 s). It hides it on leave, screen off, tracking stop and service destroy.
  - Pure rules: `PillRules` (mm:ss / h:mm:ss, green/yellow/red levels, `shouldShow`) and `PillPositioner` (clamping to screen minus status bar, cutout and nav bar; top-center default).
  - Settings live in `OverlayPreferences` (DataStore): on/off, today's total, swipe count, time thresholds (10/20 min), swipe thresholds (50/100), size, opacity, position per orientation.
  - While scroll counting is on, the pill shows "12:41 · 86 swipes" (toggle in Settings), turns yellow/red by swipes too (`PillRules.levelForSwipes`/`combinedLevel`: whichever is further along), and shows "N swipes left" from 80 % of a swipe limit. `OverlayWindow.updateSwipes(PillSwipes)` updates it on every swipe without rebuilding the pill.
  - Settings screen: a live preview and "Reset position". Tap the pill to collapse it to a dot.
- **Limits and blocking** (`domain/blocking`, `data/blocking`, `service/BlockEnforcer`, `ui/apps`, `ui/block`):
  - Per app: daily limit, "Block completely", and a schedule (days plus a start/end time, which may cross midnight).
  - `BlockEvaluator` (pure, injectable `Clock`) returns `Allowed(remainingMillis)` or `Blocked(reason, until)`. Order of precedence: always, then schedule, then daily limit; an extension only lifts the daily limit.
  - `LimitChangePolicy`: stronger changes apply at once; anything weaker becomes a pending change after the cooldown (default 10 min). Pending changes are applied on read by `LimitRepository.getLimit`/`applyDueChanges`, so they take effect on time even if the app was closed.
  - The Apps cards show the pending *target* (a switch being turned off already shows off, dimmed, with "Turns off in 9:42"), plus a box per change ("Block turns off in 9:42", from `LimitChangePolicy.describe`) with a live countdown, Cancel and "Type phrase to unlock now" (in both friction modes). Edits go through `LimitChangePolicy.edit` (`LimitRepository.editLimit`): they apply to the target, keep the countdown unless they loosen it further, apply their stronger parts at once, and tapping a pending switch again undoes it.
  - `FrictionPolicy` applies the same rule to the friction settings themselves (`BlockingPreferences`: wait vs typed phrase, cooldown length).
  - `BlockEnforcer` re-evaluates when the limit or extension runs out (at least every 15 s). When blocked, it sends the user home and opens `BlockActivity` in its own task (Back disabled).
  - "I need access" is offered only for the daily limit. It needs the typed phrase or a 30 s wait, grants 5 min, and is logged in `block_overrides`.
  - `BlockSafety` only allows tracked packages, and never Unscroll, launchers, Settings, the dialer or emergency apps.
- **Swipe limit (hard stop, M9)** (`domain/blocking/SwipeLimitRules`, `data/blocking/SwipeLimitRepository`, `service/SwipeLimitEnforcer`, `overlay/SwipeLimitCover`, `ui/apps/SwipeLimitSection`):
  - Per app, in `LimitSettings`/`app_limits`: `swipeLimit` (off by default; 25/50/100/200/300 or custom), `swipeLimitScope` (DAY default, or SESSION with `swipeSessionGapMinutes`, default 30) and `swipeAccessAllowed`. It goes through `LimitChangePolicy` like the time limit: lowering applies at once; raising, removing, day→session, a shorter gap or allowing "I need access" wait out the cooldown (or the typed phrase).
  - `SwipeLimitRules` (pure): the window (local midnight, or the latest run of sessions with gaps shorter than the reset gap; back after the gap means a fresh window), `status` (used, +20 per extension, remaining, reached, 80 % warning).
  - `SwipeLimitEnforcer` evaluates on foreground changes, every swipe, limit changes and the accessibility window event (faster than usage stats). When reached, `SwipeLimitCover` covers the app: a full-screen `TYPE_APPLICATION_OVERLAY` that is touchable and focusable (consumes every touch and Back), with swipes, time today, "Go home", and "I need access" (typed phrase, +20 swipes, logged in `block_overrides` with method `SWIPES`) only if the user enabled it. It is shown again every time the app comes back until the window resets, and removed when the user leaves. Without the overlay permission: `GLOBAL_ACTION_HOME` via the service plus `BlockActivity` (`BlockReason.SWIPE_LIMIT_REACHED`). Without scroll counting nothing is enforced.
- **Friction and nudges** (`domain/friction`, `data/friction`, `service/FrictionCoordinator`):
  - **Design decision: no pause screen.** Apps open instantly; the live overlay timer is the stopper. The only screen that ever comes up when opening a tracked app is the block screen, for a blocked app or an exceeded limit. (A "mindful gate" pause screen existed in M6 and was removed.)
  - Per-app settings (`FrictionSettings` in `app_friction`; defaults until changed; "Reset to defaults") cover: open-count nudges (thresholds), break reminders (interval), limit warnings, an experimental tint and swipe breaks. They live under each Apps card ("Nudges and breaks").
  - `NudgeRules` (pure): open-count thresholds and 80/100 % limit warnings, each once per day via `nudge_log`. `BreakReminders`: every interval of a session.
  - Break reminders and limit warnings expand the pill (`OverlayMessages`, Keep going / Leave). Without the overlay they become notifications (`NudgeNotifier`; "nudges" and high-importance "breaks" channels).
  - `TintOverlay`: a gray, non-touchable overlay with alpha 0.45, shown while over the limit with an extension running.
  - `QuietHours` (global, DataStore, may cross midnight) silences nudges, break reminders and limit warnings, but never blocking or swipe breaks. Everything goes through `BlockSafety`.
- **Scroll counting (optional)** (`domain/scroll`, `data/scroll`, `service/ScrollAccessibilityService`, `ui/scroll`):
  - `ScrollAccessibilityService`: opt-in, `canRetrieveWindowContent="false"`, only `typeViewScrolled`/`typeWindowStateChanged`, only tracked packages (`res/xml/accessibility_service_config.xml`, kept equal to `TrackedApps` by `AccessibilityConfigTest` and set from `TrackedApps` on connect), `isAccessibilityTool="false"`. Reads only the event type and package name, and ignores everything without in-app consent. Window events go to `SwipeLimitEnforcer.onAppWindow`; its only action is `GLOBAL_ACTION_HOME`, the swipe limit's fallback.
  - `SwipeDetector` (pure, injectable `Clock`): scroll events within `SWIPE_BURST_GAP_MILLIS` (300 ms) of each other are one swipe; screen off, untracked apps and window changes end a burst. Each swipe goes to `SessionManager.onSwipe`.
  - Consent: `AccessibilityDisclosureScreen` (Settings › Scroll counting › Set up) must be accepted ("I agree") before the user is sent to Accessibility settings; "No thanks" keeps everything else working. `RestrictedSettingHelpScreen` explains the Android 13+ "Restricted setting".
  - `ScrollCountingPreferences` (consent, first connection) and `ScrollCountingRepository` (enabled in `Settings.Secure`, re-checked on resume; connected, as reported by the service). `ScrollCountingRules.status` (pure) gives OFF / NEEDS_CONSENT / NEEDS_ENABLING / ACTIVE / NEEDS_REENABLE. "Turn off" withdraws consent and the service calls `disableSelf()`.
  - Take a break: per-app `FrictionSettings.swipeBreakAfter` (off by default; 25/50/100/200). `SwipeBreakTracker` (pure) decides when; `FrictionCoordinator` opens `BreakActivity` (swipes, time, 15 s breathing countdown, "Keep scrolling" / "I'm done", Back disabled, Home always works). A blocked app gets the block screen instead.
  - Play docs: `docs/ACCESSIBILITY_DECLARATION.md` and the disclosure wording in `STORE_LISTING.md`.
- **Your data** (`data/history`, `domain/export`, Settings): "Export sessions (CSV)" writes `SessionCsv` (RFC 4180, ISO times with offset) to a file the user picks (`CreateDocument`, no storage permission). "Delete usage history" (`HistoryRepository`) ends the open session, then deletes sessions, the nudge log and the extension log in one transaction, and resets the swipe-stats start. Settings, limits, goal and consent stay on purpose.
- **Release docs** (`docs/`): `PRIVACY_POLICY.md`, `DATA_SAFETY.md` (no data collected or shared; no `INTERNET` permission), `ACCESSIBILITY_DECLARATION.md`, `RELEASE.md` (signing, testing tracks, per-release checks).
- **Debug tools**: in debug builds, Settings has "Insert sample data", which seeds 30 days of sessions (`SampleSessionGenerator`/`SampleDataSeeder`), and a "10-second cooldown" switch (`BlockingSettings.debugShortCooldown`, ignored in release builds) for testing pending changes.
- **Not built yet**: accessibility events for foreground detection (still `UsageStatsManager` only); a weekly report notification (would need WorkManager); per-app goals; R8/minify for release; the manual Play steps (signing, screenshots, testing tracks).
- **Tests**: JVM unit tests only (`app/src/test`):
  - Pure domain logic, `SessionManager` with a fake clock (virtual time) and fakes.
  - Repositories with fakes or a temp-file DataStore, and ViewModels via `MainDispatcherRule`.
  - Room DAO tests with an in-memory DB under Robolectric (`@Config(application = Application::class)`, SDK 34 via `robolectric.properties`).
  - Shared fakes are in `testing/Fakes.kt`.
- **Cloud sessions**: the Claude Code cloud environment can't reach `dl.google.com`/`maven.google.com`, so Android builds can't run there. CI is the source of truth for `lint test assembleDebug`.

## Principles
- **Privacy first**: all data stays on-device. No analytics SDKs, no network calls, no accounts in v1. Never read or store screen content, only package names and timestamps (plus swipe counts from the opt-in accessibility service).
- **Friction over hard blocks**: hard blocks get bypassed. Prefer the live timer, cooldowns, and nudges. No pause screen before an app opens: apps open instantly and the live timer is the stopper.
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
  onboarding/  dashboard/  apps/  settings/  block/  scroll/
util/        time formatting, permission helpers
```

## Tracked apps (package names)
- Instagram: `com.instagram.android`
- TikTok: `com.zhiliaoapp.musically` and `com.ss.android.ugc.trill`
- Facebook: `com.facebook.katana`
Keep this in one `TrackedApps` file so users can later add any installed app. Use `<queries>` in the manifest for package visibility (Android 11+). Do NOT use QUERY_ALL_PACKAGES.

## Permissions
- `PACKAGE_USAGE_STATS` (special access, user grants in Settings)
- `SYSTEM_ALERT_WINDOW` (overlay timer, swipe-limit cover + block screen)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED` (restart tracking after reboot)
- Optional: AccessibilityService for scroll counting (`BIND_ACCESSIBILITY_SERVICE`, M7). Opt-in, behind its own disclosure screen and in-app consent.

## Core data model
- `SessionEntity(id, packageName, startTime, endTime, scrollCount)`; `scrollCount` is filled only while the optional accessibility service runs
- `AppLimitEntity(packageName, dailyLimitMinutes?, blockedAlways, scheduleEnabled, scheduleDays bitmask, scheduleStartMinute, scheduleEndMinute, pendingChangeJson?, pendingChangeAppliesAt?)`
- `AppLimitEntity` v6 adds `swipeLimit?`, `swipeLimitScope` ("DAY"/"SESSION"), `swipeSessionGapMinutes`, `swipeAccessAllowed`
- `BlockOverrideEntity(id, packageName, grantedAt, expiresAt, method)` logs every "I need access" extension: "PHRASE"/"WAIT" (time, until `expiresAt`) or "SWIPES" (+20 swipes for the current swipe window)
- `AppFrictionEntity(packageName, nudgesEnabled, nudgeThresholds, breakRemindersEnabled, breakIntervalMinutes, limitWarningsEnabled, tintEnabled, swipeBreakAfter?)`
- `NudgeLogEntity(packageName, day, kind, value, sentAt)`
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
