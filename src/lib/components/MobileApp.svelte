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

<!-- Native Android Mobile Container -->
<div class="mobile-app-shell">
  <!-- Material 3 Top App Bar -->
  <header class="top-app-bar">
    <div class="app-brand">
      <div class="brand-badge">BK</div>
      <div class="title-group">
        <h1 class="app-title">Quota Check</h1>
        <span class="app-subtitle">Antigravity AI</span>
      </div>
    </div>

    <div class="app-bar-actions">
      <button 
        class="icon-btn ripple-btn" 
        onclick={handleRefresh} 
        aria-label="Refresh Quota" 
        disabled={s.isRefreshing}
      >
        <svg class="nav-icon" class:spinning={s.isRefreshing} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
        </svg>
      </button>

      <a href="/settings" class="icon-btn ripple-btn" aria-label="Settings">
        <svg class="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </a>
    </div>
  </header>

  <!-- Scrollable Main App Canvas -->
  <main class="mobile-content">
    <!-- Hero Status Card (Material Overview) -->
    <section class="hero-card">
      <div class="hero-header">
        <div class="account-pill">
          <svg class="cloud-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/>
          </svg>
          <span class="account-email">
            {s.accountEmail ? formatEmail(s.accountEmail, s.maskAccountEmail) : s.source === 'local' ? 'Local Daemon' : 'Quota Cloud'}
          </span>
        </div>

        <div 
          class="status-chip" 
          class:status-chip--live={!s.isOffline && !s.isStale}
          class:status-chip--stale={s.isStale}
          class:status-chip--offline={s.isOffline}
          title={s.statusTooltip}
        >
          <span class="status-pulse-dot"></span>
          <span>{s.statusLabel}</span>
        </div>
      </div>

      <div class="hero-footer">
        <span class="last-sync-label">Last updated</span>
        <span class="last-sync-value">{s.timeAgo}</span>
      </div>
    </section>

    <!-- Quota Pool Cards Section -->
    <section class="pools-section">
      <h2 class="section-title">Quota Pools</h2>

      <div class="pools-list">
        {#if s.isLoading}
          <SkeletonRow />
          <SkeletonRow short />
        {:else}
          {#each s.pools as pool (pool.label)}
            <div animate:flip={{ duration: 300 }} class="material-card-wrapper">
              <PoolRow {pool} isOffline={s.isOffline} poolsLength={s.pools.length} />
            </div>
          {:else}
            <!-- Empty / Offline Android Card -->
            <div class="offline-card">
              <div class="offline-icon-wrapper">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 1l22 22M16.72 11.06A10.94 10.94 0 0 1 19 12.55M5 12.55a10.94 10.94 0 0 1 5.17-2.39M10.71 5.05A16 16 0 0 1 22.58 9M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/>
                </svg>
              </div>
              
              <h3 class="offline-title">
                {s.errorReason === 'process_not_found' ? 'Process Not Found' : s.isOffline ? 'Offline Mode' : 'No Quota Data'}
              </h3>
              
              <p class="offline-body">
                Set up an OAuth Refresh Token to enable direct cloud sync on Android.
              </p>

              <button class="material-btn ripple-btn" onclick={() => s.showTokenInput = !s.showTokenInput}>
                {s.showTokenInput ? 'Close Setup' : '⚙️ Setup OAuth Token'}
              </button>

              {#if s.showTokenInput}
                <div class="token-sheet-box">
                  <input
                    type="password"
                    class="material-input"
                    placeholder="Paste OAuth Refresh Token..."
                    bind:value={s.tokenInput}
                  />
                  <button class="material-btn material-btn--primary ripple-btn" onclick={handleSaveToken}>
                    Save & Sync
                  </button>
                  {#if s.tokenSaveStatus}
                    <span class="save-status-text">{s.tokenSaveStatus}</span>
                  {/if}
                </div>
              {/if}
            </div>
          {/each}
        {/if}
      </div>
    </section>
  </main>

  <!-- Material 3 Android Bottom Navigation Bar -->
  <nav class="bottom-nav-bar">
    <div class="nav-item nav-item--active">
      <svg class="bottom-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
        <polyline points="9 22 9 12 15 12 15 22"/>
      </svg>
      <span class="nav-label">Quota</span>
    </div>

    <button class="nav-item ripple-btn" onclick={handleRefresh} disabled={s.isRefreshing}>
      <svg class="bottom-nav-icon" class:spinning={s.isRefreshing} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
      </svg>
      <span class="nav-label">Sync</span>
    </button>

    <a href="/settings" class="nav-item ripple-btn">
      <svg class="bottom-nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
      </svg>
      <span class="nav-label">Settings</span>
    </a>
  </nav>
</div>

<style>
  /* ── Modern Material 3 Android App Design Tokens ── */
  :root {
    --m3-bg:              oklch(12% 0.01 260);
    --m3-surface:         oklch(17% 0.015 260);
    --m3-surface-variant: oklch(22% 0.02 260);
    --m3-outline:         oklch(28% 0.02 260);
    
    --m3-primary:         oklch(68% 0.18 240);
    --m3-primary-container: oklch(26% 0.08 240);
    --m3-on-primary-container: oklch(88% 0.12 240);

    --m3-ink-high:        oklch(96% 0.005 260);
    --m3-ink-mid:         oklch(78% 0.01 260);
    --m3-ink-muted:       oklch(62% 0.01 260);

    --m3-success:         oklch(75% 0.18 145);
    --m3-warning:         oklch(75% 0.16 75);
    --m3-offline:         oklch(55% 0 0);
  }

  :global(html, body) {
    margin: 0;
    padding: 0;
    background: var(--m3-bg) !important;
    font-family: "Roboto", "Inter", system-ui, -apple-system, sans-serif;
    height: 100%;
    overflow: hidden;
    -webkit-tap-highlight-color: transparent;
  }

  /* Keyframe animations */
  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
  @keyframes pulseGlow {
    0%, 100% { transform: scale(1); opacity: 1; }
    50% { transform: scale(1.3); opacity: 0.6; }
  }

  .spinning { animation: spin 1s linear infinite; }

  /* App Shell Container */
  .mobile-app-shell {
    width: 100vw;
    height: 100vh;
    display: flex;
    flex-direction: column;
    background: var(--m3-bg);
    color: var(--m3-ink-high);
    box-sizing: border-box;
    overflow: hidden;
    user-select: none;
  }

  /* Top App Bar */
  .top-app-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px 12px 20px;
    background: var(--m3-bg);
    border-bottom: 1px solid oklch(20% 0.01 260);
  }
  .app-brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .brand-badge {
    width: 36px;
    height: 36px;
    border-radius: 12px;
    background: var(--m3-primary-container);
    color: var(--m3-on-primary-container);
    font-weight: 800;
    font-size: 0.9375rem;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 2px 8px oklch(0% 0 0 / 0.3);
  }
  .title-group {
    display: flex;
    flex-direction: column;
  }
  .app-title {
    margin: 0;
    font-size: 1.125rem;
    font-weight: 700;
    letter-spacing: -0.01em;
    color: var(--m3-ink-high);
    line-height: 1.2;
  }
  .app-subtitle {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--m3-ink-muted);
  }
  .app-bar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* Buttons & Touch Interactivity */
  .icon-btn {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    border: none;
    background: var(--m3-surface);
    color: var(--m3-ink-mid);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    text-decoration: none;
    transition: background 0.2s, transform 0.15s;
  }
  .ripple-btn:active {
    transform: scale(0.94);
    opacity: 0.85;
  }
  .icon-btn:hover {
    background: var(--m3-surface-variant);
    color: var(--m3-ink-high);
  }
  .nav-icon { width: 20px; height: 20px; }

  /* Content Scroll Area */
  .mobile-content {
    flex: 1;
    overflow-y: auto;
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  /* Hero Overview Card */
  .hero-card {
    background: linear-gradient(135deg, oklch(19% 0.02 260) 0%, oklch(15% 0.01 260) 100%);
    border: 1px solid var(--m3-outline);
    border-radius: 20px;
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    box-shadow: 0 4px 16px oklch(0% 0 0 / 0.25);
  }
  .hero-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .account-pill {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: oklch(24% 0.02 260);
    border-radius: 20px;
    font-size: 0.8125rem;
    font-weight: 500;
    color: var(--m3-ink-mid);
  }
  .cloud-icon { width: 16px; height: 16px; color: var(--m3-primary); }

  .status-chip {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 12px;
    border-radius: 20px;
    font-size: 0.75rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    background: oklch(24% 0 0);
    color: var(--m3-ink-muted);
  }
  .status-chip--live {
    background: oklch(24% 0.06 145);
    color: var(--m3-success);
  }
  .status-chip--stale {
    background: oklch(26% 0.08 75);
    color: var(--m3-warning);
  }
  .status-chip--offline {
    background: oklch(22% 0 0);
    color: var(--m3-offline);
  }
  .status-pulse-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentColor;
    animation: pulseGlow 2s infinite ease-in-out;
  }

  .hero-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid oklch(25% 0.015 260);
  }
  .last-sync-label {
    font-size: 0.75rem;
    color: var(--m3-ink-muted);
  }
  .last-sync-value {
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--m3-ink-high);
  }

  /* Pools Section */
  .pools-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .section-title {
    margin: 0 0 4px 4px;
    font-size: 0.8125rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: var(--m3-ink-muted);
  }
  .pools-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .material-card-wrapper {
    width: 100%;
  }

  /* Offline Card */
  .offline-card {
    background: var(--m3-surface);
    border: 1px solid var(--m3-outline);
    border-radius: 20px;
    padding: 24px 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 14px;
  }
  .offline-icon-wrapper {
    width: 52px;
    height: 52px;
    border-radius: 50%;
    background: oklch(24% 0.02 260);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--m3-ink-muted);
  }
  .offline-icon-wrapper svg { width: 26px; height: 26px; }

  .offline-title {
    margin: 0;
    font-size: 1rem;
    font-weight: 700;
    color: var(--m3-ink-high);
  }
  .offline-body {
    margin: 0;
    font-size: 0.8125rem;
    color: var(--m3-ink-muted);
    line-height: 1.4;
  }

  .material-btn {
    padding: 10px 18px;
    border-radius: 12px;
    border: 1px solid var(--m3-outline);
    background: var(--m3-surface-variant);
    color: var(--m3-ink-high);
    font-size: 0.8125rem;
    font-weight: 600;
    cursor: pointer;
  }
  .material-btn--primary {
    background: var(--m3-primary);
    color: oklch(10% 0 0);
    border: none;
  }

  .token-sheet-box {
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;
    margin-top: 8px;
    padding: 14px;
    background: oklch(14% 0.01 260);
    border-radius: 14px;
    border: 1px solid oklch(24% 0.02 260);
  }
  .material-input {
    width: 100%;
    box-sizing: border-box;
    padding: 12px;
    border-radius: 10px;
    border: 1px solid oklch(28% 0.02 260);
    background: oklch(10% 0 0);
    color: #fff;
    font-size: 0.8125rem;
  }
  .save-status-text {
    font-size: 0.75rem;
    color: var(--m3-success);
  }

  /* Material 3 Bottom Navigation Bar */
  .bottom-nav-bar {
    height: 64px;
    background: oklch(14% 0.01 260);
    border-top: 1px solid oklch(22% 0.015 260);
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding-bottom: env(safe-area-inset-bottom, 0px);
  }
  .nav-item {
    background: none;
    border: none;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    padding: 8px 16px;
    border-radius: 16px;
    color: var(--m3-ink-muted);
    cursor: pointer;
    text-decoration: none;
    transition: color 0.2s, background 0.2s;
  }
  .nav-item--active {
    color: var(--m3-primary);
    background: var(--m3-primary-container);
  }
  .bottom-nav-icon { width: 22px; height: 22px; }
  .nav-label { font-size: 0.6875rem; font-weight: 600; }
</style>
