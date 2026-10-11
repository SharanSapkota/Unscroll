# Section blocking: capturing identifiers

Section blocking covers only the short-video section of an app (Instagram Reels, TikTok For You, Facebook Reels, YouTube Shorts). It recognizes the section from **technical identifiers only**: view IDs (`viewIdResourceName`), class names, which tab is selected (`isSelected`) and the window (activity) class name. Never text or content descriptions.

The identifiers live in one file, `app/src/main/java/com/unscroll/app/domain/section/SectionRulesConfig.kt`. An app without identifiers returns UNKNOWN and **nothing is blocked**. Don't add identifiers from memory or guesses: only ones captured with the Section Inspector below.

### Current status (captured on a Pixel 8, Android 17, 2026-10-11)

| App (version) | Rule | Confidence |
|---|---|---|
| Instagram 450.0.0.50.77 | (`clips_tab` selected **or** `clips_viewer_view_pager`) **and** `clips_viewer_container`; chat markers `direct_inbox_action_bar`, `inbox_refreshable_thread_list_recyclerview`, `message_thread_container`, `row_thread_composer_container` win | High for the captured screens. Readable names. |
| YouTube 21.40.161 | `reel_watch_player` **and** `reel_recycler` **and** `reel_watch_fragment_root` | High for the captured screens. Readable names. `reel_time_bar` and `reel_container` also show up outside Shorts, so never use a `reel_` prefix. |
| TikTok 47.0.3 | none (UNKNOWN) | Obfuscated IDs (`id/omq`) that change between releases; For You can't be told from Following (only the Home tab `omq` and the top tab `android:id/text1` are selected). Needed: a Following capture plus the version code, so a rule can be limited to one version. |
| Facebook 581.0.0.45.58 | none (UNKNOWN) | Every resource name is stripped (`id/(name removed)`); Reels only differs by `SurfaceView`/`SeekBar`/`RelativeLayout`, which Stories have too. No safe rule. |

Version codes weren't in those captures, so both rule sets have open version ranges (they fail open after a rename). The Inspector header now logs `version=<versionCode>`, so the next capture can pin them. Missing captures that would raise confidence: Search and the regular video player for YouTube; Reels opened from a DM, a profile and the home feed for Instagram; Search, Following and LIVE for TikTok; Search, Messenger and Watch/Video for Facebook. `SectionCaptureTest` checks every rule against node trees transcribed from the captures (`SectionCaptures`).

## 1. Capture on your phone (debug build)

1. Install a debug build (`./gradlew installDebug`) and finish onboarding.
2. With payments on only: Settings › Debug › Force Plus: **Plus** (section blocking is a Plus feature then; the inspector itself works without Plus). While payments are off (`billingEnabled = false`) everyone has it and the switch is hidden; skip this step.
3. Settings › Tracking › Section blocking › Set up › **I agree**, then switch on **Unscroll section blocking** in Accessibility settings.
4. Add the app you want to capture to the tracked list (Apps › +), for example YouTube.
5. Settings › Debug › **Section Inspector** › turn on **Log screen identifiers**.
6. On a computer: `adb logcat -c && adb logcat -s SectionInspector > capture.txt`
7. Open the app. A small **Mark** button floats near the bottom left. For each screen:
   - go to it (Reels tab, a reel opened from the feed, the chat inbox, a chat thread, search, a profile, the home feed),
   - tap **Mark** › Reels / Chat / Home / Search / Other.

   Each mark logs `MARK <LABEL> package=… window=…` followed by one line per visible view: `id=… class=… selected=…`. New windows are also logged unmarked.
8. Turn the inspector off when done, and send `capture.txt` back.

The log never contains text, content descriptions, usernames or messages: the inspector only sees the same identifiers the detector uses (`SectionInspectorFormat`, built from `SectionNode`, which has no text field).

## 2. Turn captures into rules

Per app, in `SectionRulesConfig.kt`:

| Field | Use for | Example (made up) |
|---|---|---|
| `selectedTabViewIds` | the bottom-bar tab that is `selected=true` only while the section is open | `com.example.app:id/clips_tab` |
| `blockedViewIds` | views that appear **only** in the section (the full-screen player) | `id/clips_viewer_pager` |
| `blockedClassNames` | view classes that appear only in the section | `com.example.app.ClipsPlayerView` |
| `blockedWindowClasses` | activities that are the section | `com.example.app.ClipsActivity` |
| `requiredViewIds` | IDs that must **all** be visible as well, so a rule is a combination | `id/clips_viewer_container` |
| `allowedViewIds` / `allowedWindowClasses` | chat and anything that must never be blocked; they win over every blocked marker | `id/direct_thread_composer` |

Rules of thumb:
- Prefer an ID that is present on every Reels screen and absent on every Chat/Home/Search screen in the captures. Check both directions with `grep`.
- An ID without the package (`id/name`) matches every package of the app (TikTok has two).
- Always add an allowed marker for chat, so a reel shared in a chat thread never blocks the thread.
- When an app update renames things, add a second `SectionRuleSet` with `minVersionCode` (the app's version code, `adb shell dumpsys package <pkg> | grep versionCode`) and cap the old one with `maxVersionCode`.
- Prefer a combination (`requiredViewIds`) over one ID.
- Add every capture to `SectionCaptures` (test sources) as a node tree; `SectionCaptureTest` then checks the blocked screen is blocked and no allowed screen ever is.
- Record the capture in the rule set's `verifiedOn` (device, Android version, app version name and code, date).

## 3. How blocking behaves

- **Fail open:** an unreadable screen, an unknown version or no matching identifier never blocks.
- The cover is an accessibility overlay of the service: touches don't reach the section, but it isn't focusable, so the system **Back**, **Home** and **Recents** always work. "Take me back to chat" removes the cover and sends Back to the app; "Go home" goes Home. If the app is still in the section afterwards, the cover comes back.
- Per app, the switch is the quick toggle at the top of App detail ("Block Reels only", "Block For You feed", "Block Shorts only"; only for the apps in `ReelsCapableApps`), for 15 min, 30 min, 1 hour, 2 hours or until turned off. It is stored as `reelsBlockedUntil` in `app_limits` and checked against the clock on every evaluation (`BlockEvaluator.blocksSection`). While an app's installed version has no identifiers here, the toggle is disabled with "Not available for this app version yet".
- Per app: "Always", or "After limit" (only once today's time reached the app's daily limit; with a daily limit the whole app is blocked at the limit anyway, so "After limit" matters while an "I need access" extension runs).
- Settings › Section blocking › **Turn off section blocking** stops everything at once; **Stop and remove consent** also switches the service off.
