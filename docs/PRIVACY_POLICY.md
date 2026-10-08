# Unscroll privacy policy (draft)

_Last updated: [date of release]. Replace the bracketed parts before publishing, and host this page at a public URL for the Play Console._

Unscroll helps you spend less time scrolling social media. It is built so that your data never leaves your phone.

## Summary

- Unscroll has **no account, no servers, no analytics, no ads and no internet permission**. It can't send your data anywhere.
- Unscroll Plus (optional subscription) is bought through **Google Play**. Unscroll never sees your payment details, and Google Play never gets your usage data.
- Everything Unscroll records is stored only on your phone, in the app's private storage, and is deleted when you uninstall the app.
- Unscroll never reads what is on your screen: no posts, messages, usernames, text or images.

## What Unscroll records on your phone

| Data | Why |
|---|---|
| Which tracked app was open (Instagram, TikTok and Facebook by default, plus any app you add), and when each session started and ended | Session timer, dashboard, limits, nudges |
| The apps you track: package name, name, when you added it, and your per-app switches | So Unscroll tracks only the apps you chose |
| How many times you swiped in a session (only if you turn on the optional swipe counter) | Swipe count, swipe stats, "take a break after N swipes" |
| Sent nudges and "I need access" extensions | So nudges aren't repeated, and to log extensions |
| Your settings: limits, schedules, goal, overlay, quiet hours, consent choices | So the app works the way you set it up |
| Whether you have Unscroll Plus (as last reported by Google Play), and which app you picked as your free app | So Plus keeps working offline, and so the right apps are tracked |

## Permissions and what they are used for

- **Seeing your installed apps:** the "Add apps" list reads the apps that have an icon in your app drawer (Android's launcher query, not "all packages"), on the phone, only while the list is open. Only the apps you add are saved.

- **Usage access:** to see which app is in the foreground and measure time. Only app package names and timestamps are read.
- **Display over other apps:** to show the floating timer and the break and block screens.
- **Notifications** (optional): nudges and break reminders.
- **Accessibility service** (optional, off by default, needs your explicit consent in the app): counts scroll gestures in the tracked apps. It can't read screen content (`canRetrieveWindowContent` is off) and only receives scroll and window-change events from the tracked apps.
- **Run at startup** and a foreground service: to keep tracking after a restart.

## Purchases (Unscroll Plus)

Unscroll Plus is a monthly subscription sold through Google Play Billing. The purchase happens in Google Play's own screens, under your Google account and [Google's privacy policy](https://policies.google.com/privacy). The app asks Google Play, through the Play Store app on your phone, whether you have an active subscription; it never sends anything itself, and it gives Google Play no usage data. Unscroll has no account and no server, so we never receive your name, email or payment details. If Plus ends, nothing is deleted.

## Sharing

Unscroll doesn't share data with anyone, because it never sends data off your phone. If you export your sessions as a CSV file (Settings › Your data), the file goes where you choose, and from then on it is yours to manage.

## Your choices

- Turn off tracking, the overlay, nudges or the swipe counter at any time in Settings.
- **Export:** Settings › Your data › Export sessions (CSV).
- **Delete:** Settings › Data & privacy › Delete history deletes all recorded sessions, swipe counts and logs. Settings › Data & privacy › Delete all my data deletes everything, settings, limits and goal included, and the app starts over like a new install. Uninstalling the app, or "Clear storage" in Android's App info, also deletes everything.

## Children

Unscroll isn't directed at children under 13 and doesn't knowingly collect any personal information from anyone.

## Changes and contact

If this policy changes, the new version will be published at this URL with a new date. Questions: [contact email].
