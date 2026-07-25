<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { flip } from 'svelte/animate';
  import PoolRow from '$lib/components/PoolRow.svelte';
  import SkeletonRow from '$lib/components/SkeletonRow.svelte';
  import { formatEmail } from '$lib/quota-utils';
  import type { Cache, Config } from '$lib/types';
  import { quotaStore } from '$lib/quota-store.svelte';

  // Shorthand destructure — reactive because quotaStore fields are $state/$derived
  const s = quotaStore;

  // ── Desktop-only: load from Rust backend ──────────────────────────────────
  async function loadQuotaData() {
    try {
      const cache = await invoke<Cache>('get_current_quota');
      s.applyCache(cache);
    } catch (e) {
      console.error('Failed to load initial cache', e);
    } finally {
      s.isLoading = false;
    }
  }

  // ── Desktop-only: refresh via Rust command ─────────────────────────────────
  async function handleRefresh() {
    s.isRefreshing = true;
    try {
      await invoke('manual_refresh_trigger');
    } catch (err) {
      console.error('Refresh failed', err);
    }
    setTimeout(async () => {
      await loadQuotaData();
      s.isRefreshing = false;
    }, 1500);
  }

  // ── Desktop-only: save token via Rust config ───────────────────────────────
  async function handleSaveToken() {
    if (!s.tokenInput.trim()) return;
    s.tokenSaveStatus = 'Saving token...';
    try {
      const cfg = await invoke<Config>('get_config');
      cfg.refresh_token_override = s.tokenInput.trim();
      await invoke('save_config', { newConfig: cfg });
      s.tokenSaveStatus = 'Saved to config! Syncing...';
      setTimeout(async () => {
        await handleRefresh();
        s.showTokenInput = false;
        s.tokenSaveStatus = '';
      }, 1000);
    } catch (e) {
      s.tokenSaveStatus = `Error: ${e}`;
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
  id="desktop-widget-root"
  aria-label="Antigravity Quota Widget"
>
  <div class="row-top">
    <span class="label" id="widget-title" aria-hidden="true">BK</span>
    
    <div class="live-badge" role="status" aria-live="polite" id="widget-status" title={s.statusTooltip}>
      <span
        class="dot"
        class:dot-live={!s.isOffline && !s.isStale}
        class:dot-stale={s.isStale}
        id="widget-status-dot"
        aria-hidden="true"
      ></span>
      {s.statusLabel}
    </div>
  </div>

  <div class="pools-container" id="quota-pools-list">
    {#if s.isLoading}
      <SkeletonRow variant="desktop" />
      <SkeletonRow variant="desktop" short />
    {:else}
      {#each s.pools as pool (pool.label)}
        <div animate:flip={{ duration: 300 }}>
          <PoolRow {pool} isOffline={s.isOffline} poolsLength={s.pools.length} variant="desktop" />
        </div>
      {:else}
        <div class="no-pools" id="no-pools-placeholder">
          <div class="offline-box">
            <span class="placeholder-text" title={s.statusTooltip}>
              {s.errorReason === 'process_not_found' ? 'Process Not Found' : s.isOffline ? 'Offline Mode' : 'No Quota Data'}
            </span>
            <p class="offline-desc">
              {#if s.isOffline}
                Connect your account or set an OAuth Refresh Token to sync Quota directly.
              {/if}
            </p>

            <button class="btn-setup" onclick={() => s.showTokenInput = !s.showTokenInput}>
              {s.showTokenInput ? 'Close Setup' : 'Setup Refresh Token'}
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
    <span class="meta" id="quota-source">
      {s.isOffline && s.pools.length === 0 ? 'Offline' : s.source === 'local' ? 'Local' : s.accountEmail ? `Cloud • ${formatEmail(s.accountEmail, s.maskAccountEmail)}` : 'Cloud'}
    </span>
    <span class="meta" id="quota-time-ago">{s.timeAgo}</span>
  </div>
</main>

<style>
  /* ── Design Tokens ── */
  :root {
    /* Surfaces */
    --color-bg:         oklch(15% 0 0 / 0.65);
    --color-surface:    oklch(20% 0 0 / 0.8);
    --color-border:     oklch(25% 0 0 / 0.4);
    --color-separator:  oklch(20% 0 0 / 0.4);

    /* Skeleton shimmer layers */
    --color-shimmer-base:     oklch(18% 0 0 / 0.5);
    --color-shimmer-highlight: oklch(25% 0 0 / 0.5);

    /* Ink scale */
    --color-ink:        oklch(85% 0 0);
    --color-ink-high:   oklch(90% 0 0);   
    --color-ink-mid:    oklch(65% 0 0);   
    --color-ink-muted:  oklch(55% 0 0);   
    --color-ink-dim:    oklch(50% 0 0);   
    --color-ink-subtle: oklch(45% 0 0);   

    /* Status dot */
    --color-dot-offline: oklch(42% 0 0);
    --color-dot-stale:   oklch(65% 0.15 80);

    /* Accent */
    --color-accent:      oklch(48% 0 0);
    --color-accent-glow: oklch(48% 0 0 / 0.5);

    /* Bar colors */
    --color-bar-track:   oklch(20% 0 0 / 0.5);
    --color-bar-offline: oklch(36% 0 0);
    --color-bar-low:     oklch(42% 0 0);

    /* Live dot color */
    --color-dot-live:    oklch(75% 0 0);
    --color-dot-live-glow: oklch(75% 0 0 / 0.4);
  }

  :global(html:has(#desktop-widget-root), body:has(#desktop-widget-root)) {
    margin: 0;
    padding: 0;
    background: transparent !important;
    overflow: hidden;
  }

  @keyframes pulseDot {
    0%, 100% { opacity: 1;   box-shadow: 0 0 0 0   var(--color-dot-live-glow); }
    50%       { opacity: 0.8; box-shadow: 0 0 0 3px oklch(68% 0.17 160 / 0); }
  }


  .widget {
    width: 100vw;
    height: 100vh;
    box-sizing: border-box;
    padding: 8px 12px;
    background: var(--color-bg);
    border: 1px solid var(--color-border);
    border-radius: 8px;
    font-family: "Inter", system-ui, sans-serif;
    color: var(--color-ink);
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    user-select: none;
    pointer-events: none;
    transition: opacity 300ms ease, filter 300ms ease;
  }

  .widget.offline {
    opacity: 0.55;
    filter: grayscale(1);
  }

  .row-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .label {
    font-size: 0.6875rem;
    font-weight: 600;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--color-ink-high);
    margin: 0;
    line-height: 1;
    background: none;
    padding: 0;
  }

  .live-badge {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 0.5625rem;
    font-weight: 500;
    letter-spacing: 0.04em;
    color: var(--color-ink-mid);
    cursor: help;
  }

  .dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--color-dot-offline);
    display: inline-block;
    flex-shrink: 0;
    will-change: opacity, box-shadow;
  }
  .dot-live { background: var(--color-dot-live); animation: pulseDot 2.4s ease-in-out infinite; }
  .dot-stale { background: var(--color-dot-stale); }

  .pools-container {
    display: flex;
    flex-direction: column;
    gap: 0;
    flex-grow: 1;
    justify-content: flex-start;
    margin: 6px 0;
    overflow-y: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--color-ink-dim) transparent;
  }
  .pools-container::-webkit-scrollbar {
    width: 3px;
  }
  .pools-container::-webkit-scrollbar-thumb {
    background: var(--color-ink-dim);
    border-radius: 3px;
  }

  .no-pools {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-grow: 1;
    padding: 10px 0;
  }

  .offline-box {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 8px;
    max-width: 280px;
    padding: 12px;
    background: oklch(18% 0 0);
    border: 1px solid oklch(26% 0 0);
    border-radius: 8px;
    pointer-events: auto;
  }

  .placeholder-text {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-ink-high);
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }

  .offline-desc {
    margin: 0;
    font-size: 0.6875rem;
    color: var(--color-ink-muted);
    line-height: 1.3;
  }

  .btn-setup {
    padding: 6px 10px;
    background: oklch(26% 0 0);
    border: 1px solid oklch(34% 0 0);
    color: var(--color-ink-high);
    font-size: 0.6875rem;
    font-weight: 600;
    border-radius: 4px;
    cursor: pointer;
  }

  .token-form {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
    margin-top: 4px;
  }

  .token-form input {
    padding: 6px 8px;
    border-radius: 4px;
    border: 1px solid oklch(30% 0 0);
    background: oklch(12% 0 0);
    color: #fff;
    font-size: 0.6875rem;
  }

  .btn-save {
    padding: 6px;
    background: oklch(48% 0.16 230);
    color: #fff;
    border: none;
    border-radius: 4px;
    font-size: 0.6875rem;
    font-weight: 600;
    cursor: pointer;
  }

  .save-status {
    font-size: 0.625rem;
    color: oklch(75% 0.15 140);
  }

  .row-bottom {
    display: flex;
    justify-content: space-between;
  }
  .meta {
    font-size: 0.5625rem;
    font-weight: 500;
    letter-spacing: 0.02em;
    color: var(--color-ink-dim);
  }
</style>
