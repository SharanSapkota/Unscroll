# Google Play: Accessibility API declaration (draft)

Draft answers for the Play Console **Accessibility API** declaration (App content › Accessibility API). Review them against the current form before submitting: Google changes the form's wording from time to time. The in-app prominent disclosure is quoted in [STORE_LISTING.md](../STORE_LISTING.md).

## Is the app an accessibility tool?

**No.** Unscroll is a digital-wellbeing app, not a tool that helps people with disabilities use their device. Both of its services declare `android:isAccessibilityTool="false"` (`app/src/main/res/xml/accessibility_service_config.xml` for scroll counting, `section_blocking_service_config.xml` for section blocking).

Unscroll has **two separate, optional services**, each with its own in-app disclosure and consent: **scroll counting** (below) and **section blocking** (further down). They are separate on purpose: section blocking needs `canRetrieveWindowContent="true"`, a static setting, and scroll counting keeps it off.

## Core functionality

Unscroll helps people cut down on compulsive scrolling ("doomscrolling") in apps they choose to limit: Instagram, TikTok and Facebook by default, plus any other installed app they add. It shows a live session timer, logs time spent, lets users set daily limits, and adds friction such as nudges and break reminders. All of this works without the Accessibility API.

**Scroll counting** is an optional, opt-in feature. It counts how many times the user swipes in those apps, so that Unscroll can:
- show the swipe count next to the live timer,
- show swipe statistics on the dashboard (swipes today, swipes per session, swipes per minute),
- offer a "take a break" screen after a number of swipes that the user chooses (off by default),
- enforce a **swipe limit** the user sets per app (off by default, per day or per session): when it is reached, Unscroll covers the app with its own full-screen "Swipe limit reached" screen until the limit resets, with a "Go home" button that always works.

## Why the Accessibility API is needed

Android has no other API that tells an app when the user scrolls inside another app. `UsageStatsManager`, which Unscroll already uses to measure time, only reports which app is in the foreground. Counting swipes is the core of this feature and can't be done any other way. The window-change event (package name only) lets Unscroll cover an app that is already over its swipe limit the moment it opens, instead of about a second later.

## What data is accessed

- **Accessed:** only the event type (`TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED`) and the package name of the app the event came from.
- **Limited by configuration:**
  - `canRetrieveWindowContent="false"`, so the service never gets the screen's text, content descriptions or view hierarchy.
  - `packageNames` covers only the tracked apps.
  - `accessibilityEventTypes` covers only scroll and window-change events.
- **Stored:** one number per usage session (the swipe count), next to the session's package name and start and end times, in the app's private on-device database.
- **Not accessed or stored:** screen content, text, messages, posts, usernames, passwords, keystrokes, contacts or any personal information. The service doesn't interact with other apps' interfaces: it never taps, types or reads them.
- **One action, only for the user's own limit:** if the user's swipe limit is reached and Unscroll can't draw its cover (the "Display over other apps" permission was removed), the service calls `performGlobalAction(GLOBAL_ACTION_HOME)` to go to the home screen, and Unscroll shows its own block screen. That is the only action it performs.

## Data sharing and collection

- No data is collected, transmitted or shared. The app has no account, no analytics SDK, no ads SDK and makes no network calls; everything stays on the device.
- The Data safety form declares no data collected and no data shared.

## User consent and control

- Before Unscroll ever sends the user to Accessibility settings, it shows a dedicated in-app disclosure screen (Settings › Scroll counting › Set up). The screen explains what the service does, what it does not do, and that it is optional.
- The user must tap **"I agree"**; **"No thanks"** leaves the app fully working.
- Consent is stored on the device. If the service is switched on in system settings without in-app consent, every event is ignored.
- The user can turn the feature off at any time in Settings › Scroll counting (the service then calls `disableSelf()`), or in the system Accessibility settings.

## Section blocking (second service, Unscroll Plus)

### Core functionality

Section blocking lets the user block only the short-video section of an app they chose (Instagram Reels, TikTok's For You feed, Facebook Reels, YouTube Shorts) while chat, search, profiles and everything else keep working. When the section opens, Unscroll covers it with its own screen ("Reels are blocked. Chat is open.") with "Take me back to chat" and "Go home". The user chooses the apps (App detail › Section blocking, off for every app by default) and when: always, or only after the app's daily limit is reached.

### Why the Accessibility API is needed

Only an accessibility service can tell which part of another app is on screen. `UsageStatsManager` reports the foreground app, not whether the user is in Reels or in a chat, and blocking the whole app would also block messaging, which users told us they need.

### What data is accessed

- **Accessed:** the event's package name; the window (activity) class name from window-state changes; and, for each node on screen, only `viewIdResourceName`, `className`, `isSelected` and `isVisibleToUser`. These technical identifiers are compared on the device with a fixed list for each app (`SectionRulesConfig`).
- **Never accessed:** node or event text, content descriptions, hint text, state descriptions, tooltips, pane titles, extras, or any user content (messages, usernames, captions, comments). One file copies nodes (`SectionNodeSnapshot`) into a structure that has no field for text, and a unit test (`SectionPrivacyGuardTest`) fails the build if any code on the detection path calls `getText`, `getContentDescription` or similar.
- **Limited by configuration:** `packageNames` lists only the apps with section rules, and the service narrows it at runtime to the tracked apps the user blocks a section in (none while it is turned off or the user has no Plus). Event types: window state, window content and view-selected changes only.
- **Stored:** nothing about the screen. Only the user's settings (consent, the on/off switch, which apps, always or after limit).
- **Fail open:** if the app's screen can't be read or isn't positively identified as the short-video section, nothing is blocked.
- **Actions:** `GLOBAL_ACTION_BACK` when the user taps "Take me back to chat" and `GLOBAL_ACTION_HOME` for "Go home". The cover is an accessibility overlay that is not focusable, so the system Back, Home and Recents always work, and it disappears with the service.

### User consent and control

- Its own disclosure screen (Settings › Tracking › Section blocking › Set up, or App detail › Section blocking): "Unscroll checks which section of the app is open to block short videos. It never reads your messages or content and sends nothing anywhere.", what it does, what it never does, and that it is optional. **"I agree"** / **"No thanks"**.
- Without consent every event is ignored and the service switches itself off if the user had declined. Scroll counting is unaffected either way.
- Settings › Section blocking: "Turn off section blocking" (a kill switch: nothing is read or covered), "Stop and remove consent" (the service calls `disableSelf()`), or the system Accessibility settings.

### Debug builds only

Debug builds have a "Section Inspector" (Settings › Debug) that logs the same identifiers (package, window class, view IDs, class names, selected state) to logcat so the developer can capture them on their own phone. It never logs text and doesn't exist in release builds' UI; release builds ignore the setting.

## Video for the declaration (to record)

Show:
1. Settings › Scroll counting › Set up, with the disclosure screen visible.
2. Tapping "I agree", then switching on "Unscroll swipe counter" in Accessibility settings.
3. The swipe count on the timer while scrolling Instagram.
4. The dashboard's Swipes card.
5. Turning the feature off.
6. Section blocking: Settings › Section blocking › Set up with its disclosure, "I agree", switching on "Unscroll section blocking", App detail › Section blocking › Block Reels, opening Reels (covered, "Take me back to chat"), then chat working, then "Turn off section blocking".
