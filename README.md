# Emergency Service 24/7 — Android client

This repository builds an installable Android app that opens the existing secure ES 24/7 client portal in an Android WebView. The portal/backend remains hosted separately. Internet access is required; location permission is requested when the portal asks for it.

## Permanent signed release

The GitHub Actions workflow builds a release APK signed with a persistent release key. It verifies the APK signature before publishing the permanent GitHub Release asset.

**Required one-time setup:** Add these GitHub Actions repository secrets before running the workflow:
- `ES247_KEYSTORE_BASE64`
- `ES247_KEYSTORE_PASSWORD`
- `ES247_KEY_ALIAS`
- `ES247_KEY_PASSWORD`

Do not commit the keystore or its passwords to this public repository. Back up the keystore securely. Future app updates must use the same key, or Android will reject them as a signature mismatch.

Once the secrets are configured and a workflow run succeeds, the permanent download is:
https://github.com/globalenglishacademyonlineptyltd/es247-andriod/releases/download/android-latest/app-release.apk

## Build and verification

Open the Actions tab and run **Build and publish signed ES 24/7 Android APK** on the `main` branch. A successful run must pass the **Verify APK signature** step before publishing the release.

The APK is an online WebView app shell, not a fully offline/native rewrite. Test login, panic, GPS, family and incident flows on real Android devices before broad distribution.
