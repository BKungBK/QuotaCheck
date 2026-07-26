---
name: android-apk-builder
description: Build, verify, package, and export the Android APK and home-screen widget in Tauri v2. Use whenever the user asks to build the Android app/APK, assemble debug/release APK, copy ready-to-install QuotaCheck APK to root, compile Android Kotlin code, test Android home-screen widget sync, or package the mobile build.
---

# Android APK & Widget Build Skill

> Complete workflow for building, testing, verifying, packaging, and exporting ready-to-install Android APKs (`QuotaCheck-debug.apk` / `QuotaCheck-release.apk`) in this Tauri v2 codebase.

## Prerequisites & Checklist

Before running an Android build, ensure:
- Node dependencies and Svelte 5 frontend are valid (`npx svelte-check`).
- Android SDK & NDK environments are configured properly.
- Rust mobile targets are installed (`aarch64-linux-android`, `armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`).

---

## Step 1: Pre-Build Frontend Verification

Run Svelte typecheck to ensure frontend code in `src/` and `src/lib/` has no errors before triggering the native compilation:

```powershell
npx svelte-check
```

---

## Step 2: Execute Android Build

Build the Android APK using Gradle or Tauri CLI.

### Option A: Gradle Build (Recommended for Native/Widget/Kotlin Changes)

```powershell
# For Debug APK:
cd src-tauri/gen/android
.\gradlew assembleDebug

# For Release APK:
# .\gradlew assembleRelease
```

### Option B: Tauri CLI Build

```powershell
npx tauri android build --debug
```

---

## Step 3: Android Widget & Native Logic Verification

When modifying native Android files, verify the following core contracts:

### 1. Widget Colors (`QuotaWidgetProvider.kt`)
- All constants in `WidgetColors` must map directly to `src/lib/theme.css` tokens:
  - `ACCENT` = `#5D5D5D` (`--color-accent`)
  - `INK_HIGH` = `#E6E6E6` (`--color-ink-high`)
  - `DOT_LIVE` = `#AEAEAE` (`--color-dot-live`)
  - `DOT_STALE` = `#BA8400` (`--color-dot-stale`)
  - `DOT_OFFLINE` = `#4D4D4D` (`--color-dot-offline`)
  - `LOW_REMAINING` = `#6B6B6B` (`--color-bar-low`)

### 2. Widget Sync Flow (`QuotaSyncWorker.kt`)
- Must match `src-tauri/src/quota_client.rs` protocol sequence:
  1. **OAuth token exchange:** `https://oauth2.googleapis.com/token`
  2. **`loadCodeAssist` Project Discovery:** `https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist` with headers `User-Agent: antigravity/1.104.0 windows/amd64` and `Client-Metadata: {"ideType":"ANTIGRAVITY","platform":"WINDOWS","pluginType":"GEMINI"}`
  3. **ResourceManager Fallback:** `https://cloudresourcemanager.googleapis.com/v1/projects` scanning for `gen-lang-client` or `generative-language`
  4. **Quota Summary API:** `https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary` sending `{"project": "<projectId>"}` with `platform: WINDOWS`

---

## Step 4: Export Ready-to-Install APK to Project Root

**IMPORTANT:** Always copy the freshly built APK from the Gradle build output folder to the project root directory so users and developers can easily find and download it:

```powershell
# Export Debug APK to root:
Copy-Item -Path src-tauri\gen\android\app\build\outputs\apk\debug\app-debug.apk -Destination QuotaCheck-debug.apk -Force

# Export Release APK to root (when available):
# Copy-Item -Path src-tauri\gen\android\app\build\outputs\apk\release\app-release-unsigned.apk -Destination QuotaCheck-release.apk -Force
```

### Ready-to-Install APK Locations
- **Debug APK (Root):** `QuotaCheck-debug.apk`
- **Release APK (Root):** `QuotaCheck-release.apk`

---

## Step 5: Test & Install via ADB (Optional)

To test the generated APK directly on a connected Android device or emulator:

```powershell
adb install -r QuotaCheck-debug.apk
```

---

## Step 6: Git Commit & Release Workflow

When committing build fixes or updated skills:
1. Commit isolated cosmetic/UI changes first (e.g. `fix(android): update widget colors`).
2. Commit native logic/worker fixes second with clear root cause documentation.
3. Commit updated skill and export documentation.
4. Push to remote:
   ```powershell
   git push
   ```
