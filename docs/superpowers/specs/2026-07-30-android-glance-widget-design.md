# Android Jetpack Glance Home Screen Widget Design Spec

**Date:** 2026-07-30  
**Status:** Approved  
**Target Project:** `android-app` (`com.quotacheck.app`)  
**Technology:** AndroidX Glance AppWidget 1.1.1 (`androidx.glance:glance-appwidget`)

---

## 1. Overview & Goal

Implement an Android Home Screen Widget for the QuotaCheck app using **Jetpack Glance 1.1.1**. The widget provides real-time scannability of quota remaining across model pools (Claude, Gemini, etc.), auto-syncs with the app's background worker, supports responsive resizing (Compact 2x2 and Expanded 4x2), and provides an interactive refresh button.

---

## 2. Key Requirements & Features

1. **Responsive Layout (`SizeMode.Responsive`)**:
   - **Compact (2x2)**: Displays quick overview (lowest remaining quota pool, percentage, status indicator).
   - **Expanded (4x2 / 4x3)**: Displays list of quota pools with status progress bars and provider identity colors.
2. **Auto-Sync Integration**:
   - Reads cached data directly from `OfflineFirstQuotaRepository`.
   - `QuotaSyncWorker` calls `QuotaWidget().updateAll(context)` upon successful data synchronization.
   - Periodic OS updates via `updatePeriodMillis="1800000"` (30 mins) configured in `quota_widget_info.xml`.
3. **Interactivity**:
   - **Clicking the Refresh icon button**: Triggers `RefreshQuotaActionCallback` which invokes `WorkManagerSyncScheduler.triggerImmediateSync()` and updates widget state.
   - **Clicking anywhere else on widget**: Launches `MainActivity` (Navigates to Home screen).
4. **Theme & Branding**:
   - Uses custom `GlanceTheme` mapped from `Color.kt` (`BackgroundDark`, `SurfaceDark`, `StatusHealthy/Warning/Critical`, `ProviderClaude/Gemini`).
5. **Empty & Error States**:
   - Displays friendly prompt card when no quota pools or credentials are configured.

---

## 3. Tech Stack & Dependencies

| Dependency | Specification |
| :--- | :--- |
| `androidx.glance:glance-appwidget` | `1.1.1` |
| `androidx.glance:glance-material3` | `1.1.1` |
| `compileSdk` / `targetSdk` | 36 |
| Kotlin | 2.3.21 |

---

## 4. Architecture & File Structure

All new widget components reside in package `com.quotacheck.app.widget`:

```
android-app/app/src/main/java/com/quotacheck/app/widget/
├── QuotaWidget.kt                   # GlanceAppWidget implementation with Responsive sizeMode
├── QuotaWidgetReceiver.kt           # GlanceAppWidgetReceiver broadcast receiver
├── RefreshQuotaActionCallback.kt     # ActionCallback executing immediate sync via WorkManager
└── ui/
    ├── QuotaWidgetContent.kt        # Composable layouts (Compact & Expanded)
    └── QuotaWidgetTheme.kt          # Glance ColorProviders mapped from Color.kt
```

### Resource Files

```
android-app/app/src/main/res/
├── xml/quota_widget_info.xml        # AppWidgetProviderInfo XML metadata
├── values/strings_widget.xml        # Strings for Widget name and description
└── drawable/
    ├── widget_preview_layout.xml    # Preview layout for launcher selection
    └── ic_widget_refresh.xml        # Refresh icon asset
```

---

## 5. Detailed Component Specifications

### 5.1 Gradle & Dependency Setup (`gradle/libs.versions.toml` & `app/build.gradle.kts`)
- Add `glance = "1.1.1"` under `[versions]`.
- Add `androidx-glance-appwidget` and `androidx-glance-material3` under `[libraries]`.
- Declare `implementation(libs.androidx.glance.appwidget)` and `implementation(libs.androidx.glance.material3)` in `app/build.gradle.kts`.

### 5.2 `QuotaWidget.kt`
- Inherits `GlanceAppWidget()`.
- Sets `override val sizeMode = SizeMode.Responsive(setOf(SMALL_SQUARE, MEDIUM_HORIZONTAL))` where:
  - `SMALL_SQUARE = DpSize(120.dp, 100.dp)`
  - `MEDIUM_HORIZONTAL = DpSize(220.dp, 100.dp)`
- Implements `override suspend fun provideGlance(context: Context, id: GlanceId)`:
  - Obtains `QuotaRepository` from `(context.applicationContext as QuotaCheckApp).appContainer`.
  - Observes latest quota pools (`quotaRepository.currentPools.first()`).
  - Calls `provideContent` wrapping layout with `QuotaWidgetTheme`.

### 5.3 `RefreshQuotaActionCallback.kt`
- Implements `ActionCallback`.
- Inside `onAction(context, glanceId, parameters)`:
  - Calls `appContainer.syncScheduler.triggerImmediateSync()`.
  - Calls `QuotaWidget().update(context, glanceId)` or `QuotaWidget().updateAll(context)`.

### 5.4 `QuotaWidgetContent.kt`
- Reads `LocalSize.current` to determine whether to render `CompactLayout` or `ExpandedLayout`.
- Uses Glance Composables: `Column`, `Row`, `Text`, `Image`, `LinearProgressIndicator`, `Spacer`, `Box`, `Alignment`, `GlanceModifier`, `actionStartActivity`.
- Uses provider brand colors (Claude orange, Gemini blue) and status colors (Healthy green, Warning yellow, Critical red).

### 5.5 Sync Worker Integration (`QuotaSyncWorker.kt`)
- In `doWork()`, after `synchronize(trigger)` completes successfully, execute:
  ```kotlin
  QuotaWidget().updateAll(applicationContext)
  ```

### 5.6 Manifest & Provider Metadata
- `app/src/main/res/xml/quota_widget_info.xml`:
  - `minWidth="170dp"`, `minHeight="110dp"`
  - `resizeMode="horizontal|vertical"`
  - `updatePeriodMillis="1800000"`
- `AndroidManifest.xml`:
  - Declare `QuotaWidgetReceiver` with `APPWIDGET_UPDATE` intent filter.

---

## 6. Verification & Test Plan

1. **Gradle Build Verification**:
   - Run `./gradlew :app:assembleDebug` to verify compilation with Glance 1.1.1.
2. **Unit / Integration Tests**:
   - Verify `QuotaSyncWorker` triggers widget updates cleanly without exceptions.
3. **Manual Verification**:
   - Add QuotaCheck widget to Android launcher / home screen.
   - Verify 2x2 compact and 4x2 expanded layout rendering.
   - Click Refresh button on widget to trigger immediate background sync.
   - Click widget body to verify navigation to `MainActivity`.
