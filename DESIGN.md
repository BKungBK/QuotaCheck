---
name: Antigravity Quota Widget
description: Glanceable developer quota widget in dark gray style
colors:
  accent:              "oklch(48% 0 0)"        # Natural gray — progress bar fill (online, >20%)
  accent-glow:         "oklch(48% 0 0 / 0.5)"  # Accent glow highlight
  bg:                  "oklch(15% 0 0 / 0.65)" # Transparent natural gray widget body background
  surface:             "oklch(20% 0 0 / 0.8)"  # Inset surface / dropdown background
  surface-variant:     "oklch(24% 0 0 / 0.9)"  # Elevated surface / button background
  border:              "oklch(25% 0 0 / 0.4)"  # Widget outer border
  separator:           "oklch(20% 0 0 / 0.4)"  # Pool divider lines
  ink:                 "oklch(85% 0 0)"        # Primary text (verified contrast on bg)
  ink-high:            "oklch(90% 0 0)"        # Labels, pool name, percent
  ink-mid:             "oklch(65% 0 0)"        # Live badge text, labels
  ink-muted:           "oklch(55% 0 0)"        # Sub-meta / reset time
  ink-dim:             "oklch(50% 0 0)"        # Footer meta (source, time-ago)
  ink-subtle:          "oklch(45% 0 0)"        # Placeholder text / decorative
  dot-offline:         "oklch(42% 0 0)"        # Offline status dot
  dot-stale:           "oklch(65% 0.15 80)"    # Stale cache status dot (warm amber)
  dot-live:            "oklch(75% 0 0)"        # Live status dot (soft white/gray)
  dot-live-glow:       "oklch(75% 0 0 / 0.4)"  # Live pulse dot glow halo
  bar-track:           "oklch(25% 0 0 / 0.7)"  # Progress bar track
  bar-offline:         "oklch(36% 0 0)"        # Bar fill when offline
  bar-low:             "oklch(42% 0 0)"        # Low quota bar fill (≤20% remaining)
  shimmer-base:        "oklch(18% 0 0 / 0.5)"  # Skeleton shimmer base
  shimmer-highlight:   "oklch(25% 0 0 / 0.5)"  # Skeleton shimmer peak
typography:
  widget-label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 600
    letterSpacing: "0.04em"
    textTransform: uppercase
  pool-name:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.2
  pool-percent:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1.2
  sub-meta:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.5625rem"
    letterSpacing: "0.02em"
    lineHeight: 1.2
  footer-meta:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.5625rem"
    fontWeight: 500
    letterSpacing: "0.02em"
rounded:
  sm: "3px"
  md: "4px"
  lg: "8px"
spacing:
  xs: "4px"
  sm: "6px"
  md: "8px"
  lg: "12px"
components:
  widget-container:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.ink}"
    borderColor: "{colors.border}"
    rounded: "{rounded.lg}"
    padding: "8px 12px"
  progress-bar:
    trackColor: "{colors.bar-track}"
    fillColor: "{colors.accent}"
    fillColorLow: "{colors.bar-low}"
    fillColorOffline: "{colors.bar-offline}"
    height: "5px"
    radius: "{rounded.sm}"
    transition: "width 400ms ease, background 600ms ease"
---

# Design System: Antigravity Quota Widget

## 1. Overview

**Creative North Star: "The Ambient Dashboard Status"**

A minimal, high-contrast desktop widget that blends into the background of a developer's desktop. It mimics the clean, utilitarian, and distraction-free dark gray aesthetic of GitHub and the Antigravity IDE. The interface prioritizes raw data visibility and subtle state changes, stripping away all unnecessary visual clutter.

**Key Characteristics:**
- **Sleek Utility**: Flat, transparent natural grays with subtle visual indicators; zero distraction.
- **Glanceability**: Clear, highly legible text sizes that can be scanned in milliseconds.
- **Atmospheric State**: Seamless integration with wallpaper via transparency.

## 2. Colors

All base colors are defined centrally as CSS custom properties on `:root` in `src/lib/theme.css` and imported globally in `+layout.svelte`. All values use OKLCH for perceptual uniformity.

### Accent & Status
- **Active Gray** `oklch(48% 0 0)` — `var(--color-accent)`: Progress bar fill (online, >20% remaining).
- **Accent Glow** `oklch(48% 0 0 / 0.5)` — `var(--color-accent-glow)`: Subtle accent highlights/focus indicators.
- **Live Dot Pulse** `oklch(75% 0 0)` — `var(--color-dot-live)`: Pulsing live status indicator.
- **Live Dot Glow** `oklch(75% 0 0 / 0.4)` — `var(--color-dot-live-glow)`: Keyframe glow halo for active status pulse.
- **Stale Dot** `oklch(65% 0.15 80)` — `var(--color-dot-stale)`: Amber status indicator when cache data is stale.
- **Low Quota Gray** `oklch(42% 0 0)` — `var(--color-bar-low)`: Progress bar fill when ≤20% remaining.

**The Monochromatic Rule.** The primary widget design is monochromatic. Progress indicators, container surfaces, and text use pure natural gray levels (chroma = 0) to ensure zero distraction and perfect wallpaper integration. Accent/stale indicators use controlled hues strictly for status awareness.

### Ink Scale (all verified ≥4.5:1 on `--color-bg`)
| Token | Value | Contrast | Usage |
|---|---|---|---|
| `--color-ink` | `oklch(85% 0 0)` | ~6.7:1 | General body text |
| `--color-ink-high` | `oklch(90% 0 0)` | ~8.7:1 | Pool name, percent, widget title |
| `--color-ink-mid` | `oklch(65% 0 0)` | ~4.7:1 | Live badge text, field labels |
| `--color-ink-muted` | `oklch(55% 0 0)` | ~4.5:1 | Sub-meta, reset time |
| `--color-ink-dim` | `oklch(50% 0 0)` | ~4.5:1 | Footer meta (source, time-ago), scrollbars |
| `--color-ink-subtle` | `oklch(45% 0 0)` | ~3.1:1 | Decorative / subtle placeholders |

> Note: `--color-ink-subtle` sits below 4.5:1. It is intentionally reserved for secondary non-essential contexts.

### Surface / Structure
- **Background** `oklch(15% 0 0 / 0.65)` — `var(--color-bg)`: Transparent natural gray widget body.
- **Surface** `oklch(20% 0 0 / 0.8)` — `var(--color-surface)`: Input background, inset surfaces.
- **Surface Variant** `oklch(24% 0 0 / 0.9)` — `var(--color-surface-variant)`: Interactive button background.
- **Bar Track** `oklch(25% 0 0 / 0.7)` — `var(--color-bar-track)`: Progress bar track.
- **Border** `oklch(25% 0 0 / 0.4)` — `var(--color-border)`: Outer widget border and input borders.
- **Separator** `oklch(20% 0 0 / 0.4)` — `var(--color-separator)`: Pool divider lines.

## 3. Typography

**Font:** Inter (fallback: system-ui, sans-serif) — single family, multiple weights.

### Hierarchy
| Role | Size | Weight | Line Height | Notes |
|---|---|---|---|---|
| Widget label | 0.6875rem | 600 | 1 | Uppercase, tracked (0.04em) |
| Pool name | 0.75rem | 500 | 1.2 | Truncated with ellipsis |
| Pool percent | 0.75rem | 600 | 1.2 | Tabular nums, flex-shrink: 0 |
| Sub-meta | 0.5625rem | 400 | 1.2 | Reset time, tabular nums |
| Footer meta | 0.5625rem | 500 | Normal | Source, time-ago, tabular nums |
| Placeholder | 0.75rem | 600 | Normal | Uppercase, empty/offline box header |

## 4. Elevation

The widget uses flat tonal layering and borders — no drop shadows. Reparented to the desktop layer (e.g. WorkerW on Windows), drop shadows float awkwardly over wallpaper icons.

**The Flat-Surface Rule.** Depth is achieved via the thin `--color-border` outline and transparent `--color-bg` fill. No `box-shadow` on container surfaces.

## 5. Components

### Widget Container
- **Shape:** `border-radius: 8px`
- **Background:** `var(--color-bg)`
- **Border:** `1px solid var(--color-border)`
- **Padding:** `8px 12px`
- **Pointer events:** `none` (click-through for background overlay)

### Progress Bar (`PoolRow.svelte`)
- **Track:** `var(--color-bar-track)`, `height: 5px`, `border-radius: 3px`
- **Fill (online, normal):** `var(--color-accent)` — Active Gray
- **Fill (online, ≤20%):** `var(--color-bar-low)` — Low quota fill
- **Fill (offline):** `var(--color-bar-offline)` — Offline fill
- **Transition:** `width 400ms ease, background 600ms ease`

### Status Dot
- **Offline:** `var(--color-dot-offline)` — static gray circle
- **Stale:** `var(--color-dot-stale)` — warm amber circle
- **Live:** `var(--color-dot-live)` — white/gray pulse dot with `pulseDot` keyframe animation (`0 0 0 0 var(--color-dot-live-glow)`)

### Skeleton Loaders (`SkeletonRow.svelte`)
- **Shimmer:** `linear-gradient(90deg, var(--color-shimmer-base) 25%, var(--color-shimmer-highlight) 37%, var(--color-shimmer-base) 63%)`
- **Timing:** `1.4s linear infinite` (`shimmer` keyframe animation)

## 6. Accessibility

- `<main>` carries `aria-label="Antigravity Quota Widget"`.
- The `.label` span is `aria-hidden="true"` (decorative logo text).
- `.live-badge` carries `role="status"` and `aria-live="polite"` for dynamic status updates.
- Status dots carry `aria-hidden="true"`.
- Tabular numerals (`font-variant-numeric: tabular-nums`) applied to percentages and timestamps to avoid layout jitter during updates.

## 7. Do's and Don'ts

### Do:
- **Do** centralize all color tokens in `src/lib/theme.css` and consume them via `var(--color-*)`.
- **Do** desaturate the widget (`opacity: 0.55; filter: grayscale(1)`) when offline with empty pools.
- **Do** transition the bar fill color when quota drops ≤20%.
- **Do** use `SkeletonRow` for loading states instead of spinner overlays.

### Don't:
- **Don't** re-define `:root` color tokens inside individual `.svelte` component `<style>` blocks.
- **Don't** hardcode raw OKLCH or HEX color literals directly in components when a CSS token exists.
- **Don't** add card drop shadows or container blurs.
- **Don't** enable pointer events on the main desktop overlay container (keep `pointer-events: none` on shell, enabling `pointer-events: auto` only on interactive inner boxes).

