# Play Store listing draft

**Title (30 max):** Unscroll: Stop Doomscrolling
**Short description (80 max):** Live scroll timer and app blocker to help you stop doomscrolling.

## Full description (draft)
Doomscrolling steals hours you never get back. Unscroll shows you exactly how much time you spend on social media, while you spend it.

- Live timer: a floating clock ticks on screen while you scroll, changing color the longer you stay
- App blocking: block apps entirely, on a schedule, or after your daily limit
- Hours invested: see how many hours and days you've given each app
- Insights: time-of-day heatmap, number of opens, longest sessions, weekly trends
- Friction, not guilt: a live timer, cooldowns, and gentle nudges that actually work
- Swipe counter and swipe limit (optional): see how many times you swipe, and cover the app when you hit the limit you set
- Private by design: all your data stays on your phone. No account. No tracking.

## Permission disclosures to prepare
- Usage access: to detect which app is open and measure time
- Display over other apps: to show the live timer and block screen
- Accessibility (optional, M7): to count scroll gestures in the tracked apps; no screen content is read or stored. Full wording below; Play declaration draft in docs/ACCESSIBILITY_DECLARATION.md.

## Accessibility prominent disclosure (in-app, required)
Shown on its own screen (Settings › Scroll counting › Set up) before the user is sent to Accessibility settings, and only acted on after an explicit "I agree" tap. Keep the app's `scroll_disclosure_*` strings and this text in sync:

> **Count your swipes (optional)**
> Unscroll can count how many times you swipe in the apps you choose. You'll see the count on the timer and the dashboard, you can ask for a break after a number of swipes, and you can set a swipe limit that covers the app when you reach it.
>
> **What it does:** It uses Android's Accessibility Service to notice that a scroll happened in Instagram, TikTok or Facebook, and in which of these apps. It counts, and it can cover the screen when your swipe limit is reached. It doesn't look at any other app.
>
> **What it does not do:** It does not read your screen, posts, messages, usernames or anything you type. It does not tap or type anything for you; the only thing it can do is send you to the home screen when your own swipe limit is reached. It does not send data anywhere: the counts stay on this phone, and Unscroll has no account and no internet access for this.
>
> **It's optional:** Everything else in Unscroll works without it. You can turn it off at any time in Settings, or in your phone's Accessibility settings.
>
> [I agree] [No thanks]

Store listing sentence (for the full description, near the feature list):
> Unscroll's optional swipe counter uses the Accessibility Service API to count scroll gestures in the apps you choose to track, and to cover an app when the swipe limit you set is reached. It never reads your screen content or messages, and no data leaves your phone.

## Checklist
- Privacy policy URL (required): draft in docs/PRIVACY_POLICY.md
- Data Safety form: no data collected or shared (answers in docs/DATA_SAFETY.md)
- Accessibility API declaration form (draft in docs/ACCESSIBILITY_DECLARATION.md, plus a short video of the disclosure and the feature)
- 4-8 screenshots, 512x512 icon, 1024x500 feature graphic
- Do not use "Instagram", "TikTok", or "Facebook" in the title
