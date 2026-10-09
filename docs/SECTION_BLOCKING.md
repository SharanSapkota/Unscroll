# Section blocking: capturing identifiers

Section blocking covers only the short-video section of an app (Instagram Reels, TikTok For You, Facebook Reels, YouTube Shorts). It recognizes the section from **technical identifiers only**: view IDs (`viewIdResourceName`), class names, which tab is selected (`isSelected`) and the window (activity) class name. Never text or content descriptions.

The identifiers live in one file, `app/src/main/java/com/unscroll/app/domain/section/SectionRulesConfig.kt`. Every entry ships empty, so until real identifiers are filled in, every detector returns UNKNOWN and **nothing is blocked**. Don't add identifiers from memory or guesses: only ones captured with the Section Inspector below.

## 1. Capture on your phone (debug build)

1. Install a debug build (`./gradlew installDebug`) and finish onboarding.
2. Settings › Debug › Force Plus: **Plus** (section blocking is a Plus feature; the inspector itself works without Plus).
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
| `allowedViewIds` / `allowedWindowClasses` | chat and anything that must never be blocked; they win over every blocked marker | `id/direct_thread_composer` |

Rules of thumb:
- Prefer an ID that is present on every Reels screen and absent on every Chat/Home/Search screen in the captures. Check both directions with `grep`.
- An ID without the package (`id/name`) matches every package of the app (TikTok has two).
- Always add an allowed marker for chat, so a reel shared in a chat thread never blocks the thread.
- When an app update renames things, add a second `SectionRuleSet` with `minVersionCode` (the app's version code, `adb shell dumpsys package <pkg> | grep versionCode`) and cap the old one with `maxVersionCode`.
- Add a test to `SectionDetectorTest` (or a new test) with a node tree built from the capture: the section blocked, chat and home not blocked.

## 3. How blocking behaves

- **Fail open:** an unreadable screen, an unknown version or no matching identifier never blocks.
- The cover is an accessibility overlay of the service: touches don't reach the section, but it isn't focusable, so the system **Back**, **Home** and **Recents** always work. "Take me back to chat" removes the cover and sends Back to the app; "Go home" goes Home. If the app is still in the section afterwards, the cover comes back.
- Per app: "Always", or "After limit" (only once today's time reached the app's daily limit; with a daily limit the whole app is blocked at the limit anyway, so "After limit" matters while an "I need access" extension runs).
- Settings › Section blocking › **Turn off section blocking** stops everything at once; **Stop and remove consent** also switches the service off.
