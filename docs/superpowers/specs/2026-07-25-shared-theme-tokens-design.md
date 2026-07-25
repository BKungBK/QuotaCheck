# Shared Base Theme Tokens with Mobile Surface Overrides Design

## Summary
Extract CSS custom properties (`--color-*`) into a centralized stylesheet (`src/lib/theme.css`) imported globally in `+layout.svelte`. Eliminate the redundant and incompatible `--m3-*` color token system in `MobileApp.svelte`, replacing all color usage across mobile and desktop components with a single `--color-*` token system. Override platform-specific surface colors (`--color-bg`, `--color-surface`) locally within `MobileApp.svelte` to maintain solid opaque background for mobile while preserving transparent glass overlay for desktop.

## Proposed Design Details

### 1. Centralized Theme File (`src/lib/theme.css`)
- Create `src/lib/theme.css` containing `:root` custom properties derived from `DesktopWidget.svelte`:
  - **Surfaces**: `--color-bg`, `--color-surface`, `--color-border`, `--color-separator`
  - **Shimmer**: `--color-shimmer-base`, `--color-shimmer-highlight`
  - **Ink Scale**: `--color-ink`, `--color-ink-high`, `--color-ink-mid`, `--color-ink-muted`, `--color-ink-dim`, `--color-ink-subtle`
  - **Status & Accent**: `--color-dot-offline`, `--color-dot-stale`, `--color-dot-live`, `--color-dot-live-glow`, `--color-accent`, `--color-accent-glow`
  - **Bar Track & Offline**: `--color-bar-track`, `--color-bar-offline`, `--color-bar-low`
- Import `src/lib/theme.css` inside `src/routes/+layout.svelte`.

### 2. DesktopWidget Cleanup (`src/lib/components/DesktopWidget.svelte`)
- Remove `:root { --color-*... }` block from `DesktopWidget.svelte`.
- Maintain existing `DesktopWidget` layout, overlay behaviors, and CSS rules untouched.

### 3. Mobile App Token Migration (`src/lib/components/MobileApp.svelte`)
- Remove `:root` with `--m3-*` color token definitions from `MobileApp.svelte`.
- Map all `--m3-*` occurrences to corresponding `--color-*` tokens:
  - `--m3-ink-high` -> `--color-ink-high`
  - `--m3-ink-mid` -> `--color-ink-mid`
  - `--m3-ink-muted` -> `--color-ink-muted`
  - `--m3-primary` -> `--color-accent`
  - `--m3-surface` -> `--color-surface`
  - `--m3-bg` -> `--color-bg`
- Scope mobile surface overrides locally in `MobileApp.svelte`:
  ```css
  .mobile-app-shell {
    --color-bg: oklch(14% 0 0); /* Solid background for mobile screens */
    --color-surface: oklch(20% 0 0);
  }
  ```
- Retain all mobile layout, DOM structure, and logic untouched.

### 4. Constraints & Verification Plan
- **Tauri Glass Overlay**: Keep `tauri.conf.json` desktop window transparency untouched.
- **Platform Detection**: Keep `isMobilePlatform` detection logic in `+page.svelte` untouched.
- **Static Analysis & Build Verification**: Run `svelte-check` / `npm run build` to confirm zero unresolved CSS variable usages or build warnings across mobile & desktop routes.
