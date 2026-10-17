# Google Play: Accessibility API declaration (draft)

Draft answers for the Play Console **Accessibility API** declaration (App content › Accessibility API). Review them against the current form before submitting: Google changes the form's wording from time to time. The in-app prominent disclosure is quoted in [STORE_LISTING.md](../STORE_LISTING.md).

## Is the app an accessibility tool?

**No.** Unscroll is a digital-wellbeing app, not a tool that helps people with disabilities use their device. Both of its services declare `android:isAccessibilityTool="false"` (`app/src/main/res/xml/accessibility_service_config.xml` for scroll counting, `section_blocking_service_config.xml` for section blocking).

Unscroll has **two separate, optional services**, each with its own in-app disclosure and consent: the **swipe counter** (scroll counting and the Reels-only timer, below) and **section blocking** (further down; switched off in current builds, `SectionBlockingConfig.ENABLED = false`, so its settings and disclosure are hidden and it never covers anything). Both now declare `canRetrieveWindowContent="true"`: the swipe counter needs it for the Reels-only timer, which only runs after its own consent (see below).

## Core functionality

Unscroll helps people cut down on compulsive scrolling ("doomscrolling") in apps they choose to limit: Instagram, TikTok and Facebook by default, plus any other installed app they add. It shows a live session timer, logs time spent, lets users set daily limits, and adds friction such as nudges and break reminders. All of this works without the Accessibility API.

**Scroll counting** is an optional, opt-in feature. It counts how many times the user swipes in those apps, so that Unscroll can:
- show the swipe count next to the live timer,
- show swipe statistics on the dashboard (swipes today, swipes per session, swipes per minute),
- offer a "take a break" screen after a number of swipes that the user chooses (off by default),
- enforce a **swipe limit** the user sets per app (off by default, per day or per session): when it is reached, Unscroll covers the app with its own full-screen "Swipe limit reached" screen until the limit resets, with a "Go home" button that always works.

The **Reels-only timer** (Instagram and TikTok only, on by default per app once the user agreed to screen checks) uses the same service to tell which screen of those two apps is open, so the floating timer shows only while the user watches Instagram Reels or the TikTok For You feed, and only that time and those swipes are counted for them. Every other screen (home feed, chat, search, profile, stories, upload) hides the timer.

## Why the Accessibility API is needed

Android has no other API that tells an app when the user scrolls inside another app. `UsageStatsManager`, which Unscroll already uses to measure time, only reports which app is in the foreground. Counting swipes is the core of this feature and can't be done any other way. The window-change event (package name only) lets Unscroll cover an app that is already over its swipe limit the moment it opens, instead of about a second later. Likewise only an accessibility service can tell Reels from chat inside Instagram or TikTok, which the Reels-only timer needs.

## What data is accessed

- **Accessed for swipe counting:** only the event type (`TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED`), the package name of the app the event came from and, for scroll events, the event's numeric item positions (`fromIndex`/`toIndex`: which item numbers the scrolled view shows, used to tell quick swipes in full-screen video feeds apart). Numbers only, nothing from the items themselves.
- **Accessed for the Reels-only timer** (Instagram and TikTok only, only after the user agreed to screen checks): the window (activity) class from window-change events and, for a fixed list of view IDs per app (`SectionRulesConfig`, looked up with `findAccessibilityNodeInfosByViewId`, never a walk of the whole screen), each matching view's resource ID, class name, whether it is selected and whether it is visible. From that the app decides "Reels" or "not Reels". It never reads `getText`, `getContentDescription`, `getHintText` or any other content of a view (a unit test, `SectionPrivacyGuardTest`, fails the build if that code ever does). Checks run at most once every 300 ms, only while the screen is on, only in those two apps.
- **Limited by configuration:**
  - `canRetrieveWindowContent="true"` and `flagReportViewIds`: needed so view IDs can be looked up for the Reels-only timer. Without the screen-check consent, or for any app other than Instagram and TikTok, the service never reads a view.
  - `packageNames` covers only the tracked apps.
  - `accessibilityEventTypes`: scroll and window-change events, plus window-content and view-selected changes (to notice a tab switch). The service turns the last two off at runtime while no app's screen is checked.
  - `accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds"`: full-screen video feeds mark their scrolling view as not important for accessibility, and Android only delivers those views' scroll events with that flag; `flagReportViewIds` gives the view IDs above. No other flag is set.
- **Stored:** one number per usage session (the swipe count), next to the session's package name and start and end times; for Instagram and TikTok also the parts of a session spent in Reels or elsewhere (start and end time, "REELS" or "OTHER", swipe count). All in the app's private on-device database. Which view IDs matched is kept only in memory (debug builds show it on a Settings line).
- **Not accessed or stored:** text on screen, messages, posts, captions, comments, usernames, passwords, keystrokes, contacts or any personal information. The service doesn't interact with other apps' interfaces: it never taps or types in them.
- **One action, only for the user's own limit:** if the user's swipe limit is reached and Unscroll can't draw its cover (the "Display over other apps" permission was removed), the service calls `performGlobalAction(GLOBAL_ACTION_HOME)` to go to the home screen, and Unscroll shows its own block screen. That is the only action it performs.

## Data sharing and collection

- No data is collected, transmitted or shared. The app has no account, no analytics SDK, no ads SDK and makes no network calls; everything stays on the device.
- The Data safety form declares no data collected and no data shared.

## User consent and control

- Before Unscroll ever sends the user to Accessibility settings, it shows the in-app disclosure (which includes the screen-check sentence: "Unscroll checks which screen of TikTok and Instagram is open so the timer only shows on Reels. It never reads your messages or content and sends nothing anywhere."): on the optional "Count your swipes" onboarding page ("Enable" / "Skip"), or on the disclosure screen (Settings › Scroll counting › Set up, the Settings permission checklist, or Home's dismissible "Turn on swipe counting" banner). It explains what the service does, what it does not do, and that it is optional.
- The user must tap **"I agree"**; **"No thanks"** leaves the app fully working.
- Users who agreed to swipe counting before the Reels-only timer existed see the updated disclosure again (Home's "Timer only in Reels" banner, or "Review" in Settings › Timer pill and App detail) and must agree to screen checks separately. Until then, and after "No thanks", no screen is read and the timer shows whenever the app is open, as before.
- Consent is stored on the device. If the service is switched on in system settings without in-app consent, every event is ignored.
- The user can turn the feature off at any time in Settings › Scroll counting (the service then calls `disableSelf()`), or in the system Accessibility settings.

## Section blocking (second service; an Unscroll Plus feature only while payments are on; switched off in current builds)

### Core functionality

Section blocking lets the user block only the short-video section of an app they chose (Instagram Reels, TikTok's For You feed, Facebook Reels, YouTube Shorts) while chat, search, profiles and everything else keep working. When the section opens, Unscroll covers it with its own screen ("Reels are blocked. Chat is open.") with "Take me back to chat" and "Go home". The user chooses the apps and for how long (App detail › Quick block › "Block Reels only" / "Block For You feed" / "Block Shorts only", off for every app by default; 15 min to 2 hours, or until they turn it off) and when: always, or only after the app's daily limit is reached (App detail › Advanced options).

### Why the Accessibility API is needed

Only an accessibility service can tell which part of another app is on screen. `UsageStatsManager` reports the foreground app, not whether the user is in Reels or in a chat, and blocking the whole app would also block messaging, which users told us they need.

### What data is accessed

- **Accessed:** the event's package name; the window (activity) class name from window-state changes; and, for each node on screen, only `viewIdResourceName`, `className`, `isSelected` and `isVisibleToUser`. These technical identifiers are compared on the device with a fixed list for each app (`SectionRulesConfig`).
- **Never accessed:** node or event text, content descriptions, hint text, state descriptions, tooltips, pane titles, extras, or any user content (messages, usernames, captions, comments). One file copies nodes (`SectionNodeSnapshot`) into a structure that has no field for text, and a unit test (`SectionPrivacyGuardTest`) fails the build if any code on the detection path calls `getText`, `getContentDescription` or similar.
- **Limited by configuration:** `packageNames` lists only the apps with section rules, and the service narrows it at runtime to the tracked apps the user blocks a section in (none while it is turned off, or, with payments on, without Plus). Event types: window state, window content and view-selected changes only.
- **Stored:** nothing about the screen. Only the user's settings (consent, the on/off switch, which apps, always or after limit).
- **Fail open:** if the app's screen can't be read or isn't positively identified as the short-video section, nothing is blocked.
- **Actions:** `GLOBAL_ACTION_BACK` when the user taps "Take me back to chat" and `GLOBAL_ACTION_HOME` for "Go home". The cover is an accessibility overlay that is not focusable, so the system Back, Home and Recents always work, and it disappears with the service.

### User consent and control

- Its own disclosure screen (Settings › Tracking › Section blocking › Set up, or App detail › Quick block › Block Reels only): "Unscroll checks which section of the app is open to block short videos. It never reads your messages or content and sends nothing anywhere.", what it does, what it never does, and that it is optional. **"I agree"** / **"No thanks"**.
- Without consent every event is ignored and the service switches itself off if the user had declined. Scroll counting is unaffected either way.
- Settings › Section blocking: "Turn off section blocking" (a kill switch: nothing is read or covered), "Stop and remove consent" (the service calls `disableSelf()`), or the system Accessibility settings.

### Debug builds only

Debug builds have a "Section Inspector" (Settings › Debug) that logs the same identifiers (package, window class, view IDs, class names, selected state) to logcat so the developer can capture them on their own phone. It never logs text and doesn't exist in release builds' UI; release builds ignore the setting.

## Video for the declaration (to record)

Show:
1. Settings › Scroll counting › Set up, with the disclosure screen visible.
2. Tapping "I agree", then switching on "Unscroll swipe counter" in Accessibility settings.
3. The swipe count on the timer while scrolling Instagram Reels; switching to the chat tab hides the timer, going back to Reels shows it again.
3b. For an existing user: Home's "Timer only in Reels" banner, the updated disclosure and "I agree".
4. The dashboard's Swipes card.
5. Turning the feature off.
6. Section blocking: Settings › Section blocking › Set up with its disclosure, "I agree", switching on "Unscroll section blocking", App detail › Quick block › Block Reels only, opening Reels (covered, "Take me back to chat"), then chat working, then "Turn off section blocking".
