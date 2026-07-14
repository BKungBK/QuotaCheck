<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";

  interface QuotaPool {
    label: string;
    remaining_fraction: number;
    reset_time: string | null;
  }

  interface Cache {
    pools: QuotaPool[];
    last_updated: string;
    is_offline: boolean;
    source: string;
  }

  let pools = $state<QuotaPool[]>([]);
  let isOffline = $state(true);
  let lastUpdated = $state("");
  let source = $state("");

  let now = $state(Date.now());
  $effect(() => {
    const interval = setInterval(() => {
      now = Date.now();
    }, 5000);
    return () => clearInterval(interval);
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
    let unlisten: (() => void) | undefined;

    const init = async () => {
      try {
        const cache = await invoke<Cache>("get_current_quota");
        pools = cache.pools || [];
        isOffline = cache.is_offline;
        lastUpdated = cache.last_updated;
        source = cache.source;
      } catch (e) {
        console.error("Failed to load initial cache", e);
      }

      unlisten = await listen<Cache>("quota-update", (event) => {
        pools = event.payload.pools || [];
        isOffline = event.payload.is_offline;
        lastUpdated = event.payload.last_updated;
        source = event.payload.source;
      });
    };

    init();

    return () => {
      if (unlisten) unlisten();
    };
  });
</script>

<main class="widget" class:offline={isOffline} id="quota-widget">
  <div class="row-top">
    <h1 class="label" id="widget-title">Antigravity</h1>
    <span class="dot" class:dot-blue={!isOffline} id="widget-status-dot"></span>
  </div>

  <div class="pools-container" id="quota-pools-list">
    {#each pools as pool}
      <div class="pool-row" id="pool-{pool.label.toLowerCase()}">
        <div class="pool-meta">
          <span class="pool-label">{pool.label}</span>
          <span class="pool-percent">{Math.min(100, Math.round(pool.remaining_fraction * 100))}%</span>
        </div>
        <div class="bar-track">
          <div class="bar-fill" style="width: {isOffline ? 0 : pool.remaining_fraction * 100}%"></div>
        </div>
      </div>
    {:else}
      <div class="no-pools" id="no-pools-placeholder">
        <span class="placeholder-text">{isOffline ? "Offline" : "No Pools"}</span>
      </div>
    {/each}
  </div>

  <div class="row-bottom">
    <span class="meta" id="quota-source">{isOffline ? "Offline" : source === "local" ? "Local" : "Cloud"}</span>
    <span class="meta" id="quota-time-ago">{timeAgo}</span>
  </div>
</main>

<style>
  :global(html, body) {
    margin: 0;
    padding: 0;
    background: transparent !important;
    overflow: hidden;
  }

  .widget {
    width: 100vw;
    height: 100vh;
    box-sizing: border-box;
    padding: 8px 12px;
    background: #1e1e1e;
    border: 1px solid #333333;
    border-radius: 8px;
    font-family: "Inter", system-ui, sans-serif;
    color: #ffffff;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    user-select: none;
    pointer-events: none;
    transition: opacity 300ms ease, filter 300ms ease;
  }

  .widget.offline {
    opacity: 0.6;
    filter: grayscale(1);
  }

  .row-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .label {
    font-size: 0.6875rem;
    font-weight: 500;
    line-height: 1.1;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: #969696;
    margin: 0;
  }

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #969696;
  }

  .dot-blue {
    background: #007acc;
  }

  .pools-container {
    display: flex;
    flex-direction: column;
    gap: 6px;
    justify-content: center;
    flex-grow: 1;
    margin: 4px 0;
  }

  .pool-row {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    line-height: 1;
  }

  .pool-label {
    font-size: 0.75rem;
    font-weight: 500;
    color: #969696;
  }

  .pool-percent {
    font-size: 0.75rem;
    font-weight: 600;
    color: #007acc;
  }

  .widget.offline .pool-percent {
    color: #ffffff;
  }

  .no-pools {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-grow: 1;
  }

  .placeholder-text {
    font-size: 0.75rem;
    font-weight: 500;
    color: #969696;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  .row-bottom {
    display: flex;
    justify-content: space-between;
  }

  .meta {
    font-size: 0.625rem;
    font-weight: 500;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: #969696;
  }

  .bar-track {
    width: 100%;
    height: 4px;
    background: #333333;
    border-radius: 2px;
    overflow: hidden;
  }

  .bar-fill {
    height: 100%;
    background: #007acc;
    transition: width 300ms ease;
  }
</style>