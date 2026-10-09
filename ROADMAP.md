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
- **Done when**: user can't reach the dashboard until required permissions are granted (with a skip for optional ones). (Since "Tracking on by default": a permission revoked after onboarding shows a "Tracking paused" banner on Home instead of sending the user back to onboarding.)

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

## M9: Swipe limit (hard stop) ✅ Done (awaiting device testing)
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

## UI/UX redesign after M9 ✅ Done (awaiting device testing)
- Presentation only: tracking, blocking, overlay and accessibility logic unchanged (one exception, below)
- One theme (brand teal + neutrals, status green/amber/red, bold numbers, 20–28 dp shapes, spacing and motion tokens), Material You dynamic color as an option, dark mode first
- A small design system in `ui/components` reused everywhere; haptics, 48 dp targets, light/dark previews
- Home replaces the Dashboard: huge total with trend chip, Day/Week/Month/All, app tiles with limit bars, hours invested, collapsible Insights, tracking pill
- Apps tab: a control list with a Block switch per app and Block all/Unblock all; App detail: Limits, Blocking, Timer & nudges, mini stats, instant save with a snackbar
- Settings: permission health, then Tracking, Timer pill (live preview), Notifications, Data & privacy (+ Appearance, Debug in debug builds)
- Onboarding: two swipeable pages (welcome, permissions with Grant buttons)
- Restyled pill, block screen and swipe cover (shared calm dark `StopScreen`)
- The one logic change: the timer pill and its swipe count can be switched off per app (`OverlaySettings.pillHiddenFor`/`swipesHiddenFor`)
- Not in this change: adding other installed apps to the tracked list (needs a user-editable tracked list; its own milestone)
- **Done when**: every existing feature is reachable in 1–2 taps from the main screens and works as before.

## Fox mascot after the redesign ✅ Done (awaiting device testing)
- Presentation only: tracking, blocking, overlay and accessibility logic unchanged
- A geometric fox in the accent color as the brand symbol, all drawing behind one `FoxMascot` API (swappable for commissioned art)
- Purely reactive: it mirrors today's usage (happy, alert at 80 % of a time or swipe limit, concerned over a limit, sleepy with little use or tracking off) and never asks for anything. No points, streak pets, rewards or "come back" notifications
- Home (peeking fox with blink, ear twitch and tail wag; tap for a short message, 20+ per mood, never the same twice in a row), Apps tab, empty states, the collapsed pill (tiny fox face) and the block screen / swipe cover (concerned fox)
- Settings › Appearance: "Show fox" and "Fox messages", on by default, applied at once; idle animation stops with "Remove animations" and when the screen isn't visible
- Fox adaptive app icon (foreground, background, themed monochrome) and notification icon
- **Done when**: the fox shows the right mood on Home as usage crosses 80 % and 100 % of a limit, and switching it off hides it everywhere.

## Tracking on by default ✅ Done (awaiting device testing)
- Tracking defaults to on: a fresh install (never set) tracks; an explicit "off" from the user is kept
- The service starts by itself: when onboarding finishes with the required permissions, every time the app opens and it isn't running, when a missing permission (Usage access, Overlay) is granted later, and after a reboot or an app update
- Only from contexts Android allows (visible activity, boot/update broadcasts); a refused start is logged, never a crash, and retried the next time the app opens
- Missing permissions are never silent: Home shows "Tracking paused: fix permissions" with a one-tap button, and the Settings checklist shows green checks for what is done (Accessibility only once scroll counting is on)
- The Settings switch stays for turning it off on purpose; Home's pill says "Tracking off" with a tap to turn it back on. The overlay timer is on by default too
- **Done when**: a fresh install tracks right after onboarding without touching any switch, and comes back after a reboot.

## Final app icon ✅ Done (awaiting device testing)
- Presentation only: tracking, blocking, overlay and accessibility logic unchanged
- The launcher icon is built from `assets/icon/icon.svg` (fox face and a scroll bar with a red limit line): adaptive foreground inside the 66 dp safe zone, the dark teal gradient background, a themed monochrome layer, legacy PNGs; the old icon files are replaced
- White fox-face notification icon for the tracking notification
- The in-app fox (`FoxMascot`) now matches the icon's fox: same head shape and colors, all four moods kept; the collapsed pill shows it inside the level-colored dot
- **Done when**: the icon reads clearly at 48 and 72 dp under circle, squircle, rounded-square and square masks, on light, dark and wallpaper backgrounds and as a themed icon.

## Trust message ✅ Done (awaiting device testing)
- Presentation only: tracking, blocking, overlay and accessibility logic unchanged
- Onboarding permissions page: the privacy promise with the happy fox, one plain-language reason per permission, and a footer with "How we protect your data" (3 bullets in a sheet)
- Settings › Data & privacy: the same promise as a row, plus "Delete all my data" (confirmation; wipes Room and DataStore and returns to onboarding)
- Verified before shipping: no INTERNET permission, no network/analytics/crash SDKs, no accounts, backups off
- **Done when**: a new user sees why each permission is needed and that their data stays on the phone, and can delete everything from Settings.

## Pill shows today's total ✅ Done (awaiting device testing)
- The timer pill shows today's total in the app ("Instagram 47:12"), continuing where it left off on every visit and resetting at local midnight (also while showing)
- The base (earlier sessions today, clipped to midnight) is read once per visit; the open session is added from the clock every second; the total never steps back on the same day
- Optional "Show session time" ("this visit 3:05", off by default); today's swipes instead of the session's
- Colors by today's total: with a daily limit green under 60 %, yellow to 100 %, red at the limit; without one the daily thresholds (30/60 min by default). Settings and preview updated
- Break reminders, swipe breaks and per-session swipe limits still use the session; blocking and accessibility unchanged
- **Done when**: opening an app twice shows the total continue from the first visit, and it turns yellow at 60 % of a daily limit.

## Fox welcome and splash ✅ Done (awaiting device testing)
- Presentation only: tracking, blocking, overlay and accessibility logic unchanged
- Welcome page: the hourglass illustration is replaced by a large happy fox (idle motion, still with "Remove animations") that fades and slides in; bold "Unscroll" and "Stop doomscrolling."
- Launch splash with the SplashScreen API (androidx.core:splashscreen): the fox icon on dark teal, no extra delay
- Other illustrations were already the fox (empty states, permissions page, stop screens); the hourglass and timer drawables stay as small row icons
- **Done when**: a fresh launch shows the fox splash, then the welcome fox animating in.

## Launcher icon: fox and phone ✅ Done (awaiting device testing)
- Presentation only, no logic changes
- Adaptive icon from `assets/icon/icon-foreground.svg` (fox, phone with feed and red limit line) on solid #0F4142, a two-tone themed (monochrome) layer, the round icon and legacy PNGs; the old gradient background and layers are replaced
- The foreground is drawn at 90 %: as given, the phone's corners reached outside the 66 dp safe zone and circle masks clipped one
- Notification icon unchanged (white fox face); launcher label unchanged
- **Done when**: the icon reads clearly at 48 and 72 dp under circle, squircle and square masks, on light, dark and wallpaper backgrounds and as a themed icon.

## Freemium: Unscroll Plus ✅ Done (awaiting device testing)
- Free: 1 tracked app (`FreeTier.FREE_APPS`, one constant) with everything included: timer, limits, swipe limit, blocking, stats, fox. Plus (monthly subscription `unscroll_plus_monthly`, base plan `monthly`) only unlocks tracking every app
- The price is set in Play Console (EUR 0.67/month) and never hardcoded: the paywall shows the price Play Billing returns, in the user's currency
- Google Play Billing Library 9.1.0 is the only payment system: no accounts, no backend. `EntitlementRepository` asks Billing on app start, on every resume and on purchase updates, acknowledges purchases, handles pending, cancelled/expired, grace period and Billing unavailable (keeps the last known state, cached in DataStore), and never crashes
- "Pick your free app" after onboarding (and for existing users with more tracked apps than the free tier); the others are paused: history and settings kept, no tracking, pill, limits or blocking. Apps tab: paused apps greyed out with a lock and a "Plus" badge; tapping one opens the paywall
- Plus ends: the picked app (or the most used, remembered) stays; nothing is deleted; a calm "Plus ended. 1 app stays free." banner on Home with a neutral fox. Resubscribing resumes the paused apps with their settings and history
- Paywall: the fox, "Unscroll Plus", "{price} / month", 3 bullets (unlimited apps, supports an independent app, cancel anytime), "Your data stays with you, always. We only charge to support the people who build Unscroll…", Continue, renewal terms, Restore purchases, Not now, privacy policy and terms links; loading and Billing-unavailable states; no urgency or pre-selection
- Settings › Unscroll Plus: Free/Plus, the free app (change it), Manage subscription, Restore purchases. Debug builds only: "Force Plus" (Billing / Plus / Free); release builds ignore it
- Privacy promise kept true: the manifest removes `INTERNET` even if a library asks for it, CI lists every permission in the merged manifest and fails on INTERNET; trust sheet gains "No accounts. Purchases are handled by Google Play. We never see your usage data."; privacy policy, data safety, store listing (pricing and subscription disclosure), terms and release steps updated
- Not in this change: the "+" to add other apps (not built yet), so the paywall is reached from paused apps, the pick screen and Settings
- **Done when**: a fresh install picks one app and tracks only it; buying Plus (license tester) tracks every app; letting it lapse shows the banner, keeps the picked app and pauses the rest without losing history; resubscribing resumes them.

## Add any app ✅ Done (awaiting device testing)
- The tracked list moves to Room (`tracked_apps`, v8 with `MIGRATION_7_8`): `TrackedAppsRepository` is the single source of truth (package, name, added at, ACTIVE/PAUSED, "Count swipes", removed). Instagram, both TikTok packages and Facebook are seeded on a fresh install and removable like any app; no code checks a fixed package list any more
- "+" on the Apps tab opens a full-screen picker: live case-insensitive search, a "Popular" row (YouTube, Reddit, X, Snapchat, Pinterest, Threads, Discord, Telegram, WhatsApp, LinkedIn, Twitch, Netflix, only if installed; Chrome too in debug builds), then every launchable app by name with "Add" / "Added". Loaded off the main thread, icons cached. Each package is listed separately (TikTok's two stay separate)
- Package visibility through the launcher intent in `<queries>`; no QUERY_ALL_PACKAGES
- Safety exclusions (Unscroll, home screens, Settings, dialers and the default dialer, messaging and the default SMS app, emergency apps, the Play Store, system UI and installer/permission packages): hidden in the picker, refused by the repository, never blocked by `BlockEvaluator` or the enforcers
- Every feature works per package for added apps: sessions, pill with today's total and colors, daily limit, block completely, schedule, swipe counting and swipe limit, break reminders, open nudges, fox mood, Home stats. New per-app "Count swipes" switch
- The accessibility service follows the list: `serviceInfo.packageNames` is replaced on connect and on every change, and events from untracked apps are dropped on arrival as well
- Freemium through one provider (`EntitlementRepository.maxActiveApps`): a free user at the limit who taps "Add" gets the paywall
- App detail › Tracking: "Track this app" (pause), "Count swipes", "Remove from Unscroll" (confirmation). Removing keeps history and settings; adding again restores them. Uninstalled apps show "Not installed" and aren't tracked; re-checked on resume
- **Done when**: YouTube (or Chrome in a debug build) added from the picker gets the pill, a daily limit blocks it, its swipes count, and removing and re-adding it keeps its history.

## Billing diagnostics (debug builds) ✅ Done
- Debug builds log the full Play Billing result (responseCode, name, debugMessage) for startConnection, queryProductDetailsAsync (product ID and ProductDetails count) and launchBillingFlow, and the paywall shows the last code name in small text (BILLING_UNAVAILABLE, ITEM_UNAVAILABLE, DEVELOPER_ERROR, …). Nothing changes in release builds
- **Done when**: on a debug build whose Play Console product isn't set up, the paywall says why (e.g. "OK · 0 ProductDetails" or ITEM_UNAVAILABLE).

## Play package name ✅ Done
- Application ID `com.sharansapkota.unscroll` (registered in Play Console); the Kotlin namespace stays `com.unscroll.app`, so no sources moved and behavior is unchanged
- Every use of the app's own ID is dynamic (`context.packageName`); a guard test fails if the old ID is used as an app ID in manifests, Gradle or resources
- Release signing from a gitignored `keystore.properties` with a clear error when it is missing; version bumping documented in docs/RELEASE.md

## Section blocking (Unscroll Plus) 🟡 Built, awaiting identifiers and device testing
- Opt-in: block only the short-video section (Instagram Reels, TikTok For You, Facebook Reels, YouTube Shorts) while chat, search, profiles and everything else keep working
- A second accessibility service with window content (IDs only) and its own disclosure ("Unscroll checks which section of the app is open to block short videos. It never reads your messages or content and sends nothing anywhere."), "I agree" / "No thanks"; scroll counting's service is unchanged (still no window content)
- Strict privacy: only package, window class, view IDs, class names and selected/visible state are read; never text or content descriptions (a unit test fails the build otherwise); nothing stored beyond settings; nothing sent
- One rules file (`SectionRulesConfig`): per app package names, version-aware rule sets (blocked window/view IDs/classes, selected tabs, allowed chat markers). Seeded empty with TODO markers: until real identifiers are captured, every app returns UNKNOWN and nothing is blocked
- Fail open: unknown, unreadable or not positively identified never blocks; chat markers always win
- Cover: the calm stop screen with the concerned fox, "Reels are blocked. Chat is open.", "Take me back to chat" (Back for the app) and "Go home"; an accessibility overlay that is touchable but not focusable, so Back, Home and Recents always work
- Per app in App detail under Limits: "Block Reels" (off by default), Always / After limit; global kill switch "Turn off section blocking" in Settings; re-enable banner on Home when Android switches the service off
- Unscroll Plus only (the debug Force Plus applies); paywall, terms and store text updated
- Debug builds: Section Inspector (Settings › Debug) logs package, window class and deduplicated view ID / class / selected to logcat (tag `SectionInspector`), with a floating "Mark this screen as: Reels / Chat / Home / Search / Other" button; capture steps in docs/SECTION_BLOCKING.md
- **Done when**: identifiers captured on a device fill in `SectionRulesConfig`; opening Reels/For You/Shorts is covered within a second, "Take me back to chat" leaves it, chat and search are never covered, and "Turn off section blocking" stops everything at once.
