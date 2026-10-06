# Unscroll Roadmap

Each milestone = one PR. Don't start the next until the previous is merged and tested on a real device.

## M0: Project setup
- Android project (Kotlin, Compose, Material 3, Hilt, Room, DataStore), package `com.unscroll.app`
- CI: GitHub Actions running `./gradlew lint test assembleDebug`
- Theme, navigation skeleton with 3 tabs: Dashboard, Apps, Settings
- **Done when**: app launches, CI is green.

## M1: Permissions onboarding
- Welcome -> explanation screens -> deep link to Usage Access, Overlay, Notifications
- Detect granted/not granted state, re-check on resume
- Battery optimization guidance screen (with OEM-specific hints)
- **Done when**: user can't reach the dashboard until required permissions are granted (with a skip for optional ones).

## M2: Foreground detection + session logging
- `TrackingService` (foreground, notification channel)
- `AppDetector` polls UsageStatsManager events (~1s) only while the screen is on
- `SessionManager` opens/closes sessions with debounce (short app switches under ~3s don't split a session)
- Room `sessions` table + DAO + repository
- Recover orphaned sessions after process death/reboot
- **Done when**: opening Instagram/TikTok/Facebook creates a session; leaving closes it; survives service restart.

## M3: Dashboard
- Today / Week / Month / All-time per app
- Opens count, average session, longest session
- "Hours invested" reframing (days spent, books read equivalent)
- Time-of-day heatmap, trend vs last week
- **Done when**: numbers match a manual stopwatch test within a few seconds.

## M4: Live overlay timer
- Small floating pill at top of screen via WindowManager `TYPE_APPLICATION_OVERLAY`
- Shows current session time, optional today's total, updates every second
- Color escalation: green -> yellow -> red (configurable thresholds)
- Draggable, user can hide/show, hides when the tracked app leaves foreground
- **Done when**: timer ticks smoothly in all 3 apps and disappears immediately on exit.

## M5: Limits + block screen
- Per-app daily limit, block always, block on schedule
- `BlockActivity` shown when a blocked app opens (send user to home on dismiss)
- Cooldown: unblock/limit changes take effect after N minutes or require typing a phrase
- **Done when**: blocked app can't be used without passing friction.

## M6: Friction + nudges
- Pause screen with 10s breathing prompt before the app opens
- "You've opened Instagram 14 times today" notifications
- Break reminders at set intervals
- Optional grayscale/limit exceeded warning screen
- **Done when**: each feature can be toggled per app.

## M7: Accessibility Service (optional)
- Opt-in scroll counting (TYPE_VIEW_SCROLLED), "take a break" after N swipes
- More reliable foreground detection
- Separate explanation + Play Store disclosure text
- **Done when**: scroll count per session shows on the dashboard.

## M8: Polish + release
- Streaks, weekly report, goals
- Settings: data export (CSV), delete all data
- App icon, screenshots, privacy policy, Data Safety form
- Internal testing track -> closed testing (Play requires testers for new personal accounts) -> production
