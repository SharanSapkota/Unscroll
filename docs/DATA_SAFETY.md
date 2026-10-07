# Google Play: Data safety form (draft answers)

Answers for Play Console › App content › Data safety. They match [PRIVACY_POLICY.md](PRIVACY_POLICY.md). Check them against the current form before submitting.

## Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play counts data as "collected" only when it is sent off the device. Unscroll has no `INTERNET` permission, no SDKs that send data, and keeps everything in app-private storage on the phone. |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is transmitted) | |
| Do you provide a way for users to request that their data is deleted? | Yes (in-app) | Settings › Data & privacy › Delete history (usage only) or Delete all my data (everything); uninstalling also deletes everything. |

With "No" to collection and sharing, the listing shows **"No data collected"** and **"No data shared with third parties"**.

## Things to double-check before each release

- `app/src/main/AndroidManifest.xml` still has no `android.permission.INTERNET`.
- No new library sends data (analytics, crash reporting, ads). Adding one changes these answers.
- The CSV export is started by the user and saved where they choose, so it is not "sharing" by Play's definition.
- The Accessibility API declaration is filed separately: [ACCESSIBILITY_DECLARATION.md](ACCESSIBILITY_DECLARATION.md).
