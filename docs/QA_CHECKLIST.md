# QA checklist (real devices)

Run before every release. Mark each cell ✅ / ❌ / n/a and write the build (versionName, versionCode) and date at the top of the copy you fill in. Attach "Copy diagnostics" output (Settings › Help) to every ❌.

## Devices

At least one of each class, covering Android 12, 13, 14 and 15 between them:

| ID | Class | Example | Android |
|----|-------|---------|---------|
| P  | Pixel | Pixel 7 / 8 | 14, 15 |
| S  | Samsung (One UI) | Galaxy A54 / S23 | 13, 14 |
| X  | Xiaomi / Oppo / OnePlus (aggressive battery) | Redmi Note 12, Oppo A78 | 12, 13 |
| F  | Foldable (optional) | Galaxy Z Fold / Pixel Fold | 14 |

Apps: Instagram (version in `SectionRulesConfig`, 450.0.0.50.77 at the time of writing) and TikTok 47.0.3 (`com.zhiliaoapp.musically`), plus one other tracked app (e.g. YouTube or Reddit). Also test one unsupported Instagram or TikTok version if available.

Setup: fresh install, onboarding done, Usage access + Overlay granted, swipe counting enabled with consent (incl. Reels screen checks), battery optimization off unless a row says otherwise.

## 1. Pill and Timer visibility (Instagram and TikTok)

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 1.1 | Open Instagram on Reels | Pill appears within ~0.5 s, ticking, Reels time and Reels swipes | | | |
| 1.2 | Reels → Messages (tab or DM icon) | Pill stays, dims (~60 %), pause mark, time and swipes frozen; never disappears or flickers | | | |
| 1.3 | Messages → Reels | Pill back to normal, resumes ticking from the frozen value | | | |
| 1.4 | 10 × Reels → Messages → Reels quickly | No flicker, no duplicate pill, numbers never jump back | | | |
| 1.5 | Same as 1.2–1.4 in TikTok (For You ↔ Inbox) | Same | | | |
| 1.6 | Home feed, search, profile, stories | Pill dimmed and paused on each | | | |
| 1.7 | App detail › Timer visibility = Only on Reels; repeat 1.2/1.3 | Pill invisible off Reels, touches pass through where it was, back on Reels in the same place without a re-add animation | | | |
| 1.8 | Swipe in Messages / home feed | Swipe count doesn't change | | | |
| 1.9 | Drag the pill, tap to collapse, rotate | Position kept per orientation, collapse works in both modes | | | |

## 2. Covers only in Reels

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 2.1 | Time limit 15 min, watch Reels until reached | Cover "Time limit reached" over Reels only | | | |
| 2.2 | With the cover up, tap "Take me back to chat" | Cover gone at once, app goes back (chat/previous screen); no cover in chat | | | |
| 2.3 | With the cover up, system Back / back gesture | App navigates back, cover gone within ~0.5 s once off Reels | | | |
| 2.4 | Limit reached in Reels, then open Messages and chat for 2 min | Chat fully usable, no cover, no touch blocked, no nudge | | | |
| 2.5 | Return to Reels with the limit still reached | Cover returns | | | |
| 2.6 | Swipe limit 50 per day, reach it in Reels | Cover "Swipe limit reached" over Reels only | | | |
| 2.7 | Both limits set, reach the swipe limit first, then the time limit | Cover keeps saying "Swipe limit reached" | | | |
| 2.8 | Turn the limit off in App detail while the cover is up | Cover gone right away (no cooldown) | | | |
| 2.9 | "Go home" on the cover | Home screen; cover gone | | | |
| 2.10 | "I need access" (time limit) | 5 more minutes of Reels, then the cover again | | | |
| 2.11 | Break reminder / limit warning due while in Messages | Nothing in Messages; it appears on the pill once back in Reels | | | |
| 2.12 | Tint on, extension running, go to Messages | No tint in Messages; tint back in Reels | | | |
| 2.13 | Block entire app on | Block screen for the whole app (unchanged) | | | |

## 3. Fail open

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 3.1 | Turn swipe counting off in Accessibility settings, limit reached | No cover anywhere in Instagram/TikTok, whole-app pill; App detail note "…limits won't block" | | | |
| 3.2 | Withdraw Reels screen-check consent | Same as 3.1, note offers "Review" | | | |
| 3.3 | Unsupported app version | Note "Reels detection isn't available for your {App} version yet, so limits won't block." | | | |
| 3.4 | Pull down the notification shade over Reels for 5 s | No crash; after closing the shade the pill returns to Reels mode | | | |

## 4. Other apps (unchanged behavior)

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 4.1 | YouTube/Reddit time limit reached | Block screen "Time limit reached" for the whole app | | | |
| 4.2 | Swipe limit reached | Full-screen cover anywhere in the app, Back swallowed, "Go home" works | | | |
| 4.3 | App detail of a non-Reels app | No Timer visibility, no Reels line, no "Block reels only"; Time limit and Swipe limit only | | | |
| 4.4 | Newly added app | Time limit on at 30 min, swipe limit off | | | |
| 4.5 | Existing user upgrade (install the previous build, set limits, upgrade) | Every stored limit unchanged; apps without limits still have none | | | |

## 5. Lifecycle and permissions

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 5.1 | Reboot | Tracking restarts after unlock; pill works | | | |
| 5.2 | App update (install a newer build over it) | Tracking restarts | | | |
| 5.3 | Swipe Unscroll away from Recents | Tracking keeps running (or restarts on X-class phones; note it) | | | |
| 5.4 | Force stop from App info, reopen | Orphaned session closed at last heartbeat; tracking resumes | | | |
| 5.5 | Revoke Overlay while the pill/cover is up | Windows removed within 2 s, no crash; Home banner "Tracking paused" | | | |
| 5.6 | Revoke Usage access while tracking | No crash; Home banner asks for it | | | |
| 5.7 | Revoke Notifications | No crash; tracking continues | | | |
| 5.8 | Turn Accessibility off mid-Reels | No crash; fail open (3.1) | | | |
| 5.9 | Battery saver on, Doze (screen off 30 min) | Tracking resumes on screen on; no stuck pill | | | |
| 5.10 | Lock screen while the pill is shown | Pill gone; back after unlock in the app | | | |

## 6. Edge cases

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 6.1 | Rotation in Reels | Pill repositions, stays in Reels mode | | | |
| 6.2 | Split screen / multi-window with Instagram | No crash; pill follows the foreground app | | | |
| 6.3 | Picture-in-picture (YouTube) | No crash; no cover over the PiP window | | | |
| 6.4 | Foldable fold/unfold | Pill stays on screen, inside the safe area | | | |
| 6.5 | Keyboard open in Messages | Pill doesn't steal focus; typing works | | | |
| 6.6 | Rapid app switching (Recents) between Instagram, TikTok and YouTube | One pill at a time, correct app, no stuck cover | | | |
| 6.7 | Gesture navigation and 3-button navigation | Back/Home always work with a cover up | | | |
| 6.8 | Dark mode on/off | Pill and cover always dark and readable | | | |
| 6.9 | Font size largest, display size largest | Pill and cover text not clipped | | | |
| 6.10 | Display cutout / punch hole | Pill never under the cutout | | | |
| 6.11 | Midnight while the pill is showing | Time rolls over to the time since midnight | | | |
| 6.12 | Process death mid-session (`adb shell am kill`) | Session closed at last heartbeat; restarts cleanly | | | |

## 7. Diagnostics and release

| # | Scenario | Expected | P | S | X |
|---|----------|----------|---|---|---|
| 7.1 | Settings › Help › Copy diagnostics | Preview shows versions, model, permissions, detector, installed versions, ≤200 log lines; no text or content; Share opens the share sheet; nothing sent otherwise | | | |
| 7.2 | Release build (signed) | Launches, onboarding, pill and cover work (R8) | | | |
| 7.3 | Debug build | LeakCanary reports no leak after 10 Reels ↔ Messages cycles and closing the app | | | |
