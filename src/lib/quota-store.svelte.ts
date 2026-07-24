// src/lib/quota-store.svelte.ts
// Shared Svelte 5 runes reactive state — singleton module for quota data.
// Uses a class-based store pattern so components can mutate state via method calls
// and direct property assignment on the store object.
//
// Safe to use as a module-level singleton because DesktopWidget and MobileApp
// are never mounted simultaneously (runtime UA switching in +page.svelte).

import { listen } from '@tauri-apps/api/event';
import type { Cache, Config, QuotaPool } from '$lib/types';

class QuotaStore {
  // ── Mutable state ─────────────────────────────────────────────────────────
  pools = $state<QuotaPool[]>([]);
  isOffline = $state(true);
  errorReason = $state<string | undefined>(undefined);
  lastUpdated = $state('');
  source = $state('');
  accountEmail = $state<string | undefined>(undefined);
  maskAccountEmail = $state(false);
  isLoading = $state(true);
  isRefreshing = $state(false);
  tokenInput = $state('');
  showTokenInput = $state(false);
  tokenSaveStatus = $state('');

  // ── Now ticker ─────────────────────────────────────────────────────────────
  now = $state(Date.now());

  constructor() {
    $effect.root(() => {
      const interval = setInterval(() => {
        this.now = Date.now();
      }, 5000);
      return () => clearInterval(interval);
    });
  }

  // ── Derived values ──────────────────────────────────────────────────────────

  /** True when data has not been updated in more than 10 minutes */
  isStale = $derived.by(() => {
    if (!this.lastUpdated) return false;
    const diffSecs = Math.floor((this.now - new Date(this.lastUpdated).getTime()) / 1000);
    return diffSecs > 600;
  });

  /** Short status text for the live badge */
  statusLabel = $derived.by(() => {
    if (this.isLoading || this.isRefreshing) return 'syncing...';
    if (this.isOffline) {
      if (this.errorReason === 'process_not_found') return 'process not found';
      return 'offline';
    }
    if (this.isStale) return 'stale';
    return 'live';
  });

  /** Full tooltip description for the status badge */
  statusTooltip = $derived.by(() => {
    if (this.isLoading || this.isRefreshing) return 'Fetching latest quota data...';
    if (this.isOffline) {
      if (this.errorReason === 'process_not_found')
        return 'Antigravity IDE or CLI process is not running';
      return 'Unable to connect to local/cloud quota endpoint';
    }
    if (this.isStale) return 'Quota data has not updated in over 10 minutes';
    return 'Connected to quota service';
  });

  /** Human-readable "time ago" string for lastUpdated */
  timeAgo = $derived.by(() => {
    if (!this.lastUpdated) return 'Never';
    const diffSecs = Math.floor((this.now - new Date(this.lastUpdated).getTime()) / 1000);
    if (diffSecs < 10) return 'Now';
    if (diffSecs < 60) return `${diffSecs}s ago`;
    const mins = Math.floor(diffSecs / 60);
    return mins < 60 ? `${mins}m ago` : `${Math.floor(mins / 60)}h ago`;
  });

  // ── Methods ─────────────────────────────────────────────────────────────────

  /**
   * Apply a Cache payload to the store state.
   * Called from initial load and from the quota-update Tauri event.
   */
  applyCache(payload: Cache): void {
    this.pools = payload.pools || [];
    this.isOffline = payload.is_offline;
    this.errorReason = payload.error_reason;
    this.lastUpdated = payload.last_updated;
    this.source = payload.source;
    this.accountEmail = payload.account_email;
    this.isLoading = false;
    this.isRefreshing = false;
  }

  /**
   * Register Tauri event listeners and store their unlisten callbacks in `refs`.
   * Call the ref functions in the component's onMount cleanup to prevent leaks.
   */
  async setupListeners(refs: {
    quota?: () => void;
    refresh?: () => void;
    config?: () => void;
  }): Promise<void> {
    try {
      refs.quota = await listen<Cache>('quota-update', (event) => {
        this.applyCache(event.payload);
      });

      refs.config = await listen<Config>('config-updated', (event) => {
        this.maskAccountEmail = event.payload.mask_account_email ?? false;
      });

      refs.refresh = await listen('refresh-started', () => {
        this.isRefreshing = true;
      });
    } catch (_e) {
      // Non-Tauri environment (e.g. browser dev preview) — silently ignore
    }
  }
}

/** Singleton store instance shared across all components */
export const quotaStore = new QuotaStore();
