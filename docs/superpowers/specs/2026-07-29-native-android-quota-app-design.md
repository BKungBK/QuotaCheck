# QuotaCheck Native Android App Design

**Date:** 2026-07-29  
**Status:** Approved design; implementation not started  
**Distribution:** Signed APK for personal use or a small internal team  

## 1. Product Goal

Build a clean-room native Android application that shows current quota,
usage history, billing-cycle timing, sync health, and actionable alerts. The
app must make every quota pool visible immediately, continue to work from
cached data while offline, and remain maintainable when the provider's private
API changes.

The Android implementation must not reuse or port the existing Tauri, Svelte,
Rust, generated Android, or historical Kotlin implementation. `DESIGN.md` is
the only approved source from the existing product and is used solely for
visual tokens and brand personality.

## 2. Success Criteria

- A user can understand every current quota pool, its remaining percentage,
  and reset time within three seconds of opening Home.
- Four quota pools fit without scrolling on a 360×800 dp-class viewport at
  default font scale. Smaller viewports, larger font scales, or additional
  pools scroll only the content area; bottom navigation remains fixed.
- The app remains useful without notification permission and while offline.
- Background and manual sync use one idempotent pipeline and cannot run in
  parallel.
- Low, critical, reset, and sync alerts are deduplicated across app and device
  restarts.
- Provider schema changes fail safely without crashing or corrupting cached
  data.
- No credential, authorization header, complete account email, or raw private
  API response is written to logs.
- Project files, Android SDK, Gradle caches, build outputs, and Android-specific
  temporary files use drive D or E rather than consuming substantial space on
  drive C. Existing small tools on C may be reused instead of downloaded again.

## 3. V1 Scope

### Included

- Single account configured by pasting a refresh token once
- Current quota pools with used, remaining, percentage, progress, and reset
  time
- Day, week, and month usage history
- Low-quota, critical-quota, billing-reset, sync-failure, and optional
  sync-success notifications
- Manual refresh and 30-minute automatic synchronization
- Room-backed offline cache and 90-day history retention
- Alert, sync, appearance, credential, and retention settings
- Standard Android heads-up, lock-screen, and notification-shade behavior
- Dark, light, and system theme selection with Dark as the default

### Explicitly Excluded

- Tauri, Svelte, Rust, or reused historical Android code
- Multiple accounts
- Public Google Play distribution
- CameraX, camera permission, QR, and barcode scanning
- Home-screen widget or Jetpack Glance
- Launcher or notification badge
- Foreground service
- Custom boot receiver
- Custom notification layouts
- Analytics and crash-reporting SDKs
- Arbitrary server or endpoint editing in release builds

## 4. Architecture Decision

Use a single Gradle application module with feature-first packages and strict
interfaces. This minimizes build and agent coordination overhead for V1 while
preserving boundaries that can become Gradle modules later.

```text
app/
├── core/model
├── core/database
├── core/network
├── core/security
├── core/notifications
├── core/designsystem
├── core/testing
├── feature/onboarding
├── feature/home
├── feature/history
├── feature/alerts
├── feature/settings
└── sync
```

The application uses Kotlin, Jetpack Compose, Material 3, MVVM with
unidirectional data flow, manual constructor injection, Room, DataStore,
WorkManager, Coroutines and Flow, Retrofit, OkHttp, and Kotlin serialization.
An application-scoped `AppContainer` wires dependencies without Hilt/Dagger or
annotation-processing overhead beyond Room's required KSP processor.

The dependency direction is:

```text
Compose → ViewModel → Repository → Room / DataStore / CredentialVault / Remote
```

Compose screens never access Room, DataStore, WorkManager, Retrofit, or the
credential vault directly.

## 5. Component Responsibilities

### UI and ViewModels

- Each screen receives immutable `UiState` and emits explicit `UiAction`
  values.
- ViewModels combine repository `Flow` values into lifecycle-aware
  `StateFlow`.
- Navigation arguments contain stable pool IDs, never serialized API
  responses.
- Existing content remains visible during refresh; refresh state is rendered
  inline.

### `QuotaRepository`

- Exposes Room as the only observable source of quota and history data.
- Coordinates atomic replacement of current pools, insertion of history
  samples, sync-state updates, retention cleanup, and alert evaluation.
- Never exposes private API DTOs to the UI or database layers.

### `PrivateQuotaRemoteDataSource`

- Owns token exchange, quota request, strict response validation, and mapping
  from provider DTOs to domain models.
- Is the only component aware of private endpoint paths, headers, schema
  shape, and provider-specific identifiers.
- Converts failures into typed domain errors without exposing sensitive
  response bodies.

### `CredentialVault`

- Generates a non-exportable AES-GCM key in Android Keystore.
- Stores only encrypted refresh-token ciphertext in app-private storage.
- Excludes the encrypted payload from Android backup because the device-bound
  key cannot be restored elsewhere.
- Keeps access tokens in memory only and clears them when replaced or when the
  process ends.

### `SyncCoordinator`

- Provides one synchronization pipeline for onboarding validation, manual
  refresh, periodic work, and notification actions.
- Uses unique WorkManager work names to prevent parallel execution.
- Writes data and sync state transactionally before evaluating notifications.

### `AlertEvaluator`

- Evaluates threshold crossings, cycle changes, and consecutive sync failures
  against persisted alert records.
- Produces alert commands only; it does not call Android notification APIs.

### `NotificationPublisher`

- Owns notification channels, notification IDs, deep links, visibility, and
  actions.
- Explicitly disables badges on every channel.

## 6. Data Model

### `quota_pools`

One current row per provider pool and billing window:

- `poolId`: stable provider identifier
- `displayName`: user-facing pool name
- `windowLabel`: billing window such as `5 hours` or `Weekly`
- `unitLabel`: provider unit when available
- `totalUnits`, `usedUnits`, `remainingUnits`: nullable decimal values
- `remainingFraction`: normalized value from `0.0` to `1.0`
- `cycleStartAt`, `cycleEndAt`: nullable UTC instants
- `providerUpdatedAt`: nullable provider timestamp
- `receivedAt`: device UTC instant
- `schemaVersion`: adapter schema identifier

The app never invents absolute units. When the API exposes only a fraction,
absolute fields remain null and the UI shows percentage and reset timing.

### `usage_samples`

Immutable time-series samples:

- generated sample ID
- pool ID
- remaining fraction
- nullable absolute unit values
- cycle end
- device receive time

A successful sync inserts a sample only when the quota value or cycle differs
from the most recent sample. Day, week, and month aggregates are calculated by
Room queries. When absolute units are unavailable, history is expressed as
percentage consumed rather than fabricated units.

### `sync_runs`

- start and finish timestamps
- trigger: onboarding, manual, periodic, or notification action
- result: success, retryable failure, auth required, schema failure, or local
  persistence failure
- sanitized error category
- consecutive failure count

### `alert_events`

- deterministic alert key
- pool ID
- cycle identifier
- alert type
- threshold where applicable
- delivery timestamp

The deterministic key prevents duplicate delivery after process death or
device restart.

### DataStore Preferences

- auto-sync enabled
- refresh interval fixed to supported choices, default 30 minutes
- Wi-Fi-only preference
- low threshold, default 20 percent
- critical threshold, default 10 percent
- reset, sync-failure, and sync-success toggles
- theme selection
- history retention, default 90 days
- onboarding and notification-rationale completion

## 7. Private API Feasibility Gate

Implementation begins with an isolated discovery task and must not inspect or
reuse the old QuotaCheck implementation.

The discovery task must:

1. Observe an authorized account session to identify token exchange and quota
   requests.
2. Produce sanitized request and response fixtures with tokens, cookies,
   account identifiers, and unrelated payload fields removed.
3. Confirm stable pool identifiers, remaining fraction, reset or cycle timing,
   and whether absolute units are available.
4. Capture representative success, 401/403, 429, 5xx, malformed, and
   schema-change responses.
5. Document required headers and token expiry behavior without recording live
   secrets.

Implementation stops before application scaffolding if access requires TLS
bypass, certificate-pinning bypass, account impersonation, or another
mechanism that creates unacceptable account risk. This is a feasibility gate,
not a request to circumvent provider security.

## 8. Sync Design

### Periodic Work

- Default interval: 30 minutes
- Constraint: connected network; unmetered network when Wi-Fi-only is enabled
- Scheduling is approximate and may be delayed by Android power management
- Unique periodic work prevents duplicate schedules
- Disabling auto-sync cancels its unique periodic work

### Manual Work

- Uses the same coordinator and transaction path as periodic work
- Does not clear or hide cached content while running
- An offline request reports cached state without starting a retry storm

### Retry Policy

- Network timeout and 5xx: exponential backoff
- 429: honor `Retry-After`, then retry
- 401/403: no retry; transition to Auth Required
- Schema mismatch: no blind retry loop; preserve cache and record Schema
  Failure
- Local database failure: no notification evaluation from uncommitted data

No foreground service, expedited work, polling loop, or custom reboot receiver
is used. WorkManager owns supported persistence and lifecycle behavior.

## 9. Application States

- **Unconfigured:** no valid credential
- **Initial Loading:** credential exists but no cached quota exists
- **Fresh:** the latest sync completed within two expected intervals
- **Stale:** cached data is older than two expected intervals
- **Offline Cached:** network is unavailable and cache exists
- **Auth Required:** the provider rejected the credential
- **Error Empty:** no usable cache exists after a failure
- **Refreshing:** a transient overlay state combined with any existing content
  state

All empty and failure states provide one primary recovery action. Permission
denial never blocks quota viewing or synchronization.

## 10. Screen Design

### Onboarding

1. Explain what the app reads and how credentials are stored.
2. Accept a masked pasted refresh token.
3. Validate the token before persisting the connected state.
4. Perform initial sync.
5. After quota is visible, explain alerts and request notification permission
   only if the user enables them.

No WebView login, browser-cookie extraction, camera, or QR flow is used.

### Home

Home uses the approved Pool-first layout:

- `All quotas` is the first content card; there is no aggregate summary card.
- Each row contains pool name, billing window, remaining percentage, reset
  time, and progress bar.
- Warning text accompanies amber styling; status is never color-only.
- Footer shows last update, automatic interval, and manual Refresh.
- Four rows fit a normal 360×800 viewport at default font scale.
- Content scrolls when necessary while bottom navigation remains fixed.

### History

History uses the approved Bars + Timeline layout:

- Pool selector
- Day, Week, and Month segmented control
- Bar chart for the selected period
- Total usage or percentage-consumed summary
- Daily breakdown with exact values and sample counts
- Empty explanation when history has not accumulated enough samples

The chart is drawn with Compose primitives to avoid a chart dependency in V1.

### Alerts

- Low-quota toggle and adjustable threshold
- Critical alert toggle with default 10 percent
- Billing-reset toggle
- Sync-failure toggle
- Sync-success toggle, off by default
- Link to Android application notification settings

### Settings

- Masked connected account
- Replace or remove refresh token
- Auto-sync toggle
- Refresh interval selection
- Wi-Fi-only toggle
- Dark, Light, or System theme
- System reduced-motion status
- History retention selection
- Clear-local-history action with confirmation

## 11. Visual System

The design uses `DESIGN.md` only as a visual reference.

- Brand character: serious, clean, compact, low-distraction, and
  developer-oriented
- Default theme: opaque dark neutral surfaces
- Depth: tonal layering and one-pixel borders; no shadows, gradients, or blur
- Accent usage: neutral gray for normal progress; amber only for warning or
  stale states; red only for errors and destructive actions
- Shape: 12 dp cards and 3 dp progress indicators
- Spacing scale: 4, 8, 12, 16, 20, and 24 dp
- Typography: bundled Inter for Latin text and tabular numbers, with Android
  system fallback for unsupported glyphs
- Motion: 200–400 ms state transitions, disabled or simplified when reduced
  motion is active

Light theme preserves the same neutral hierarchy by reversing surface and ink
tones. Material dynamic colors are disabled to preserve brand consistency.

## 12. Accessibility

- Minimum 48 dp interactive targets
- Screen-reader labels for icons, progress, chart values, and status
- Text labels accompany every status color
- Contrast target of at least 4.5:1 for essential text
- Tabular numerals reduce layout movement
- Layout supports large font scale by allowing content scrolling and row
  expansion
- Motion follows system animator and accessibility settings
- Notification permission denial has a persistent but non-blocking in-app
  explanation

## 13. Notification Design

### Channels

1. **Quota alerts**, High importance: low, critical, reset, and sync failure
2. **Sync status**, Default importance: optional manual-sync and recovery
   success

Both channels call `setShowBadge(false)`.

### Delivery Rules

- Low: deliver once when a pool crosses downward through 20 percent in a cycle
- Critical: deliver once when it crosses downward through 10 percent
- Reset: deliver once when the cycle changes
- Sync failure: deliver after three consecutive failures and suppress repeats
  until recovery
- Sync success: when enabled, deliver only for manual success or recovery from
  a previously notified failure

Notification body taps deep-link to the relevant screen. Sync failure includes
a Refresh action. Lock-screen visibility is Private and Android system/user
settings remain authoritative. A generic public version hides quota and
account details on restricted lock screens.

## 14. Permissions

The application manifest directly declares:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `POST_NOTIFICATIONS`

`POST_NOTIFICATIONS` is requested on Android 13 and later only after an
in-context explanation and only when alerts are enabled. The application does
not declare camera permission or its own foreground-service component.
WorkManager may merge `RECEIVE_BOOT_COMPLETED` and its internal rescheduling
receiver so persisted periodic work survives reboot; the app adds no custom
boot receiver or reboot logic.

## 15. Security and Privacy

- Refresh token encrypted with a device-bound Keystore key
- Access token retained in memory only
- Credential ciphertext excluded from backup
- Release endpoint cannot be changed from the UI
- Network Security Config disallows cleartext traffic
- No trust-all certificate manager or hostname-verification bypass
- No credential or private response in logs, crash files, test fixtures, or CI
- Account email masked outside the account detail row
- Clear Credential removes encrypted payload, cancels sync, and returns the
  app to Unconfigured state
- Debug fake API and fixtures contain synthetic identities only

## 16. Verification Strategy

### Unit Tests

- DTO-to-domain validation and mapping
- Percentage and nullable-unit formatting
- cycle-change detection
- low and critical threshold crossing
- notification deduplication
- sync error classification
- day, week, and month aggregation

### Data and Integration Tests

- Room DAO and transaction behavior
- retention cleanup
- repository source-of-truth behavior
- MockWebServer contract tests using sanitized fixtures
- WorkManager retry, auth-stop, constraints, and unique-work behavior
- encrypted credential save, read, replace, and clear behavior

### UI Tests

- onboarding and token validation
- Home pool ordering and four-row fit
- History filters and empty state
- alert preference persistence
- notification permission denial
- fresh, stale, offline, auth-required, and error-empty states
- navigation deep links and Refresh action
- accessibility semantics and large-font scrolling

### Manual Release Matrix

- User's primary physical Android phone over USB debugging
- Android 13+ notification permission when applicable
- heads-up, restricted lock screen, and notification shade
- offline launch and reconnection
- reboot and delayed WorkManager execution
- battery optimization behavior
- dark, light, and system themes
- signed release build installed on the real internal device

API 26 compatibility is checked by `minSdk`, Android Lint, and automated tests.
V1 does not download emulator or system images solely to expand the device
matrix.

A real-account smoke test runs only as a local release checklist. Live tokens
are never added to CI.

## 17. Release Design

- Signed internal release APK
- Signing material stored outside the repository
- Release minification and resource shrinking enabled
- Debug build supports only the synthetic fake API and sanitized fixtures
- Release build uses the fixed private-provider adapter
- Verbose network logging disabled in release
- Android Lint, unit tests, instrumented tests, and release assembly must pass
  before distribution

## 18. Local Development Storage

The project uses the smallest working command-line toolchain. Existing
installations are reused; no IDE or virtual-device stack is required. The
verified Windows layout is:

- Project: `E:\QuotaCheck\android-app`
- Android SDK: `D:\Android\Sdk`
- Gradle user home and dependency cache: `E:\Android\.gradle`
- Android user home: `E:\Android\.android`
- Android-specific temporary files: `E:\tmp\android`
- Existing JDK 17: `C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot`

Setup scripts and developer documentation must configure
`ANDROID_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME`,
`GRADLE_USER_HOME`, `JAVA_HOME`, and task-scoped `TEMP`/`TMP` values before
dependency installation or Gradle execution.

Build outputs stay under `E:\QuotaCheck\android-app`; Gradle local properties
point to the D-drive SDK. V1 reuses installed API 36, Build Tools 35.0.0,
Platform Tools, command-line tools, and JDK 17. It does not install Android
Studio, an emulator, AVD/system images, NDK, another JDK, or standalone Gradle.
The existing Gradle 8.14.3 archive may be copied from the current C-drive cache
to E once, avoiding a network download; all later Gradle state stays on E.
Android instrumented and manual tests run on a physical USB-debugging device.
Only Maven dependencies required by the app may be downloaded during the first
build.

## 19. Implementation Order

1. Private API feasibility and sanitized contract fixtures
2. Android project foundation, design system, and dependency injection
3. Domain models, Room schema, preferences, and credential vault
4. Private API adapter and repository transaction
5. Sync coordinator and WorkManager scheduling
6. Alert evaluator and Android notifications
7. Onboarding and navigation shell
8. Home
9. History
10. Alerts and Settings
11. Accessibility, error states, test matrix, and signed release verification

No production implementation begins until this specification and its separate
implementation plan are reviewed and approved.
