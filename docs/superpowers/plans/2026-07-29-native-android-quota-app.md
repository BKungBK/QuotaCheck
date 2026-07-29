# QuotaCheck Native Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a clean-room native Android APK that securely reads private-provider quota data, stores offline history, synchronizes in the background, and delivers deduplicated Android notifications.

**Architecture:** One Gradle application module organized by feature-first packages. Compose ViewModels consume repository flows; Room is the observable source of truth; the provider adapter, credential vault, WorkManager pipeline, and notification publisher remain isolated behind interfaces. A small application-scoped `AppContainer` performs manual constructor injection.

**Tech Stack:** Kotlin 2.3.21, AGP 8.13.2, Gradle 8.14.3, existing JDK 17, compile/target SDK 36, min SDK 26, Compose BOM 2026.06.00, Material 3 1.4.0, Vico 3.2.3, Room 2.8.4, DataStore 1.2.1, WorkManager 2.11.2, Lifecycle 2.10.0, Navigation Compose 2.9.8, Retrofit 3.0.0, OkHttp 5.3.0, Coroutines 1.11.0, Kotlin serialization, and KSP 2.3.9 for Room only. Do not add a library unless an implemented V1 feature needs it.

## Global Constraints

- Create a new implementation under `E:\QuotaCheck\android-app`; do not reuse Tauri, Svelte, Rust, generated Android, or historical Kotlin implementation.
- Use `E:\QuotaCheck\DESIGN.md` only for visual tokens and brand personality.
- Reuse `D:\Android\Sdk` and the existing C-drive JDK 17; keep new Gradle cache, build output, and Android temporary files on E.
- Support Android 8.0/API 26 through target SDK 36.
- Do not install Android Studio, emulator/AVD/system images, NDK, another JDK, standalone Gradle, API 37, or Build Tools 36.
- Use manual constructor injection through `AppContainer`; do not add Hilt or Dagger.
- Run device tests on a physical USB-debugging Android phone.
- For charts, add only Vico 3.2.3 `compose` and `compose-m3`; do not add its Material 2, Glance, or other modules.
- Keep normal new development data on E within approximately 3 GB and peak build usage within 4 GB; target release APK 10-18 MB and installed app plus data below 80 MB.
- Use one account and pasted refresh-token onboarding in V1.
- Keep Room as the only observable quota/history source of truth.
- Default auto-sync interval is 30 minutes; default history retention is 90 days.
- Default low threshold is 20%; default critical threshold is 10%.
- Never log credentials, authorization headers, complete account email, or raw private responses.
- Do not use TLS bypass, certificate-pinning bypass, CameraX, QR, barcode, widget, badge, foreground service, custom boot receiver, analytics, or crash-reporting SDK.
- V1 is a single-owner, sideload-only unofficial direct-API compatibility mode; no hosted backend is used.
- The user accepts that static OAuth client metadata and compatibility headers can be extracted from the APK and that the private API may change.
- Reading the old project is permitted only in Task 1 to extract the HTTP contract and in the local secret-import step; never port its architecture, UI, storage, logging, or application code.
- Never commit OAuth client values or the owner's refresh token. Load OAuth values from `android-app/private-api.properties`, which must be gitignored and absent from packaged resources.
- Do not begin Tasks 2–15 unless Task 1 passes its feasibility gate.
- Every task commits only its own files; preserve unrelated user changes.

---

## Planned File Map

```text
E:\QuotaCheck\
├── scripts\
│   ├── android-env.ps1
│   ├── android-gradle.ps1
│   └── verify-android-storage.ps1
├── docs\api\
│   └── private-quota-contract.md
└── android-app\
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradle\libs.versions.toml
    ├── app\build.gradle.kts
    └── app\src\
        ├── main\java\com\quotacheck\app\
        │   ├── QuotaCheckApp.kt
        │   ├── MainActivity.kt
        │   ├── core\model\
        │   ├── core\database\
        │   ├── core\network\
        │   ├── core\security\
        │   ├── core\notifications\
        │   ├── core\designsystem\
        │   ├── feature\onboarding\
        │   ├── feature\home\
        │   ├── feature\history\
        │   ├── feature\alerts\
        │   ├── feature\settings\
        │   └── sync\
        ├── test\java\com\quotacheck\app\
        └── androidTest\java\com\quotacheck\app\
```

**Path notation:** Every path is relative to `E:\QuotaCheck`. Inside task file
lists, paths beginning with `core/`, `feature/`, `navigation/`, or `sync/`
resolve under `android-app/app/src/main/java/com/quotacheck/app/`.

## Subagent Dispatch Policy

- Task 1: `gpt-5.6-sol`, high reasoning. It is a security-sensitive discovery and feasibility decision.
- Tasks 2, 6, 7, 9, 10, and 15: `gpt-5.6-terra`, medium reasoning.
- Tasks 3–5, 8, and 11–14: `gpt-5.6-terra`, low reasoning.
- Give each worker only the approved spec, this plan's Global Constraints, its task section, and outputs listed under `Consumes`.
- Never dispatch two workers that modify the same file concurrently.
- After every task, run a spec-compliance review before starting the next task.

---

### Task 1: Private API Feasibility and Sanitized Contract

**Files:**

- Create: `docs/api/private-quota-contract.md`
- Create: `android-app/app/src/test/resources/fixtures/token-success.json`
- Create: `android-app/app/src/test/resources/fixtures/quota-success.json`
- Create: `android-app/app/src/test/resources/fixtures/error-401.json`
- Create: `android-app/app/src/test/resources/fixtures/error-429.json`
- Create: `android-app/app/src/test/resources/fixtures/error-500.json`
- Create: `android-app/app/src/test/resources/fixtures/schema-invalid.json`

**Interfaces:**

- Consumes: the owner's authorized account plus the narrowly authorized legacy API-client source
- Produces: exact HTTPS base URL, token path, quota path, required non-secret headers, DTO field map, expiry behavior, synthetic sanitized fixtures, and `FEASIBLE` or `STOP` decision

- [ ] **Step 1: Record the discovery rules before observing traffic**

Write the contract document with these mandatory headings:

```markdown
# Private Quota API Contract

## Decision
`FEASIBLE` when the legacy contract uses normal HTTPS, exposes the required
quota fields, and can be reproduced without TLS/certificate bypass. Record
that this is an owner-accepted unofficial compatibility integration.

## Authentication Exchange
Document method, HTTPS origin, path, content type, non-secret headers, request fields,
response fields, expiry semantics, and sanitized failure behavior.

## Quota Request
Document method, HTTPS origin, path, non-secret headers, stable pool identifier,
display name, billing window, remaining fraction, optional units, and reset/cycle fields.

## Error Classification
Map 401/403 to AuthRequired, 429 to RateLimited, 5xx/network to Retryable,
invalid JSON/shape to SchemaMismatch.

## Safety
No live token, cookie, account identifier, authorization value, or raw capture is retained.
No TLS or certificate-pinning bypass is permitted.
Static OAuth values are not copied into this committed contract.
```

- [ ] **Step 2: Observe only the authorized session**

Inspect only `src-tauri/src/quota_client.rs` and directly referenced
configuration to extract method, HTTPS origin/path, content type, required
headers, request fields, response fields, refresh behavior, and error mapping.
Do not copy logging, storage, UI, architecture, or unrelated behavior. Validate
against the owner's normal session when available. If the contract requires
TLS or certificate-pinning bypass, set Decision to `STOP`.

- [ ] **Step 3: Create synthetic fixtures**

Fixtures must preserve field names and data types but replace all values:

```json
{
  "pools": [
    {
      "poolId": "synthetic-gemini-5h",
      "displayName": "Gemini",
      "window": "5 hours",
      "remainingFraction": 0.68,
      "resetTime": "2030-01-01T03:00:00Z"
    }
  ]
}
```

- [ ] **Step 4: Run a secret scan**

Run:

```powershell
rg -n -i "authorization|bearer|refresh_token|cookie|gmail\.com|@[a-z0-9.-]+\.[a-z]{2,}" docs/api android-app/app/src/test/resources
```

Expected: matches only explanatory field names or synthetic values; no live secret or identity.

- [ ] **Step 5: Review the feasibility gate**

Expected:

- `FEASIBLE`: normal HTTPS request, refresh-token exchange, pool ID, remaining
  fraction, and reset/cycle data are extractable; the contract explicitly
  records unsupported/private-API and APK-extraction risks accepted by the
  single owner.
- `STOP`: TLS/certificate bypass is required or core quota fields are unavailable.

- [ ] **Step 6: Commit**

```powershell
git add docs/api/private-quota-contract.md android-app/app/src/test/resources/fixtures
git commit -m "docs: capture sanitized private quota contract"
```

**Worker prompt:**

```text
Read the approved Android spec, Global Constraints, and Task 1 only. The owner
explicitly authorizes reading the old quota client solely to extract its HTTP
contract for a single-owner, sideload-only unofficial compatibility adapter.
Do not port implementation code or retain secrets. Produce the sanitized
contract and synthetic fixtures. Record accepted unsupported-API and
extractable-static-metadata risks. Stop only if TLS/pinning bypass is required
or core quota fields are unavailable. Do not scaffold the Android app beyond
the fixture directories.
```

---

### Task 2: D/E-Drive Toolchain Wrapper and Android Project Foundation

**Files:**

- Create: `scripts/android-env.ps1`
- Create: `scripts/android-gradle.ps1`
- Create: `scripts/verify-android-storage.ps1`
- Create: `android-app/settings.gradle.kts`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/gradle.properties`
- Create: `android-app/.gitignore`
- Create: `android-app/private-api.properties.example`
- Create: `android-app/gradle/libs.versions.toml`
- Create: `android-app/gradlew`
- Create: `android-app/gradlew.bat`
- Create: `android-app/gradle/wrapper/gradle-wrapper.jar`
- Create: `android-app/gradle/wrapper/gradle-wrapper.properties`
- Create: `android-app/app/build.gradle.kts`
- Create: `android-app/app/proguard-rules.pro`
- Create: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/java/com/quotacheck/app/QuotaCheckApp.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/MainActivity.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/FoundationTest.kt`

**Interfaces:**

- Consumes: Task 1 `FEASIBLE`
- Produces: package `com.quotacheck.app`, manual dependency container, Compose activity, reproducible Gradle wrapper with all new large state on E

- [ ] **Step 1: Write the storage verification test script**

`verify-android-storage.ps1` must fail when these variables point to C:

```powershell
$required = @(
  'ANDROID_HOME',
  'ANDROID_SDK_ROOT',
  'ANDROID_USER_HOME',
  'GRADLE_USER_HOME',
  'TEMP',
  'TMP'
)
foreach ($name in $required) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if ([string]::IsNullOrWhiteSpace($value) -or $value -notmatch '^[DdEe]:\\') {
    throw "$name must point to drive D or E, got '$value'"
  }
}
if ($env:JAVA_HOME -ne
    'C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot') {
  throw "JAVA_HOME must reuse the verified JDK 17"
}
```

- [ ] **Step 2: Verify the script fails before environment setup**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-android-storage.ps1
```

Expected: FAIL if the current process still resolves Android paths or temp to C.

- [ ] **Step 3: Implement the environment and Gradle wrappers**

`android-env.ps1` sets:

```powershell
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\Sdk'
$env:ANDROID_USER_HOME = 'E:\Android\.android'
$env:GRADLE_USER_HOME = 'E:\Android\.gradle'
$env:JAVA_HOME = 'C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot'
$env:TEMP = 'E:\tmp\android'
$env:TMP = 'E:\tmp\android'
```

`android-gradle.ps1` dot-sources `android-env.ps1`, runs
`verify-android-storage.ps1`, then forwards remaining arguments to
`android-app\gradlew.bat`.

- [ ] **Step 4: Create the version catalog**

Use Kotlin `2.3.21`, AGP `8.13.2`, Gradle `8.14.3`, Java 17, compile/target
API 36, min API 26, and Build Tools 35.0.0. Pin the listed
API-36-compatible versions of Compose,
Material 3, Vico 3.2.3, Room, DataStore, WorkManager, Lifecycle, Navigation
Compose, Retrofit, OkHttp, Coroutines, Kotlin serialization, and only the test
libraries used by this plan. For Vico, declare only
`com.patrykandpatrick.vico:compose:3.2.3` and
`com.patrykandpatrick.vico:compose-m3:3.2.3`. Configure KSP only for Room. Do
not add Hilt, Dagger, an image loader, another chart library, analytics, or
crash reporting.

`android-app/.gitignore` must contain `/private-api.properties`.
`private-api.properties.example` contains names only:

```properties
oauthClientId=
oauthClientSecret=
```

The real local properties file must never be staged or packaged as a resource.

- [ ] **Step 5: Verify existing tools and generate the wrapper without downloads**

Verify that API 36, Build Tools 35.0.0, Platform Tools, command-line tools, and
JDK 17 exist. Copy the already cached Gradle 8.14.3 archive from C to
`E:\Android\bootstrap`; do not download another distribution or install SDK
packages. Run after `android-env.ps1` has created its E-drive directories:

```powershell
. .\scripts\android-env.ps1
Test-Path 'D:\Android\Sdk\platforms\android-36\android.jar'
Test-Path 'D:\Android\Sdk\build-tools\35.0.0'
Test-Path "$env:JAVA_HOME\bin\java.exe"
Copy-Item -LiteralPath `
  'C:\Users\KK\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3-bin.zip' `
  -Destination 'E:\Android\bootstrap\gradle-8.14.3-bin.zip'
Expand-Archive -LiteralPath 'E:\Android\bootstrap\gradle-8.14.3-bin.zip' `
  -DestinationPath 'E:\Android\bootstrap' -Force
& 'E:\Android\bootstrap\gradle-8.14.3\bin\gradle.bat' `
  -p 'E:\QuotaCheck\android-app' wrapper --gradle-version 8.14.3
```

Expected: no network download for the toolchain; the SDK remains on D, the
existing JDK remains on C, and the copied Gradle distribution plus all new
caches remain on E.

- [ ] **Step 6: Write the failing foundation test**

```kotlin
class FoundationTest {
    @Test fun packageNameIsStable() {
        assertEquals("com.quotacheck.app", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 7: Implement minimal Compose application with manual injection**

Create `QuotaCheckApp` with one lazily initialized `AppContainer`,
`MainActivity`, and a single `Text("QuotaCheck")` Compose surface. Feature
ViewModels receive dependencies through explicit factories. Manifest permissions are
`INTERNET`, `ACCESS_NETWORK_STATE`, and `POST_NOTIFICATIONS`.

- [ ] **Step 8: Run verification**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest lintDebug
```

Expected: PASS; `verify-android-storage.ps1` reports Android and all newly
created large paths on D/E. The first app build may download only declared
Maven dependencies to `E:\Android\.gradle`.

- [ ] **Step 9: Commit**

```powershell
git add scripts android-app
git commit -m "build: scaffold native Android app on E drive"
```

**Worker prompt:**

```text
Implement Task 2 only using the verified minimal toolchain. Do not install an
IDE, emulator, AVD, system image, NDK, JDK, SDK platform, Build Tools, or
standalone Gradle. Reuse the D-drive SDK and existing JDK 17, copy the cached
Gradle archive to E, and keep all new caches/temp/build output on E. Use manual
constructor injection. Run Gradle only through scripts/android-gradle.ps1. Do
not implement product screens or network logic.
```

---

### Task 3: Domain Models and Formatting

**Files:**

- Create: `android-app/app/src/main/java/com/quotacheck/app/core/model/QuotaPool.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/core/model/SyncState.kt`
- Create: `android-app/app/src/main/java/com/quotacheck/app/core/model/QuotaFormatter.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/core/model/QuotaFormatterTest.kt`

**Interfaces:**

- Produces: `QuotaPool`, `SyncState`, `QuotaFormatter`

- [ ] **Step 1: Write failing model tests**

Test percent clamping, nullable absolute values, reset formatting, and masked
email:

```kotlin
@Test fun percentIsClampedAndRounded() {
    assertEquals("68%", QuotaFormatter.percent(0.684))
    assertEquals("0%", QuotaFormatter.percent(-1.0))
    assertEquals("100%", QuotaFormatter.percent(2.0))
}
```

- [ ] **Step 2: Run targeted tests**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*QuotaFormatterTest"
```

Expected: FAIL because the formatter does not exist.

- [ ] **Step 3: Implement immutable models**

`QuotaPool` contains stable ID, name, window, nullable unit totals, remaining
fraction, cycle timestamps, received timestamp, and schema version. `SyncState`
is a sealed interface containing Unconfigured, InitialLoading, Fresh, Stale,
OfflineCached, AuthRequired, ErrorEmpty, and Refreshing.

- [ ] **Step 4: Run tests and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*QuotaFormatterTest"
git add android-app/app/src/main/java/com/quotacheck/app/core/model android-app/app/src/test/java/com/quotacheck/app/core/model
git commit -m "feat: define quota domain models"
```

**Worker prompt:**

```text
Implement Task 3 only. Keep models Android-free where possible. Use java.time
because minSdk 26. Do not add database or network annotations to domain models.
```

---

### Task 4: Room Database and History Aggregation

**Files:**

- Create: `core/database/entity/QuotaPoolEntity.kt`
- Create: `core/database/entity/UsageSampleEntity.kt`
- Create: `core/database/entity/SyncRunEntity.kt`
- Create: `core/database/entity/AlertEventEntity.kt`
- Create: `core/database/QuotaDao.kt`
- Create: `core/database/HistoryDao.kt`
- Create: `core/database/SyncDao.kt`
- Create: `core/database/AlertDao.kt`
- Create: `core/database/QuotaDatabase.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/core/database/QuotaDatabaseTest.kt`

**Interfaces:**

- Consumes: Task 3 models
- Produces: transactional `replaceCurrentAndAppendSamples`, current-pool Flow,
  day/week/month aggregates, latest sync Flow, and alert dedup queries

- [ ] **Step 1: Write failing in-memory Room tests**

Cover:

- atomic current-pool replacement
- skip identical consecutive sample
- preserve cycle-change sample
- 90-day cleanup
- daily aggregation
- deterministic alert-key uniqueness

- [ ] **Step 2: Run instrumented test**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.core.database.QuotaDatabaseTest
```

Expected: FAIL because database classes do not exist.

- [ ] **Step 3: Implement schema version 1**

Use UTC epoch milliseconds, `Double?` for optional absolute units, indices on
`poolId`, `receivedAt`, `cycleEndAt`, and a unique index on `alertKey`.
Database writes that feed alerts must run in one `withTransaction` block.
Construct the singleton database and DAOs lazily in `AppContainer`.

- [ ] **Step 4: Run tests and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.core.database.QuotaDatabaseTest
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/database android-app/app/src/androidTest/java/com/quotacheck/app/core/database
git commit -m "feat: add offline quota database"
```

**Worker prompt:**

```text
Implement Task 4 only. Room is the source of truth. Keep API DTOs out of
entities. Prove transactionality, deduplication, aggregation, and retention
with in-memory Room tests before committing.
```

---

### Task 5: Preferences DataStore

**Files:**

- Create: `core/model/UserPreferences.kt`
- Create: `core/preferences/UserPreferencesRepository.kt`
- Create: `core/preferences/DataStoreUserPreferencesRepository.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/core/preferences/UserPreferencesRepositoryTest.kt`

**Interfaces:**

- Produces: `Flow<UserPreferences>` and suspend setters for auto sync, interval,
  Wi-Fi only, low/critical thresholds, notification toggles, theme, retention,
  and rationale completion

- [ ] **Step 1: Write failing default and validation tests**

Defaults: auto sync true, 30 minutes, Wi-Fi only false, low 20, critical 10,
reset/failure true, success false, Dark theme, 90 days.

- [ ] **Step 2: Implement typed keys and validation**

Allowed intervals are 30, 60, 120, and 240 minutes. Enforce critical threshold
below low threshold and retention choices of 30, 90, or 180 days.
Construct the repository lazily in `AppContainer`.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*UserPreferencesRepositoryTest"
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/preferences android-app/app/src/main/java/com/quotacheck/app/core/model/UserPreferences.kt android-app/app/src/test/java/com/quotacheck/app/core/preferences
git commit -m "feat: persist user preferences"
```

**Worker prompt:**

```text
Implement Task 5 only. Use Preferences DataStore in the data layer, expose one
Flow, validate every setter, and write deterministic defaults tests.
```

---

### Task 6: Android Keystore Credential Vault

**Files:**

- Create: `core/security/CredentialVault.kt`
- Create: `core/security/AndroidCredentialVault.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `main/res/xml/backup_rules.xml`
- Create: `main/res/xml/data_extraction_rules.xml`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/core/security/AndroidCredentialVaultTest.kt`

**Interfaces:**

- Produces:

```kotlin
interface CredentialVault {
    suspend fun saveRefreshToken(token: CharArray)
    suspend fun readRefreshToken(): CharArray?
    suspend fun clear()
}
```

- [ ] **Step 1: Write failing save/read/replace/clear tests**

Also assert plaintext is absent from the encrypted storage file and backup
rules exclude the ciphertext file.

- [ ] **Step 2: Implement AES/GCM/NoPadding**

Use a non-exportable Android Keystore AES-256 key, random 12-byte IV per write,
authenticated encryption, app-private storage, zero temporary character/byte
arrays where practical, and no biometric requirement. Construct the vault
lazily in `AppContainer`.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.core.security.AndroidCredentialVaultTest
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/security android-app/app/src/main/res/xml android-app/app/src/androidTest/java/com/quotacheck/app/core/security
git commit -m "feat: secure refresh token with Android Keystore"
```

**Worker prompt:**

```text
Implement Task 6 only. Use Android Keystore AES-GCM; never use deprecated
EncryptedSharedPreferences. Exclude ciphertext from backup and prove no
plaintext remains in storage.
```

---

### Task 7: Private API Adapter and Contract Tests

**Files:**

- Create: `scripts/import-private-api-compat.ps1`
- Create: `core/network/PrivateQuotaApi.kt`
- Create: `core/network/PrivateApiContract.kt`
- Create: `core/network/dto/TokenDtos.kt`
- Create: `core/network/dto/QuotaDtos.kt`
- Create: `core/network/RemoteError.kt`
- Create: `core/network/QuotaRemoteDataSource.kt`
- Create: `core/network/RetrofitQuotaRemoteDataSource.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Modify: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/test/java/com/quotacheck/app/core/network/PrivateQuotaContractTest.kt`

**Interfaces:**

- Consumes: Task 1 contract/fixtures, Task 3 domain model, Task 6 vault
- Produces:

```kotlin
interface QuotaRemoteDataSource {
    suspend fun validate(refreshToken: CharArray): Result<String?>
    suspend fun fetchQuota(refreshToken: CharArray): Result<List<QuotaPool>>
}
```

- [ ] **Step 1: Write MockWebServer contract tests**

Test success mapping, nullable units, 401/403 AuthRequired, 429 Retryable with
Retry-After, 5xx Retryable, malformed JSON SchemaMismatch, fraction clamping,
and absence of authorization values in captured logs.

- [ ] **Step 2: Run targeted tests**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*PrivateQuotaContractTest"
```

Expected: FAIL because adapter classes do not exist.

- [ ] **Step 3: Implement exact discovered contract**

Copy exact non-secret constants from Task 1 into `PrivateApiContract.kt`.
Release base URL is a BuildConfig constant with no Settings override. Configure
timeouts, Kotlin serialization with unknown-key tolerance, a redacting
interceptor, and no body-level network logging. Wire Retrofit and the data
source lazily in `AppContainer`.

`scripts/import-private-api-compat.ps1` may read only the authorized legacy
quota-client constants and writes `android-app/private-api.properties` without
printing values. Gradle loads `oauthClientId` and `oauthClientSecret` into
release `BuildConfig` fields, fails release builds when either is missing, and
never copies the properties file into resources or assets. The script must
refuse to run if its output path resolves outside
`E:\QuotaCheck\android-app`.

- [ ] **Step 4: Run secret scan, tests, and commit**

```powershell
rg -n -i "bearer [a-z0-9._-]+|refresh_token\s*[:=]\s*[^\" ]+" android-app/app/src
git check-ignore android-app/private-api.properties
git status --short -- android-app/private-api.properties
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*PrivateQuotaContractTest"
git add scripts/import-private-api-compat.ps1 android-app/app/build.gradle.kts android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/network android-app/app/src/test/java/com/quotacheck/app/core/network
git commit -m "feat: add private quota API adapter"
```

**Worker prompt:**

```text
Implement Task 7 only from the approved sanitized contract. Do not infer or
invent endpoints. Use MockWebServer fixtures, redact all auth data, prohibit
cleartext and trust-all TLS, and keep DTOs inside core/network. Import legacy
OAuth values only into the gitignored local properties file without printing
them; never commit them or claim they are protected inside the APK.
```

---

### Task 8: Repository Source of Truth and Sync Transaction

**Files:**

- Create: `core/model/QuotaRepository.kt`
- Create: `core/model/SyncTrigger.kt`
- Create: `core/model/SyncResult.kt`
- Create: `core/repository/OfflineFirstQuotaRepository.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/core/repository/OfflineFirstQuotaRepositoryTest.kt`

**Interfaces:**

- Consumes: database DAOs, preferences, remote data source
- Produces current pools/history/sync flows plus
  `suspend fun synchronize(trigger: SyncTrigger): SyncResult`

- [ ] **Step 1: Write failing repository tests**

Prove remote success commits current data/sample/sync run atomically; remote
failure preserves cache; database failure prevents alert evaluation; identical
samples are skipped; retention follows preferences.

- [ ] **Step 2: Implement minimal offline-first repository**

The repository emits database flows only. Map all remote errors to sealed
`SyncResult`; never expose Retrofit types. Wire it lazily in `AppContainer`.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*OfflineFirstQuotaRepositoryTest"
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/repository android-app/app/src/main/java/com/quotacheck/app/core/model/QuotaRepository.kt android-app/app/src/test/java/com/quotacheck/app/core/repository
git commit -m "feat: add offline-first quota repository"
```

**Worker prompt:**

```text
Implement Task 8 only. Room flows are the only observable source. Preserve
cached data on remote failures and prove transaction/retention behavior.
```

---

### Task 9: WorkManager Sync Coordinator

**Files:**

- Create: `sync/QuotaSyncWorker.kt`
- Create: `sync/SyncScheduler.kt`
- Create: `sync/WorkManagerSyncScheduler.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/sync/QuotaSyncWorkerTest.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/sync/WorkManagerSyncSchedulerTest.kt`

**Interfaces:**

- Produces `schedulePeriodic`, `cancelPeriodic`, and `refreshNow`; unique names
  are `quota-periodic-sync` and `quota-manual-sync`

- [ ] **Step 1: Write failing Worker tests**

Map success to `Result.success`, network/5xx/429 to retry, auth/schema to
failure, and local persistence failure to failure. Test connected versus
unmetered constraints and 30/60/120/240-minute intervals.

- [ ] **Step 2: Implement CoroutineWorker and scheduler**

Use exponential backoff starting at 30 seconds. Periodic policy is UPDATE;
manual work is unique KEEP. Do not use expedited or foreground execution.
The worker obtains its repository from
`(applicationContext as QuotaCheckApp).appContainer`; the scheduler is
constructed lazily in that container.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*QuotaSyncWorkerTest" --tests "*WorkManagerSyncSchedulerTest"
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/sync android-app/app/src/test/java/com/quotacheck/app/sync
git commit -m "feat: schedule resilient quota synchronization"
```

**Worker prompt:**

```text
Implement Task 9 only. Reuse one repository synchronization path, unique work,
network constraints, and exponential backoff. No foreground service, expedited
work, polling, or custom boot receiver.
```

---

### Task 10: Alert Evaluation and Android Notifications

**Files:**

- Create: `core/notifications/AlertEvaluator.kt`
- Create: `core/notifications/NotificationPublisher.kt`
- Create: `core/notifications/AndroidNotificationPublisher.kt`
- Create: `core/notifications/NotificationChannels.kt`
- Create: `core/notifications/NotificationActionReceiver.kt`
- Modify: `android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/core/notifications/AlertEvaluatorTest.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/core/notifications/NotificationChannelTest.kt`

**Interfaces:**

- Consumes: committed database state, preferences, sync scheduler
- Produces Low, Critical, Reset, SyncFailure, and optional SyncSuccess commands

- [ ] **Step 1: Write failing evaluator tests**

Cover downward threshold crossing only, once per pool/cycle, reset once,
failure after exactly three consecutive failures, suppression until recovery,
and success only for manual/recovery when enabled.

- [ ] **Step 2: Implement deterministic alert keys**

Format:

```text
{poolId}:{cycleEndEpoch}:{alertType}:{thresholdOrZero}
```

Persist the alert event before publishing; retry publication only when the
persisted delivery timestamp remains null.

- [ ] **Step 3: Implement channels and notification behavior**

Create High `quota_alerts` and Default `sync_status`; call
`setShowBadge(false)` on both. Use Private visibility plus generic public
version, deep links, and Refresh action for failure. Wire the evaluator and
publisher lazily in `AppContainer`; the receiver resolves its scheduler from
the application container.

- [ ] **Step 4: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*AlertEvaluatorTest"
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.core.notifications.NotificationChannelTest
git add android-app/app/src/main/java/com/quotacheck/app/AppContainer.kt android-app/app/src/main/java/com/quotacheck/app/core/notifications android-app/app/src/test/java/com/quotacheck/app/core/notifications android-app/app/src/androidTest/java/com/quotacheck/app/core/notifications
git commit -m "feat: deliver deduplicated quota notifications"
```

**Worker prompt:**

```text
Implement Task 10 only. Persist dedup state, disable channel badges, respect
system lock-screen policy, and never notify from uncommitted quota data.
```

---

### Task 11: Design System and Navigation Shell

**Files:**

- Create: `core/designsystem/Color.kt`
- Create: `core/designsystem/Type.kt`
- Create: `core/designsystem/Shape.kt`
- Create: `core/designsystem/Spacing.kt`
- Create: `core/designsystem/QuotaCheckTheme.kt`
- Create: `core/designsystem/component/QuotaCard.kt`
- Create: `core/designsystem/component/QuotaProgressBar.kt`
- Create: `navigation/Destination.kt`
- Create: `navigation/QuotaCheckNavHost.kt`
- Create: `feature/AppShell.kt`
- Modify: `MainActivity.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/feature/AppShellTest.kt`

**Interfaces:**

- Produces fixed bottom navigation for Home, History, Alerts, Settings

- [ ] **Step 1: Write Compose shell tests**

Assert four destinations, selected semantics, fixed bottom navigation, system
insets, 48 dp targets, and no dynamic-color use.

- [ ] **Step 2: Implement approved tokens**

Use neutral dark/light palettes, amber warning, red destructive/error, 12 dp
cards, 3 dp bars, 4/8/12/16/20/24 spacing, Inter Latin/tabular numbers with
system fallback, no shadow/gradient/blur, and motion respecting animator scale.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.feature.AppShellTest
git add android-app/app/src/main/java/com/quotacheck/app/core/designsystem android-app/app/src/main/java/com/quotacheck/app/navigation android-app/app/src/main/java/com/quotacheck/app/feature android-app/app/src/main/java/com/quotacheck/app/MainActivity.kt android-app/app/src/androidTest/java/com/quotacheck/app/feature
git commit -m "feat: add mobile design system and navigation"
```

**Worker prompt:**

```text
Implement Task 11 only from DESIGN.md visual tokens and the approved mockups.
Build a responsive shell, fixed bottom nav, accessibility semantics, and no
dynamic Material colors or decorative effects.
```

---

### Task 12: Onboarding and Notification Permission

**Files:**

- Create: `feature/onboarding/OnboardingUiState.kt`
- Create: `feature/onboarding/OnboardingViewModel.kt`
- Create: `feature/onboarding/OnboardingScreen.kt`
- Create: `feature/onboarding/NotificationPermissionGate.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/feature/onboarding/OnboardingViewModelTest.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/feature/onboarding/OnboardingScreenTest.kt`

**Interfaces:**

- Consumes: vault, remote validation, repository sync, preferences
- Produces connected state only after validation and initial sync

- [ ] **Step 1: Write failing state-machine tests**

Test empty token, masked input, validation failure, successful save/sync,
permission denial, and token removal rollback.

- [ ] **Step 2: Implement ViewModel**

Pass token as `CharArray`, validate before save, clear UI copy after submission,
and request notification permission only after quota is visible and the user
enables alerts.

- [ ] **Step 3: Implement screen and tests**

No WebView, browser-cookie extraction, camera, or QR affordance. Permission
denial must still navigate to Home and show a non-blocking explanation.

- [ ] **Step 4: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*OnboardingViewModelTest"
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.feature.onboarding.OnboardingScreenTest
git add android-app/app/src/main/java/com/quotacheck/app/feature/onboarding android-app/app/src/test/java/com/quotacheck/app/feature/onboarding android-app/app/src/androidTest/java/com/quotacheck/app/feature/onboarding
git commit -m "feat: add secure quota onboarding"
```

**Worker prompt:**

```text
Implement Task 12 only. Validate before persistence, keep secrets out of
String/logs where practical, request notification permission in context, and
keep the app usable after denial.
```

---

### Task 13: Pool-First Home

**Files:**

- Create: `feature/home/HomeUiState.kt`
- Create: `feature/home/HomeViewModel.kt`
- Create: `feature/home/HomeScreen.kt`
- Create: `feature/home/QuotaPoolRow.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/feature/home/HomeViewModelTest.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/feature/home/HomeScreenTest.kt`

**Interfaces:**

- Consumes: current-pool Flow, sync-state Flow, scheduler
- Produces approved All Quotas pool-first screen

- [ ] **Step 1: Write failing ViewModel tests**

Cover Unconfigured, InitialLoading, Fresh, Stale, OfflineCached, AuthRequired,
ErrorEmpty, and Refreshing while cached content remains visible.

- [ ] **Step 2: Write failing Compose tests**

Use the connected Infinix viewport as the primary visual-fit target: assert
four rows and footer are visible without scrolling. Keep the production layout
responsive rather than fixed-size. At large font, constrained height, or five
pools, assert content scrolls while bottom navigation remains visible.

- [ ] **Step 3: Implement Home**

No summary card. Each row shows pool/window, remaining percentage, reset,
progress, and warning text. Footer shows updated time, interval, and Refresh.

- [ ] **Step 4: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*HomeViewModelTest"
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.feature.home.HomeScreenTest
git add android-app/app/src/main/java/com/quotacheck/app/feature/home android-app/app/src/test/java/com/quotacheck/app/feature/home android-app/app/src/androidTest/java/com/quotacheck/app/feature/home
git commit -m "feat: add pool-first quota home"
```

**Worker prompt:**

```text
Implement Task 13 only from the approved pool-first mockup. No summary card.
Prove four-row fit on the connected Infinix and accessible responsive scrolling
for large-font or constrained layouts; do not hard-code the production viewport.
```

---

### Task 14: Bars-and-Timeline History

**Files:**

- Create: `feature/history/HistoryPeriod.kt`
- Create: `feature/history/HistoryUiState.kt`
- Create: `feature/history/HistoryViewModel.kt`
- Create: `feature/history/HistoryScreen.kt`
- Create: `feature/history/UsageBarChart.kt`
- Create: `android-app/app/src/test/java/com/quotacheck/app/feature/history/HistoryViewModelTest.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/feature/history/HistoryScreenTest.kt`

**Interfaces:**

- Consumes: Room day/week/month aggregate Flow
- Produces pool selector, period control, Vico Cartesian bar chart, daily breakdown

- [ ] **Step 1: Write failing period/aggregation tests**

Test selected-pool switching, Day/Week/Month boundaries in device locale,
nullable-unit fallback to percentage consumed, and insufficient-history empty
state.

- [ ] **Step 2: Implement the Vico chart**

Use Vico's Compose Material 3 Cartesian column chart with design tokens from
the approved spec. Keep semantic descriptions for every bar, stable scaling,
tabular labels, and no animation when reduced motion is active. Do not add
Vico Material 2, Glance, or a second chart library. The timeline below the
chart remains the exact-value and accessibility fallback.

- [ ] **Step 3: Run and commit**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 testDebugUnitTest --tests "*HistoryViewModelTest"
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quotacheck.app.feature.history.HistoryScreenTest
git add android-app/app/src/main/java/com/quotacheck/app/feature/history android-app/app/src/test/java/com/quotacheck/app/feature/history android-app/app/src/androidTest/java/com/quotacheck/app/feature/history
git commit -m "feat: add quota usage history"
```

**Worker prompt:**

```text
Implement Task 14 only. Use the approved bars-plus-timeline layout with Vico
3.2.3 Compose and Material 3 modules only. Preserve semantic access to exact
values and do not add another chart dependency.
```

---

### Task 15: Alerts, Settings, End-to-End Verification, and Signed Release

**Files:**

- Create: `feature/alerts/AlertsViewModel.kt`
- Create: `feature/alerts/AlertsScreen.kt`
- Create: `feature/settings/SettingsViewModel.kt`
- Create: `feature/settings/SettingsScreen.kt`
- Create: `feature/settings/ClearDataDialog.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/feature/AlertsSettingsTest.kt`
- Create: `android-app/app/src/androidTest/java/com/quotacheck/app/EndToEndQuotaFlowTest.kt`
- Create: `docs/release/android-internal-release-checklist.md`
- Modify: `android-app/app/build.gradle.kts`
- Modify: `android-app/app/proguard-rules.pro`

**Interfaces:**

- Consumes: preferences, vault, scheduler, notification settings intent, Room
- Produces approved Alerts/Settings UI and signed release workflow

- [ ] **Step 1: Write failing Alerts/Settings tests**

Test threshold validation, sync success default off, Android notification
settings intent, interval/Wi-Fi rescheduling, theme selection, retention,
credential removal, clear-history confirmation, and no badge UI.

- [ ] **Step 2: Implement Alerts and Settings**

Match approved mockups. Credential removal cancels work, clears encrypted
storage and local account state, and returns to Unconfigured.

- [ ] **Step 3: Write the end-to-end fake-server test**

The test performs onboarding → initial sync → Home → second changed response →
History → threshold notification → offline cached launch → token removal.

- [ ] **Step 4: Configure release hardening**

Enable R8 and resource shrinking, fixed HTTPS release endpoint, no body logging,
no debug endpoint switch, external signing properties, and release manifest
inspection for forbidden permissions/components.

- [ ] **Step 5: Create external signing material**

Create `E:\Android\signing\quotacheck-release.jks` with the verified existing
`C:\Users\KK\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.15.6-hotspot\bin\keytool.exe`.
Supply passwords interactively, then expose
only process-scoped `QUOTACHECK_STORE_FILE`, `QUOTACHECK_STORE_PASSWORD`,
`QUOTACHECK_KEY_ALIAS`, and `QUOTACHECK_KEY_PASSWORD` variables. Gradle must
fail release assembly with a clear message when any variable is absent. Never
write the passwords or keystore into the repository.

- [ ] **Step 6: Run the complete gate on the connected physical phone**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android-gradle.ps1 clean testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease
```

Expected: all tests/lint pass and signed APK appears under
`E:\QuotaCheck\android-app\app\build\outputs\apk\release`.

- [ ] **Step 7: Inspect the merged release manifest**

Verify:

- present: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS
- WorkManager internal rescheduling entries only
- absent: camera, foreground service, app widget, custom boot receiver, badge
  metadata

- [ ] **Step 8: Measure actual storage**

Record the release APK size and these E-drive directory totals in
`docs/release/android-internal-release-checklist.md`:

```powershell
$paths = @(
  'E:\Android\.gradle',
  'E:\QuotaCheck\android-app\app\build',
  'E:\tmp\android'
)
foreach ($path in $paths) {
  $bytes = (Get-ChildItem -LiteralPath $path -Recurse -File |
    Measure-Object -Property Length -Sum).Sum
  '{0}: {1:N2} GB' -f $path, ($bytes / 1GB)
}
Get-Item 'E:\QuotaCheck\android-app\app\build\outputs\apk\release\app-release.apk' |
  Select-Object FullName, @{Name='SizeMB';Expression={[math]::Round($_.Length / 1MB, 2)}}
```

After installing, record the app size shown by Android Settings > Apps >
QuotaCheck > Storage. Investigate before release if E-drive peak usage exceeds
4 GB, the release APK exceeds 18 MB, or installed app plus data exceeds 80 MB.

- [ ] **Step 9: Run local real-account smoke checklist**

Never put the token in source or CI. On the user's actual phone, verify
notification permission, heads-up, restricted lock screen, shade,
offline/reconnect, reboot scheduling, dark/light/system themes, and four-pool
fit. API 26 compatibility is enforced by `minSdk`, lint, and automated tests;
do not install an emulator merely to expand the device matrix.

- [ ] **Step 10: Commit**

```powershell
git add android-app/app/src/main/java/com/quotacheck/app/feature/alerts android-app/app/src/main/java/com/quotacheck/app/feature/settings android-app/app/src/androidTest docs/release android-app/app/build.gradle.kts android-app/app/proguard-rules.pro
git commit -m "feat: complete Android quota MVP"
```

**Worker prompt:**

```text
Implement Task 15 only. Match approved Alerts/Settings mockups, complete the
fake-server E2E flow, harden release, inspect the merged manifest, and keep
signing secrets outside git. Do not add excluded V1 features.
```

---

## Review Gates

After every task:

1. Run the task's exact tests through `scripts/android-gradle.ps1`.
2. Inspect `git diff --check` and confirm only declared files changed.
3. Run a spec-compliance review against Global Constraints.
4. For Tasks 7, 10, and 15, run a security review for token leakage,
   notification duplication, and manifest permissions.
5. Commit only after tests and review pass.

## Final Acceptance

- Private API feasibility is documented without security bypass.
- Four quota pools fit the default Home viewport; accessible layouts scroll.
- Day/Week/Month history matches stored samples.
- Auto/manual sync cannot overlap and preserves cache on failure.
- Alerts cross thresholds once per pool/cycle and badges remain disabled.
- Notification permission denial does not block the app.
- Token is Keystore-encrypted, excluded from backup, and absent from logs.
- All large Android files and caches are verified on D/E.
- Unit, lint, instrumented, E2E, and release assembly gates pass.
- Signed internal APK is produced without forbidden components or permissions.
