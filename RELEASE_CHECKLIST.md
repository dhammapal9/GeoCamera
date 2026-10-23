# Release checklist

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] Replace the Google Maps SDK API key placeholder with a production-restricted key.
- [ ] Replace the AdMob App ID with the production ID (or leave disabled).
- [ ] Run `./gradlew testDebugUnitTest`.
- [ ] Run `./gradlew lint` and resolve release-level warnings.
- [ ] Capture screenshots on at least one phone and one tablet form factor.
- [ ] Test offline capture (no network).
- [ ] Test GPS-off capture (manual location fallback).
- [ ] Test front and rear cameras.
- [ ] Verify photos save to Pictures/GeoStamp in the system Gallery (API 29+).
- [ ] Confirm the share, delete, and open-in-map actions on the photo viewer.
- [ ] Confirm the QR scanner torch toggles and reads sample codes.
- [ ] Prepare the Play Store privacy policy URL that links to `PRIVACY_POLICY.md`.
- [ ] Sign the release AAB with your production keystore.
- [ ] Attach `mapping.txt` to the Play Console upload.
