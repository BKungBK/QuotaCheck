# Mobile & Desktop UI Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate white scrollbars and height cutoff on the PC Desktop Widget, fix vertical cutoff, pool ordering, and cyan bar coloring on the Android Home Screen Widget, apply safe-area insets (notch & gesture bar) to the Mobile App UI, and provide a seamless OAuth Refresh Token setup UX directly on the main page.

**Architecture:**
- **PC Desktop Widget (`DesktopWidget.svelte`, `PoolRow.svelte`):** Hide scrollbars on `.pools-container`, tighten padding/spacing so 2 pool cards fit 100% inside 200px height matching commit `e629ff8`.
- **Android Widget (`widget_quota.xml`, `QuotaWidgetProvider.kt`, drawables):** Restructure layout XML with compact padding (8dp), dynamic slot binding in Kotlin, and unified Cyan Blue (`#38BDF8`) bar color matching PC desktop.
- **Mobile App UI (`MobileApp.svelte`, `app.html`):** Add `viewport-fit=cover` to meta viewport, set safe area top/bottom padding for status bar and gesture bar, synchronize design tokens with PC desktop dark gray.
- **Token Setup UX (`MobileApp.svelte`):** Render a prominent inline token card on the main page when offline/missing token.

**Tech Stack:** Svelte 5 (Runes `$state`, `$derived`), Tailwind-free Vanilla CSS (OKLCH tokens), Kotlin (Android AppWidget Provider), Tauri v2.

## Global Constraints
- Monochrome/Active Cyan Blue palette (`#38BDF8` / `oklch(62% 0.16 230)`).
- Low quota ($\le 20\%$) uses Warning Red/Amber (`#EF4444`).
- Offline/empty uses Neutral Dark Gray (`#52525B`).
- Safe-area inset aware (`env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`).
- Zero scrollbars on PC Desktop widget.

---

### Task 1: Fix PC Desktop Widget Heights & Remove White Scrollbar

**Files:**
- Modify: `src/lib/components/DesktopWidget.svelte:200-350`
- Modify: `src/lib/components/PoolRow.svelte:35-80`

**Interfaces:**
- Consumes: `quotaStore`, `PoolRow`
- Produces: Seamless 200px desktop widget without scrollbars or clipped text

- [ ] **Step 1: Hide scrollbars and adjust container padding in `DesktopWidget.svelte`**

Update `.pools-container` and `.widget` in `src/lib/components/DesktopWidget.svelte`:
```css
  .widget {
    width: 100vw;
    height: 100vh;
    box-sizing: border-box;
    padding: 12px 14px;
    background: var(--color-bg);
    border-radius: 0px;
    font-family: "Inter", system-ui, -apple-system, sans-serif;
    color: var(--color-ink);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    user-select: none;
    pointer-events: auto;
    overflow: hidden;
  }

  .pools-container {
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex-grow: 1;
    justify-content: flex-start;
    margin: 6px 0;
    overflow-y: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
  }
  .pools-container::-webkit-scrollbar {
    display: none;
  }
```

- [ ] **Step 2: Adjust `PoolRow.svelte` padding and height for tight 200px fit**

Update `src/lib/components/PoolRow.svelte`:
```css
  .pool-row {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 10px;
    background: oklch(20% 0 0 / 0.5);
    border: 1px solid oklch(26% 0 0 / 0.6);
    border-radius: 8px;
  }
  .bar-track {
    width: 100%;
    height: 6px;
    background: var(--color-bar-track);
    border-radius: 4px;
    overflow: hidden;
  }
```

- [ ] **Step 3: Test build**

Run: `npm run build`
Expected: Build passes with zero errors.

- [ ] **Step 4: Commit**

```bash
git add src/lib/components/DesktopWidget.svelte src/lib/components/PoolRow.svelte
git commit -m "fix(desktop): remove white scrollbar and optimize height padding for 200px widget"
```

---

### Task 2: Redesign Android Widget Layout & Dynamic Color Provider

**Files:**
- Modify: `src-tauri/gen/android/app/src/main/res/layout/widget_quota.xml:1-236`
- Modify: `src-tauri/gen/android/app/src/main/res/drawable/progress_bar_custom_claude.xml:1-18`
- Modify: `src-tauri/gen/android/app/src/main/res/drawable/progress_bar_custom_gemini.xml:1-18`
- Modify: `src-tauri/gen/android/app/src/main/java/com/antigravity/quota/widget/QuotaWidgetProvider.kt:80-135`

**Interfaces:**
- Consumes: JSON cache (`quota_cache`) from SharedPreferences
- Produces: Android widget with dynamic pool ordering, 100% Cyan Blue active bar color (`#38BDF8`), and compact 110dp cell sizing without vertical cutoff

- [ ] **Step 1: Update `progress_bar_custom_claude.xml` to match Active Cyan Blue (`#38BDF8`)**

Update `src-tauri/gen/android/app/src/main/res/drawable/progress_bar_custom_claude.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape>
            <corners android:radius="4dp" />
            <solid android:color="#27272A" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <scale android:scaleWidth="100%">
            <shape>
                <corners android:radius="4dp" />
                <solid android:color="#38BDF8" />
            </shape>
        </scale>
    </item>
</layer-list>
```

- [ ] **Step 2: Restructure `widget_quota.xml` with compact 8dp padding**

Update `src-tauri/gen/android/app/src/main/res/layout/widget_quota.xml`:
- Set root `LinearLayout` padding to `8dp`.
- Header `RelativeLayout`: `layout_marginBottom="6dp"`.
- `pool1_container` and `pool2_container`: `padding="8dp"`, `layout_marginBottom="6dp"`.
- `pool1_percent` and `pool2_percent` default text color `#38BDF8`.
- Progress bars `pool1_progress` and `pool2_progress`: `layout_height="5dp"`, `layout_marginTop="4dp"`.
- Footer `RelativeLayout`: `layout_marginTop="4dp"`.

- [ ] **Step 3: Update `QuotaWidgetProvider.kt` for dynamic pool binding & cyan colors**

Update `src-tauri/gen/android/app/src/main/java/com/antigravity/quota/widget/QuotaWidgetProvider.kt`:
- Bind `pools.optJSONObject(0)` to `pool1` and `pools.optJSONObject(1)` to `pool2`.
- Read label (`label = p.optString("label", "Pool")`) and percentage `pct`.
- Set percent text color to `#38BDF8` when `pct > 20`, or `#EF4444` when `pct <= 20`.
- Hide `pool2_container` if `pools.length() < 2`.

- [ ] **Step 4: Commit**

```bash
git add src-tauri/gen/android/app/src/main/res/layout/widget_quota.xml src-tauri/gen/android/app/src/main/res/drawable/progress_bar_custom_claude.xml src-tauri/gen/android/app/src/main/java/com/antigravity/quota/widget/QuotaWidgetProvider.kt
git commit -m "fix(widget): prevent vertical cutoff, bind pools dynamically, and use unified cyan blue bar color"
```

---

### Task 3: Apply Safe Area Insets & Color Tokens to Mobile App

**Files:**
- Modify: `src/app.html:6`
- Modify: `src/lib/components/MobileApp.svelte:183-225, 407-432, 434-530`

**Interfaces:**
- Consumes: CSS `env(safe-area-inset-top)` / `env(safe-area-inset-bottom)`
- Produces: Edge-to-edge mobile app without top notch or bottom gesture bar collisions

- [ ] **Step 1: Add `viewport-fit=cover` to `src/app.html`**

Update `src/app.html` line 6:
```html
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
```

- [ ] **Step 2: Update MobileApp top app bar & bottom nav CSS for safe areas**

In `src/lib/components/MobileApp.svelte`:
```css
  .mobile-app-shell {
    width: 100vw;
    height: 100dvh;
    display: flex;
    flex-direction: column;
    background: var(--color-bg);
    color: var(--color-ink-high);
    box-sizing: border-box;
    overflow: hidden;
    user-select: none;
    position: relative;
  }

  .top-app-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: max(14px, env(safe-area-inset-top, 0px));
    padding-bottom: 12px;
    padding-left: 20px;
    padding-right: 20px;
    background: var(--color-bg);
    border-bottom: 1px solid oklch(22% 0 0 / 0.5);
    z-index: 10;
  }

  .bottom-nav-bar {
    height: calc(56px + max(12px, env(safe-area-inset-bottom, 0px)));
    background: oklch(14% 0 0 / 0.95);
    border-top: 1px solid oklch(22% 0 0 / 0.5);
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
    z-index: 10;
  }
```

- [ ] **Step 3: Align MobileApp color design tokens with PC Desktop**

Update `:root` tokens in `MobileApp.svelte`:
```css
  :root {
    --m3-bg:              oklch(14% 0 0 / 0.95);
    --m3-surface:         oklch(20% 0 0 / 0.9);
    --m3-surface-variant: oklch(24% 0 0 / 0.9);
    --m3-outline:         oklch(28% 0 0 / 0.6);
    
    --m3-primary:         oklch(62% 0.16 230);
    --m3-primary-container: oklch(25% 0 0);
    --m3-on-primary-container: oklch(96% 0 0);

    --m3-ink-high:        oklch(96% 0 0);
    --m3-ink-mid:         oklch(70% 0 0);
    --m3-ink-muted:       oklch(60% 0 0);
  }
```
Remove side-stripe card borders (`.card--high`, `.card--medium`, `.card--low`).

- [ ] **Step 4: Commit**

```bash
git add src/app.html src/lib/components/MobileApp.svelte
git commit -m "fix(mobile): add safe area insets for notch and gesture bar, align colors with PC desktop"
```

---

### Task 4: Streamline Main Page OAuth Token Setup UX

**Files:**
- Modify: `src/lib/components/MobileApp.svelte:300-340`

**Interfaces:**
- Consumes: `quotaStore.isOffline`, `quotaStore.tokenInput`
- Produces: Direct inline token input banner on the main page for instant saving and syncing

- [ ] **Step 1: Add inline Quick Setup Token Card on main view when offline**

In `MobileApp.svelte` inside `.mobile-content`:
```svelte
{#if s.isOffline && s.pools.length === 0}
  <section class="offline-card">
    <div class="offline-icon-wrapper">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M1 1l22 22M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/>
      </svg>
    </div>
    
    <h3 class="offline-title">OAuth Refresh Token Required</h3>
    
    <p class="offline-body">
      Paste your Refresh Token below to enable direct cloud sync on Android without opening Settings.
    </p>

    <div class="token-sheet-box">
      <input
        type="password"
        class="material-input"
        placeholder="Paste OAuth Refresh Token (1//0...)"
        bind:value={s.tokenInput}
      />
      <button class="material-btn material-btn--primary ripple-btn" onclick={handleSaveToken}>
        Save & Sync Quota
      </button>
      {#if s.tokenSaveStatus}
        <span class="save-status-text">{s.tokenSaveStatus}</span>
      {/if}
    </div>
  </section>
{/if}
```

- [ ] **Step 2: Test build**

Run: `npm run build`
Expected: Clean build success.

- [ ] **Step 3: Commit**

```bash
git add src/lib/components/MobileApp.svelte
git commit -m "feat(mobile): add inline OAuth Refresh Token quick setup card on main page"
```
