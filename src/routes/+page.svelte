<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";
  import { flip } from "svelte/animate";

  interface QuotaPool {
    label: string;
    remaining_fraction: number;
    reset_time: string | null;
  }

  interface Cache {
    pools: QuotaPool[];
    last_updated: string;
    is_offline: boolean;
    error_reason?: string;
    source: string;
    account_email?: string;
  }

  interface Config {
    mask_account_email?: boolean;
    refresh_token_override?: string;
  }

  let pools = $state<QuotaPool[]>([]);
  let isOffline = $state(true);
  let errorReason = $state<string | undefined>(undefined);
  let lastUpdated = $state("");
  let source = $state("");
  let accountEmail = $state<string | undefined>(undefined);
  let maskAccountEmail = $state(false);
  let isLoading = $state(true);
  let isRefreshing = $state(false);
  let tokenInput = $state("");
  let showTokenInput = $state(false);
  let tokenSaveStatus = $state("");

  let now = $state(Date.now());
  $effect(() => {
    const interval = setInterval(() => {
      now = Date.now();
    }, 5000);
    return () => clearInterval(interval);
  });

  // Watchdog: detect if data is stale (older than 10 minutes)
  let isStale = $derived.by(() => {
    if (!lastUpdated) return false;
    const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
    return diffSecs > 600;
  });

  let statusLabel = $derived.by(() => {
    if (isLoading || isRefreshing) return "syncing...";
    if (isOffline) {
      if (errorReason === "process_not_found") return "process not found";
      return "offline";
    }
    if (isStale) return "stale";
    return "live";
  });

  let statusTooltip = $derived.by(() => {
    if (isLoading || isRefreshing) return "Fetching latest quota data...";
    if (isOffline) {
      if (errorReason === "process_not_found") return "Antigravity IDE or CLI process is not running";
      return "Unable to connect to local/cloud quota endpoint";
    }
    if (isStale) return "Quota data has not updated in over 10 minutes";
    return "Connected to quota service";
  });

  let timeAgo = $derived.by(() => {
    if (!lastUpdated) return "Never";
    const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
    if (diffSecs < 10) return "Now";
    if (diffSecs < 60) return `${diffSecs}s ago`;
    const mins = Math.floor(diffSecs / 60);
    return mins < 60 ? `${mins}m ago` : `${Math.floor(mins / 60)}h ago`;
  });

  function formatEmail(email: string | undefined, mask: boolean): string {
    if (!email) return "";
    if (!mask) return email;
    const parts = email.split("@");
    if (parts.length !== 2) return email;
    const name = parts[0];
    const domain = parts[1];
    if (name.length <= 2) {
      return `${name}***@${domain}`;
    }
    return `${name.slice(0, 2)}***@${domain}`;
  }

  async function loadQuotaData() {
    // 1. Try Android Kotlin Plugin cache first
    try {
      const res = await invoke<{ cache: string }>("plugin:quota|getQuotaCache");
      if (res && res.cache) {
        const parsed = JSON.parse(res.cache);
        if (parsed.pools && parsed.pools.length > 0) {
          pools = parsed.pools;
          isOffline = parsed.is_offline ?? false;
          errorReason = parsed.error_reason;
          if (parsed.last_updated) {
            lastUpdated = parsed.last_updated;
          }
          source = "cloud";
          isLoading = false;
          return;
        }
      }
    } catch (_e) {
      // Not on Android or plugin not available
    }

    // 2. Desktop Rust command fallback
    try {
      const cache = await invoke<Cache>("get_current_quota");
      pools = cache.pools || [];
      isOffline = cache.is_offline;
      errorReason = cache.error_reason;
      lastUpdated = cache.last_updated;
      source = cache.source;
      accountEmail = cache.account_email;
    } catch (e) {
      console.error("Failed to load initial cache", e);
    } finally {
      isLoading = false;
    }
  }

  async function handleRefresh() {
    isRefreshing = true;
    try {
      // Try Android plugin refresh first
      await invoke("plugin:quota|triggerManualSync");
    } catch (_e) {
      // Try desktop Rust refresh
      try {
        await invoke("manual_refresh_trigger");
      } catch (err) {
        console.error("Refresh failed", err);
      }
    }
    setTimeout(async () => {
      await loadQuotaData();
      isRefreshing = false;
    }, 1500);
  }

  async function handleSaveToken() {
    if (!tokenInput.trim()) return;
    tokenSaveStatus = "Saving token...";
    try {
      // Try Android plugin
      await invoke("plugin:quota|saveRefreshToken", { token: tokenInput.trim() });
      tokenSaveStatus = "Token saved! Syncing...";
      setTimeout(async () => {
        await handleRefresh();
        showTokenInput = false;
        tokenSaveStatus = "";
      }, 1000);
    } catch (_e) {
      tokenSaveStatus = "Saved to config";
    }
  }

  onMount(() => {
    let unlistenQuota: (() => void) | undefined;
    let unlistenRefresh: (() => void) | undefined;
    let unlistenConfig: (() => void) | undefined;

    const init = async () => {
      try {
        const cfg = await invoke<Config>("get_config");
        maskAccountEmail = cfg.mask_account_email ?? false;
        if (cfg.refresh_token_override) {
          tokenInput = cfg.refresh_token_override;
        }
      } catch (e) {
        console.error("Failed to load config in page", e);
      }

      await loadQuotaData();

      try {
        unlistenQuota = await listen<Cache>("quota-update", (event) => {
          pools = event.payload.pools || [];
          isOffline = event.payload.is_offline;
          errorReason = event.payload.error_reason;
          lastUpdated = event.payload.last_updated;
          source = event.payload.source;
          accountEmail = event.payload.account_email;
          isLoading = false;
          isRefreshing = false;
        });

        unlistenConfig = await listen<Config>("config-updated", (event) => {
          maskAccountEmail = event.payload.mask_account_email ?? false;
        });

        unlistenRefresh = await listen("refresh-started", () => {
          isRefreshing = true;
        });
      } catch (_e) {
        // Event listener fallback on platforms without Tauri event bus
      }
    };

    init();

    return () => {
      if (unlistenQuota) unlistenQuota();
      if (unlistenRefresh) unlistenRefresh();
      if (unlistenConfig) unlistenConfig();
    };
  });

  function barColor(fraction: number): string {
    if (isOffline && pools.length === 0) return "var(--color-bar-offline)";
    if (fraction <= 0.2) return "var(--color-bar-low)";
    return "var(--color-accent)";
  }

  function formatResetTime(raw: string | null): string {
    if (!raw) return "";
    const d = new Date(raw);
    if (!isNaN(d.getTime())) {
      const diffMs = d.getTime() - Date.now();
      if (diffMs <= 0) return "";
      const totalMins = Math.floor(diffMs / 60_000);
      const h = Math.floor(totalMins / 60);
      const m = totalMins % 60;
      if (h > 0 && m > 0) return `${h}h ${m}m`;
      if (h > 0) return `${h}h`;
      return `${m}m`;
    }
    return raw;
  }
</script>

<main
  class="widget"
  class:offline={isOffline && pools.length === 0}
  id="quota-widget"
  aria-label="Antigravity Quota Widget"
>
  <div class="row-top">
    <div class="header-left">
      <span class="label" id="widget-title">BK</span>
      <span class="sub-title">Antigravity Quota</span>
    </div>
    
    <div class="header-right">
      <button class="btn-icon" onclick={handleRefresh} title="Refresh Quota" disabled={isRefreshing}>
        <svg class="refresh-icon" class:spinning={isRefreshing} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21.5 2v6h-6M2.5 22v-6h6M2 11.5a10 10 0 0 1 18.8-4.3M22 12.5a10 10 0 0 1-18.8 4.2"/>
        </svg>
      </button>

      <a href="/settings" class="btn-icon" title="Settings">
        <svg class="refresh-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </a>

      <div class="live-badge" role="status" aria-live="polite" id="widget-status" title={statusTooltip}>
        <span
          class="dot"
          class:dot-live={!isOffline && !isStale}
          class:dot-stale={isStale}
          id="widget-status-dot"
          aria-hidden="true"
        ></span>
        {statusLabel}
      </div>
    </div>
  </div>

  <div class="pools-container" id="quota-pools-list">
    {#if isLoading}
      <!-- Skeleton shimmer loaders -->
      <div class="pool-row skeleton">
        <div class="pool-meta">
          <div class="skeleton-text name"></div>
          <div class="skeleton-text percent"></div>
        </div>
        <div class="bar-track">
          <div class="bar-fill skeleton-bar"></div>
        </div>
        <div class="sub-row">
          <div class="skeleton-text sub"></div>
        </div>
      </div>
      <div class="pool-row skeleton">
        <div class="pool-meta">
          <div class="skeleton-text name name--short"></div>
          <div class="skeleton-text percent"></div>
        </div>
        <div class="bar-track">
          <div class="bar-fill skeleton-bar"></div>
        </div>
        <div class="sub-row">
          <div class="skeleton-text sub sub--short"></div>
        </div>
      </div>
    {:else}
      {#each pools as pool (pool.label)}
        {@const pct = Math.min(100, Math.round(pool.remaining_fraction * 100))}
        {@const resetText = formatResetTime(pool.reset_time)}
        <div class="pool-row" id="pool-{pool.label.toLowerCase()}" animate:flip={{ duration: 300 }}>
          <div class="pool-meta">
            <span class="pool-label">{pool.label}</span>
            <span class="pool-percent">{pct}%</span>
          </div>
          <div class="bar-track">
            <div
              class="bar-fill"
              class:bar-fill--low={pool.remaining_fraction <= 0.2}
              style="width: {pool.remaining_fraction * 100}%; background: {barColor(pool.remaining_fraction)}"
            ></div>
          </div>
          {#if resetText}
            <div class="sub-row">
              <span class="sub-meta">reset {resetText}</span>
            </div>
          {/if}
        </div>
      {:else}
        <div class="no-pools" id="no-pools-placeholder">
          <div class="offline-box">
            <span class="placeholder-text" title={statusTooltip}>
              {errorReason === "process_not_found" ? "Process Not Found" : isOffline ? "Offline Mode" : "No Quota Data"}
            </span>
            <p class="offline-desc">
              {#if isOffline}
                Connect your account or set an OAuth Refresh Token to sync Quota directly.
              {/if}
            </p>

            <button class="btn-setup" onclick={() => showTokenInput = !showTokenInput}>
              {showTokenInput ? "Close Setup" : "⚙️ Setup Refresh Token"}
            </button>

            {#if showTokenInput}
              <div class="token-form">
                <input
                  type="password"
                  placeholder="Paste OAuth Refresh Token..."
                  bind:value={tokenInput}
                />
                <button class="btn-save" onclick={handleSaveToken}>Save & Sync</button>
                {#if tokenSaveStatus}
                  <span class="save-status">{tokenSaveStatus}</span>
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
      {isOffline && pools.length === 0 ? "Offline" : source === "local" ? "Local 🟢" : accountEmail ? `Cloud ☁️ • ${formatEmail(accountEmail, maskAccountEmail)}` : "Cloud ☁️"}
    </span>
    <span class="meta" id="quota-time-ago">{timeAgo}</span>
  </div>
</main>

<style>
  /* ── Design Tokens ── */
  :root {
    /* Surfaces */
    --color-bg:         oklch(14% 0 0 / 0.95);
    --color-surface:    oklch(20% 0 0 / 0.9);
    --color-border:     oklch(28% 0 0 / 0.6);
    --color-separator:  oklch(22% 0 0 / 0.5);

    /* Skeleton shimmer layers */
    --color-shimmer-base:     oklch(18% 0 0 / 0.5);
    --color-shimmer-highlight: oklch(25% 0 0 / 0.5);

    /* Ink scale */
    --color-ink:        oklch(88% 0 0);
    --color-ink-high:   oklch(96% 0 0);   
    --color-ink-mid:    oklch(70% 0 0);   
    --color-ink-muted:  oklch(60% 0 0);   
    --color-ink-dim:    oklch(52% 0 0);   
    --color-ink-subtle: oklch(45% 0 0);   

    /* Status dot */
    --color-dot-offline: oklch(45% 0 0);
    --color-dot-stale:   oklch(65% 0.15 80);

    /* Accent */
    --color-accent:      oklch(62% 0.16 230);
    --color-accent-glow: oklch(62% 0.16 230 / 0.4);

    /* Bar colors */
    --color-bar-track:   oklch(22% 0 0 / 0.8);
    --color-bar-offline: oklch(38% 0 0);
    --color-bar-low:     oklch(62% 0.22 25);

    /* Live dot color */
    --color-dot-live:    oklch(75% 0.18 145);
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
  @keyframes shimmer {
    0%   { background-position: -200% 0; }
    100% { background-position:  200% 0; }
  }
  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  .spinning {
    animation: spin 1s linear infinite;
  }

  .widget {
    width: 100vw;
    height: 100vh;
    box-sizing: border-box;
    padding: 16px;
    background: var(--color-bg);
    border-radius: 0px;
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

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 10px;
  }

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
    transition: background 0.2s;
  }
  .btn-icon:hover {
    background: oklch(25% 0 0);
    color: var(--color-ink-high);
  }

  .refresh-icon {
    width: 16px;
    height: 16px;
  }

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
  .dot-live {
    background: var(--color-dot-live);
    animation: pulseDot 2.4s ease-in-out infinite;
  }
  .dot-stale {
    background: var(--color-dot-stale);
  }

  .pools-container {
    display: flex;
    flex-direction: column;
    gap: 12px;
    flex-grow: 1;
    justify-content: flex-start;
    margin: 12px 0;
    overflow-y: auto;
  }

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