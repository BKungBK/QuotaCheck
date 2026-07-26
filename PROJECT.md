# Project: QuotaCheck Mobile & Widget Enhancements

## Architecture
QuotaCheck is a cross-platform desktop/mobile Tauri v2 + Svelte 5 application with custom Kotlin native plugins (`QuotaPlugin.kt`) and Android app widgets (`QuotaWidgetProvider.kt`, `QuotaSyncWorker.kt`).

- Frontend: Svelte 5 (Runes, SvelteKit routes)
- Backend / Desktop: Rust + Tauri v2
- Mobile (Android): Kotlin Tauri Plugin (`QuotaPlugin.kt`), Android App Widget (`QuotaWidgetProvider.kt`)

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M0 | Exploration & Strategy | Codebase analysis, file location confirmation | None | DONE |
| M1 | R1: App Icon Generation | Replace icon.png with 1024x1024 BK logo, run `cargo tauri icon` | None | DONE |
| M2 | R2: Runtime Permission & Intent | `QuotaPlugin.kt`, `MobileApp.svelte`, `settings/+page.svelte` | None | DONE |
| M3 | R3: Mobile Settings Safe-Area & Theme Tokens | `src/routes/settings/+page.svelte` CSS & Layout | None | DONE |
| M4 | R4: Android Widget Token Colors & Refresh Diagnostics | `QuotaWidgetProvider.kt` WidgetColors & logcat | None | DONE |
| M5 | R5: First-Run Token Save Fallback | `MobileApp.svelte` `handleSaveToken()` | None | DONE |
| M6 | E2E & Verification | `npx svelte-check`, Android Gradle build, Forensic Audit | M1-M5 | DONE |

## Interface Contracts
### Tauri Kotlin Plugin `QuotaPlugin` ↔ `MobileApp.svelte` / `settings/+page.svelte`
- `checkNotificationPermission()` -> returns boolean `{ "granted": Boolean }` or `{ "permission": String/Boolean }`
- `requestNotificationPermission()` -> triggers Android 13+ POST_NOTIFICATIONS permission prompt
- `openNotificationSettings()` -> opens Android App Notification Settings Intent (`ACTION_APP_NOTIFICATION_SETTINGS`)

### Config Fallback `get_config` / `save_config`
- `save_config(config)` / `get_config()` fallback with `refresh_token_override` in `MobileApp.svelte`

## Code Layout
- Frontend: `src/` (Svelte 5 components & routes)
  - `src/routes/MobileApp.svelte`
  - `src/routes/settings/+page.svelte`
  - `src/theme.css`
- Tauri & Native: `src-tauri/`
  - `src-tauri/icons/`
  - `src-tauri/gen/android/`
  - Kotlin Plugin: `src-tauri/gen/android/app/src/main/java/com/quotacheck/app/QuotaPlugin.kt` (or similar package path)
  - Kotlin Widget: `src-tauri/gen/android/app/src/main/java/com/quotacheck/app/QuotaWidgetProvider.kt`
