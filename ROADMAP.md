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
- Paywall: the fox, "Unscroll Plus", "{price} / month", 3 bullets (unlimited apps, supports an independent app, cancel anytime; a 4th, section blocking, came with "Section blocking"), "Your data stays with you, always. We only charge to support the people who build Unscroll…", Continue, renewal terms, Restore purchases, Not now, privacy policy and terms links; loading and Billing-unavailable states; no urgency or pre-selection
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

## Section blocking (Unscroll Plus) 🟡 Built, Instagram and YouTube identifiers captured, awaiting device testing
- Opt-in: block only the short-video section (Instagram Reels, TikTok For You, Facebook Reels, YouTube Shorts) while chat, search, profiles and everything else keep working
- A second accessibility service with window content (IDs only) and its own disclosure ("Unscroll checks which section of the app is open to block short videos. It never reads your messages or content and sends nothing anywhere."), "I agree" / "No thanks"; scroll counting's service is unchanged (still no window content)
- Strict privacy: only package, window class, view IDs, class names and selected/visible state are read; never text or content descriptions (a unit test fails the build otherwise); nothing stored beyond settings; nothing sent
- One rules file (`SectionRulesConfig`): per app package names, version-aware rule sets (blocked window/view IDs/classes, selected tabs, allowed chat markers). Seeded empty with TODO markers. Filled in from Section Inspector captures (Pixel 8, Android 17, 2026-10-11) for Instagram 450.0.0.50.77 (Reels) and YouTube 21.40.161 (Shorts), as ID combinations (`requiredViewIds`); TikTok (obfuscated IDs, For You vs Following unclear) and Facebook (resource names stripped) stay empty and return UNKNOWN. `SectionCaptureTest` checks the rules against node trees from every capture
- Fail open: unknown, unreadable or not positively identified never blocks; chat markers always win
- Cover: the calm stop screen with the concerned fox, "Reels are blocked. Chat is open.", "Take me back to chat" (Back for the app) and "Go home"; an accessibility overlay that is touchable but not focusable, so Back, Home and Recents always work
- Per app in App detail under Limits: "Block Reels" (off by default), Always / After limit (since "Quick block": the reels toggle at the top of App detail, Always / After limit under Advanced options); global kill switch "Turn off section blocking" in Settings; re-enable banner on Home when Android switches the service off
- Unscroll Plus only (the debug Force Plus applies); paywall, terms and store text updated
- Debug builds: Section Inspector (Settings › Debug) logs package, window class and deduplicated view ID / class / selected to logcat (tag `SectionInspector`), with a floating "Mark this screen as: Reels / Chat / Home / Search / Other" button; capture steps in docs/SECTION_BLOCKING.md
- **Done when**: identifiers captured on a device fill in `SectionRulesConfig`; opening Reels/For You/Shorts is covered within a second, "Take me back to chat" leaves it, chat and search are never covered, and "Turn off section blocking" stops everything at once.

## Quick block toggles 🟡 Built, awaiting device testing (the reels toggle also awaits section identifiers)
- Simpler per-app controls, first on App detail: "Block entire app" for every tracked app, plus "Block reels only" for the five Reels-capable packages, defined once in `ReelsCapableApps` (Instagram "Block Reels only", TikTok "Block For You feed", Facebook "Block Reels only", YouTube "Block Shorts only")
- Each toggle has duration chips: 15 min, 30 min, 1 hour, 2 hours, Until I turn it off (default), remembered per app and per toggle. On starts the block, a new chip while on restarts it, off is immediate (no cooldown, no confirmation)
- Live countdown in the row ("12:41 left") and in the app's status line ("Reels blocked · 12 min left", "Blocked · until you turn it off"). When the time is up the toggle turns itself off, even with Unscroll closed: every check compares with the clock, and an AlarmManager alarm (exact where allowed without a new permission, inexact otherwise) plus resume, screen-on and boot clear the stored state
- "Block entire app" includes reels: the reels toggle shows on and disabled ("Included"); turning the entire block off returns to the reels toggle's own state
- The reels toggle uses section blocking as it is (detector config, cover with the fox and "Take me back to chat", fail open): Plus first (paywall), then the disclosure and Accessibility settings, after which it turns on by itself; disabled with "Not available for this app version yet" while there are no identifiers for the installed version (today: TikTok and Facebook; Instagram and YouTube have identifiers)
- "Advanced options" (collapsed, chevron) holds everything that existed before, moved not removed: daily limit, swipe limit (per day / per session), section blocking Always / After limit, schedule, timer pill and swipe count, nudges and breaks, mini stats, Track this app / Count swipes / Remove from Unscroll
- Room v9 (`MIGRATION_8_9`): `entireAppBlockedUntil`, `reelsBlockedUntil` (Long.MAX_VALUE = until turned off), `lastEntireDuration`, `lastReelsDuration`; every "Block completely" becomes "Block entire app" until turned off. The old per-app "Block Reels" set moves to the reels toggle once at app start
- Safety exclusions and Back/Home/Recents unchanged; no new permission
- Not in this change: a "When limit is reached: block / dim and nudge" setting and a "chat pass" don't exist in the app, so there was nothing to move
- **Done when**: a 15 min "Block entire app" blocks the app at once, counts down on App detail and the Apps tab, and lets the app open again 15 minutes later with the toggle off, also after a force stop or reboot in between.

## Payments off (billing switch) 🟡 Built, awaiting device testing
- State before this change (checked in the code): `EntitlementRepository` (Play Billing as the source of truth, cached in DataStore), `PlayBillingGateway` (Billing 9.1.0), the paywall, the free tier of **1** app (`FreeTier.FREE_APPS = 1`, not 2), "Pick your free app", the "Plus ended" banner, Plus gating of section blocking and the reels toggle, Settings › Unscroll Plus (Free/Plus, Free app, Manage subscription, Restore purchases) and the debug "Force Plus" switch
- One master switch, `billingEnabled` in `app/build.gradle.kts`, now `false` → `BuildConfig.BILLING_ENABLED` → `PlusConfig.BILLING_ENABLED` / `@BillingEnabled`. How to turn it back on: docs/RELEASE.md "Payments on or off"
- While off: everything is unlocked for everyone (unlimited apps, section blocking, the reels toggle); `isPlus` is true without asking Google Play; no BillingClient is created (`NoBillingGateway`), so no connection, query or billing error; the paywall and pick screen are unreachable; Settings › Unscroll Plus, Manage subscription, Restore purchases, the "Plus ended" banner and Force Plus are hidden; trust sheet, privacy note and section blocking disclosure use payment-free text
- Apps paused by the free tier come back by themselves with history and settings (that pause was never stored); nothing is deleted or migrated, and the stored Plus state stays for later
- Release builds remove `com.android.vending.BILLING` (`app/src/billingOff/AndroidManifest.xml`); CI checks the merged release manifest
- All billing code, the paywall and their tests stay; tests run both switch states (`BillingSwitch`)
- Store listing, privacy policy and terms without subscription, price or Plus; the paid texts are kept under "Enable when billing is turned on"
- **Done when**: a fresh install and an update from a free user with locked apps both track every app, Settings shows no Plus rows, nothing opens a paywall, logcat shows no Play Billing activity, and the release manifest has no BILLING permission.


## Insights 🟡 Built, awaiting device testing
- State before this change (checked in the code): Home (hero total, Day/Week/Month/All, app tiles, "Hours invested" from `Equivalents`, a collapsible stats section), the fox with mood-based bubbles from `fox_messages_*` string arrays, session/swipe queries in `SessionDao`, opt-in swipe counting with per-app "Count swipes", per-app limits and a daily goal with streaks, and the "nudges", "breaks" and tracking notification channels
- Message bank from `docs/insight-templates.md` (134 templates, [G]/[H]/[B] tones, placeholders, constants, landmarks, rotation rules), bundled as `assets/insights/templates.tsv` plus `phrases.tsv` for the words that fill placeholders; no message text in Kotlin, so it can be translated. A test keeps the asset identical to the docs, and the engine's placeholders identical to the docs' table
- `InsightEngine` (pure, injectable random): plurals, thousands separators, m/km, landmarks with a count of 1–20, thumb distance (real swipes × 55 % of the screen height, fallback 6 cm; without swipe data 4 a minute, marked "about"), life projection (last 30 days, after 7+ days, 16 waking hours, to age 80 or 50 more years), wins and milestones from streaks, savings vs last week, time reclaimed and the first week. Templates whose data is missing are skipped. Numbers are rounded down, never inflated
- Rotation: no repeat within the last 30, never the same category twice in a row, tone filter, no [B] in the first 3 days or after a good day, no negative messages on a day under the limit (Wins instead), life projection at most once a week, at most 10 a day
- Home: an Insight card under the total (big bold number, the line, a muted label, the fox in the message's mood), "Next" and "Share". Sundays: the weekly life projection card; optional weekly notification on its own "insights" channel, off by default, at most once a week
- Share: the card rendered to an image on the phone and sent through the system share sheet (FileProvider, `${applicationId}.fileprovider`); nothing is uploaded
- Fox bubbles use the bank's fox section (by mood, filled with today's numbers); the old string arrays are removed
- Settings › Insights: on/off, tone (Gentle / Honest / Brutal, default Honest; Brutal notes the messages are blunt), optional age, weekly notification
- Privacy: computed on the device, no network; only settings and recent template ids are stored. Privacy policy and Data safety notes updated (shared images and the stored Insights settings); trust text unchanged (still true)
- Tracking, blocking and overlay logic unchanged
- **Done when**: Home shows an insight that matches the numbers on screen, "Next" never repeats one within 30 and stops at 10 a day, Brutal only shows when chosen, Sunday shows the projection (after a week of data), and Share opens the share sheet with the image.

## Pill, swipe count and swipe-counting setup 🟡 Built, awaiting device testing
- State before this change (checked in the code): the pill was a filled traffic-light capsule with the app name, today's time and "· 86 swipes" (`TimerPill`, medium by default); the swipe count only appeared while scroll counting ran (consent and service connected); the accessibility service (`ScrollAccessibilityService`, `canRetrieveWindowContent="false"`, `flagDefault`, scroll and window-change events, package list from `TrackedAppsRepository`) and its enabled check (`ScrollCountingRules.isServiceEnabled` with `context.packageName` and the class name, already correct for `com.sharansapkota.unscroll`; no old ID left in code, manifests or XML) existed; the only ways to set it up were Settings › Tracking › Scroll counting and, once agreed, the Settings checklist; onboarding had two pages; Home only had the "Swipe counting stopped" banner
- Root cause of the missing swipe count: (1) the service declared only `flagDefault`, and Android drops scroll events from views marked "not important for accessibility" for such services; full-screen video pagers (ViewPager2's inner RecyclerView, Reels/TikTok/Shorts feeds) are exactly those views, so the swipes that matter never arrived. (2) A service switched on in Android without the in-app "I agree" counted nothing and nothing outside one Settings sheet said so. (3) Nothing outside Settings › Tracking › Scroll counting asked the user to set it up at all, and on Android 13+ sideloaded installs can't switch it on without "Allow restricted settings". (4) Quick pager swipes without a 300 ms pause merged into one
- Pill: a tiny dark capsule, no app name: fox face, today's time (bold, tabular), swipe icon and count; 28 dp high, small by default; the level is a tint on the border, the swipe icon and the time; swipes only while the service is connected (never "0" when off); "N left" near a swipe limit; drag, position memory, collapse to the fox dot, opacity and the swipe toggle kept; Settings preview matches
- Swipe chain: `flagIncludeNotImportantViews` (XML and runtime), pager-aware `SwipeDetector` (a new landed page within a burst is a new swipe; numeric `fromIndex`/`toIndex` only), `scrollServiceComponent(context)` plus an `AccessibilityManager` fallback for the enabled check, the swipe counter's own page in Accessibility settings (Android 11+). Debug builds: a `SwipeDebug` log and Settings › Debug › Swipe debug line (connected, last event, package, outcome, count)
- Hard to miss: onboarding page "Count your swipes" (disclosure, Enable, Skip); Settings checklist always lists "Accessibility (swipe counting)" (red "Off, tap to enable" / green "On", in "Fix N issues"); Home banner "Turn on swipe counting" while never enabled (dismiss = 7 days), "Finish setting up" while on without consent, "Swipe counting stopped" when the phone switched it off; Android 13+ restricted-setting help when the user comes back without it on; re-checked on every resume
- Privacy unchanged: `canRetrieveWindowContent="false"`, no text, content descriptions or nodes; the disclosure text is unchanged (still accurate); the accessibility declaration lists the flag and the position numbers
- **Done when**: on a device, Reels/TikTok/Shorts swipes count one per video (also when swiping quickly), the pill shows "🦊 12:41 ☝ 86" and only the fox and time with the service off, and a fresh install meets the swipe-counting setup in onboarding, Settings and Home.

## Reels-only timer (Instagram, TikTok) 🟡 Built, awaiting device testing
- State before this change (checked in the code): the pill (`OverlayTimerManager`, `TimerPill`) showed today's total for any tracked app while it was in the foreground; the swipe counter (`ScrollAccessibilityService`, `res/xml/accessibility_service_config.xml`) had `canRetrieveWindowContent="false"`, `flagDefault|flagIncludeNotImportantViews` and only scroll and window-change events, and counted every swipe; the section detector (`SectionRulesConfig`, `RuleBasedSectionDetector`, `SectionNodeSnapshot`) lived only in the second service (`SectionBlockingService`); the "version resolver" was `SectionRuleSet` version-code ranges plus `InstalledApps.versionCode` and `SectionDetectors.isAvailable` (no separate class); Instagram and YouTube had rules, TikTok and Facebook were empty; the raw captures were in `app/captures/`; Room was v9; onboarding had the optional "Count your swipes" page; there was **no** `SectionBlockingConfig` flag on `main` ("Block reels only" was visible; the PR that proposed the flag was closed without merging)
- `SectionBlockingConfig.ENABLED = false` (new): "Block reels only", Section blocking under Advanced options, its Settings row, Home banner and "Reels blocked" status lines are hidden and the section service never covers; code, rules and stored blocks are kept. Blocking logic itself (`BlockEvaluator`, `BlockEnforcer`, limits) is unchanged
- `ReelsOnlyPillConfig.ENABLED = true` (new, separate): for Instagram and TikTok the pill shows only on Reels / For You, with today's Reels time and Reels swipes; hidden at once on any other screen; 400 ms hysteresis, brief UNKNOWN frames ignored
- Detection in the swipe counter's service, deliberately with `canRetrieveWindowContent="true"` and `flagReportViewIds`: only after a new consent (updated disclosure, shown again to existing users), only for Instagram and TikTok, targeted `findAccessibilityNodeInfosByViewId` lookups at most every 300 ms off the main thread, nodes recycled, nothing with the screen off. Only IDs, classes, selected and visible state; a unit test fails if that path reads text, content descriptions or hints
- Rules from `app/captures/` only: Instagram 450.0.0.50.77 (selected `clips_tab` or `clips_viewer_view_pager`, with `clips_viewer_container`; chat markers win; tab-bar anchor), TikTok 47.0.3 pinned by version name and package (selected `omq` + `video_seek_bar` + `view_pager_layout_wrapper`)
- Data: Room v10 `section_segments` (REELS/OTHER parts of a session with swipes; `MIGRATION_9_10`, no wipe); swipes outside Reels aren't counted for these apps, so the swipe limit, swipe stats and Insights' swipe figures follow Reels; totals and the dashboard keep total app time; a "Reels" line on the Apps tab and App detail; midnight clipping and orphan recovery like sessions
- Fallback: unsupported version, no consent, service off or UNKNOWN for over 3 s → the whole-app pill and every swipe, with a note in Settings › Timer pill and App detail ("Reels-only timer isn't available for your {App} version yet", ...)
- Per app: "Show timer only in Reels" / "… in For You" in App detail (on by default, hidden for other apps)
- Debug builds: Settings › Debug › Reels debug (state, matched rule, version code)
- Docs: privacy policy, accessibility declaration, store listing, data safety and the trust sheet say what is true now (the swipe counter can retrieve window content and checks which screen is open, by IDs only)
- **Done when**: on a device with Instagram 450.0.0.50.77 and TikTok 47.0.3, the pill appears on Reels / For You within about half a second, disappears on chat, home, search, profile and stories without flicker, counts only Reels time and swipes, and falls back with a note on another TikTok version.
