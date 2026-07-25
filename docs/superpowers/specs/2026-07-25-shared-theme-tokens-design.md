# Shared Base Theme Tokens with Mobile Surface Overrides Design (v2 — Complete)

## Summary
Extract CSS custom properties (`--color-*`) into a centralized stylesheet
(`src/lib/theme.css`) imported globally in `+layout.svelte`. Eliminate the
redundant and incompatible `--m3-*` color token system in `MobileApp.svelte`,
replacing **all 13** `--m3-*` usages with `--color-*` tokens (verified against
actual usage in the file — see mapping table below). Override
platform-specific surface colors (`--color-bg`, `--color-surface`) locally
within `MobileApp.svelte` to keep a solid opaque background for mobile while
preserving the transparent glass overlay for desktop.

> **v1 → v2 change:** v1 only mapped 6 of the 13 `--m3-*` tokens actually used
> in `MobileApp.svelte`. This version accounts for all 13, verified by
> `grep -n -- "--m3-" src/lib/components/MobileApp.svelte`.

## Proposed Design Details

### 1. Centralized Theme File (`src/lib/theme.css`)
Create `src/lib/theme.css` containing all existing desktop tokens **plus one
new token** (`--color-surface-variant`, see §3):

```css
:root {
  /* Surfaces */
  --color-bg:         oklch(15% 0 0 / 0.65);
  --color-surface:    oklch(20% 0 0 / 0.8);
  --color-surface-variant: oklch(24% 0 0 / 0.9); /* NEW — see §3 */
  --color-border:     oklch(25% 0 0 / 0.4);
  --color-separator:  oklch(20% 0 0 / 0.4);

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

Import `src/lib/theme.css` inside `src/routes/+layout.svelte` so both
`DesktopWidget` and `MobileApp` inherit it regardless of which one mounts.

### 2. DesktopWidget Cleanup (`src/lib/components/DesktopWidget.svelte`)
- Remove the `:root { --color-*... }` block (lines 162–197) — now lives in
  `theme.css`.
- Do not touch any other rule, layout, or overlay behavior in this file.

### 3. Mobile App Token Migration (`src/lib/components/MobileApp.svelte`)

Remove the `:root { --m3-*... }` block (lines 429–445). Replace **all 13**
`--m3-*` references per this complete mapping:

| `--m3-*` token | Used for (verified in file) | Maps to | Note |
|---|---|---|---|
| `--m3-bg` | app shell bg, top app bar bg | `--color-bg` | |
| `--m3-surface` | cards, bottom sheet drawer bg | `--color-surface` | |
| `--m3-surface-variant` | icon-btn hover bg, material-btn bg, token-sheet-box bg (4 usages) | `--color-surface-variant` **(new token)** | Desktop has no "hover/secondary surface" concept (single card, no interactive buttons) — new token added to theme.css, does not affect desktop since nothing there references it |
| `--m3-outline` | border on hero card, pull-refresh badge, offline card, material-btn (5 usages) | `--color-border` | Same semantic role |
| `--m3-primary` | pull-spinner icon, mask-toggle, cloud-icon, nav-item--active text | `--color-accent` | |
| `--m3-primary-container` | brand badge "BK" bg, nav-item--active bg | `--color-accent-glow` | Desktop already uses accent-glow as the translucent companion to accent |
| `--m3-on-primary-container` | brand badge text, active nav text | `--color-ink-high` | Brightest ink, standard for text on accent-glow |
| `--m3-ink-high` | primary text throughout | `--color-ink-high` | |
| `--m3-ink-mid` | secondary text | `--color-ink-mid` | |
| `--m3-ink-muted` | tertiary/muted text | `--color-ink-muted` | |
| `--m3-success` | `.status-chip--live` text color (3 usages incl. save-status-text) | `--color-dot-live` | ⚠️ Visual change: desktop's "live" indicator is grayscale, not green. Mobile will lose the green hue here — see Risk Notes |
| `--m3-warning` | `.status-chip--stale` text color | `--color-dot-stale` | Semantic match ("stale"); amber hue close to original |
| `--m3-offline` | `.status-chip--offline` text color | `--color-dot-offline` | Exact semantic match |

- Scope mobile-only surface overrides locally (do **not** edit `theme.css`
  for these — desktop must keep its transparent values):

```css
.mobile-app-shell {
  --color-bg: oklch(14% 0 0);       /* solid, not desktop's 0.65 alpha */
  --color-surface: oklch(20% 0 0);  /* solid, not desktop's 0.8 alpha */
}
```

- Do not change any layout, DOM structure, component logic, gesture
  handling, or class names in `MobileApp.svelte` — this is a color-token
  substitution only.

### 4. PoolRow.svelte
No changes needed. `PoolRow.svelte` already references `--color-*` tokens
directly for both `.pool-row--desktop` and `.pool-row--mobile` variants —
once `theme.css` is loaded globally, both variants resolve correctly on
every platform. This was the original bug (undefined vars when
`DesktopWidget` doesn't mount) and is fixed purely by centralizing the
tokens in step 1.

## Constraints
- Do not modify `tauri.conf.json` transparency settings for the desktop
  window.
- Do not modify the `isMobilePlatform` detection logic in
  `src/routes/+page.svelte`.
- Do not combine this change with any other refactor/optimization in the
  same commit.

## Verification Plan
1. `grep -rn -- "--m3-" src/` returns zero results after migration.
2. `npx svelte-check` — zero new errors/warnings.
3. `npm run build` — succeeds for both targets (desktop `tauri build`,
   android `tauri android build`), confirming no unresolved CSS custom
   properties reach the compiled output.
4. Visual check (manual or screenshot diff) on both platforms:
   - Desktop widget: unchanged appearance (still transparent overlay).
   - Mobile app: solid background, all text/borders/status chips visible
     (previously some may have rendered as transparent/black due to the
     undefined-variable bug).
5. Confirm `.status-chip--live` on mobile now renders desktop's grayscale
   "live" tone instead of green — flag this explicitly in the PR
   description as an intentional visual change, not a regression.

## Risk Notes
- **Low risk, cosmetic-only change.** No JS/TS logic touched, no DOM
  structure touched, no build config touched.
- **One deliberate visual change**: mobile's "live" status chip color
  shifts from green (`oklch(75% 0.18 145)`) to desktop's grayscale
  (`oklch(75% 0 0)`). This is a direct, intended consequence of "mobile
  theme must match desktop" — call it out in the commit/PR so it isn't
  mistaken for a bug during review.
- New token `--color-surface-variant` is additive only; verified zero
  existing references to it in `DesktopWidget.svelte`, so it cannot alter
  desktop rendering.
