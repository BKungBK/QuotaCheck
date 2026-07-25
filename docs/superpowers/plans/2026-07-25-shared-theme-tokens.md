# Shared Base Theme Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize CSS theme custom properties into `src/lib/theme.css` and migrate `MobileApp.svelte` from `--m3-*` tokens to unified `--color-*` tokens while preserving mobile solid surface opacity.

**Architecture:** Create a single global CSS file `src/lib/theme.css` imported in `+layout.svelte`. Remove `:root` custom property definitions from `DesktopWidget.svelte` and `MobileApp.svelte`. Map all 13 `--m3-*` usages in `MobileApp.svelte` to corresponding `--color-*` tokens, with local surface color overrides scoped to `.mobile-app-shell`.

**Tech Stack:** Svelte 5, CSS Custom Properties, SvelteKit, Vite.

## Global Constraints
- Do not modify `tauri.conf.json` transparency settings for desktop overlay.
- Do not modify `isMobilePlatform` detection logic in `src/routes/+page.svelte`.
- Do not alter layout, DOM structure, or JavaScript logic in `MobileApp.svelte` or `DesktopWidget.svelte`.
- Do not combine multiple tasks in a single commit.

---

### Task 1: Create Central Theme File (`src/lib/theme.css`) and Import in `+layout.svelte`

**Files:**
- Create: `src/lib/theme.css`
- Modify: `src/routes/+layout.svelte:1-10`

**Interfaces:**
- Consumes: Theme token definitions from design spec v2.
- Produces: Global CSS `:root` variables for `--color-*` tokens accessible by all components.

- [ ] **Step 1: Create `src/lib/theme.css` with single source of truth tokens**

```css
:root {
  /* Surfaces */
  --color-bg:              oklch(15% 0 0 / 0.65);
  --color-surface:         oklch(20% 0 0 / 0.8);
  --color-surface-variant: oklch(24% 0 0 / 0.9);
  --color-border:          oklch(25% 0 0 / 0.4);
  --color-separator:       oklch(20% 0 0 / 0.4);

  /* Shimmer */
  --color-shimmer-base:      oklch(18% 0 0 / 0.5);
  --color-shimmer-highlight: oklch(25% 0 0 / 0.5);

  /* Ink scale */
  --color-ink:        oklch(85% 0 0);
  --color-ink-high:   oklch(90% 0 0);
  --color-ink-mid:    oklch(65% 0 0);
  --color-ink-muted:  oklch(55% 0 0);
  --color-ink-dim:    oklch(50% 0 0);
  --color-ink-subtle: oklch(45% 0 0);

  /* Status dots */
  --color-dot-offline:    oklch(42% 0 0);
  --color-dot-stale:      oklch(65% 0.15 80);
  --color-dot-live:       oklch(75% 0 0);
  --color-dot-live-glow:  oklch(75% 0 0 / 0.4);

  /* Accent */
  --color-accent:      oklch(48% 0 0);
  --color-accent-glow: oklch(48% 0 0 / 0.5);

  /* Bar track */
  --color-bar-track:   oklch(25% 0 0 / 0.7);
  --color-bar-offline: oklch(36% 0 0);
  --color-bar-low:     oklch(42% 0 0);
}
```

- [ ] **Step 2: Import `src/lib/theme.css` in `src/routes/+layout.svelte`**

```svelte
<script lang="ts">
  import '$lib/theme.css';
  let { children } = $props();
</script>

{@render children()}
```

- [ ] **Step 3: Run build check to verify syntax**

Run: `npx svelte-check`
Expected: 0 errors

- [ ] **Step 4: Commit Task 1**

```bash
git add src/lib/theme.css src/routes/+layout.svelte
git commit -m "feat(theme): create central theme.css and import in root layout"
```

---

### Task 2: Remove Local `:root` Token Block from `DesktopWidget.svelte`

**Files:**
- Modify: `src/lib/components/DesktopWidget.svelte:161-198`

**Interfaces:**
- Consumes: `--color-*` variables from `src/lib/theme.css`.
- Produces: Scoped desktop widget styles relying on global CSS custom properties.

- [ ] **Step 1: Remove `:root` block from `DesktopWidget.svelte`**

In `src/lib/components/DesktopWidget.svelte`, delete the `:root { ... }` block inside `<style>` (lines 162–197) leaving the rest of the style rules intact starting from `:global(...)`.

- [ ] **Step 2: Run build check**

Run: `npx svelte-check`
Expected: 0 errors

- [ ] **Step 3: Commit Task 2**

```bash
git add src/lib/components/DesktopWidget.svelte
git commit -m "refactor(desktop): remove local :root theme tokens in favor of central theme.css"
```

---

### Task 3: Migrate MobileApp Color Tokens from `--m3-*` to `--color-*`

**Files:**
- Modify: `src/lib/components/MobileApp.svelte:429-580`

**Interfaces:**
- Consumes: Global `--color-*` tokens and local `.mobile-app-shell` surface overrides.
- Produces: Fully migrated mobile UI styling using unified theme tokens.

- [ ] **Step 1: Replace `:root` in `MobileApp.svelte` with local `.mobile-app-shell` overrides**

Remove `:root { --m3-*... }` block (lines 429–445).
Add local surface opacity overrides for mobile app shell:

```css
.mobile-app-shell {
  --color-bg: oklch(14% 0 0);
  --color-surface: oklch(20% 0 0);
}
```

- [ ] **Step 2: Replace all 13 `--m3-*` occurrences in `MobileApp.svelte`**

Substitute all `--m3-*` custom property references according to the mapping:
- `var(--m3-bg)` → `var(--color-bg)`
- `var(--m3-surface)` → `var(--color-surface)`
- `var(--m3-surface-variant)` → `var(--color-surface-variant)`
- `var(--m3-outline)` → `var(--color-border)`
- `var(--m3-primary)` → `var(--color-accent)`
- `var(--m3-primary-container)` → `var(--color-accent-glow)`
- `var(--m3-on-primary-container)` → `var(--color-ink-high)`
- `var(--m3-ink-high)` → `var(--color-ink-high)`
- `var(--m3-ink-mid)` → `var(--color-ink-mid)`
- `var(--m3-ink-muted)` → `var(--color-ink-muted)`
- `var(--m3-success)` → `var(--color-dot-live)`
- `var(--m3-warning)` → `var(--color-dot-stale)`
- `var(--m3-offline)` → `var(--color-dot-offline)`

- [ ] **Step 3: Verify zero `--m3-` references remain in codebase**

Run: `grep -rn -- "--m3-" src/`
Expected: Empty output (no matches found)

- [ ] **Step 4: Run svelte-check and project build**

Run: `npx svelte-check && npm run build`
Expected: Build succeeds with 0 errors

- [ ] **Step 5: Commit Task 3**

```bash
git add src/lib/components/MobileApp.svelte
git commit -m "feat(mobile): migrate MobileApp color tokens from --m3-* to shared --color-*"
```
