# Design Spec: Mobile UI, Android Widget, and Desktop PC Fixes

**Date:** 2026-07-24  
**Scope:** Mobile App UI (`MobileApp.svelte`), Android Widget Layout & Provider (`widget_quota.xml`, `QuotaWidgetProvider.kt`), PC Desktop Widget (`DesktopWidget.svelte`, `PoolRow.svelte`), Safe Area Configuration (`app.html`), and Main Page Token Setup UX.

---

## 1. Overview & Objectives

This design addresses all mobile and PC desktop widget issues:
1. **PC Desktop Widget Fix (Commit `e629ff8` Precision Match):**
   - **White Scrollbar Removal:** Hide scrollbars on `.pools-container` (`scrollbar-width: none`, `::-webkit-scrollbar { display: none }`).
   - **Height & Spacing Optimization:** Tighten padding (`12px 14px` root, `8px 10px` per card, `8px` gap) so 2 pools + header + footer fit 100% within the 200px desktop window height without any vertical overflow or cut-off text (`reset 41m`).
2. **Android Home Screen Widget:**
   - **Ordering Fix:** Dynamically render pool slots in `QuotaWidgetProvider.kt` to strictly match the app's pool order (`pools[0]` $\rightarrow$ Slot 1, `pools[1]` $\rightarrow$ Slot 2). Remove hardcoded XML text defaults.
   - **Color Exact Match with PC:** On PC and Main App, ALL active pools (both Gemini and Claude) use the exact same **Cyan Blue (`#38BDF8`)** progress bar and percentage text color. Fix the widget drawables and Kotlin code so that ALL active pools use `#38BDF8` (Cyan Blue), switching to `#EF4444` (Red/Amber) when quota $\le 20\%$, and `#52525B` when offline, matching PC desktop 100%.
   - **Size Cutoff Fix:** Redesign `widget_quota.xml` layout padding (8dp), card padding (8dp), and margins to fit 2 pools + header + footer inside standard 2-cell launcher height (110dp) without bottom clipping.
3. **Mobile App Layout & Safe Areas:**
   - Add `viewport-fit=cover` to `<meta name="viewport">` in `app.html`.
   - Update `MobileApp.svelte` header and bottom nav bar with `env(safe-area-inset-top)` and `env(safe-area-inset-bottom)` safe area padding.
   - Use `100dvh` for full viewport height to prevent collision with mobile status bar and system gesture bar (`===`).
4. **Desktop Color Token Alignment:**
   - Replace bluish M3 tokens with PC desktop tokens (`--color-bg: oklch(14% 0 0)`, `--color-surface: oklch(20% 0 0)`, `--color-border: oklch(28% 0 0)`, `--color-accent: oklch(62% 0.16 230)`).
   - Remove card side-stripe borders for visual consistency with the desktop widget.
5. **Main Page Token Setup UX:**
   - Display a prominent, seamless OAuth Refresh Token input card directly on the main page when offline/token is missing, enabling one-click token saving and syncing without navigating to Settings.

---

## 2. Component Design & Changes

### A. PC Desktop Widget (`DesktopWidget.svelte` & `PoolRow.svelte`)
- Hide scrollbars on `.pools-container`:
  ```css
  .pools-container {
    overflow-y: auto;
    scrollbar-width: none; /* Firefox */
    -ms-overflow-style: none; /* IE/Edge */
  }
  .pools-container::-webkit-scrollbar {
    display: none; /* WebKit */
  }
  ```
- Padding and layout adjustments for 200px window height:
  - Root `.widget`: `padding: 12px 14px;`
  - `.pools-container`: `gap: 8px; margin: 6px 0;`
  - `.pool-row` (`PoolRow.svelte`): `padding: 8px 10px; gap: 4px;`
  - `.bar-track`: `height: 6px;`

### B. Android Native Widget (`widget_quota.xml` & `QuotaWidgetProvider.kt`)

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

### C. Mobile App Safe Area & Layout (`MobileApp.svelte` & `app.html`)

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

### D. Main Page Token Quick Setup UX
- When `s.isOffline && !s.tokenInput` or when no pools exist:
  - Render an inline **OAuth Refresh Token Banner** on the main scroll view.
  - Input field with password masking, clear "Save & Sync Quota" button, and single-tap paste support.
  - On submit, invokes `plugin:quota|saveRefreshToken` (Android) or `save_config` (Rust) and immediately triggers `handleRefresh()`.

---

## 3. Verification & Testing Strategy

1. **PC Desktop Widget:**
   - Verify zero white scrollbar appears on the desktop widget.
   - Verify 2 pools fit 100% inside 200px height without cutting off reset text.
2. **Android Widget:**
   - Verify both Gemini and Claude active progress bars use **Cyan Blue (`#38BDF8`)**, matching PC desktop.
   - Verify 2 pools fit inside 3x2 / 2x2 launcher widget without bottom text cutoff.
   - Verify pool order (Gemini vs Claude) matches main app.
3. **Mobile App:**
   - Verify top header is positioned below status bar (no overlap with clock).
   - Verify bottom nav bar is positioned above system gesture indicator line (`===`).
