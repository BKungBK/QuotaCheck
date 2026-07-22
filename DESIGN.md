---
name: Antigravity Quota Widget
description: Glanceable developer quota widget in dark gray style
colors:
  accent:          "oklch(48% 0 0)"        # Natural gray — bar fill, live state
  bg:              "oklch(15% 0 0 / 0.65)" # Transparent natural gray widget body background
  surface:         "oklch(20% 0 0 / 0.8)"  # Bar track / inset surfaces
  border:          "oklch(25% 0 0 / 0.4)"  # Widget outer border
  separator:       "oklch(20% 0 0 / 0.4)"  # Pool divider lines
  ink:             "oklch(85% 0 0)"        # Primary text (verified contrast on bg)
  ink-high:        "oklch(90% 0 0)"        # Labels, pool name, percent
  ink-mid:         "oklch(65% 0 0)"        # Live badge text
  ink-muted:       "oklch(55% 0 0)"        # Sub-meta / reset time
  ink-dim:         "oklch(50% 0 0)"        # Footer meta
  ink-subtle:      "oklch(45% 0 0)"        # Placeholder text
  dot-offline:     "oklch(42% 0 0)"        # Offline status dot
  dot-live:        "oklch(75% 0 0)"        # Live pulse dot (soft white/gray)
  bar-offline:     "oklch(36% 0 0)"        # Bar fill when offline
  bar-low:         "oklch(42% 0 0)"        # Low quota bar fill
  shimmer-base:    "oklch(18% 0 0 / 0.5)"  # Skeleton shimmer trough
  shimmer-high:    "oklch(25% 0 0 / 0.5)"  # Skeleton shimmer peak
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
    lineHeight: 1
  pool-percent:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1
  sub-meta:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.5625rem"
    letterSpacing: "0.02em"
  footer-meta:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.5625rem"
    fontWeight: 500
    letterSpacing: "0.02em"
rounded:
  sm: "4px"
  md: "8px"
spacing:
  sm: "8px"
  md: "12px"
components:
  widget-container:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.ink}"
    borderColor: "{colors.border}"
    rounded: "{rounded.md}"
    padding: "8px 12px"
  progress-bar:
    trackColor: "{colors.surface}"
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

All colors are defined as CSS custom properties on `:root` in `+page.svelte`. All values use OKLCH for perceptual uniformity.

### Accent
- **Active Gray** `oklch(48% 0 0)` — `var(--color-accent)`: Progress bar fill (online, >20% remaining).
- **Live Dot Pulse** `oklch(75% 0 0)` — `var(--color-dot-live)`: Pulsing live status indicator.
- **Low Quota Gray** `oklch(42% 0 0)` — `var(--color-bar-low)`: Progress bar fill when ≤20% remaining.

**The Monochromatic Rule.** The design is completely monochromatic. Progress indicators, state indicators, and text use pure natural gray levels (chroma = 0) to ensure zero distraction and perfect wallpaper integration.

### Ink Scale (all verified ≥4.5:1 on `--color-bg`)
| Token | Value | Contrast | Usage |
|---|---|---|---|
| `--color-ink` | `oklch(85% 0 0)` | ~6.7:1 | General body text |
| `--color-ink-high` | `oklch(90% 0 0)` | ~8.7:1 | Pool name, percent, widget title |
| `--color-ink-mid` | `oklch(65% 0 0)` | ~4.7:1 | Live badge text |
| `--color-ink-muted` | `oklch(55% 0 0)` | ~4.5:1 | Sub-meta, reset time |
| `--color-ink-dim` | `oklch(50% 0 0)` | ~4.5:1 | Footer meta (source, time-ago) |
| `--color-ink-subtle` | `oklch(45% 0 0)` | ~3.1:1 | Placeholder "No Pools" / "Offline" |

> Note: `--color-ink-subtle` sits below 4.5:1. It is intentionally placed in a non-essential decorative context (empty state placeholder), not body text.

### Surface / Structure
- **Background** `oklch(15% 0 0 / 0.65)` — `var(--color-bg)`: Transparent natural gray widget body.
- **Bar Track** `oklch(20% 0 0 / 0.5)` — `var(--color-bar-track)`: Progress bar track.
- **Border** `oklch(25% 0 0 / 0.4)` — `var(--color-border)`: Outer widget border.
- **Separator** `oklch(20% 0 0 / 0.4)` — `var(--color-separator)`: Pool divider lines.

## 3. Typography

**Font:** Inter (fallback: system-ui, sans-serif) — single family, multiple weights.

### Hierarchy
| Role | Size | Weight | Notes |
|---|---|---|---|
| Widget label | 0.6875rem | 600 | Uppercase, tracked |
| Pool name | 0.75rem | 500 | Truncated with ellipsis |
| Pool percent | 0.75rem | 600 | Flex-shrink: 0 |
| Sub-meta | 0.5625rem | 400 | Reset time, fraction |
| Footer meta | 0.5625rem | 500 | Source, time-ago |
| Placeholder | 0.6875rem | 500 | Uppercase, empty state |

## 4. Elevation

The widget uses flat tonal layering and borders — no drop shadows. Reparented to the WorkerW desktop layer, shadows would float awkwardly over desktop icons.

**The Flat-Surface Rule.** Depth is achieved via the thin `--color-border` outline and the solid `--color-bg` fill. No `box-shadow` on the container, ever.

## 5. Components

### Widget Container
- **Shape:** `border-radius: 8px`
- **Background:** `var(--color-bg)`
- **Border:** `1px solid var(--color-border)`
- **Padding:** `8px 12px`
- **Pointer events:** `none` (click-through)

### Progress Bar
- **Track:** `var(--color-bar-track)`, `border-radius: 4px`
- **Fill (online, normal):** `var(--color-accent)` — Active Blue
- **Fill (online, ≤20%):** `var(--color-bar-low)` — Amber warning
- **Fill (offline):** `var(--color-bar-offline)` — Neutral gray
- **Thickness:** 5px
- **Transition:** `width 400ms ease, background 600ms ease`
- **Reduced motion:** transitions disabled via `@media (prefers-reduced-motion: reduce)`

### Status Dot
- **Offline:** `var(--color-dot-offline)` — static gray circle
- **Live:** `var(--color-dot-live)` — green, `pulseDot` keyframe animation

### Skeleton Loaders
- **Shimmer:** `linear-gradient` from `var(--color-shimmer-base)` to `var(--color-shimmer-highlight)`
- **Timing:** `1.4s linear infinite` (linear for smooth sweep)
- **Reduced motion:** animation disabled, shows static `--color-shimmer-base` fill

## 6. Accessibility

- `<main>` carries `aria-label="Antigravity Quota Widget"`.
- The `.label` span is `aria-hidden="true"` (decorative logo text).
- `.live-badge` carries `role="status"` and `aria-live="polite"` for dynamic state announcements.
- The `.dot` span is `aria-hidden="true"` (adjacent text conveys the same state).
- All interactive transitions respect `@media (prefers-reduced-motion: reduce)`.

## 7. Do's and Don'ts

### Do:
- **Do** desaturate the widget (`opacity: 0.55; filter: grayscale(1)`) when offline.
- **Do** transition the bar fill from blue → amber when quota drops ≤20%.
- **Do** keep skeleton loaders for loading state, not spinners.
- **Do** use `var(--color-*)` tokens exclusively — no bare `oklch()` literals in component CSS.

### Don't:
- **Don't** use any card drop shadows or container blurs (glassmorphism).
- **Don't** use neon green or neon blue gradient highlights.
- **Don't** allow mouse clicks on the widget while on desktop (keep `pointer-events: none`).
- **Don't** add colors not in this token system without updating both `:root` and this document.
