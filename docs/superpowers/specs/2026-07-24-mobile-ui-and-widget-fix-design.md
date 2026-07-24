# Design Spec: Mobile UI & Android Widget Optimization

**Date:** 2026-07-24  
**Scope:** Mobile App UI (`MobileApp.svelte`), Android Widget Layout & Provider (`widget_quota.xml`, `QuotaWidgetProvider.kt`), Safe Area Configuration (`app.html`), and Main Page Token Setup UX.

---

## 1. Overview & Objectives

This design addresses all user-reported mobile issues:
1. **Android Home Screen Widget:**
   - **Ordering Fix:** Dynamically render pool slots in `QuotaWidgetProvider.kt` to strictly match the app's pool order (`pools[0]` $\rightarrow$ Slot 1, `pools[1]` $\rightarrow$ Slot 2). Remove hardcoded XML text defaults.
   - **Color Exact Match with PC:** On PC and Main App, ALL active pools (both Gemini and Claude) use the exact same **Cyan Blue (`#38BDF8`)** progress bar and percentage text color. In the previous widget code, Claude was mistakenly colored Purple (`#818CF8`). Fix the widget drawables and Kotlin code so that ALL active pools use `#38BDF8` (Cyan Blue), switching to `#EF4444` (Red/Amber) when quota $\le 20\%$, and `#52525B` when offline, matching PC desktop 100%.
   - **Size Cutoff Fix:** Redesign `widget_quota.xml` layout padding (8dp), card padding (8dp), and margins to fit 2 pools + header + footer inside standard 2-cell launcher height (110dp) without bottom clipping.
2. **Mobile App Layout & Safe Areas:**
   - Add `viewport-fit=cover` to `<meta name="viewport">` in `app.html`.
   - Update `MobileApp.svelte` header and bottom nav bar with `env(safe-area-inset-top)` and `env(safe-area-inset-bottom)` safe area padding.
   - Use `100dvh` for full viewport height to prevent collision with mobile status bar and system gesture bar (`===`).
3. **Desktop Color Token Alignment:**
   - Replace bluish M3 tokens with PC desktop tokens (`--color-bg: oklch(14% 0 0)`, `--color-surface: oklch(20% 0 0)`, `--color-border: oklch(28% 0 0)`, `--color-accent: oklch(62% 0.16 230)`).
   - Remove card side-stripe borders for visual consistency with the desktop widget.
4. **Main Page Token Setup UX:**
   - Display a prominent, seamless OAuth Refresh Token input card directly on the main page when offline/token is missing, enabling one-click token saving and syncing without navigating to Settings.

---

## 2. Component Design & Changes

### A. Android Native Widget (`widget_quota.xml` & `QuotaWidgetProvider.kt`)

#### `widget_quota.xml` Layout Restructure
- Root `LinearLayout`: `padding="8dp"` (reduced from `14dp`), `background="@drawable/widget_bg"`.
- Header: `margin_marginBottom="6dp"`.
- Pool Cards (`pool1_container`, `pool2_container`): `padding="8dp"` (reduced from `10dp`), `marginBottom="6dp"`.
- Progress Bars (`pool1_progress`, `pool2_progress`): `layout_height="5dp"`.
- Footer: `layout_marginTop="4dp"`, guaranteed visibility within 110dp launcher height.

#### `QuotaWidgetProvider.kt` Dynamic Binding & Unified Color Rule
- Slot 1 (`pool1`) binds to `pools[0]`; Slot 2 (`pool2`) binds to `pools[1]`.
- If `pools.length() == 1`, set `pool2_container` visibility to `GONE`.
- Progress drawables and percent text colors match PC Desktop Widget exactly:
  - $>20\%$: Active Cyan Blue (`#38BDF8`) for ALL pools (Gemini & Claude)
  - $\le 20\%$: Warning Amber/Red (`#EF4444`)
  - Offline: Dark Muted Gray (`#52525B`)

### B. Mobile App Safe Area & Layout (`MobileApp.svelte` & `app.html`)

#### `src/app.html`
- `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />`

#### `src/lib/components/MobileApp.svelte`
- Root shell: `height: 100dvh; overflow: hidden;`
- `.top-app-bar`:
  ```css
  padding-top: max(14px, env(safe-area-inset-top, 0px));
  padding-bottom: 12px;
  padding-left: 20px;
  padding-right: 20px;
  ```
- `.bottom-nav-bar`:
  ```css
  padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
  height: calc(56px + max(12px, env(safe-area-inset-bottom, 0px)));
  ```

### C. Desktop Color Token Harmonization
- Colors match PC Desktop (`DesktopWidget.svelte`):
  - Background: `oklch(14% 0 0 / 0.95)` (`#18181B`)
  - Cards / Surface: `oklch(20% 0 0 / 0.9)` (`#27272A`)
  - Borders: `oklch(28% 0 0 / 0.6)` (`#3F3F46`)
  - Accent (All Active Progress Bars): `oklch(62% 0.16 230)` (`#38BDF8`)
  - High Contrast Ink: `oklch(96% 0 0)` (`#FAFAFA`)
  - Muted Ink: `oklch(60% 0 0)` (`#A1A1AA`)

### D. Main Page Token Quick Setup UX
- When `s.isOffline && !s.tokenInput` or when no pools exist:
  - Render an inline **OAuth Refresh Token Banner** on the main scroll view.
  - Input field with password masking, clear "Save & Sync Quota" button, and single-tap paste support.
  - On submit, invokes `plugin:quota|saveRefreshToken` (Android) or `save_config` (Rust) and immediately triggers `handleRefresh()`.

---

## 3. Verification & Testing Strategy

1. **Android Widget:**
   - Verify both Gemini and Claude active progress bars use **Cyan Blue (`#38BDF8`)**, matching PC desktop.
   - Verify 2 pools fit inside 3x2 / 2x2 launcher widget without bottom text cutoff.
   - Verify pool order (Gemini vs Claude) matches main app.
2. **Mobile App:**
   - Verify top header is positioned below the status bar (no overlap with clock `21:18`).
   - Verify bottom nav bar is positioned above system gesture indicator line (`===`).
3. **Token Setup UX:**
   - Test pasting token directly on main page and triggering sync without touching Settings.
