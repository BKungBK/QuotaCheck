# Android internal release checklist

## Build inputs

- [x] Import `android-app/private-api.properties` from the authorized compatibility source; keep it outside version control.
- [x] Supply `QUOTACHECK_STORE_FILE`, `QUOTACHECK_STORE_PASSWORD`, `QUOTACHECK_KEY_ALIAS`, and `QUOTACHECK_KEY_PASSWORD` only to the release Gradle process.
- [x] Store the dedicated keystore and DPAPI-encrypted password outside the repository at `E:\Android\signing`.
- [x] Build the signed internal APK with `scripts/android-release.ps1 assembleRelease`; missing signing inputs fail with named variables.

## Artifact inspection

- [x] Run the release manifest inspector from `android-app`.
- [x] Confirm the merged release manifest has no forbidden debug, cleartext, test-only, profileable, or package-query setting.
- [x] Confirm R8 minification and resource shrinking ran, using `proguard-android-optimize.txt`.
- [x] Verify the adaptive launcher icon resources package successfully and the signed APK installs on Android 15.

## Required smoke test

- [x] Record final APK size: **2,317,551 bytes (2.21 MiB)**.
- [x] Record cold-start time on Infinix X6725 / Android 15: **554 ms TotalTime**.
- [x] Install and launch the signed internal build on Infinix X6725.
- [ ] Complete onboarding with the owner's refresh token.
- [ ] Run refresh; verify pools load, refresh failure remains non-sensitive, and tokens never appear in logcat.
- [ ] Toggle offline mode, reopen the app, and verify cached data remains usable.
- [ ] Verify notification permission and an alert delivery path on Android 13+.

## Automated coverage

- [x] Run `connectedDebugAndroidTest`: **30/30 passed**, including the fake-server end-to-end flow.
- [x] Run all debug unit tests and `lintDebug`: **passed**.
