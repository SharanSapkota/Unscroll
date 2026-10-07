# Unscroll Roadmap

Each milestone = one PR. Don't start the next until the previous is merged and tested on a real device.

## M0: Project setup ✅ Done
- Android project (Kotlin, Compose, Material 3, Hilt, Room, DataStore), package `com.unscroll.app`
- CI: GitHub Actions running `./gradlew lint test assembleDebug`
- Theme, navigation skeleton with 3 tabs: Dashboard, Apps, Settings
- **Done when**: app launches, CI is green.

## M1: Permissions onboarding ✅ Done
- Welcome -> explanation screens -> deep link to Usage Access, Overlay, Notifications
- Detect granted/not granted state, re-check on resume
- Battery optimization guidance screen (with OEM-specific hints)
- **Done when**: user can't reach the dashboard until required permissions are granted (with a skip for optional ones).

## M2: Foreground detection + session logging ✅ Done
- `TrackingService` (foreground, notification channel)
- `AppDetector` polls UsageStatsManager events (~1s) only while the screen is on
- `SessionManager` opens/closes sessions with debounce (short app switches under ~3s don't split a session)
- Room `sessions` table + DAO + repository
- Recover orphaned sessions after process death/reboot
- **Done when**: opening Instagram/TikTok/Facebook creates a session; leaving closes it; survives service restart.

## M3: Dashboard ✅ Done
- Today / Week / Month / All-time per app
- Opens count, average session, longest session
- "Hours invested" reframing (days spent, books read equivalent)
- Time-of-day heatmap, trend vs last week
- **Done when**: numbers match a manual stopwatch test within a few seconds.

## M4: Live overlay timer ✅ Done
- Small floating pill at top of screen via WindowManager `TYPE_APPLICATION_OVERLAY`
- Shows current session time, optional today's total, updates every second
- Color escalation: green -> yellow -> red (configurable thresholds)
- Draggable, user can hide/show, hides when the tracked app leaves foreground
- **Done when**: timer ticks smoothly in all 3 apps and disappears immediately on exit.

## M5: Limits + block screen ✅ Done
- Per-app daily limit, block always, block on schedule
- `BlockActivity` shown when a blocked app opens (send user to home on dismiss)
- Changes apply immediately (a cooldown with a typed-phrase unlock was built here and later removed by design: no cooldown, changes apply immediately)
- **Done when**: a blocked app can't be used until the user changes its limits.

## M6: Friction + nudges ✅ Done
- No pause screen: apps open instantly, the live timer is the stopper (a pause screen was built and later removed by design)
- "You've opened Instagram 14 times today" notifications
- Break reminders at set intervals
- Optional grayscale/limit exceeded warning screen
- **Done when**: each feature can be toggled per app.

## M7: Accessibility Service (optional) ✅ Done
- Opt-in scroll counting (TYPE_VIEW_SCROLLED), "take a break" after N swipes
- More reliable foreground detection (not built: foreground detection still uses UsageStatsManager; window events only speed up the swipe-limit cover)
- Separate explanation + Play Store disclosure text
- **Done when**: scroll count per session shows on the dashboard.

## M8: Polish + release ✅ Done (code and docs; the Play release steps are manual, see docs/RELEASE.md)
- Streaks, weekly report, goals
- Settings: data export (CSV), delete usage history
- App icon, screenshots, privacy policy, Data Safety form
- Internal testing track -> closed testing (Play requires testers for new personal accounts) -> production

## M9: Swipe limit (hard stop)
- Swipe count on the live pill, and pill color by swipes (configurable thresholds)
- Per-app swipe limit (off by default; 25/50/100/200/300 or custom; per day or per session with a reset gap)
- "N swipes left" on the pill from 80 %
- When reached: a full-screen, touch-blocking cover over the app ("Swipe limit reached", swipes, time today, "Go home"), every time the app opens until the limit resets; block screen + Home as fallback without the overlay permission
- Changes apply immediately like any limit; optional "I need access" (one tap, +20 swipes), logged with the other extensions
- **Done when**: the cover appears at the limit, no touch reaches the app, and the user can always go home.

## Design change after M9: no cooldown ✅ Done (awaiting device testing)
- No cooldown: changes apply immediately. Turning off "Block completely", raising or removing a limit, or disabling a schedule takes effect at once; no pending changes, countdown, Cancel or typed phrase
- Removed the friction settings (wait vs typed phrase, cooldown length) and the debug 10-second cooldown; their DataStore keys are deleted on upgrade
- Room v7 applies any pending change, then drops the pending columns
- An open block screen closes as soon as the app is no longer blocked
- "I need access" on the block screen and the swipe-limit cover is one tap, still logged
- **Done when**: unblocking an app in the Apps tab lets it open right away, and a block screen showing for it disappears.

## Fix after M9: swipe-limit cover crash ✅ Done (awaiting device testing)
- The cover crashed the app when the limit was reached: the Compose owners were set on the ComposeView, but Compose looks them up from the window's root frame
- One cover window at most, added and removed on the main thread, window errors logged with the block screen + Home as fallback, shown once per visit instead of on every swipe (`SwipeCoverTracker`)
- **Done when**: reaching a swipe limit shows the cover without a crash, no touch reaches the app, and "Go home" always works.
