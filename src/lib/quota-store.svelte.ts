// src/lib/quota-store.svelte.ts
// Shared Svelte 5 runes reactive state — singleton module for quota data.
// Safe to use as a module-level singleton because DesktopWidget and MobileApp
// are never mounted simultaneously (runtime UA switching in +page.svelte).

import { listen } from '@tauri-apps/api/event';
import type { Cache, Config, QuotaPool } from '$lib/types';

// ── Mutable state ─────────────────────────────────────────────────────────────
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

// ── Now ticker (updates every 5s for timeAgo + isStale recalculations) ────────
export let now = $state(Date.now());

$effect.root(() => {
  const interval = setInterval(() => {
    now = Date.now();
  }, 5000);
  return () => clearInterval(interval);
});

// ── Derived values ────────────────────────────────────────────────────────────

/** True when data has not been updated in more than 10 minutes */
export const isStale = $derived.by(() => {
  if (!lastUpdated) return false;
  const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
  return diffSecs > 600;
});

/** Short status text for the live badge */
export const statusLabel = $derived.by(() => {
  if (isLoading || isRefreshing) return 'syncing...';
  if (isOffline) {
    if (errorReason === 'process_not_found') return 'process not found';
    return 'offline';
  }
  if (isStale) return 'stale';
  return 'live';
});

/** Full tooltip description for the status badge */
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

/** Human-readable "time ago" string for lastUpdated */
export const timeAgo = $derived.by(() => {
  if (!lastUpdated) return 'Never';
  const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
  if (diffSecs < 10) return 'Now';
  if (diffSecs < 60) return `${diffSecs}s ago`;
  const mins = Math.floor(diffSecs / 60);
  return mins < 60 ? `${mins}m ago` : `${Math.floor(mins / 60)}h ago`;
});

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Apply a Cache payload to the shared store state.
 * Called both from the initial load and from the quota-update Tauri event.
 */
export function applyCache(payload: Cache): void {
  pools = payload.pools || [];
  isOffline = payload.is_offline;
  errorReason = payload.error_reason;
  lastUpdated = payload.last_updated;
  source = payload.source;
  accountEmail = payload.account_email;
  isLoading = false;
  isRefreshing = false;
}

/**
 * Register Tauri event listeners and store their unlisten callbacks in `refs`.
 * Call the unlisten callbacks in the component's onDestroy / onMount cleanup.
 *
 * @param refs - Object to store unlisten functions; callers pass an empty `{}`.
 */
export async function setupListeners(refs: {
  quota?: () => void;
  refresh?: () => void;
  config?: () => void;
}): Promise<void> {
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
    // Non-Tauri environment (e.g. browser dev preview) — silently ignore
  }
}
