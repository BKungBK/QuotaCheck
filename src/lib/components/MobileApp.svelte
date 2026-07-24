<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { flip } from 'svelte/animate';
  import PoolRow from '$lib/components/PoolRow.svelte';
  import SkeletonRow from '$lib/components/SkeletonRow.svelte';
  import { formatEmail } from '$lib/quota-utils';
  import type { Cache, Config } from '$lib/types';
  import { quotaStore } from '$lib/quota-store.svelte';

  const s = quotaStore;

  // ── Mobile-only: try Kotlin plugin cache first, fall back to Rust ──────────
  async function loadQuotaData() {
    // 1. Try Android Kotlin Plugin cache first
    try {
      const res = await invoke<{ cache: string }>('plugin:quota|getQuotaCache');
      if (res && res.cache) {
        const parsed = JSON.parse(res.cache);
        if (parsed.pools && parsed.pools.length > 0) {
          s.applyCache({ ...parsed, source: 'cloud' });
          s.isLoading = false;
          return;
        }
      }
    } catch (_e) {
      // Not on Android or plugin not available
    }

    // 2. Fallback to get_current_quota (Rust backend)
    try {
      const cache = await invoke<Cache>('get_current_quota');
      s.applyCache(cache);
    } catch (e) {
      console.error('Failed to load initial cache', e);
    } finally {
      s.isLoading = false;
    }
  }

  // ── Mobile-only: try Android plugin sync first ─────────────────────────────
  async function handleRefresh() {
    s.isRefreshing = true;
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
      s.isRefreshing = false;
    }, 1500);
  }

  // ── Mobile-only: save token via Kotlin plugin ──────────────────────────────
  async function handleSaveToken() {
    if (!s.tokenInput.trim()) return;
    s.tokenSaveStatus = 'Saving token...';
    try {
      await invoke('plugin:quota|saveRefreshToken', { token: s.tokenInput.trim() });
      s.tokenSaveStatus = 'Token saved! Syncing...';
      setTimeout(async () => {
        await handleRefresh();
        s.showTokenInput = false;
        s.tokenSaveStatus = '';
      }, 1000);
    } catch (_e) {
      s.tokenSaveStatus = 'Saved to config';
    }
  }

  onMount(() => {
    const refs: { quota?: () => void; refresh?: () => void; config?: () => void } = {};

    const init = async () => {
      try {
        const cfg = await invoke<Config>('get_config');
        s.maskAccountEmail = cfg.mask_account_email ?? false;
        if (cfg.refresh_token_override) {
          s.tokenInput = cfg.refresh_token_override;
        }
      } catch (e) {
        console.error('Failed to load config in page', e);
      }

      await loadQuotaData();
      await s.setupListeners(refs);
    };

    init();

    return () => {
      if (refs.quota) refs.quota();
      if (refs.refresh) refs.refresh();
      if (refs.config) refs.config();
    };
  });
</script>

<main
  class="widget"
  class:offline={s.isOffline && s.pools.length === 0}
  id="mobile-quota-widget"
  aria-label="Antigravity Quota Mobile App"
>
  <div class="row-top">
    <div class="header-left">
      <span class="label">BK</span>
      <span class="sub-title">Antigravity Quota</span>
    </div>
    
    <div class="header-right">
      <button class="btn-icon" onclick={handleRefresh} title="Refresh Quota" disabled={s.isRefreshing}>
        <svg class="refresh-icon" class:spinning={s.isRefreshing} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
        </svg>
      </button>

      <a href="/settings" class="btn-icon" title="Settings">
        <svg class="refresh-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </a>

      <div class="live-badge" role="status" aria-live="polite" title={s.statusTooltip}>
        <span
          class="dot"
          class:dot-live={!s.isOffline && !s.isStale}
          class:dot-stale={s.isStale}
          aria-hidden="true"
        ></span>
        {s.statusLabel}
      </div>
    </div>
  </div>

  <div class="pools-container">
    {#if s.isLoading}
      <SkeletonRow />
      <SkeletonRow short />
    {:else}
      {#each s.pools as pool (pool.label)}
        <div animate:flip={{ duration: 300 }}>
          <PoolRow {pool} isOffline={s.isOffline} poolsLength={s.pools.length} />
        </div>
      {:else}
        <div class="no-pools">
          <div class="offline-box">
            <span class="placeholder-text" title={s.statusTooltip}>
              {s.errorReason === 'process_not_found' ? 'Process Not Found' : s.isOffline ? 'Offline Mode' : 'No Quota Data'}
            </span>
            <p class="offline-desc">
              {#if s.isOffline}
                Set an OAuth Refresh Token to sync Quota directly on mobile.
              {/if}
            </p>

            <button class="btn-setup" onclick={() => s.showTokenInput = !s.showTokenInput}>
              {s.showTokenInput ? 'Close Setup' : '⚙️ Setup Refresh Token'}
            </button>

            {#if s.showTokenInput}
              <div class="token-form">
                <input
                  type="password"
                  placeholder="Paste OAuth Refresh Token..."
                  bind:value={s.tokenInput}
                />
                <button class="btn-save" onclick={handleSaveToken}>Save & Sync</button>
                {#if s.tokenSaveStatus}
                  <span class="save-status">{s.tokenSaveStatus}</span>
                {/if}
              </div>
            {/if}
          </div>
        </div>
      {/each}
    {/if}
  </div>

  <div class="row-bottom">
    <span class="meta">
      {s.isOffline && s.pools.length === 0 ? 'Offline' : s.source === 'local' ? 'Local 🟢' : s.accountEmail ? `Cloud ☁️ • ${formatEmail(s.accountEmail, s.maskAccountEmail)}` : 'Cloud ☁️'}
    </span>
    <span class="meta">{s.timeAgo}</span>
  </div>
</main>

<style>
  /* ── Mobile Design Tokens ── */
  :root {
    --color-bg:         oklch(14% 0 0 / 0.95);
    --color-surface:    oklch(20% 0 0 / 0.9);
    --color-border:     oklch(28% 0 0 / 0.6);
    --color-separator:  oklch(22% 0 0 / 0.5);

    --color-shimmer-base:      oklch(18% 0 0 / 0.5);
    --color-shimmer-highlight: oklch(25% 0 0 / 0.5);

    --color-ink:        oklch(88% 0 0);
    --color-ink-high:   oklch(96% 0 0);
    --color-ink-mid:    oklch(70% 0 0);
    --color-ink-muted:  oklch(60% 0 0);
    --color-ink-dim:    oklch(52% 0 0);

    --color-dot-offline: oklch(45% 0 0);
    --color-dot-stale:   oklch(65% 0.15 80);

    --color-accent:      oklch(62% 0.16 230);
    --color-bar-track:   oklch(22% 0 0 / 0.8);
    --color-bar-offline: oklch(38% 0 0);
    --color-bar-low:     oklch(62% 0.22 25);

    --color-dot-live:     oklch(75% 0.18 145);
    --color-dot-live-glow: oklch(75% 0.18 145 / 0.4);
  }

  :global(html, body) {
    margin: 0;
    padding: 0;
    background: oklch(10% 0 0) !important;
    overflow: hidden;
    height: 100%;
  }

  @keyframes pulseDot {
    0%, 100% { opacity: 1;   box-shadow: 0 0 0 0   var(--color-dot-live-glow); }
    50%       { opacity: 0.8; box-shadow: 0 0 0 3px var(--color-dot-live-glow); }
  }
  @keyframes spin {
    0%   { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  .spinning { animation: spin 1s linear infinite; }

  .widget {
    width: 100vw;
    height: 100vh;
    box-sizing: border-box;
    padding: 16px;
    background: var(--color-bg);
    font-family: "Inter", system-ui, -apple-system, sans-serif;
    color: var(--color-ink);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    user-select: none;
    pointer-events: auto;
  }

  .row-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .header-left { display: flex; align-items: center; gap: 8px; }
  .header-right { display: flex; align-items: center; gap: 10px; }

  .label {
    font-size: 0.875rem;
    font-weight: 700;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: var(--color-ink-high);
    margin: 0;
    line-height: 1;
    background: oklch(25% 0 0);
    padding: 4px 8px;
    border-radius: 4px;
  }

  .sub-title {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--color-ink-mid);
  }

  .btn-icon {
    background: none;
    border: none;
    color: var(--color-ink-mid);
    padding: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 4px;
    text-decoration: none;
    transition: background 0.2s;
  }
  .btn-icon:hover {
    background: oklch(25% 0 0);
    color: var(--color-ink-high);
  }

  .refresh-icon { width: 18px; height: 18px; }

  .live-badge {
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 0.6875rem;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--color-ink-mid);
    text-transform: uppercase;
  }
  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-dot-offline);
    display: inline-block;
    flex-shrink: 0;
  }
  .dot-live  { background: var(--color-dot-live); animation: pulseDot 2.4s ease-in-out infinite; }
  .dot-stale { background: var(--color-dot-stale); }

  .pools-container {
    display: flex;
    flex-direction: column;
    gap: 12px;
    flex-grow: 1;
    justify-content: flex-start;
    margin: 12px 0;
    overflow-y: auto;
  }

  .no-pools {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-grow: 1;
    padding: 20px 0;
  }

  .offline-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 10px;
    max-width: 320px;
    padding: 20px;
    background: oklch(18% 0 0);
    border: 1px solid oklch(26% 0 0);
    border-radius: 12px;
  }

  .placeholder-text {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--color-ink-high);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .offline-desc {
    margin: 0;
    font-size: 0.75rem;
    color: var(--color-ink-muted);
    line-height: 1.4;
  }

  .btn-setup {
    padding: 8px 14px;
    background: oklch(26% 0 0);
    border: 1px solid oklch(34% 0 0);
    color: var(--color-ink-high);
    font-size: 0.75rem;
    font-weight: 600;
    border-radius: 6px;
    cursor: pointer;
  }

  .token-form {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
    margin-top: 6px;
  }

  .token-form input {
    padding: 8px 10px;
    border-radius: 6px;
    border: 1px solid oklch(30% 0 0);
    background: oklch(12% 0 0);
    color: #fff;
    font-size: 0.75rem;
  }

  .btn-save {
    padding: 8px;
    background: oklch(48% 0.16 230);
    color: #fff;
    border: none;
    border-radius: 6px;
    font-size: 0.75rem;
    font-weight: 600;
    cursor: pointer;
  }

  .save-status {
    font-size: 0.6875rem;
    color: oklch(75% 0.15 140);
  }

  .row-bottom {
    display: flex;
    justify-content: space-between;
    padding-top: 8px;
    border-top: 1px solid oklch(20% 0 0);
  }

  .meta {
    font-size: 0.6875rem;
    font-weight: 500;
    color: var(--color-ink-dim);
  }
</style>
