# Unscroll

Android app that helps people stop doomscrolling: it times sessions in Instagram, TikTok and Facebook, shows a live overlay timer, and adds friction (nudges, break reminders, blocks). Apps open instantly; the live timer is the stopper. All data stays on the device.

See [CLAUDE.md](CLAUDE.md) for principles and architecture, and [ROADMAP.md](ROADMAP.md) for milestones.

## Requirements

- Android Studio (latest stable), which bundles a suitable JDK (17 or newer)
- Android SDK Platform 36 (Android Studio offers to install it on first sync)
- A device or emulator running Android 8.0 (API 26) or newer

## Build and run in Android Studio

1. Clone the repository: `git clone https://github.com/SharanSapkota/Unscroll.git`
2. In Android Studio choose **File > Open** and select the cloned `Unscroll` folder (the one with `settings.gradle.kts`).
3. Wait for the Gradle sync to finish. If prompted, install the missing SDK platform.
4. Pick the `app` run configuration and a device or emulator, then click **Run**.

## Build from the command line

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device
./gradlew lint test assembleDebug   # what CI runs on every pull request
```

The command line build needs `ANDROID_HOME` set (or a `local.properties` file with `sdk.dir=...`). Android Studio creates `local.properties` for you.

## Project layout

```
app/src/main/java/com/unscroll/app/
  data/      Room, DataStore, repositories
  domain/    models and use cases (pure Kotlin)
  service/   tracking service (M2)
  overlay/   overlay timer and block screen (M4, M5)
  ui/        Compose screens, navigation, theme
  util/      helpers
```

Dependencies are declared in `gradle/libs.versions.toml`.
