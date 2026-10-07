# Unscroll privacy policy (draft)

_Last updated: [date of release]. Replace the bracketed parts before publishing, and host this page at a public URL for the Play Console._

Unscroll helps you spend less time scrolling social media. It is built so that your data never leaves your phone.

## Summary

- Unscroll has **no account, no servers, no analytics, no ads and no internet permission**. It can't send your data anywhere.
- Everything Unscroll records is stored only on your phone, in the app's private storage, and is deleted when you uninstall the app.
- Unscroll never reads what is on your screen: no posts, messages, usernames, text or images.

## What Unscroll records on your phone

| Data | Why |
|---|---|
| Which tracked app (Instagram, TikTok, Facebook) was open, and when each session started and ended | Session timer, dashboard, limits, nudges |
| How many times you swiped in a session (only if you turn on the optional swipe counter) | Swipe count, swipe stats, "take a break after N swipes" |
| Sent nudges and "I need access" extensions | So nudges aren't repeated, and to log extensions |
| Your settings: limits, schedules, goal, overlay, quiet hours, consent choices | So the app works the way you set it up |

## Permissions and what they are used for

- **Usage access:** to see which app is in the foreground and measure time. Only app package names and timestamps are read.
- **Display over other apps:** to show the floating timer and the break and block screens.
- **Notifications** (optional): nudges and break reminders.
- **Accessibility service** (optional, off by default, needs your explicit consent in the app): counts scroll gestures in the tracked apps. It can't read screen content (`canRetrieveWindowContent` is off) and only receives scroll and window-change events from the tracked apps.
- **Run at startup** and a foreground service: to keep tracking after a restart.

## Sharing

Unscroll doesn't share data with anyone, because it never sends data off your phone. If you export your sessions as a CSV file (Settings › Your data), the file goes where you choose, and from then on it is yours to manage.

## Your choices

- Turn off tracking, the overlay, nudges or the swipe counter at any time in Settings.
- **Export:** Settings › Your data › Export sessions (CSV).
- **Delete:** Settings › Your data › Delete usage history deletes all recorded sessions, swipe counts and logs. Uninstalling the app, or "Clear storage" in Android's App info, deletes everything, settings included.

## Children

Unscroll isn't directed at children under 13 and doesn't knowingly collect any personal information from anyone.

## Changes and contact

If this policy changes, the new version will be published at this URL with a new date. Questions: [contact email].
