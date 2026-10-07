# Unscroll

Android app that helps people stop doomscrolling. It detects when Instagram, TikTok, or Facebook is in the foreground, shows a live ticking session timer as a floating overlay, logs every session, lets the user block apps, and shows a Home screen of time invested.

## Current state
Last updated with the UI/UX redesign (after M9). Keep this section in sync when a milestone lands.

- **Done**: M0 (project setup, CI), M1 (permissions onboarding), M2 (foreground detection and session logging), M3 (dashboard), M4 (overlay timer), M5 (limits and blocking), M6 (friction and nudges), M7 (opt-in accessibility scroll counting) and M8 (goals, streaks, weekly report, data export/delete and release docs; the Play release steps themselves are manual, docs/RELEASE.md). M9 (hard swipe limit) is implemented and awaiting device testing. After M9: the cooldown/pending-change system was removed (changes apply immediately), the swipe-limit cover crash was fixed, and the UI was redesigned (Home, Apps, App detail, Settings, onboarding, pill and stop screens; presentation only). See ROADMAP.md.
- **Build**: AGP 8.13, Kotlin 2.2, Gradle 8.14 wrapper, compileSdk/targetSdk 36, KSP for Hilt and Room. Versions live in `gradle/libs.versions.toml`. CI (`.github/workflows/ci.yml`) runs `./gradlew lint test assembleDebug` on every PR and on pushes to `main`.
- **App shell**: `MainActivity` (edge-to-edge; theme from `AppearancePreferences`) → `UnscrollRoot`, which uses `AppViewModel`/`AppGate` to pick onboarding or the main app. The main app (`UnscrollApp`) is a bottom bar with Home, Apps and Settings plus a full-screen App detail route (`app/{packageName}`, `AppDetailRoute`, no bottom bar); screens fade through (200–300 ms). `MainActivity.onResume` refreshes permissions and the accessibility-service state, and restarts tracking if it is enabled.
- **Design system** (`ui/theme`, `ui/components`):
  - One theme file set: `Color.kt` (brand teal accent over restrained neutrals, light and dark; `StatusColors` good/warn/danger with containers, via `UnscrollTheme.status`; fixed `PillColors` for the floating pill), `Type.kt` (system font, bold numbers and headlines with tabular figures), `Tokens.kt` (`Shapes` 8–28 dp, `Dimens` spacing/sizes, `Motion` durations). Screens never hardcode colors or dp values.
  - The brand palette is the default; Material You dynamic color is an opt-in switch (Settings › Appearance, Android 12+, `AppearancePreferences`). Dark mode follows the system; the block screen, swipe cover, break screen and pill are always dark.
  - Components: `AppTile`, `StatCard`/`StatValue`/`UnscrollCard`, `SettingRow`/`SettingSwitchRow`/`SettingsGroup`, `ChipGroup`, `SegmentedControl`, `SectionHeader` (with an (i) button that opens `InfoSheet`), `PermissionRow`, `MiniBarChart`, `ProgressPill`/`LimitBar` (`ProgressLevel`: green < 80 % ≤ amber < 100 % ≤ red), `UnscrollSheet`, `EmptyState`, `LoadingPlaceholder`, `StopScreen`, `AppIcon`, pickers. Haptic ticks on switches, chips and segments; 48 dp touch targets; previews in light and dark (`@PreviewLightDark`).
  - Copy is short (1–3 word labels); help text lives behind (i) icons.
- **Onboarding** (`ui/onboarding`, `domain/onboarding`): two swipeable pages (`HorizontalPager`): Welcome (one line), then Permissions (Usage access and Overlay required, Notifications optional, each a `PermissionRow` with "Grant" that turns into a green check; plus Battery with OEM hints behind (i)). "Done" is enabled once both required permissions are granted. Rules are pure Kotlin in `OnboardingFlow`; the page is kept in `SavedStateHandle` (unknown saved names start over). The accessibility disclosure screen is unchanged.
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
  - Room `UnscrollDatabase` (v7): `sessions` (`SessionEntity`/`SessionDao`/`SessionRepository`), plus `app_limits` (v6 adds the swipe-limit columns, `MIGRATION_5_6`; v7 applies any pending change and drops `pendingChangeJson`/`pendingChangeAppliesAt`, `MIGRATION_6_7`, decoding the old JSON with `LegacyPendingChange`) and `block_overrides` (`BlockingDao`/`LimitRepository`), plus `app_friction` (v4 adds `swipeBreakAfter`; v5 drops the pause columns) and `nudge_log` (`FrictionDao`/`FrictionRepository`). v5 also drops the old `pause_outcomes` table (`MIGRATION_4_5`).
  - Migrations are explicit (`data/db/Migrations.kt`, `ALL_MIGRATIONS`); never use destructive fallback. `MigrationTest` builds the old schema by hand and migrates it.
  - The schema is exported to `app/schemas/` by the `androidx.room` Gradle plugin. Don't use the `room.schemaLocation` KSP argument: parallel variants race on the same file.
  - Preferences DataStore (`user_preferences`) holds the onboarding flag, the tracking switch and the session heartbeat (`TrackingPreferences`), plus the overlay, quiet-hours, goal, scroll-counting and appearance (dynamic color) preferences. `RemovedPreferencesMigration` (a DataStore migration) deletes the keys of removed features (the old cooldown/friction settings).
- **Home** (`ui/home`, `domain/insights`; replaced the Dashboard):
  - A "Tracking on/off" pill at the top (tap toggles tracking), then the hero: the period's total, huge, with a trend chip ("-18% vs yesterday" for Day, "vs last week" for Week) and, with a goal, a goal/streak pill.
  - A segmented control (Day / Week / Month / All) for the whole screen; a row of `AppTile`s (`HomeTiles`, pure: every installed tracked app, most used first, time and swipes in the period, a thin bar toward today's daily limit in the Day view).
  - "Hours invested" hero card (all-time hours, one equivalent; tap for books and flights), then a collapsible "Insights" section: hour heatmap, this week vs last (rounded bars, no gridlines), swipes, weekly report.
  - Data flow:
    - SQL in `SessionDao` clips sessions to a range (sessions crossing midnight are split; open sessions run until `now`).
    - `UsageMath` splits sessions into local days and hours (DST-safe).
    - Use cases (`GetPeriodUsageUseCase`, `GetTodayTrendUseCase`, `GetWeekComparisonUseCase`, `GetHoursInvestedUseCase`) sit on `UsageDataSource` (implemented by `UsageRepository`).
    - Conversion constants live in `Equivalents`.
  - The swipes card (per app: total, per session, per minute, via `GetScrollStatsUseCase` and `SessionDao.appScrollStats`) only shows once scroll counting was ever on, and only counts sessions since then. A banner asks to re-enable the service when the system switched it off.
  - Daily goal and streaks (`domain/goals`): `GoalPreferences` (global minutes per day, off by default). `GetStreakHistoryUseCase` builds complete days from the first tracked day (max 365) with the linear `UsageMath.totalsByDay`; `StreakRules` (pure) gives the streak ending yesterday and the best, and `withToday` adds today while it is within the goal. The goal pill on Home shows the time left (or over) and the current streak.
  - Weekly report (`GetWeeklyReportUseCase`): the last complete calendar week (locale's first day of week) against the week before: total, trend, daily average, busiest day, top app, opens and goal days. Shown as a "Week of …" card under Insights.
  - `HomeViewModel` exposes one `StateFlow<HomeUiState>` (it also observes the limits for the tiles). The live part refreshes on DB changes, every second while a session is open, and every minute otherwise; goal history and the weekly report reload only on DB changes and once a minute. Only while collected.
- **Overlay timer** (`overlay/`, `domain/overlay`):
  - The pill lives in a `TYPE_APPLICATION_OVERLAY` window (`OverlayWindow`: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`, wrap content, so touches outside it pass through).
  - Compose runs in a `ComposeView` with its own `OverlayLifecycleOwner` (lifecycle, ViewModelStore and SavedStateRegistry).
  - `OverlayTimerManager` (owned by `TrackingService`) shows it while `foregroundSession` is set, the overlay is enabled, and `Settings.canDrawOverlays` holds (re-checked every 2 s). It hides it on leave, screen off, tracking stop and service destroy.
  - Pure rules: `PillRules` (mm:ss / h:mm:ss, green/yellow/red levels, `shouldShow`) and `PillPositioner` (clamping to screen minus status bar, cutout and nav bar; top-center default).
  - Settings live in `OverlayPreferences` (DataStore): on/off, today's total, swipe count, time thresholds (10/20 min), swipe thresholds (50/100), size, opacity, position per orientation, and per app `pillHiddenFor`/`swipesHiddenFor` (App detail › Timer & nudges). `OverlaySettings.showsPillFor`/`showsSwipesFor` combine the global and per-app switches; with the pill off for an app, its break reminders and limit warnings go to notifications.
  - While scroll counting is on, the pill shows "12:41 · 86 swipes" (toggle in Settings), turns yellow/red by swipes too (`PillRules.levelForSwipes`/`combinedLevel`: whichever is further along), and shows "N swipes left" from 80 % of a swipe limit. `OverlayWindow.updateSwipes(PillSwipes)` updates it on every swipe without rebuilding the pill.
  - Look: a compact capsule with a soft shadow (`TimerPill`, `PillColors`), bold tabular time, smaller app name and swipe count, colors cross-fading between levels. Settings › Timer pill has a live preview at the top that follows the sliders (opacity, time and swipe color ranges), size S/M and "Reset position". Tap the pill to collapse it to a dot.
- **Limits and blocking** (`domain/blocking`, `data/blocking`, `service/BlockEnforcer`, `ui/apps`, `ui/block`):
  - Per app: daily limit, "Block completely", and a schedule (days plus a start/end time, which may cross midnight).
  - `BlockEvaluator` (pure, injectable `Clock`) returns `Allowed(remainingMillis)` or `Blocked(reason, until)`. Order of precedence: always, then schedule, then daily limit; an extension only lifts the daily limit.
  - **Design decision: no cooldown, changes apply immediately.** Every edit (Apps tab, App detail) goes straight to the stored settings (`LimitRepository.updateLimit`), stronger or weaker: no pending changes, countdown, Cancel or typed phrase. The card's status line ("No limits", "12 min left today", "Blocked", ...) updates with it. (A cooldown with pending changes and a typed-phrase unlock existed in M5–M9 and was removed.)
  - `BlockEnforcer` re-evaluates when the limit or extension runs out (at least every 15 s) and on every limit change. When blocked, it sends the user home and opens `BlockActivity` in its own task (Back disabled).
  - `BlockViewModel` re-checks every limit change after the block screen opened (`BlockScreenRules.current`, pure): once the app is no longer blocked (unblocked, limit raised, schedule off) the screen closes itself (`finishAndRemoveTask`), even from the background.
  - "I need access" is offered only for the daily limit. One tap grants 5 min (`AccessExtension.MILLIS`), logged in `block_overrides` with method `TAP` (older rows: `PHRASE`/`WAIT`).
  - `BlockSafety` only allows tracked packages, and never Unscroll, launchers, Settings, the dialer or emergency apps.
  - Apps tab (`AppsViewModel`): one row per app (icon, name, today's time, colored status line from `limitStatusText`/`limitStatusColor`, a Block switch) and "Block all"/"Unblock all". Tapping a row opens App detail.
  - App detail (`AppDetailViewModel`, `AppDetailSections`): header (icon, name, big time today, status), then three sections: Limits (daily limit chips Off/15m/30m/45m/1h/2h/Custom; swipe limit chips Off/25/50/100/200/Custom, per day/per session, reset gap, "Allow I need access"), Blocking (Block completely, schedule with day chips and From/To rows), Timer & nudges (pill and swipe count for this app, open nudges, break reminders, limit warnings, tint, swipe break, reset). Every change saves at once with a "Saved" snackbar. Mini stats: a 7-day bar chart, opens and average session.
  - The block screen and the swipe-limit cover share `StopScreen`: calm dark page, the app, a bold headline ("Limit reached"), the key number in the accent color, one big "Go home", and the optional one-tap "I need access".
- **Swipe limit (hard stop, M9)** (`domain/blocking/SwipeLimitRules`, `data/blocking/SwipeLimitRepository`, `service/SwipeLimitEnforcer`, `overlay/SwipeLimitCover`, `ui/apps/SwipeLimitSection`):
  - Per app, in `LimitSettings`/`app_limits`: `swipeLimit` (off by default; 25/50/100/200/300 or custom), `swipeLimitScope` (DAY default, or SESSION with `swipeSessionGapMinutes`, default 30) and `swipeAccessAllowed`. Like every limit, any change applies at once; raising or removing it takes the cover down right away.
  - `SwipeLimitRules` (pure): the window (local midnight, or the latest run of sessions with gaps shorter than the reset gap; back after the gap means a fresh window), `status` (used, +20 per extension, remaining, reached, 80 % warning).
  - `SwipeLimitEnforcer` evaluates on foreground changes, every swipe, limit changes and the accessibility window event (faster than usage stats). When reached, `SwipeLimitCover` covers the app: a full-screen `TYPE_APPLICATION_OVERLAY` that is touchable and focusable (consumes every touch and Back), with swipes, time today, "Go home", and "I need access" (one tap, +20 swipes, logged in `block_overrides` with method `SWIPES`) only if the user enabled it. It is shown again every time the app comes back until the window resets, and removed when the user leaves. Without the overlay permission: `GLOBAL_ACTION_HOME` via the service plus `BlockActivity` (`BlockReason.SWIPE_LIMIT_REACHED`). Without scroll counting nothing is enforced.
  - `SwipeCoverTracker` (pure) decides show/hide/fallback, under the enforcer's mutex: the cover (or the fallback) fires once per visit, not on every swipe; it hides when the limit is lifted or the app leaves the foreground; a failed add falls back.
  - `SwipeLimitCover` keeps one window: `isShowing` is set on a successful add (not on attach, a frame later), add/remove always run on the main thread (posted if needed), `canDrawOverlays` is checked first, and `BadTokenException`/`SecurityException`/`IllegalStateException` are logged and reported as a failed show, never thrown. The Compose owners (lifecycle, ViewModelStore, SavedStateRegistry) are set on the window's root frame and the `ComposeView` before adding: Compose resolves them from the window root, and owners only on the child crashed with "ViewTreeLifecycleOwner not found". On removal the composition is disposed and `OverlayLifecycleOwner.onDestroy` (safe to call twice) runs.
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
  - Consent: `AccessibilityDisclosureScreen` (Settings › Tracking › Scroll counting › Set up; its text is unchanged for Play review) must be accepted ("I agree") before the user is sent to Accessibility settings; "No thanks" keeps everything else working. `RestrictedSettingHelpScreen` explains the Android 13+ "Restricted setting".
  - `ScrollCountingPreferences` (consent, first connection) and `ScrollCountingRepository` (enabled in `Settings.Secure`, re-checked on resume; connected, as reported by the service). `ScrollCountingRules.status` (pure) gives OFF / NEEDS_CONSENT / NEEDS_ENABLING / ACTIVE / NEEDS_REENABLE. "Turn off" withdraws consent and the service calls `disableSelf()`.
  - Take a break: per-app `FrictionSettings.swipeBreakAfter` (off by default; 25/50/100/200). `SwipeBreakTracker` (pure) decides when; `FrictionCoordinator` opens `BreakActivity` (swipes, time, 15 s breathing countdown, "Keep scrolling" / "I'm done", Back disabled, Home always works). A blocked app gets the block screen instead.
  - Play docs: `docs/ACCESSIBILITY_DECLARATION.md` and the disclosure wording in `STORE_LISTING.md`.
- **Settings** (`ui/settings`): a permission health row at the top ("All set" in green, or "Fix 2 issues" in red, from `PermissionHealth`, pure) that opens a checklist sheet (Usage access, Overlay, Notifications, Battery, Accessibility when scroll counting is agreed but not running) with one-tap buttons to the system screens. Then four groups: Tracking (tracking switch, daily goal sheet, scroll counting sheet), Timer pill, Notifications (system notifications, quiet hours with From/To), Data & privacy (export, delete, privacy note behind (i)); plus Appearance (dynamic color, Android 12+) and, in debug builds only, Debug. Details open in bottom sheets, one level deep.
- **Your data** (`data/history`, `domain/export`, Settings): "Export CSV" writes `SessionCsv` (RFC 4180, ISO times with offset) to a file the user picks (`CreateDocument`, no storage permission). "Delete history" (`HistoryRepository`) ends the open session, then deletes sessions, the nudge log and the extension log in one transaction, and resets the swipe-stats start. Settings, limits, goal and consent stay on purpose.
- **Release docs** (`docs/`): `PRIVACY_POLICY.md`, `DATA_SAFETY.md` (no data collected or shared; no `INTERNET` permission), `ACCESSIBILITY_DECLARATION.md`, `RELEASE.md` (signing, testing tracks, per-release checks).
- **Debug tools**: in debug builds, Settings › Debug has "Insert sample data", which seeds 30 days of sessions (`SampleSessionGenerator`/`SampleDataSeeder`).
- **Not built yet**: adding other installed apps to the tracked list (the "+" on the Apps tab: needs a user-editable `TrackedApps`, launcher `<queries>` and a dynamic accessibility package list, so it is its own change); accessibility events for foreground detection (still `UsageStatsManager` only); a weekly report notification (would need WorkManager); per-app goals; R8/minify for release; the manual Play steps (signing, screenshots, testing tracks).
- **Tests**: JVM unit tests only (`app/src/test`):
  - Pure domain logic, `SessionManager` with a fake clock (virtual time) and fakes.
  - Repositories with fakes or a temp-file DataStore, and ViewModels via `MainDispatcherRule`.
  - Room DAO tests with an in-memory DB under Robolectric (`@Config(application = Application::class)`, SDK 34 via `robolectric.properties`).
  - Shared fakes are in `testing/Fakes.kt`.
- **Cloud sessions**: the Claude Code cloud environment can't reach `dl.google.com`/`maven.google.com`, so Android builds can't run there. CI is the source of truth for `lint test assembleDebug`.

## Principles
- **Privacy first**: all data stays on-device. No analytics SDKs, no network calls, no accounts in v1. Never read or store screen content, only package names and timestamps (plus swipe counts from the opt-in accessibility service).
- **Friction over hard blocks**: hard blocks get bypassed. Prefer the live timer, limits, and nudges. No cooldown: changes to limits and blocks apply immediately. No pause screen before an app opens: apps open instantly and the live timer is the stopper.
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
  onboarding/  home/  apps/  settings/  block/  scroll/  components/
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
- `AppLimitEntity(packageName, dailyLimitMinutes?, blockedAlways, scheduleEnabled, scheduleDays bitmask, scheduleStartMinute, scheduleEndMinute)`; v7 dropped `pendingChangeJson?`/`pendingChangeAppliesAt?`
- `AppLimitEntity` v6 adds `swipeLimit?`, `swipeLimitScope` ("DAY"/"SESSION"), `swipeSessionGapMinutes`, `swipeAccessAllowed`
- `BlockOverrideEntity(id, packageName, grantedAt, expiresAt, method)` logs every "I need access" extension: "TAP" (time, until `expiresAt`; "PHRASE"/"WAIT" before v7) or "SWIPES" (+20 swipes for the current swipe window)
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
