# Product

## Register

product

## Users
Developers using VS Code / Antigravity IDE on Windows who want to track their remaining quota without breaking their flow. They need a glanceable, desktop-native summary that is visible only when they look at the desktop.

## Product Purpose
A lightweight desktop widget that monitors and displays the remaining Antigravity quota. It resides under desktop icons (WorkerW layer) and runs transparently and click-through to serve as part of the system wallpaper.

## Brand Personality
Sleek, integrated, professional. It uses a clean dark gray palette matching GitHub/Antigravity IDE's aesthetic, conveying developer-focused utility and modern craftsmanship.

## Anti-references
- Oversaturated, flashy, or neon desktop widgets that distract the developer.
- Widgets that block mouse clicks or overlap active windows (avoid always-on-top behavior).
- Traditional clunky widget containers with heavy borders or shadows.

## Design Principles
- **Wallpaper Integration**: The UI must look like it is printed directly on the desktop wallpaper, blending seamlessly.
- **Glanceable Status**: High-contrast text and a simple progress bar allow checking status in a fraction of a second.
- **Zero Friction**: The widget is click-through and doesn't steal focus or capture user clicks.
- **Performance First**: The polling interval changes dynamically to minimize API requests and battery drain.

## Accessibility & Inclusion
High-contrast elements to remain readable across diverse wallpaper patterns. No strict requirement for reduced motion, but animations must remain non-distracting.
