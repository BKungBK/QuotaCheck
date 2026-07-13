---
name: Antigravity Quota Widget
description: Glanceable developer quota widget in dark gray style
colors:
  primary: "#f0883e"
  neutral-bg: "#0d1117"
  neutral-surface: "#161b22"
  neutral-text: "#c9d1d9"
  neutral-muted: "#8b949e"
  border-color: "#30363d"
typography:
  display:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "tight"
  body:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: "normal"
rounded:
  sm: "4px"
  md: "8px"
spacing:
  sm: "8px"
  md: "12px"
components:
  widget-container:
    backgroundColor: "{colors.neutral-bg}"
    textColor: "{colors.neutral-text}"
    rounded: "{rounded.md}"
    padding: "12px"
---

# Design System: Antigravity Quota Widget

## 1. Overview

**Creative North Star: "The Ambient Dashboard Status"**

A minimal, high-contrast desktop widget that blends into the background of a developer's desktop. It mimics the clean, utilitarian, and distraction-free dark gray aesthetic of GitHub and the Antigravity IDE. The interface prioritizes raw data visibility and subtle state changes, stripping away all unnecessary visual clutter.

**Key Characteristics:**
- **Sleek Utility**: Flat dark grays with a sharp gold-orange progress bar indicator.
- **Glanceability**: Clear, highly legible text sizes that can be scanned in milliseconds.
- **Atmospheric State**: Changes contrast and saturation dynamically when the network or API state changes.

## 2. Colors

The color palette is composed of cold, high-contrast dark grays serving as the container canvas, accented by a single warm orange-gold for active state metrics.

### Primary
- **Active Gold** (#f0883e): Used for progress indicators, active quota highlights, and success status elements.

### Neutral
- **GitHub Dark Canvas** (#0d1117): The main background color of the widget, ensuring it sits comfortably on both dark and light wallpapers.
- **Surface Container** (#161b22): Subtle background highlights or dividers.
- **Ink Primary** (#c9d1d9): The high-contrast text color for active quota numbers.
- **Ink Muted** (#8b949e): Muted label text, secondary time indicators, or offline warning icons.
- **Border Gray** (#30363d): The thin boundary separating card sections or outer frames.

**The Singularity Rule.** Only the active progress bar and current remaining count may use the Active Gold accent. All other labels and metadata must remain in Neutral colors.

## 3. Typography

**Display Font:** Inter (fallback: system-ui, sans-serif)  
**Body Font:** Inter (fallback: system-ui, sans-serif)

The widget uses a single geometric sans-serif typeface to maintain a clean technical character.

### Hierarchy
- **Display** (600, 1.25rem, 1.2): Large remaining quota count (e.g. "350").
- **Body** (400, 0.875rem, 1.4): Labels and supporting metadata (e.g. "/ 500").
- **Label** (500, 0.75rem, 1.1, uppercase): Tiny metadata tags like "UPDATED 3M AGO" or "OFFLINE".

**The Clean Line Rule.** All text elements must use uppercase for small labels under 10px to ensure maximum crispness and block-alignment at small sizes.

## 4. Elevation

The widget uses flat tonal layering and borders rather than drop shadows. Because it is reparented directly to the desktop wallpaper level (WorkerW), traditional drop shadows can look disconnected or float awkwardly on top of desktop icons.

**The Flat-Surface Rule.** No drop shadows are allowed on the widget container. Separation from the wallpaper is achieved strictly via the thin Border Gray outline and the solid GitHub Dark background.

## 5. Components

### Widget Container
- **Shape:** Rounded corners (8px)
- **Background:** GitHub Dark Canvas (#0d1117)
- **Border:** 1px solid Border Gray (#30363d)
- **Padding:** 12px internally

### Progress Bar
- **Track:** 1px solid Border Gray (#30363d) or dark neutral container.
- **Indicator:** Active Gold (#f0883e) fill with a smooth 300ms CSS width transition.
- **Thickness:** Thin strip (4px).

## 6. Do's and Don'ts

### Do:
- **Do** desaturate the widget (opacity 0.6, colors shifted to grayscale) when the application cache is outdated or offline.
- **Do** keep the widget size fixed to 150x80px to fit standard desktop grid spacings.
- **Do** align the progress bar flush with the bottom or side of the layout.

### Don't:
- **Don't** use any card drop shadows or container blurs (glassmorphism).
- **Don't** use neon green or neon blue gradient highlights.
- **Don't** allow mouse clicks or hover triggers on the widget itself while on the desktop (keep click-through enabled).
