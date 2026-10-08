# Play Store listing draft

**Title (30 max):** Unscroll: Stop Doomscrolling
**Short description (80 max):** Live scroll timer and app blocker to help you stop doomscrolling.

## Full description (draft)
Doomscrolling steals hours you never get back. Unscroll shows you exactly how much time you spend on social media, while you spend it.

- Any app: Instagram, TikTok and Facebook out of the box; add YouTube, Reddit or any other app you can't put down
- Live timer: a floating clock ticks on screen while you scroll, changing color the longer you stay
- App blocking: block apps entirely, on a schedule, or after your daily limit
- Hours invested: see how many hours and days you've given each app
- Insights: time-of-day heatmap, number of opens, longest sessions, weekly trends
- Friction, not guilt: a live timer, limits, and gentle nudges that actually work
- Swipe counter and swipe limit (optional): see how many times you swipe, and cover the app when you hit the limit you set
- Private by design: all your data stays on your phone. No account. No tracking.

**Pricing (in the full description; the price itself is set in Play Console, never in the app):**
> Free for 1 app, with every feature included. Unscroll Plus: {price}/month for unlimited apps, cancel anytime in Google Play.
>
> Your data stays with you. We only charge to support the people who build Unscroll.

The "1" follows `FreeTier.FREE_APPS`. Play shows the local price on the listing ("Offers in-app purchases"); don't write a price in the text, so it never disagrees with the user's currency.

## Subscription disclosure (Play subscriptions policy)
Shown on the in-app paywall next to "Continue", and in the full description:

> Unscroll Plus is a monthly subscription for {price}, billed through Google Play. It renews automatically until you cancel. Cancel anytime in Google Play › Payments & subscriptions › Subscriptions; you keep Plus until the end of the paid month. If Plus ends, nothing is deleted: your free app stays tracked and the others are paused, with their history kept.

In the app: the paywall shows the price Play returns ("{price} / month"), "Billed monthly through Google Play. Renews automatically until you cancel.", "Restore purchases", "Not now", and links to the privacy policy and terms ([docs/TERMS.md](docs/TERMS.md)). No countdowns, trials or pre-selected options. Settings › Unscroll Plus has "Manage subscription" (the Play subscriptions page for `unscroll_plus_monthly`) and "Restore purchases".

## Permission disclosures to prepare
- Usage access: to detect which app is open and measure time
- Display over other apps: to show the live timer and block screen
- Accessibility (optional, M7): to count scroll gestures in the tracked apps; no screen content is read or stored. Full wording below; Play declaration draft in docs/ACCESSIBILITY_DECLARATION.md.

## Accessibility prominent disclosure (in-app, required)
Shown on its own screen (Settings › Scroll counting › Set up) before the user is sent to Accessibility settings, and only acted on after an explicit "I agree" tap. Keep the app's `scroll_disclosure_*` strings and this text in sync:

> **Count your swipes (optional)**
> Unscroll can count how many times you swipe in the apps you choose. You'll see the count on the timer and the dashboard, you can ask for a break after a number of swipes, and you can set a swipe limit that covers the app when you reach it.
>
> **What it does:** It uses Android's Accessibility Service to notice that a scroll happened in one of the apps you track in Unscroll (Instagram, TikTok and Facebook unless you change the list), and in which one. It counts, and it can cover the screen when your swipe limit is reached. It doesn't look at any other app.
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
- Terms URL (linked from the paywall): draft in docs/TERMS.md
- Subscription `unscroll_plus_monthly` with base plan `monthly` in Play Console (see docs/RELEASE.md)
- Data Safety form: no data collected or shared (answers in docs/DATA_SAFETY.md)
- Accessibility API declaration form (draft in docs/ACCESSIBILITY_DECLARATION.md, plus a short video of the disclosure and the feature)
- 4-8 screenshots, 512x512 icon, 1024x500 feature graphic
- Do not use "Instagram", "TikTok", or "Facebook" in the title
