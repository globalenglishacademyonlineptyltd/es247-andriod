# Emergency Service 24/7 — Android client

This repository builds an installable Android APK for the ES 24/7 client app.

## Build
GitHub Actions builds a debug APK on each push to `main`. Open the Actions tab and download the `ES247-Android-APK` artifact from a successful run.

## Current implementation
The APK provides a standalone Android launcher app and opens the existing secure HTTPS client application in an Android WebView. The client portal and existing backend remain hosted by Railway. Internet access is required. Location access is requested when the portal asks for it.

This is an initial installable app shell, not a fully offline/native rewrite. Before broad public distribution, replace the debug build with a properly signed release build and test the complete login, panic, GPS, family and incident flows on real Android devices.
