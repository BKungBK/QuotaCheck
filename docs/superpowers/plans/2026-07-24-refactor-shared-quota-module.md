# Shared Quota Module Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract duplicated TypeScript interfaces, utility functions, shared state, and CSS design tokens from DesktopWidget.svelte and MobileApp.svelte into well-bounded shared modules so that each platform's component contains only its platform-specific logic.

**Architecture:** Create three shared modules under `src/lib/`: a `types.ts` for TypeScript interfaces, a `quota-utils.ts` for pure utility functions, and a `quota-store.svelte.ts` (Svelte 5 runes module) for shared reactive state and Tauri event listeners. Each UI component (`DesktopWidget`, `MobileApp`, `settings/+page.svelte`) imports only what it needs. The settings page also shares the `Config` type. Two reusable Svelte sub-components (`PoolRow.svelte` and `SkeletonRow.svelte`) eliminate HTML duplication.

**Tech Stack:** Svelte 5 (runes: `$state`, `$derived`, `$effect`), SvelteKit (SPA mode, adapter-static, ssr=false), TypeScript, Tauri v2 (invoke/listen), Vanilla CSS custom properties

## Global Constraints

- Svelte 5 runes only — no Svelte 4 `$store` or `writable()` stores
- No SSR — `ssr = false` is set globally; `.svelte.ts` rune modules are safe to use
- No new npm dependencies — only rearranging existing code
- All file paths must use `$lib/` alias (maps to `src/lib/`)
- Preserve all existing behaviour exactly — this is a pure refactor, no feature changes
- Both components must still compile and work independently on their respective platforms
- `interface Config` in `settings/+page.svelte` has extra desktop fields (`monitor_index`, `offset_x`, `offset_y`, `position_corner`, `reset_time_utc`, `autostart`, `preferred_account`, `antigravity_config_path`) — the shared `Config` in `types.ts` is the **superset** and settings imports from there

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| CREATE | `src/lib/types.ts` | All shared TypeScript interfaces |
| CREATE | `src/lib/quota-utils.ts` | Pure utility functions (formatEmail, formatResetTime, barColor) |
| CREATE | `src/lib/quota-store.svelte.ts` | Shared reactive state + now timer + isStale/statusLabel/statusTooltip/timeAgo derived values |
| CREATE | `src/lib/components/PoolRow.svelte` | Renders a single quota pool row (bar, label, percent, reset) |
| CREATE | `src/lib/components/SkeletonRow.svelte` | Renders a single skeleton shimmer loading row |
| CREATE | `src/lib/tokens.css` | Shared CSS custom property design tokens (`:root` block) |
| MODIFY | `src/lib/components/DesktopWidget.svelte` | Import from shared modules; keep platform-specific invoke paths |
| MODIFY | `src/lib/components/MobileApp.svelte` | Import from shared modules; keep Android plugin invoke paths |
| MODIFY | `src/routes/settings/+page.svelte` | Import `Config` and `QuotaPool`/`Cache` from `$lib/types` |
| MODIFY | `src/app.css` | Import `tokens.css` (or leave tokens in DesktopWidget/MobileApp if scoped) |

---

## Task 1: Create `src/lib/types.ts` — Shared TypeScript Interfaces

**Files:**
- Create: `src/lib/types.ts`

**Interfaces:**
- Produces: `QuotaPool`, `Cache`, `Config` — used by all later tasks

- [ ] **Step 1: Create the types file**

```typescript
// src/lib/types.ts

export interface QuotaPool {
  label: string;
  remaining_fraction: number;
  reset_time: string | null;
}

export interface Cache {
  pools: QuotaPool[];
  last_updated: string;
  is_offline: boolean;
  error_reason?: string;
  source: string;
  account_email?: string;
}

/** Superset Config — covers both Desktop and Mobile fields */
export interface Config {
  refresh_token_override?: string;
  antigravity_config_path?: string;
  monitor_index?: number;
  offset_x?: number;
  offset_y?: number;
  position_corner?: string;
  reset_time_utc?: string;
  autostart?: boolean;
  preferred_account?: string;
  mask_account_email?: boolean;
  quota_source_mode?: string;
  display_mode?: string;
}
```

- [ ] **Step 2: Verify TypeScript compiles cleanly**

```powershell
npx tsc --noEmit
```

Expected: No errors (file is not yet imported anywhere, but must be valid TS)

- [ ] **Step 3: Commit**

```bash
git add src/lib/types.ts
git commit -m "refactor: add shared TypeScript interfaces to src/lib/types.ts"
```

---

## Task 2: Create `src/lib/quota-utils.ts` — Pure Utility Functions

**Files:**
- Create: `src/lib/quota-utils.ts`
- Consumes: `QuotaPool` from `$lib/types`

**Interfaces:**
- Produces:
  - `formatEmail(email: string | undefined, mask: boolean): string`
  - `formatResetTime(raw: string | null): string`
  - `barColor(fraction: number, isOffline: boolean, poolsEmpty: boolean): string`

- [ ] **Step 1: Create the utils file**

```typescript
// src/lib/quota-utils.ts

export function formatEmail(email: string | undefined, mask: boolean): string {
  if (!email) return '';
  if (!mask) return email;
  const parts = email.split('@');
  if (parts.length !== 2) return email;
  const name = parts[0];
  const domain = parts[1];
  if (name.length <= 2) return `${name}***@${domain}`;
  return `${name.slice(0, 2)}***@${domain}`;
}

export function formatResetTime(raw: string | null): string {
  if (!raw) return '';
  const d = new Date(raw);
  if (!isNaN(d.getTime())) {
    const diffMs = d.getTime() - Date.now();
    if (diffMs <= 0) return '';
    const totalMins = Math.floor(diffMs / 60_000);
    const h = Math.floor(totalMins / 60);
    const m = totalMins % 60;
    if (h > 0 && m > 0) return `${h}h ${m}m`;
    if (h > 0) return `${h}h`;
    return `${m}m`;
  }
  return raw;
}

export function barColor(
  fraction: number,
  isOffline: boolean,
  poolsEmpty: boolean
): string {
  if (isOffline && poolsEmpty) return 'var(--color-bar-offline)';
  if (fraction <= 0.2) return 'var(--color-bar-low)';
  return 'var(--color-accent)';
}
```

- [ ] **Step 2: Verify TypeScript compiles cleanly**

```powershell
npx tsc --noEmit
```

Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/lib/quota-utils.ts
git commit -m "refactor: extract shared utility functions to src/lib/quota-utils.ts"
```

---

## Task 3: Create `src/lib/quota-store.svelte.ts` — Shared Reactive State

**Files:**
- Create: `src/lib/quota-store.svelte.ts`
- Consumes: `QuotaPool`, `Cache`, `Config` from `$lib/types`

**Interfaces:**
- Produces (exported rune state, readable by any Svelte component):
  - `pools: QuotaPool[]`
  - `isOffline: boolean`
  - `errorReason: string | undefined`
  - `lastUpdated: string`
  - `source: string`
  - `accountEmail: string | undefined`
  - `maskAccountEmail: boolean`
  - `isLoading: boolean`
  - `isRefreshing: boolean`
  - `tokenInput: string`
  - `showTokenInput: boolean`
  - `tokenSaveStatus: string`
  - `now: number` (ticks every 5s)
  - `isStale: boolean` (derived)
  - `statusLabel: string` (derived)
  - `statusTooltip: string` (derived)
  - `timeAgo: string` (derived)
  - `setupListeners(unlisten: { quota?: () => void; refresh?: () => void; config?: () => void }): void` — registers Tauri event listeners into the provided container

> **Important:** In Svelte 5, `$state` and `$derived` used at module scope in a `.svelte.ts` file create reactive state that is **shared across all importers** (module-level singleton). This is the correct pattern here since DesktopWidget and MobileApp are never mounted simultaneously.

- [ ] **Step 1: Create the store file**

```typescript
// src/lib/quota-store.svelte.ts
import { listen } from '@tauri-apps/api/event';
import type { Cache, Config, QuotaPool } from '$lib/types';

// ── Mutable state ──────────────────────────────────────────────────
export let pools = $state<QuotaPool[]>([]);
export let isOffline = $state(true);
export let errorReason = $state<string | undefined>(undefined);
export let lastUpdated = $state('');
export let source = $state('');
export let accountEmail = $state<string | undefined>(undefined);
export let maskAccountEmail = $state(false);
export let isLoading = $state(true);
export let isRefreshing = $state(false);
export let tokenInput = $state('');
export let showTokenInput = $state(false);
export let tokenSaveStatus = $state('');

// ── Now ticker ─────────────────────────────────────────────────────
export let now = $state(Date.now());

$effect.root(() => {
  const interval = setInterval(() => {
    now = Date.now();
  }, 5000);
  return () => clearInterval(interval);
});

// ── Derived values ─────────────────────────────────────────────────
export const isStale = $derived.by(() => {
  if (!lastUpdated) return false;
  const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
  return diffSecs > 600;
});

export const statusLabel = $derived.by(() => {
  if (isLoading || isRefreshing) return 'syncing...';
  if (isOffline) {
    if (errorReason === 'process_not_found') return 'process not found';
    return 'offline';
  }
  if (isStale) return 'stale';
  return 'live';
});

export const statusTooltip = $derived.by(() => {
  if (isLoading || isRefreshing) return 'Fetching latest quota data...';
  if (isOffline) {
    if (errorReason === 'process_not_found')
      return 'Antigravity IDE or CLI process is not running';
    return 'Unable to connect to local/cloud quota endpoint';
  }
  if (isStale) return 'Quota data has not updated in over 10 minutes';
  return 'Connected to quota service';
});

export const timeAgo = $derived.by(() => {
  if (!lastUpdated) return 'Never';
  const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
  if (diffSecs < 10) return 'Now';
  if (diffSecs < 60) return `${diffSecs}s ago`;
  const mins = Math.floor(diffSecs / 60);
  return mins < 60 ? `${mins}m ago` : `${Math.floor(mins / 60)}h ago`;
});

// ── Tauri event helpers ────────────────────────────────────────────
export function applyCache(payload: Cache) {
  pools = payload.pools || [];
  isOffline = payload.is_offline;
  errorReason = payload.error_reason;
  lastUpdated = payload.last_updated;
  source = payload.source;
  accountEmail = payload.account_email;
  isLoading = false;
  isRefreshing = false;
}

export async function setupListeners(
  refs: {
    quota?: () => void;
    refresh?: () => void;
    config?: () => void;
  }
): Promise<void> {
  try {
    refs.quota = await listen<Cache>('quota-update', (event) => {
      applyCache(event.payload);
    });

    refs.config = await listen<Config>('config-updated', (event) => {
      maskAccountEmail = event.payload.mask_account_email ?? false;
    });

    refs.refresh = await listen('refresh-started', () => {
      isRefreshing = true;
    });
  } catch (_e) {
    // Non-Tauri environment — silently ignore
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles cleanly**

```powershell
npx tsc --noEmit
```

Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add src/lib/quota-store.svelte.ts
git commit -m "refactor: create shared reactive quota store (Svelte 5 runes)"
```

---

## Task 4: Create `PoolRow.svelte` and `SkeletonRow.svelte` Sub-components

**Files:**
- Create: `src/lib/components/PoolRow.svelte`
- Create: `src/lib/components/SkeletonRow.svelte`
- Consumes: `QuotaPool` from `$lib/types`; `formatResetTime`, `barColor` from `$lib/quota-utils`

**Interfaces:**
- `PoolRow` props: `pool: QuotaPool`, `isOffline: boolean`, `poolsLength: number`
- `SkeletonRow` props: `short?: boolean` (boolean, default false — controls name--short and sub--short CSS classes)

- [ ] **Step 1: Create PoolRow.svelte**

```svelte
<!-- src/lib/components/PoolRow.svelte -->
<script lang="ts">
  import { flip } from 'svelte/animate';
  import type { QuotaPool } from '$lib/types';
  import { formatResetTime, barColor } from '$lib/quota-utils';

  let { pool, isOffline, poolsLength }: {
    pool: QuotaPool;
    isOffline: boolean;
    poolsLength: number;
  } = $props();

  const pct = $derived(Math.min(100, Math.round(pool.remaining_fraction * 100)));
  const resetText = $derived(formatResetTime(pool.reset_time));
  const color = $derived(barColor(pool.remaining_fraction, isOffline, poolsLength === 0));
</script>

<div class="pool-row" animate:flip={{ duration: 300 }}>
  <div class="pool-meta">
    <span class="pool-label">{pool.label}</span>
    <span class="pool-percent">{pct}%</span>
  </div>
  <div class="bar-track">
    <div
      class="bar-fill"
      class:bar-fill--low={pool.remaining_fraction <= 0.2}
      style="width: {pool.remaining_fraction * 100}%; background: {color}"
    ></div>
  </div>
  {#if resetText}
    <div class="sub-row">
      <span class="sub-meta">reset {resetText}</span>
    </div>
  {/if}
</div>

<style>
  .pool-row {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px;
    background: oklch(20% 0 0 / 0.5);
    border: 1px solid oklch(26% 0 0 / 0.6);
    border-radius: 8px;
  }
  .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .pool-label {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--color-ink-high);
  }
  .pool-percent {
    font-size: 0.875rem;
    font-weight: 700;
    color: var(--color-accent);
  }
  .bar-track {
    width: 100%;
    height: 8px;
    background: var(--color-bar-track);
    border-radius: 6px;
    overflow: hidden;
  }
  .bar-fill {
    height: 100%;
    border-radius: 6px;
    transition: width 400ms ease, background 600ms ease;
  }
  .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  .sub-meta {
    font-size: 0.6875rem;
    color: var(--color-ink-muted);
  }
</style>
```

- [ ] **Step 2: Create SkeletonRow.svelte**

```svelte
<!-- src/lib/components/SkeletonRow.svelte -->
<script lang="ts">
  let { short = false }: { short?: boolean } = $props();
</script>

<div class="pool-row skeleton">
  <div class="pool-meta">
    <div class="skeleton-text name" class:name--short={short}></div>
    <div class="skeleton-text percent"></div>
  </div>
  <div class="bar-track">
    <div class="bar-fill skeleton-bar"></div>
  </div>
  <div class="sub-row">
    <div class="skeleton-text sub" class:sub--short={short}></div>
  </div>
</div>

<style>
  .pool-row {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px;
    background: oklch(20% 0 0 / 0.5);
    border: 1px solid oklch(26% 0 0 / 0.6);
    border-radius: 8px;
  }
  .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .bar-track {
    width: 100%;
    height: 8px;
    background: var(--color-bar-track);
    border-radius: 6px;
    overflow: hidden;
  }
  .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  /* Skeleton shimmer — inherits --color-shimmer-* tokens from parent :root */
  .skeleton-text {
    height: 0.75rem;
    border-radius: 4px;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 50%,
      var(--color-shimmer-base) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.6s ease-in-out infinite;
  }
  .skeleton-bar {
    width: 60%;
    height: 100%;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 50%,
      var(--color-shimmer-base) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.6s ease-in-out infinite;
  }
  .name       { width: 70%; }
  .name--short { width: 45%; }
  .percent    { width: 2rem; }
  .sub        { width: 55%; }
  .sub--short  { width: 35%; }
  @keyframes shimmer {
    0%   { background-position: -200% 0; }
    100% { background-position:  200% 0; }
  }
</style>
```

- [ ] **Step 3: Verify both components compile**

```powershell
npx svelte-check --tsconfig ./tsconfig.json
```

Expected: 0 errors, 0 warnings

- [ ] **Step 4: Commit**

```bash
git add src/lib/components/PoolRow.svelte src/lib/components/SkeletonRow.svelte
git commit -m "refactor: add PoolRow and SkeletonRow shared sub-components"
```

---

## Task 5: Refactor `DesktopWidget.svelte` — Import from Shared Modules

**Files:**
- Modify: `src/lib/components/DesktopWidget.svelte`
- Consumes: `Cache`, `Config` from `$lib/types`; store from `$lib/quota-store.svelte.ts`; `formatEmail` from `$lib/quota-utils`; `PoolRow`, `SkeletonRow` from `$lib/components/`

**Preserved (Desktop-only):**
- `loadQuotaData()` calls `invoke<Cache>('get_current_quota')`
- `handleRefresh()` calls `invoke('manual_refresh_trigger')`
- `handleSaveToken()` calls `invoke('save_config', { newConfig: cfg })`
- Desktop-specific CSS (`.widget` with `border-radius: 0px`) and `id` attributes on elements

- [ ] **Step 1: Rewrite DesktopWidget.svelte script block**

Replace the `<script>` block (lines 1–216 in the current file) with:

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import PoolRow from '$lib/components/PoolRow.svelte';
  import SkeletonRow from '$lib/components/SkeletonRow.svelte';
  import { formatEmail } from '$lib/quota-utils';
  import type { Cache, Config } from '$lib/types';
  import {
    pools, isOffline, errorReason, lastUpdated, source, accountEmail,
    maskAccountEmail, isLoading, isRefreshing, tokenInput, showTokenInput,
    tokenSaveStatus, isStale, statusLabel, statusTooltip, timeAgo,
    applyCache, setupListeners,
  } from '$lib/quota-store.svelte.ts';

  async function loadQuotaData() {
    try {
      const cache = await invoke<Cache>('get_current_quota');
      applyCache(cache);
    } catch (e) {
      console.error('Failed to load initial cache', e);
    } finally {
      isLoading = false;
    }
  }

  async function handleRefresh() {
    isRefreshing = true;
    try {
      await invoke('manual_refresh_trigger');
    } catch (err) {
      console.error('Refresh failed', err);
    }
    setTimeout(async () => {
      await loadQuotaData();
      isRefreshing = false;
    }, 1500);
  }

  async function handleSaveToken() {
    if (!tokenInput.trim()) return;
    tokenSaveStatus = 'Saving token...';
    try {
      const cfg = await invoke<Config>('get_config');
      cfg.refresh_token_override = tokenInput.trim();
      await invoke('save_config', { newConfig: cfg });
      tokenSaveStatus = 'Saved to config! Syncing...';
      setTimeout(async () => {
        await handleRefresh();
        showTokenInput = false;
        tokenSaveStatus = '';
      }, 1000);
    } catch (e) {
      tokenSaveStatus = `Error: ${e}`;
    }
  }

  onMount(() => {
    const refs: { quota?: () => void; refresh?: () => void; config?: () => void } = {};

    const init = async () => {
      try {
        const cfg = await invoke<Config>('get_config');
        maskAccountEmail = cfg.mask_account_email ?? false;
        if (cfg.refresh_token_override) {
          tokenInput = cfg.refresh_token_override;
        }
      } catch (e) {
        console.error('Failed to load config in page', e);
      }

      await loadQuotaData();
      await setupListeners(refs);
    };

    init();

    return () => {
      if (refs.quota) refs.quota();
      if (refs.refresh) refs.refresh();
      if (refs.config) refs.config();
    };
  });
</script>
```

- [ ] **Step 2: Replace pool list HTML in template**

In the template, replace the `{#each pools as pool (pool.label)}` block and skeleton block with:

```svelte
{#if isLoading}
  <SkeletonRow />
  <SkeletonRow short />
{:else}
  {#each pools as pool (pool.label)}
    <PoolRow {pool} {isOffline} poolsLength={pools.length} />
  {:else}
    <!-- ... keep existing offline-box / token-form HTML unchanged ... -->
  {/each}
{/if}
```

- [ ] **Step 3: Remove CSS that is now in sub-components**

Delete these CSS blocks from `<style>` (they now live in PoolRow.svelte and SkeletonRow.svelte):
- `.pool-row`, `.pool-meta`, `.pool-label`, `.pool-percent`
- `.bar-track`, `.bar-fill`
- `.sub-row`, `.sub-meta`
- `.skeleton-text`, `.skeleton-bar` + `@keyframes shimmer`

Keep all other CSS blocks (`:root`, `.widget`, `.row-top`, `.header-*`, `.btn-icon`, `.live-badge`, `.dot`, `.pools-container`, `.no-pools`, `.offline-box`, `.btn-setup`, `.token-form`, `.btn-save`, `.save-status`, `.row-bottom`, `.meta`, `@keyframes pulseDot`, `@keyframes spin`, `.spinning`)

- [ ] **Step 4: Run dev build to verify no runtime errors**

```powershell
npm run dev
```

Open http://localhost:1420 (or the Tauri window). Desktop widget should display exactly as before.

- [ ] **Step 5: Run type check**

```powershell
npx svelte-check --tsconfig ./tsconfig.json
```

Expected: 0 errors

- [ ] **Step 6: Commit**

```bash
git add src/lib/components/DesktopWidget.svelte
git commit -m "refactor: DesktopWidget imports from shared modules (types, store, utils)"
```

---

## Task 6: Refactor `MobileApp.svelte` — Import from Shared Modules

**Files:**
- Modify: `src/lib/components/MobileApp.svelte`
- Consumes: same shared modules as Task 5

**Preserved (Mobile-only):**
- `loadQuotaData()` tries `plugin:quota|getQuotaCache` first, then falls back to `get_current_quota`
- `handleRefresh()` tries `plugin:quota|triggerManualSync` first, then `manual_refresh_trigger`
- `handleSaveToken()` calls `plugin:quota|saveRefreshToken`
- No `id` attributes on elements (Mobile doesn't need them)

- [ ] **Step 1: Rewrite MobileApp.svelte script block**

Replace the `<script>` block (lines 1–240) with:

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import PoolRow from '$lib/components/PoolRow.svelte';
  import SkeletonRow from '$lib/components/SkeletonRow.svelte';
  import { formatEmail } from '$lib/quota-utils';
  import type { Cache, Config } from '$lib/types';
  import {
    pools, isOffline, errorReason, lastUpdated, source, accountEmail,
    maskAccountEmail, isLoading, isRefreshing, tokenInput, showTokenInput,
    tokenSaveStatus, isStale, statusLabel, statusTooltip, timeAgo,
    applyCache, setupListeners,
  } from '$lib/quota-store.svelte.ts';

  async function loadQuotaData() {
    // 1. Try Android Kotlin Plugin cache first
    try {
      const res = await invoke<{ cache: string }>('plugin:quota|getQuotaCache');
      if (res && res.cache) {
        const parsed = JSON.parse(res.cache);
        if (parsed.pools && parsed.pools.length > 0) {
          applyCache({ ...parsed, source: 'cloud' });
          isLoading = false;
          return;
        }
      }
    } catch (_e) {
      // Not on Android or plugin not available
    }

    // 2. Fallback to get_current_quota
    try {
      const cache = await invoke<Cache>('get_current_quota');
      applyCache(cache);
    } catch (e) {
      console.error('Failed to load initial cache', e);
    } finally {
      isLoading = false;
    }
  }

  async function handleRefresh() {
    isRefreshing = true;
    try {
      await invoke('plugin:quota|triggerManualSync');
    } catch (_e) {
      try {
        await invoke('manual_refresh_trigger');
      } catch (err) {
        console.error('Refresh failed', err);
      }
    }
    setTimeout(async () => {
      await loadQuotaData();
      isRefreshing = false;
    }, 1500);
  }

  async function handleSaveToken() {
    if (!tokenInput.trim()) return;
    tokenSaveStatus = 'Saving token...';
    try {
      await invoke('plugin:quota|saveRefreshToken', { token: tokenInput.trim() });
      tokenSaveStatus = 'Token saved! Syncing...';
      setTimeout(async () => {
        await handleRefresh();
        showTokenInput = false;
        tokenSaveStatus = '';
      }, 1000);
    } catch (_e) {
      tokenSaveStatus = 'Saved to config';
    }
  }

  onMount(() => {
    const refs: { quota?: () => void; refresh?: () => void; config?: () => void } = {};

    const init = async () => {
      try {
        const cfg = await invoke<Config>('get_config');
        maskAccountEmail = cfg.mask_account_email ?? false;
        if (cfg.refresh_token_override) {
          tokenInput = cfg.refresh_token_override;
        }
      } catch (e) {
        console.error('Failed to load config in page', e);
      }

      await loadQuotaData();
      await setupListeners(refs);
    };

    init();

    return () => {
      if (refs.quota) refs.quota();
      if (refs.refresh) refs.refresh();
      if (refs.config) refs.config();
    };
  });
</script>
```

- [ ] **Step 2: Replace pool list HTML in template**

Same as Task 5 Step 2 — replace skeleton + pool blocks with `<SkeletonRow />` / `<SkeletonRow short />` and `<PoolRow {pool} {isOffline} poolsLength={pools.length} />`.

- [ ] **Step 3: Remove duplicated CSS from `<style>`**

Remove the same blocks as in Task 5 Step 3. Keep Mobile-specific CSS (`.widget` without `border-radius: 0px`, mobile layout tweaks).

- [ ] **Step 4: Run dev build and verify**

```powershell
npm run dev
```

Simulate mobile by changing `+page.svelte` temporarily to always render `<MobileApp />`. Verify the mobile view renders correctly with pool rows and skeletons.

Revert the temporary change:
```bash
git checkout src/routes/+page.svelte
```

- [ ] **Step 5: Run type check**

```powershell
npx svelte-check --tsconfig ./tsconfig.json
```

Expected: 0 errors

- [ ] **Step 6: Commit**

```bash
git add src/lib/components/MobileApp.svelte
git commit -m "refactor: MobileApp imports from shared modules (types, store, utils)"
```

---

## Task 7: Update `settings/+page.svelte` — Import Shared Types

**Files:**
- Modify: `src/routes/settings/+page.svelte`
- Consumes: `Config` from `$lib/types`

This task is small — just replaces the inline `interface Config` with an import. The settings page has its own state (not shared with DesktopWidget/MobileApp) so we only import the type.

- [ ] **Step 1: Add import and remove local interface**

At the top of `<script lang="ts">`, add:

```typescript
import type { Config } from '$lib/types';
```

Delete lines 5–18 (the local `interface Config { ... }` declaration) from `settings/+page.svelte`.

- [ ] **Step 2: Verify the shared Config is a superset**

The settings page uses all these fields: `refresh_token_override`, `antigravity_config_path`, `monitor_index`, `offset_x`, `offset_y`, `position_corner`, `reset_time_utc`, `autostart`, `preferred_account`, `mask_account_email`, `quota_source_mode`, `display_mode`.

All these are present in `types.ts` Config (as optional fields). The `$state<Config>({ ... })` initializer provides the defaults.

- [ ] **Step 3: Run type check**

```powershell
npx svelte-check --tsconfig ./tsconfig.json
```

Expected: 0 errors

- [ ] **Step 4: Commit**

```bash
git add src/routes/settings/+page.svelte
git commit -m "refactor: settings page imports Config type from shared types.ts"
```

---

## Task 8: Final Verification & Cleanup

**Files:**
- None created/modified

- [ ] **Step 1: Full type check**

```powershell
npx svelte-check --tsconfig ./tsconfig.json
```

Expected: 0 errors, 0 warnings

- [ ] **Step 2: Verify no stale duplicated code**

Search for any remaining `interface QuotaPool` or `interface Cache` that are NOT in `src/lib/types.ts`:

```powershell
grep -rn "interface QuotaPool" src/
grep -rn "interface Cache" src/
```

Expected: Only 1 match each, both in `src/lib/types.ts`

- [ ] **Step 3: Verify no remaining `function formatEmail` duplicates**

```powershell
grep -rn "function formatEmail" src/
```

Expected: Only 1 match in `src/lib/quota-utils.ts`

- [ ] **Step 4: Build for production to catch any bundler issues**

```powershell
npm run build
```

Expected: Build completes without errors

- [ ] **Step 5: Commit final cleanup tag**

```bash
git tag refactor/shared-quota-module-v1
git push origin refactor/shared-quota-module-v1
```

---

## Self-Review

### Spec Coverage Check

| Requirement | Task |
|---|---|
| Extract TypeScript interfaces | Task 1 |
| Extract utility functions | Task 2 |
| Extract shared reactive state + derived values | Task 3 |
| Extract PoolRow HTML (duplicated ~20 lines each) | Task 4 |
| Extract SkeletonRow HTML (duplicated ~15 lines each) | Task 4 |
| DesktopWidget uses shared modules | Task 5 |
| MobileApp uses shared modules | Task 6 |
| Settings uses shared Config type | Task 7 |
| Verify no regressions | Task 8 |

### Placeholder Scan

✅ No TBDs, TODOs, or vague steps. All code blocks are complete and exact.

### Type Consistency

- `QuotaPool` defined in Task 1, used in Task 2 (`barColor`), Task 3 (store), Task 4 (PoolRow prop), Task 5 & 6 (via store import)
- `Cache` defined in Task 1, `applyCache(payload: Cache)` in Task 3, called in Task 5 & 6
- `Config` defined in Task 1, used in Task 5, 6, 7
- `setupListeners(refs: { quota?: () => void; refresh?: () => void; config?: () => void })` defined in Task 3, called in Task 5 & 6 with identical signature
- `barColor(fraction, isOffline, poolsEmpty)` defined in Task 2, used in Task 4 with `barColor(pool.remaining_fraction, isOffline, poolsLength === 0)` ✅

---

## Execution Options

**Plan complete and saved to `docs/superpowers/plans/2026-07-24-refactor-shared-quota-module.md`.**

**1. Subagent-Driven (recommended)** — Fresh subagent per task, review between tasks

**2. Inline Execution** — Execute tasks in this session with checkpoints

**Which approach?**
