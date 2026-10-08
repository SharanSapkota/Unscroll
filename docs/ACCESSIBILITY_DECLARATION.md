# Google Play: Accessibility API declaration (draft)

Draft answers for the Play Console **Accessibility API** declaration (App content › Accessibility API). Review them against the current form before submitting: Google changes the form's wording from time to time. The in-app prominent disclosure is quoted in [STORE_LISTING.md](../STORE_LISTING.md).

## Is the app an accessibility tool?

**No.** Unscroll is a digital-wellbeing app, not a tool that helps people with disabilities use their device. The service declares `android:isAccessibilityTool="false"` in `app/src/main/res/xml/accessibility_service_config.xml`.

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

## Video for the declaration (to record)

Show:
1. Settings › Scroll counting › Set up, with the disclosure screen visible.
2. Tapping "I agree", then switching on "Unscroll swipe counter" in Accessibility settings.
3. The swipe count on the timer while scrolling Instagram.
4. The dashboard's Swipes card.
5. Turning the feature off.
