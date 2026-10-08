# Google Play: Data safety form (draft answers)

Answers for Play Console › App content › Data safety. They match [PRIVACY_POLICY.md](PRIVACY_POLICY.md). Check them against the current form before submitting.

## Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Play counts data as "collected" only when it is sent off the device. Unscroll has no `INTERNET` permission (the manifest removes it even if a library asks for it, and CI checks the merged manifest), no SDKs that send data, and keeps everything in app-private storage on the phone. Unscroll Plus is bought with Google Play Billing: the payment happens in Google Play, and the app only asks the Play Store app on the phone whether the subscription is active. Unscroll never receives or sends purchase or payment data, and gives Google Play no usage data. |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing is transmitted) | |
| Do you provide a way for users to request that their data is deleted? | Yes (in-app) | Settings › Data & privacy › Delete history (usage only) or Delete all my data (everything); uninstalling also deletes everything. |

With "No" to collection and sharing, the listing shows **"No data collected"** and **"No data shared with third parties"**.

## Things to double-check before each release

- The merged manifest still has no `android.permission.INTERNET` (CI's "Merged manifest permissions" step lists every permission and fails if INTERNET appears). Play Billing adds `com.android.vending.BILLING`, which is not a data permission.
- Play Billing data: check Play's current guidance on data handled by Google Play Billing before submitting. Unscroll itself sends nothing; if the form asks about "Purchase history", the answer stays "not collected" as long as purchases are never sent anywhere by the app.
- No new library sends data (analytics, crash reporting, ads). Adding one changes these answers.
- The CSV export is started by the user and saved where they choose, so it is not "sharing" by Play's definition.
- The Accessibility API declaration is filed separately: [ACCESSIBILITY_DECLARATION.md](ACCESSIBILITY_DECLARATION.md).
