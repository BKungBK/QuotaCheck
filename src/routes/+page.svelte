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
  }

  let pools = $state<QuotaPool[]>([]);
  let isOffline = $state(true);
  let errorReason = $state<string | undefined>(undefined);
  let lastUpdated = $state("");
  let source = $state("");
  let isLoading = $state(true);

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
    if (isLoading) return "loading...";
    if (isOffline) {
      if (errorReason === "process_not_found") return "process not found";
      return "offline";
    }
    if (isStale) return "stale";
    return "live";
  });

  let statusTooltip = $derived.by(() => {
    if (isLoading) return "Fetching latest quota data...";
    if (isOffline) {
      if (errorReason === "process_not_found") return "Antigravity IDE or CLI process is not running";
      return "Unable to connect to local quota endpoint";
    }
    if (isStale) return "Quota data has not updated in over 10 minutes";
    return "Connected to quota service";
  });

  let timeAgo = $derived.by(() => {
    if (!lastUpdated) return "Never";
    const diffSecs = Math.floor((now - new Date(lastUpdated).getTime()) / 1000);
    if (diffSecs < 10) return "Now";
    if (diffSecs < 60) return `${diffSecs}s`;
    const mins = Math.floor(diffSecs / 60);
    return mins < 60 ? `${mins}m` : `${Math.floor(mins / 60)}h`;
  });

  onMount(() => {
    let unlistenQuota: (() => void) | undefined;
    let unlistenRefresh: (() => void) | undefined;

    const init = async () => {
      try {
        const cache = await invoke<Cache>("get_current_quota");
        pools = cache.pools || [];
        isOffline = cache.is_offline;
        errorReason = cache.error_reason;
        lastUpdated = cache.last_updated;
        source = cache.source;
      } catch (e) {
        console.error("Failed to load initial cache", e);
      } finally {
        isLoading = false;
      }

      unlistenQuota = await listen<Cache>("quota-update", (event) => {
        pools = event.payload.pools || [];
        isOffline = event.payload.is_offline;
        errorReason = event.payload.error_reason;
        lastUpdated = event.payload.last_updated;
        source = event.payload.source;
        isLoading = false;
      });

      unlistenRefresh = await listen("refresh-started", () => {
        isLoading = true;
      });
    };

    init();

    return () => {
      if (unlistenQuota) unlistenQuota();
      if (unlistenRefresh) unlistenRefresh();
    };
  });

  function barColor(fraction: number): string {
    if (isOffline) return "var(--color-bar-offline)";
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

  function getFractionText(label: string, fraction: number): string {
    const l = label.toLowerCase();
    if (l.startsWith("claude")) {
      const total = 500;
      const current = Math.round(fraction * total);
      return `${current} / ${total} requests`;
    }
    if (l.startsWith("gemini")) {
      const total = 200;
      const current = Math.round(fraction * total);
      return `${current} / ${total} credits`;
    }
    const total = 100;
    const current = Math.round(fraction * total);
    return `${current} / ${total}`;
  }
</script>

<main
  class="widget"
  class:offline={isOffline}
  id="quota-widget"
  aria-label="Antigravity Quota Widget"
>
  <div class="row-top">
    <span class="label" id="widget-title" aria-hidden="true">BK</span>
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
              style="width: {isOffline ? 0 : pool.remaining_fraction * 100}%; background: {barColor(pool.remaining_fraction)}"
            ></div>
          </div>
          <div class="sub-row">
            <span class="sub-meta">{getFractionText(pool.label, pool.remaining_fraction)}</span>
            {#if resetText}
              <span class="sub-meta">reset {resetText}</span>
            {/if}
          </div>
        </div>
      {:else}
        <div class="no-pools" id="no-pools-placeholder">
          <span class="placeholder-text" title={statusTooltip}>
            {errorReason === "process_not_found" ? "Process Not Found" : isOffline ? "Offline" : "No Pools"}
          </span>
        </div>
      {/each}
    {/if}
  </div>

  <div class="row-bottom">
    <span class="meta" id="quota-source">{isOffline ? "Offline" : source === "local" ? "Local" : "Cloud"}</span>
    <span class="meta" id="quota-time-ago">{timeAgo}</span>
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

  :global(html, body) {
    margin: 0;
    padding: 0;
    background: transparent !important;
    overflow: hidden;
  }

  @keyframes pulseDot {
    0%, 100% { opacity: 1;   box-shadow: 0 0 0 0   var(--color-dot-live-glow); }
    50%       { opacity: 0.8; box-shadow: 0 0 0 3px oklch(68% 0.17 160 / 0); }
  }
  @keyframes shimmer {
    0%   { background-position: -200% 0; }
    100% { background-position:  200% 0; }
  }

  @media (prefers-reduced-motion: reduce) {
    .widget { transition: none; }
    .dot-live { animation: none; }
    .bar-fill { transition: none; }
    .skeleton .skeleton-text,
    .skeleton .skeleton-bar {
      animation: none;
      background: var(--color-shimmer-base);
    }
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
    gap: 0;
    flex-grow: 1;
    justify-content: center;
    margin: 6px 0;
  }
  .pool-row {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 0;
    transition: transform 300ms ease;
  }
  .pool-row + .pool-row {
    border-top: 1px solid var(--color-separator);
  }
  .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .pool-label {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--color-ink-high);
    line-height: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
  }
  .pool-percent {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-ink-high);
    line-height: 1;
    flex-shrink: 0;
  }

  .bar-track {
    width: 100%;
    height: 5px;
    background: var(--color-bar-track);
    border-radius: 4px;
    overflow: hidden;
  }
  .bar-fill {
    height: 100%;
    border-radius: 4px;
    will-change: width;
    transition: width 400ms ease, background 600ms ease;
  }

  .sub-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .sub-meta {
    font-size: 0.5625rem;
    color: var(--color-ink-muted);
    letter-spacing: 0.02em;
  }

  .skeleton .skeleton-text {
    height: 10px;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 37%,
      var(--color-shimmer-base) 63%
    );
    background-size: 400% 100%;
    animation: shimmer 1.4s linear infinite;
    border-radius: 4px;
  }
  .skeleton .skeleton-text.name      { width: 55px; }
  .skeleton .skeleton-text.name--short { width: 50px; }
  .skeleton .skeleton-text.percent   { width: 25px; }
  .skeleton .skeleton-text.sub       { width: 85px; height: 8px; margin-top: 1px; }
  .skeleton .skeleton-text.sub--short { width: 60px; height: 8px; margin-top: 1px; }

  .skeleton .skeleton-bar {
    width: 50%;
    height: 100%;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 37%,
      var(--color-shimmer-base) 63%
    );
    background-size: 400% 100%;
    animation: shimmer 1.4s linear infinite;
  }

  .no-pools {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-grow: 1;
  }
  .placeholder-text {
    font-size: 0.6875rem;
    font-weight: 500;
    color: var(--color-ink-subtle);
    text-transform: uppercase;
    letter-spacing: 0.06em;
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