<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";

  interface Cache {
    remaining: number;
    total: number;
    last_updated: string;
    is_offline: boolean;
    source: string;
  }

  let remaining = $state(0);
  let total = $state(0);
  let isOffline = $state(true);
  let lastUpdated = $state("");
  let source = $state("");

  let percent = $derived(total > 0 ? Math.min(100, Math.round((remaining / total) * 100)) : 0);

  let timeAgo = $state("Never");
  $effect(() => {
    const updateTime = () => {
      if (!lastUpdated) {
        timeAgo = "Never";
        return;
      }
      const diffSecs = Math.floor((Date.now() - new Date(lastUpdated).getTime()) / 1000);
      if (diffSecs < 10) timeAgo = "Now";
      else if (diffSecs < 60) timeAgo = `${diffSecs}s`;
      else {
        const mins = Math.floor(diffSecs / 60);
        timeAgo = mins < 60 ? `${mins}m` : `${Math.floor(mins / 60)}h`;
      }
    };
    updateTime();
    const interval = setInterval(updateTime, 5000);
    return () => clearInterval(interval);
  });

  onMount(async () => {
    try {
      const cache = await invoke<Cache>("get_current_quota");
      remaining = cache.remaining;
      total = cache.total;
      isOffline = cache.is_offline;
      lastUpdated = cache.last_updated;
      source = cache.source;
    } catch (e) {
      console.error("Failed to load initial cache", e);
    }

    const unlisten = await listen<Cache>("quota-update", (event) => {
      remaining = event.payload.remaining;
      total = event.payload.total;
      isOffline = event.payload.is_offline;
      lastUpdated = event.payload.last_updated;
      source = event.payload.source;
    });

    return () => unlisten();
  });
</script>

<main class="widget" class:offline={isOffline}>
  <div class="row-top">
    <span class="label">Antigravity</span>
    <span class="dot" class:dot-blue={!isOffline}></span>
  </div>

  <div class="row-mid">
    <span class="count">{remaining.toLocaleString()}</span>
    <span class="of">/ {total.toLocaleString()}</span>
  </div>

  <div class="row-bottom">
    <span class="meta">{isOffline ? "Offline" : source === "local" ? "Local" : "Cloud"}</span>
    <span class="meta">{timeAgo}</span>
  </div>

  <div class="bar-track">
    <div class="bar-fill" style="width: {isOffline ? 0 : percent}%"></div>
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
    width: 150px;
    height: 80px;
    box-sizing: border-box;
    padding: 12px;
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
    font-size: 0.75rem;
    font-weight: 500;
    line-height: 1.1;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: #969696;
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

  .row-mid {
    display: flex;
    align-items: baseline;
    gap: 4px;
  }

  .count {
    font-size: 1.25rem;
    font-weight: 600;
    line-height: 1.2;
    letter-spacing: -0.01em;
    color: #007acc;
  }

  .widget.offline .count {
    color: #ffffff;
  }

  .of {
    font-size: 0.875rem;
    font-weight: 400;
    line-height: 1.4;
    color: #969696;
  }

  .row-bottom {
    display: flex;
    justify-content: space-between;
  }

  .meta {
    font-size: 0.6875rem;
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