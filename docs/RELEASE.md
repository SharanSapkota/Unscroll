# Release checklist (M8)

Steps to take Unscroll from CI-green to Google Play. Items marked **(manual)** can't be done from the repo.

## 1. Before the first upload

- [ ] **Package name:** the application ID is `com.sharansapkota.unscroll` (registered in Play Console; `applicationId` in `app/build.gradle.kts`). It can never change after the first upload. The Kotlin `namespace` stays `com.unscroll.app` (source packages and `R`); code that needs the app's own ID uses `context.packageName`, never a literal. `PackageNameGuardTest` fails if the old ID is used as an app ID in a manifest, Gradle or resource file.
- [ ] **Signing (manual):** create an upload key and keep it outside the repo. Enroll in Play App Signing. Never commit `*.jks`, `*.keystore` or `keystore.properties` (`.gitignore` blocks them).
  - Copy `keystore.properties.example` to `keystore.properties` at the repo root and fill in `storeFile` (path relative to the repo root, ideally outside it), `storePassword`, `keyAlias` and `keyPassword`.
  - Without the file (or with a key missing), `assembleRelease`/`bundleRelease` stop with "Release signing is not configured: …". Debug builds, lint and tests don't need it.
- [ ] **Version:** in `app/build.gradle.kts` (`defaultConfig`):
  - `versionCode`: a whole number that must go up by at least 1 for **every** upload to Play (any track), even a re-upload of the same version. It is currently `1`.
  - `versionName`: what users see, e.g. `0.1.0` → `0.1.1` for a fix, `0.2.0` for new features, `1.0.0` for the public launch. It is currently `0.1.0`.
  - Bump both in the same commit as the release notes, e.g. `versionCode = 2`, `versionName = "0.1.1"`.
- [ ] **Release build (manual):** `./gradlew bundleRelease` with `keystore.properties` in place; the signed bundle is `app/build/outputs/bundle/release/app-release.aab`. `isMinifyEnabled` is still `false`. Turning on R8 needs keep-rule testing for Hilt, Room and Compose on a real device first, so leave it off for the first internal test.
- [ ] **Icon:** the adaptive icon (`mipmap-anydpi/ic_launcher.xml`, with a monochrome layer for themed icons) is in place. Export a 512×512 PNG for the store listing **(manual)**.
- [ ] **Store assets (manual):** 4–8 phone screenshots (Dashboard, the timer over an app, the block screen, Apps limits, Settings), and a 1024×500 feature graphic. Text is in [STORE_LISTING.md](../STORE_LISTING.md).
- [ ] **Privacy policy:** publish [PRIVACY_POLICY.md](PRIVACY_POLICY.md) at a public URL **(manual)** and enter it in the Play Console.
- [ ] **Terms:** publish [TERMS.md](TERMS.md) at a public URL **(manual)**. Point `url_privacy_policy` and `url_terms` in `strings.xml` at the final pages (they point at the repo's docs for now).
- [ ] **Unscroll Plus (manual, Play Console › Monetize › Subscriptions):**
  - Create the subscription `unscroll_plus_monthly` (the id in `PlusProduct.PRODUCT_ID`) with an auto-renewing base plan `monthly` (`PlusProduct.BASE_PLAN_ID`), billing period 1 month.
  - Price: EUR 0.67, and let Play convert it (or set local prices) for other countries. The app never hardcodes it: the paywall shows the price Play returns.
  - Turn on the grace period and account hold defaults. No free trial or intro offer is needed: the paywall only offers the base plan.
  - Add license testers (Setup › License testing) to buy with test cards; test subscriptions renew every few minutes, which is handy for checking "Plus ended".
  - A merchant account must be linked before the product can be activated.
- [ ] **Data safety form:** answers in [DATA_SAFETY.md](DATA_SAFETY.md).
- [ ] **Permission declarations:**
  - **Accessibility API:** [ACCESSIBILITY_DECLARATION.md](ACCESSIBILITY_DECLARATION.md), plus a short video.
  - **Foreground service, special use:** use the subtype text from the manifest property, plus a short video of the timer.
  - **Usage access** has no Play form, but the in-app explanation screen must stay.
- [ ] **Content rating questionnaire** and **target audience (manual):** 13+ is fine; nothing user-generated.

## 2. Testing tracks

1. **Internal testing:** up to 100 testers, available within minutes. Use it to smoke-test the signed release build on Xiaomi, Samsung and a Pixel.
2. **Closed testing (manual, required for new personal developer accounts):** at least 12 testers opted in for 14 days in a row before you can apply for production. Recruit testers early and keep them opted in.
3. **Production:** staged rollout (for example 10% → 50% → 100%), watching Android vitals for ANRs and crashes in the Play Console.

## 3. Every release

- [ ] CI green on `main` (`./gradlew lint test assembleDebug`).
- [ ] Manual test steps from the milestone PRs re-run on at least one OEM device with aggressive battery management.
- [ ] Room migrations: install the new build over the previous release with real data. Data must be kept.
- [ ] Re-check the Data safety answers if any library or permission changed. CI's "Merged manifest permissions" step lists every permission in the merged manifest.
- [ ] Unscroll Plus on a license-tester account: buy, restore on a second device, cancel and let it lapse (the "Plus ended" banner, the free app stays tracked), resubscribe (paused apps resume).
