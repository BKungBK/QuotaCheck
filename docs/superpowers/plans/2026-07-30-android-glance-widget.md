# Android Jetpack Glance Home Screen Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a responsive Home Screen Widget using AndroidX Glance 1.1.1 for the QuotaCheck Android app with real-time sync, interactive refresh, and adaptive Compact (2x2) and Expanded (4x2) layouts.

**Architecture:** The widget leverages Jetpack Glance (`GlanceAppWidget` & `GlanceAppWidgetReceiver`), observing `OfflineFirstQuotaRepository` cached data. It triggers immediate background syncs via an `ActionCallback` and receives auto-updates whenever `QuotaSyncWorker` completes a synchronization pass.

**Tech Stack:** Kotlin 2.3.21, AndroidX Glance AppWidget 1.1.1, Compose BOM 2026.06.00, WorkManager, Room, Material3 ColorProviders.

## Global Constraints

- AndroidX Glance version: `1.1.1` (`androidx.glance:glance-appwidget` & `androidx.glance:glance-material3`).
- All new Kotlin code must reside under `com.quotacheck.app.widget` in `android-app/app/src/main/java/com/quotacheck/app/widget/`.
- Must support both Light and Dark theme palettes matching `com.quotacheck.app.core.designsystem.Color`.
- Must not introduce any breaking changes to existing `QuotaSyncWorker` or `QuotaRepository` interfaces.

---

### Task 1: Add Glance Dependencies and Resource Metadata Files

**Files:**
- Modify: `android-app/gradle/libs.versions.toml`
- Modify: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/main/res/xml/quota_widget_info.xml`
- Create: `android-app/app/src/main/res/values/strings_widget.xml`
- Create: `android-app/app/src/main/res/drawable/ic_widget_refresh.xml`
- Create: `android-app/app/src/main/res/drawable/widget_preview_layout.xml`

**Interfaces:**
- Consumes: Version catalog and Gradle configuration.
- Produces: Glance AppWidget dependency availability and XML provider metadata for Android OS.

- [ ] **Step 1: Update `gradle/libs.versions.toml`**

Add Glance version and library tags under `[versions]` and `[libraries]`:

```toml
[versions]
glance = "1.1.1"

[libraries]
androidx-glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
```

- [ ] **Step 2: Update `app/build.gradle.kts` dependencies**

Add the dependencies:

```kotlin
dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // ...
}
```

- [ ] **Step 3: Create `app/src/main/res/xml/quota_widget_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="170dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:description="@string/widget_description"
    android:previewLayout="@layout/widget_preview_layout" />
```

- [ ] **Step 4: Create `app/src/main/res/values/strings_widget.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="widget_name">QuotaCheck Widget</string>
    <string name="widget_description">Shows AI quota remaining across pools</string>
    <string name="widget_refresh">Refresh Quota</string>
    <string name="widget_no_data">No quota pools configured</string>
    <string name="widget_updated_just_now">Updated just now</string>
</resources>
```

- [ ] **Step 5: Create Refresh Icon Vector `app/src/main/res/drawable/ic_widget_refresh.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"/>
</vector>
```

- [ ] **Step 6: Create Widget Preview Layout `app/src/main/res/drawable/widget_preview_layout.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="16dp" />
    <solid android:color="#181B21" />
</shape>
```

- [ ] **Step 7: Verify Gradle project sync and resource compilation**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew :app:assembleDebug"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit Task 1 changes**

```bash
git add android-app/gradle/libs.versions.toml android-app/app/build.gradle.kts android-app/app/src/main/res/
git commit -m "feat(widget): add glance dependencies and widget xml metadata"
```

---

### Task 2: Create Glance Theme and Color Providers

**Files:**
- Create: `android-app/app/src/main/java/com/quotacheck/app/widget/ui/QuotaWidgetTheme.kt`

**Interfaces:**
- Consumes: Colors from `com.quotacheck.app.core.designsystem.Color` (`BackgroundDark`, `SurfaceDark`, `Ink`, `InkMuted`, `StatusHealthy`, `StatusWarning`, `StatusCritical`, `ProviderClaude`, `ProviderGemini`).
- Produces: `QuotaWidgetTheme` Composable and Glance `ColorProvider` instances for light/dark mode widget rendering.

- [ ] **Step 1: Write `QuotaWidgetTheme.kt`**

```kotlin
package com.quotacheck.app.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import com.quotacheck.app.core.designsystem.AccentPrimary
import com.quotacheck.app.core.designsystem.BackgroundDark
import com.quotacheck.app.core.designsystem.BackgroundLight
import com.quotacheck.app.core.designsystem.Ink
import com.quotacheck.app.core.designsystem.InkLight
import com.quotacheck.app.core.designsystem.InkMuted
import com.quotacheck.app.core.designsystem.InkMutedLight
import com.quotacheck.app.core.designsystem.ProviderClaude
import com.quotacheck.app.core.designsystem.ProviderFallback
import com.quotacheck.app.core.designsystem.ProviderGemini
import com.quotacheck.app.core.designsystem.StatusCritical
import com.quotacheck.app.core.designsystem.StatusHealthy
import com.quotacheck.app.core.designsystem.StatusWarning
import com.quotacheck.app.core.designsystem.SurfaceDark
import com.quotacheck.app.core.designsystem.SurfaceLight

object WidgetColors {
    val background = ColorProvider(day = BackgroundLight, night = BackgroundDark)
    val surface = ColorProvider(day = SurfaceLight, night = SurfaceDark)
    val textPrimary = ColorProvider(day = InkLight, night = Ink)
    val textMuted = ColorProvider(day = InkMutedLight, night = InkMuted)
    val accent = ColorProvider(day = AccentPrimary, night = AccentPrimary)
    
    val statusHealthy = ColorProvider(day = StatusHealthy, night = StatusHealthy)
    val statusWarning = ColorProvider(day = StatusWarning, night = StatusWarning)
    val statusCritical = ColorProvider(day = StatusCritical, night = StatusCritical)

    fun statusColor(remainingFraction: Double): ColorProvider = when {
        remainingFraction <= 0.10 -> statusCritical
        remainingFraction <= 0.20 -> statusWarning
        else -> statusHealthy
    }

    fun providerColor(name: String): ColorProvider = when {
        name.contains("claude", ignoreCase = true) -> ColorProvider(day = ProviderClaude, night = ProviderClaude)
        name.contains("gemini", ignoreCase = true) -> ColorProvider(day = ProviderGemini, night = ProviderGemini)
        else -> ColorProvider(day = ProviderFallback, night = ProviderFallback)
    }
}

@Composable
fun QuotaWidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme {
        content()
    }
}
```

- [ ] **Step 2: Verify Compilation**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew :app:compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit Task 2 changes**

```bash
git add android-app/app/src/main/java/com/quotacheck/app/widget/ui/QuotaWidgetTheme.kt
git commit -m "feat(widget): add glance color providers and theme wrapper"
```

---

### Task 3: Implement Glance Widget UI Composables (Compact & Expanded)

**Files:**
- Create: `android-app/app/src/main/java/com/quotacheck/app/widget/ui/QuotaWidgetContent.kt`

**Interfaces:**
- Consumes: `List<QuotaPool>` domain model, Glance sizing (`LocalSize.current`), `RefreshQuotaActionCallback`.
- Produces: `QuotaWidgetContent` Composable rendering Compact (2x2) vs Expanded (4x2) Glance layouts.

- [ ] **Step 1: Write `QuotaWidgetContent.kt`**

```kotlin
package com.quotacheck.app.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.LinearProgressIndicator
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.quotacheck.app.MainActivity
import com.quotacheck.app.R
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.widget.RefreshQuotaActionCallback

@Composable
fun QuotaWidgetContent(
    pools: List<QuotaPool>,
    modifier: GlanceModifier = GlanceModifier,
) {
    val size = LocalSize.current
    val isExpanded = size.width >= 220.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (pools.isEmpty()) {
            EmptyWidgetContent()
        } else if (isExpanded) {
            ExpandedWidgetContent(pools = pools)
        } else {
            CompactWidgetContent(pools = pools)
        }
    }
}

@Composable
private fun EmptyWidgetContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "QuotaCheck",
            style = TextStyle(
                color = WidgetColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "No pools configured",
            style = TextStyle(
                color = WidgetColors.textMuted,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun CompactWidgetContent(pools: List<QuotaPool>) {
    val lowestPool = pools.minByOrNull { it.remainingFraction } ?: pools.first()
    val percentage = (lowestPool.remainingFraction * 100).toInt()

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QuotaCheck",
                style = TextStyle(
                    color = WidgetColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<RefreshQuotaActionCallback>()),
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(
            text = lowestPool.name,
            style = TextStyle(
                color = WidgetColors.textMuted,
                fontSize = 12.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "$percentage% remaining",
            style = TextStyle(
                color = WidgetColors.statusColor(lowestPool.remainingFraction),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun ExpandedWidgetContent(pools: List<QuotaPool>) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QuotaCheck",
                style = TextStyle(
                    color = WidgetColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<RefreshQuotaActionCallback>()),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        pools.take(3).forEach { pool ->
            val pct = (pool.remainingFraction * 100).toInt()
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pool.name,
                        style = TextStyle(
                            color = WidgetColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "$pct%",
                        style = TextStyle(
                            color = WidgetColors.statusColor(pool.remainingFraction),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                LinearProgressIndicator(
                    progress = pool.remainingFraction.toFloat(),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = WidgetColors.statusColor(pool.remainingFraction),
                    backgroundColor = WidgetColors.surface,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify Compilation**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew :app:compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit Task 3 changes**

```bash
git add android-app/app/src/main/java/com/quotacheck/app/widget/ui/QuotaWidgetContent.kt
git commit -m "feat(widget): implement responsive glance widget composable layouts"
```

---

### Task 4: Create ActionCallback, GlanceAppWidget & Receiver Classes

**Files:**
- Create: `android-app/app/src/main/java/com/quotacheck/app/widget/RefreshQuotaActionCallback.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/widget/QuotaWidget.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/widget/QuotaWidgetReceiver.kt`

**Interfaces:**
- Consumes: `QuotaCheckApp.appContainer.quotaRepository`, `QuotaCheckApp.appContainer.syncScheduler`.
- Produces: `QuotaWidgetReceiver` broadcast receiver entry point and `QuotaWidget` GlanceAppWidget instance.

- [ ] **Step 1: Write `RefreshQuotaActionCallback.kt`**

```kotlin
package com.quotacheck.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.quotacheck.app.QuotaCheckApp

class RefreshQuotaActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appContainer = (context.applicationContext as QuotaCheckApp).appContainer
        appContainer.syncScheduler.triggerImmediateSync()
        QuotaWidget().update(context, glanceId)
    }
}
```

- [ ] **Step 2: Write `QuotaWidget.kt`**

```kotlin
package com.quotacheck.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.quotacheck.app.QuotaCheckApp
import com.quotacheck.app.widget.ui.QuotaWidgetContent
import com.quotacheck.app.widget.ui.QuotaWidgetTheme
import kotlinx.coroutines.flow.first

class QuotaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            MEDIUM_HORIZONTAL,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContainer = (context.applicationContext as QuotaCheckApp).appContainer
        val pools = appContainer.quotaRepository.currentPools.first()

        provideContent {
            QuotaWidgetTheme {
                QuotaWidgetContent(pools = pools)
            }
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(120.dp, 100.dp)
        private val MEDIUM_HORIZONTAL = DpSize(220.dp, 100.dp)
    }
}
```

- [ ] **Step 3: Write `QuotaWidgetReceiver.kt`**

```kotlin
package com.quotacheck.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
```

- [ ] **Step 4: Verify Compilation**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew :app:compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit Task 4 changes**

```bash
git add android-app/app/src/main/java/com/quotacheck/app/widget/
git commit -m "feat(widget): implement QuotaWidget, Receiver, and Refresh ActionCallback"
```

---

### Task 5: Register Receiver in Manifest and Connect Sync Worker Auto-Update

**Files:**
- Modify: `android-app/app/src/main/AndroidManifest.xml`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/sync/QuotaSyncWorker.kt`

**Interfaces:**
- Consumes: `QuotaWidget().updateAll(context)`.
- Produces: System manifest intent filter registration and automatic widget updates upon worker sync completion.

- [ ] **Step 1: Add Widget Receiver to `AndroidManifest.xml`**

Insert the `<receiver>` block inside `<application>`:

```xml
        <receiver
            android:name=".widget.QuotaWidgetReceiver"
            android:exported="true"
            android:label="@string/widget_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quota_widget_info" />
        </receiver>
```

- [ ] **Step 2: Update `QuotaSyncWorker.kt` to trigger widget update**

In `QuotaSyncWorker.kt`, import `com.quotacheck.app.widget.QuotaWidget` and add `QuotaWidget().updateAll(applicationContext)` before returning:

```kotlin
import com.quotacheck.app.widget.QuotaWidget

// ... inside doWork() after alert delivery evaluateAndPublish:
        QuotaWidget().updateAll(applicationContext)
        resultFor(result)
```

- [ ] **Step 3: Verify Compilation & Build**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew :app:assembleDebug"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit Task 5 changes**

```bash
git add android-app/app/src/main/AndroidManifest.xml android-app/app/src/main/java/com/quotacheck/app/sync/QuotaSyncWorker.kt
git commit -m "feat(widget): register widget receiver in manifest and trigger updateAll in QuotaSyncWorker"
```

---

### Task 6: Comprehensive Verification & Build Validation

**Files:**
- Verification only

- [ ] **Step 1: Execute Full Gradle Unit Tests**

Run: `cmd /c "cd /d e:\QuotaCheck\android-app && gradlew testDebugUnitTest"`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Inspect Release Manifest**

Run: `powershell -ExecutionPolicy Bypass -File e:\QuotaCheck\android-app\scripts\inspect-release-manifest.ps1`
Expected: Output showing `QuotaWidgetReceiver` present in the parsed AndroidManifest.

- [ ] **Step 3: Final Commit and Verification Documentation**

Ensure working directory is clean and ready.
