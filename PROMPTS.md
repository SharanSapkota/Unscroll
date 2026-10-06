# Ready-to-paste prompts for Claude Code

Paste one per session. Start each with: "Read CLAUDE.md and ROADMAP.md first."

## M0
Read CLAUDE.md and ROADMAP.md. Implement M0: create the Android project in Kotlin with Jetpack Compose, Material 3, Hilt, Room, and DataStore under package com.unscroll.app, min SDK 26. Add a bottom navigation with Dashboard, Apps, and Settings placeholder screens, dark mode support, and a GitHub Actions workflow running ./gradlew lint test assembleDebug. Open a PR when done.

## M1
Read CLAUDE.md and ROADMAP.md. Implement M1: permissions onboarding. Create a multi-step onboarding flow explaining why Usage Access, Overlay, and Notification permissions are needed, with buttons that open the right system settings screens. Track permission state with a PermissionRepository exposing Flows, re-check when the app resumes, and gate the dashboard until required permissions are granted. Add a battery optimization step with hints for Xiaomi, Samsung, Huawei, and OnePlus. Add unit tests where possible.

## M2
Read CLAUDE.md and ROADMAP.md. Implement M2: foreground detection and session logging. Build a foreground TrackingService, an AppDetector using UsageStatsManager events that only runs while the screen is on, and a SessionManager that debounces switches shorter than 3 seconds. Persist sessions in Room. Recover orphaned sessions after process death. Include unit tests for SessionManager with a fake clock and fake detector. List manual device test steps in the PR.

## M3
Read CLAUDE.md and ROADMAP.md. Implement M3: the dashboard. Add DAO queries and use cases for per-app totals (today, week, month, all-time), open counts, average and longest session, hours-invested reframing, a time-of-day heatmap, and trend vs last week. Build the Compose UI with simple custom charts (no heavy chart libraries). Add DAO tests using in-memory Room.

## M4
Read CLAUDE.md and ROADMAP.md. Implement M4: the live overlay timer. Create OverlayTimerManager using WindowManager TYPE_APPLICATION_OVERLAY showing the current session time, updating every second, with green/yellow/red color escalation using configurable thresholds, draggable position saved in DataStore, and automatic hide when the tracked app leaves the foreground. Add settings to toggle it and choose thresholds.

## M5
Read CLAUDE.md and ROADMAP.md. Implement M5: limits and blocking. Add AppLimitEntity and a management UI in the Apps tab (daily limit, block always, schedule). Create BlockActivity that appears over a blocked app and sends the user home on dismiss. Add a cooldown mechanism so unblocking or raising a limit takes effect after a delay or requires typing a phrase. Add tests for the limit/schedule evaluation logic.

## M6
Read CLAUDE.md and ROADMAP.md. Implement M6: friction and nudges. Add a 10-second pause screen with a breathing animation before tracked apps open, open-count notifications, periodic break reminders, and per-app toggles for each feature.

## M7
Read CLAUDE.md and ROADMAP.md. Implement M7: an opt-in AccessibilityService that counts scroll events per session in tracked apps only, and shows a take-a-break screen after a configurable number of swipes. Never read or store text content from the screen. Add a clear in-app disclosure screen before the user is sent to Accessibility settings.

## M8
Read CLAUDE.md and ROADMAP.md. Implement M8: streaks, goals, weekly report, CSV data export, and delete-all-data. Then prepare release: signing config documentation, ProGuard/R8 rules check, and a checklist for Play Console in docs/RELEASE.md.

## Useful follow-ups
- "Review this PR for battery impact and Play Store policy risks."
- "Write a privacy policy for this app based on what the code actually does."
- "Find bugs in SessionManager edge cases: reboot, screen off mid-session, app crash."
