<script lang="ts">
  import { onMount } from 'svelte';
  import { invoke } from '@tauri-apps/api/core';
  import { flip } from 'svelte/animate';
  import PoolRow from '$lib/components/PoolRow.svelte';
  import SkeletonRow from '$lib/components/SkeletonRow.svelte';
  import { formatEmail, formatResetTime } from '$lib/quota-utils';
  import type { Cache, Config, QuotaPool } from '$lib/types';
  import { quotaStore } from '$lib/quota-store.svelte';

  const s = quotaStore;

  // ── Toast Notification System ──────────────────────────────────────────────
  let toastText = $state('');
  let toastTimer: ReturnType<typeof setTimeout> | null = null;

  function showToast(message: string) {
    toastText = message;
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastText = '';
    }, 2400);
  }

  // ── Detail Bottom Sheet Modal ──────────────────────────────────────────────
  let selectedPool = $state<QuotaPool | null>(null);

  function openPoolDetail(pool: QuotaPool) {
    selectedPool = pool;
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate(10);
    }
  }

  function closePoolDetail() {
    selectedPool = null;
  }

  // ── Pull-to-Refresh Gesture Handling ──────────────────────────────────────
  let touchStartY = 0;
  let pullDistance = $state(0);
  let isPulling = $state(false);
  const PULL_THRESHOLD = 75;

  function handleTouchStart(e: TouchEvent) {
    const contentEl = e.currentTarget as HTMLElement;
    if (contentEl.scrollTop <= 0) {
      touchStartY = e.touches[0].clientY;
      isPulling = true;
    }
  }

  function handleTouchMove(e: TouchEvent) {
    if (!isPulling) return;
    const currentY = e.touches[0].clientY;
    const diff = currentY - touchStartY;
    if (diff > 0) {
      // Resistance curve calculation
      pullDistance = Math.min(Math.pow(diff, 0.85) * 2, 110);
    } else {
      pullDistance = 0;
    }
  }

  async function handleTouchEnd() {
    if (!isPulling) return;
    if (pullDistance >= PULL_THRESHOLD && !s.isRefreshing) {
      if (typeof navigator !== 'undefined' && navigator.vibrate) {
        navigator.vibrate(18);
      }
      showToast('Refreshing Quota...');
      await handleRefresh();
    }
    pullDistance = 0;
    isPulling = false;
  }

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
      showToast('Quota Updated');
    }, 1500);
  }

  // ── Quick Mask Email Toggle ────────────────────────────────────────────────
  async function toggleEmailMask() {
    s.maskAccountEmail = !s.maskAccountEmail;
    showToast(s.maskAccountEmail ? 'Email masked' : 'Email visible');
    try {
      const cfg = await invoke<Config>('get_config');
      cfg.mask_account_email = s.maskAccountEmail;
      await invoke('save_config', { newConfig: cfg });
    } catch (_e) {
      // Ignore if config save unavailable
    }
  }

  // ── Mobile-only: save token via Kotlin plugin ──────────────────────────────
  async function handleSaveToken() {
    if (!s.tokenInput.trim()) return;
    s.tokenSaveStatus = 'Saving token...';
    try {
      await invoke('plugin:quota|saveRefreshToken', { token: s.tokenInput.trim() });
      s.tokenSaveStatus = 'Token saved! Syncing...';
      showToast('OAuth Token Saved');
      setTimeout(async () => {
        await handleRefresh();
        s.showTokenInput = false;
        s.tokenSaveStatus = '';
      }, 1000);
    } catch (_e) {
      s.tokenSaveStatus = 'Saved to config';
      showToast('Token Saved');
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

<!-- Native Android Mobile App Container -->
<div class="mobile-app-shell">
  <!-- Pull-to-Refresh Visual Spinner Bar -->
  {#if pullDistance > 0 || s.isRefreshing}
    <div class="pull-refresh-indicator" style="transform: translateY({Math.min(pullDistance, 60)}px)">
      <svg class="pull-spinner" class:spinning={s.isRefreshing} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
      </svg>
    </div>
  {/if}

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

  <!-- Scrollable Main App Canvas with Touch Pull Support -->
  <main 
    class="mobile-content"
    ontouchstart={handleTouchStart}
    ontouchmove={handleTouchMove}
    ontouchend={handleTouchEnd}
  >
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
          {#if s.accountEmail}
            <button class="mask-toggle-btn ripple-btn" onclick={toggleEmailMask} title="Toggle email masking">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="eye-icon">
                {#if s.maskAccountEmail}
                  <!-- Eye Off -->
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                {:else}
                  <!-- Eye Open -->
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                {/if}
              </svg>
            </button>
          {/if}
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
            <div 
              animate:flip={{ duration: 300 }} 
              class="material-card-wrapper ripple-btn"
              class:card--high={pool.remaining_fraction > 0.5}
              class:card--medium={pool.remaining_fraction >= 0.2 && pool.remaining_fraction <= 0.5}
              class:card--low={pool.remaining_fraction < 0.2}
              onclick={() => openPoolDetail(pool)}
              role="button"
              tabindex="0"
              onkeydown={(e) => e.key === 'Enter' && openPoolDetail(pool)}
            >
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
                Paste your OAuth Refresh Token below to enable direct cloud sync on Android without opening Settings.
              </p>

              <div class="token-sheet-box">
                <input
                  type="password"
                  class="material-input"
                  placeholder="Paste OAuth Refresh Token (1//0...)"
                  bind:value={s.tokenInput}
                />
                <button class="material-btn material-btn--primary ripple-btn" onclick={handleSaveToken}>
                  Save & Sync Quota
                </button>
                {#if s.tokenSaveStatus}
                  <span class="save-status-text">{s.tokenSaveStatus}</span>
                {/if}
              </div>
            </div>
          {/each}
        {/if}
      </div>
    </section>
  </main>

  <!-- Material 3 Floating Toast Notification -->
  {#if toastText}
    <div class="toast-floating-container">
      <div class="toast-pill">
        <span class="toast-dot"></span>
        <span class="toast-text">{toastText}</span>
      </div>
    </div>
  {/if}

  <!-- Detail Bottom Sheet Modal -->
  {#if selectedPool}
    <!-- Modal Backdrop -->
    <div class="bottom-sheet-backdrop" onclick={closePoolDetail} role="presentation"></div>
    
    <!-- Sheet Drawer Container -->
    <div class="bottom-sheet-drawer">
      <div class="sheet-handle"></div>

      <div class="sheet-header">
        <div class="sheet-title-group">
          <span class="sheet-category">Quota Pool Breakdown</span>
          <h3 class="sheet-pool-label">{selectedPool.label}</h3>
        </div>
        <button class="sheet-close-btn ripple-btn" onclick={closePoolDetail}>✕</button>
      </div>

      <div class="sheet-body">
        <div class="sheet-metric-row">
          <div class="metric-card">
            <span class="metric-label">Remaining</span>
            <span class="metric-value">{Math.min(100, Math.round(selectedPool.remaining_fraction * 100))}%</span>
          </div>

          <div class="metric-card">
            <span class="metric-label">Reset Status</span>
            <span class="metric-value metric-value--sub">
              {formatResetTime(selectedPool.reset_time) ? `In ${formatResetTime(selectedPool.reset_time)}` : 'Active'}
            </span>
          </div>
        </div>

        {#if selectedPool.reset_time}
          <div class="sheet-info-box">
            <span class="info-label">Scheduled Reset Time</span>
            <span class="info-val">{new Date(selectedPool.reset_time).toLocaleString()}</span>
          </div>
        {/if}

        <div class="sheet-advice-box">
          {#if selectedPool.remaining_fraction > 0.5}
            <p class="advice-text advice--good">🟢 High quota remaining — feel free to use advanced AI models.</p>
          {:else if selectedPool.remaining_fraction >= 0.2}
            <p class="advice-text advice--warn">🟡 Moderate quota remaining — monitor large prompt usages.</p>
          {:else}
            <p class="advice-text advice--alert">🔴 Low quota remaining — reset will restore full capability.</p>
          {/if}
        </div>
      </div>
    </div>
  {/if}

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
  /* ── PC Desktop Aligned Dark Gray Tokens ── */
  :root {
    --m3-bg:              oklch(14% 0 0 / 0.95);
    --m3-surface:         oklch(20% 0 0 / 0.9);
    --m3-surface-variant: oklch(24% 0 0 / 0.9);
    --m3-outline:         oklch(28% 0 0 / 0.6);
    
    --m3-primary:         oklch(62% 0.16 230);
    --m3-primary-container: oklch(25% 0 0);
    --m3-on-primary-container: oklch(96% 0 0);

    --m3-ink-high:        oklch(96% 0 0);
    --m3-ink-mid:         oklch(70% 0 0);
    --m3-ink-muted:       oklch(60% 0 0);

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

  @keyframes spin {
    from { transform: rotate(0deg); }
    to { transform: rotate(360deg); }
  }
  @keyframes pulseGlow {
    0%, 100% { transform: scale(1); opacity: 1; }
    50% { transform: scale(1.3); opacity: 0.6; }
  }
  @keyframes slideUp {
    from { transform: translateY(100%); }
    to { transform: translateY(0); }
  }
  @keyframes toastPop {
    from { transform: translate(-50%, 20px); opacity: 0; }
    to { transform: translate(-50%, 0); opacity: 1; }
  }

  .spinning { animation: spin 1s linear infinite; }

  /* App Shell Container */
  .mobile-app-shell {
    width: 100vw;
    height: 100dvh;
    display: flex;
    flex-direction: column;
    background: var(--m3-bg);
    color: var(--m3-ink-high);
    box-sizing: border-box;
    overflow: hidden;
    user-select: none;
    position: relative;
  }

  /* Pull to Refresh Spinner Bar */
  .pull-refresh-indicator {
    position: absolute;
    top: max(8px, env(safe-area-inset-top, 0px));
    left: 50%;
    margin-left: -20px;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: var(--m3-surface-variant);
    border: 1px solid var(--m3-outline);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px oklch(0% 0 0 / 0.4);
    z-index: 100;
    transition: transform 0.1s linear;
  }
  .pull-spinner { width: 20px; height: 20px; color: var(--m3-primary); }

  /* Top App Bar */
  .top-app-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: max(14px, env(safe-area-inset-top, 0px)) 20px 12px 20px;
    background: var(--m3-bg);
    border-bottom: 1px solid oklch(22% 0 0 / 0.5);
    z-index: 10;
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
  .title-group { display: flex; flex-direction: column; }
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
  .app-bar-actions { display: flex; align-items: center; gap: 8px; }

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
  .ripple-btn {
    transition: transform 0.15s ease, opacity 0.15s ease;
  }
  .ripple-btn:active {
    transform: scale(0.95);
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
    touch-action: pan-y;
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

  .mask-toggle-btn {
    background: none;
    border: none;
    color: var(--m3-ink-muted);
    padding: 2px 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .eye-icon { width: 14px; height: 14px; }

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
  .last-sync-label { font-size: 0.75rem; color: var(--m3-ink-muted); }
  .last-sync-value { font-size: 0.8125rem; font-weight: 600; color: var(--m3-ink-high); }

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
    cursor: pointer;
    border-radius: 12px;
    transition: transform 0.15s ease;
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

  .offline-title { margin: 0; font-size: 1rem; font-weight: 700; color: var(--m3-ink-high); }
  .offline-body { margin: 0; font-size: 0.8125rem; color: var(--m3-ink-muted); line-height: 1.4; }

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
  .save-status-text { font-size: 0.75rem; color: var(--m3-success); }

  /* Floating Toast Notification */
  .toast-floating-container {
    position: fixed;
    bottom: 76px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 200;
    animation: toastPop 0.25s cubic-bezier(0.17, 0.67, 0.83, 0.67);
  }
  .toast-pill {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border-radius: 24px;
    background: oklch(26% 0.03 260);
    border: 1px solid oklch(36% 0.04 260);
    color: var(--m3-ink-high);
    font-size: 0.8125rem;
    font-weight: 600;
    box-shadow: 0 8px 24px oklch(0% 0 0 / 0.4);
  }
  .toast-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--m3-primary);
  }

  /* Material 3 Detail Bottom Sheet */
  .bottom-sheet-backdrop {
    position: fixed;
    inset: 0;
    background: oklch(0% 0 0 / 0.65);
    backdrop-filter: blur(6px);
    z-index: 300;
  }
  .bottom-sheet-drawer {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: oklch(18% 0.015 260);
    border-top: 1px solid oklch(30% 0.02 260);
    border-top-left-radius: 24px;
    border-top-right-radius: 24px;
    padding: 12px 20px 24px 20px;
    z-index: 310;
    animation: slideUp 0.25s cubic-bezier(0.17, 0.67, 0.83, 0.67);
    display: flex;
    flex-direction: column;
    gap: 16px;
    box-shadow: 0 -8px 32px oklch(0% 0 0 / 0.5);
  }
  .sheet-handle {
    width: 36px;
    height: 4px;
    border-radius: 2px;
    background: oklch(38% 0.01 260);
    align-self: center;
  }
  .sheet-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .sheet-title-group { display: flex; flex-direction: column; }
  .sheet-category { font-size: 0.6875rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--m3-ink-muted); }
  .sheet-pool-label { margin: 2px 0 0 0; font-size: 1.25rem; font-weight: 700; color: var(--m3-ink-high); }

  .sheet-close-btn {
    background: oklch(24% 0.02 260);
    border: none;
    color: var(--m3-ink-mid);
    width: 32px;
    height: 32px;
    border-radius: 50%;
    cursor: pointer;
    font-weight: 700;
  }

  .sheet-body { display: flex; flex-direction: column; gap: 14px; }
  .sheet-metric-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .metric-card {
    background: oklch(14% 0.01 260);
    border: 1px solid oklch(25% 0.015 260);
    border-radius: 14px;
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .metric-label { font-size: 0.75rem; color: var(--m3-ink-muted); }
  .metric-value { font-size: 1.5rem; font-weight: 800; color: var(--m3-primary); }
  .metric-value--sub { font-size: 1rem; font-weight: 600; color: var(--m3-ink-high); margin-top: 4px; }

  .sheet-info-box {
    background: oklch(14% 0.01 260);
    border: 1px solid oklch(25% 0.015 260);
    border-radius: 14px;
    padding: 12px 14px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .info-label { font-size: 0.75rem; color: var(--m3-ink-muted); }
  .info-val { font-size: 0.8125rem; font-weight: 600; color: var(--m3-ink-high); }

  .sheet-advice-box {
    padding: 12px 14px;
    border-radius: 14px;
    background: oklch(14% 0.01 260);
    border: 1px solid oklch(24% 0.015 260);
  }
  .advice-text { margin: 0; font-size: 0.8125rem; font-weight: 500; line-height: 1.4; }
  .advice--good { color: oklch(85% 0.14 145); }
  .advice--warn { color: oklch(85% 0.14 75); }
  .advice--alert { color: oklch(80% 0.18 25); }

  /* Material 3 Bottom Navigation Bar */
  .bottom-nav-bar {
    height: calc(56px + max(12px, env(safe-area-inset-bottom, 0px)));
    background: oklch(14% 0 0 / 0.95);
    border-top: 1px solid oklch(22% 0 0 / 0.5);
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding-bottom: max(12px, env(safe-area-inset-bottom, 0px));
    z-index: 10;
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
