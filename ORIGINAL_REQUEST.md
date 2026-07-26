# Original User Request

## Initial Request — 2026-07-25T09:23:22Z

Fix mobile and widget issues in QuotaCheck across app branding, runtime permissions, mobile settings layout, native Android widget token colors, and first-run token save fallback logic.

Working directory: e:\QuotaCheck
Integrity mode: development

## Requirements

### R1. App Icon Generation
Replace source icon file in `src-tauri/icons/icon.png` with a 1024x1024 PNG of the BK brain + circuit logo and run `cargo tauri icon <path>` from `src-tauri/` to regenerate all platform icons. Do not alter `tauri.conf.json`.

### R2. Runtime Permission Request & Notification Settings Intent
Implement `checkNotificationPermission`, `requestNotificationPermission`, and `openNotificationSettings` commands in `QuotaPlugin.kt`. In `MobileApp.svelte`, check permission on first mount and display a single dismissible prompt banner if denied. Add a link in `settings/+page.svelte` (mobile view) to open app notification settings.

### R3. Mobile Settings Safe-Area & Theme Token Integration
In `src/routes/settings/+page.svelte`, add safe-area inset padding (`env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`) to `.settings-container.mobile-view`. Replace hardcoded `oklch` colors with shared `--color-*` CSS tokens from `theme.css`. Stack `.token-input-group` elements vertically for screens <= 380px. Leave desktop view unchanged.

### R4. Android Widget Token Colors & Refresh Diagnostics
In `QuotaWidgetProvider.kt`, create a `WidgetColors` constant object matching `theme.css` tokens (`ACCENT`, `DOT_LIVE`, `DOT_STALE`, `DOT_OFFLINE`) and replace inline `Color.parseColor` string literals. Perform an `adb logcat` check during widget refresh tap to verify `QuotaSyncWorker` execution flow.

### R5. First-Run Token Save Fallback Fix
In `MobileApp.svelte`, update the `handleSaveToken()` catch block to fallback to `get_config` + `save_config` with `refresh_token_override`, displaying accurate toast feedback on success or failure.

## Acceptance Criteria

### Automated & Static Verification
- [ ] `npx svelte-check` passes with zero errors.
- [ ] Android Gradle/Kotlin code builds cleanly with the new plugin commands and `WidgetColors`.

### Manual & Behavioral Verification
- [ ] App launcher icon displays BK logo on desktop and mobile.
- [ ] Permission request banner appears once on Android 13+ fresh install.
- [ ] Mobile settings UI respects safe-area cutouts and matches app theme tokens.
- [ ] Widget status dot and text colors reflect `WidgetColors` constants.
- [ ] Token save fallback persists refresh token to config when plugin call fails.
