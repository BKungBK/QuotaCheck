---
name: android-apk-builder
description: Build, verify, and export the Android APK (debug/release) in this Tauri v2 codebase. Use whenever the user asks to build the Android app/APK, assemble debug/release APK, copy a ready-to-install QuotaCheck APK to the project root, or package the mobile build after any change (including widget/Kotlin native changes).
---

# Android APK Build Skill

> Workflow for building, verifying, and exporting a ready-to-install Android
> APK (`QuotaCheck-debug.apk` / `QuotaCheck-release.apk`) in this Tauri v2
> codebase.

> **Scope note:** this skill only covers *how to build and export* the APK.
> It intentionally does **not** document specific color values, API
> endpoints, or protocol details for the widget/sync logic — those live in
> `src/lib/theme.css` (colors) and `src-tauri/src/quota_client.rs` (sync
> protocol) and must be read from there directly, not copied here, so this
> skill can't go stale when those files change.

## Prerequisites & Checklist

Before running an Android build, ensure:
- Node dependencies and Svelte 5 frontend are valid (`npx svelte-check`).
- Android SDK & NDK environments are configured properly.
- Rust mobile targets are installed (`aarch64-linux-android`,
  `armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`).

---

## Step 1: Pre-Build Frontend Verification

```powershell
npx svelte-check
```

---

## Step 2: Execute Android Build

### Option A: Gradle Build (recommended — required for native/Kotlin/widget changes)

```powershell
# Debug APK:
cd src-tauri/gen/android
.\gradlew assembleDebug

# Release APK:
.\gradlew assembleRelease
```

### Option B: Tauri CLI Build

```powershell
npx tauri android build --debug
```

**Wait for the build command to fully finish (exit code returned, terminal
back at prompt) before moving to Step 3.** Do not copy or commit the APK
while Gradle/Tauri is still running — the output file may not exist yet or
may be a stale/partial artifact from a previous build. Confirm the build
actually succeeded (look for `BUILD SUCCESSFUL` from Gradle, or a non-error
exit from the Tauri CLI) before proceeding; if it failed, stop and fix the
error instead of exporting/committing anything.

---

## Step 3: Export Ready-to-Install APK to Project Root

**Always copy the freshly built APK from the Gradle output folder to the
project root**, so it's easy to find and install — regardless of what
changed in that build (UI, widget, sync logic, or anything else):

```powershell
# Debug APK -> root:
Copy-Item -Path src-tauri\gen\android\app\build\outputs\apk\debug\app-debug.apk `
  -Destination QuotaCheck-debug.apk -Force

# Release APK -> root:
Copy-Item -Path src-tauri\gen\android\app\build\outputs\apk\release\app-release-unsigned.apk `
  -Destination QuotaCheck-release.apk -Force
```

### Ready-to-Install APK Locations
- **Debug APK (root):** `QuotaCheck-debug.apk`
- **Release APK (root):** `QuotaCheck-release.apk` — this is the one to
  hand to a user for direct install (พร้อมติดตั้ง).

---

## Step 4: Test & Install via ADB (optional)

```powershell
adb install -r QuotaCheck-debug.apk
# or
adb install -r QuotaCheck-release.apk
```

---

## Step 5: Git Commit & Push

1. Commit source/logic changes first, with a clear root-cause description
   if it's a bug fix.
2. Only after the build in Step 2 has fully completed and succeeded, and the
   APK has been copied in Step 3 — commit the updated root APK
   (`QuotaCheck-debug.apk` / `QuotaCheck-release.apk`) as a separate commit,
   e.g. `chore: update release APK with latest build`. Never commit an APK
   from a build that is still running or that failed.
3. Push:
   ```powershell
   git push
   ```

---

## Where to look for implementation details (not duplicated here)
- Widget colors: `src/lib/theme.css` (`--color-*` tokens) and how
  `src-tauri/gen/android/app/src/main/java/com/antigravity/quota/widget/QuotaWidgetProvider.kt`
  maps them.
- Widget sync protocol (OAuth, project discovery, quota API): the
  authoritative implementation is
  `src-tauri/src/quota_client.rs` — `QuotaSyncWorker.kt` must match it, but
  read the current `.rs` file each time rather than trusting a cached
  summary of it.