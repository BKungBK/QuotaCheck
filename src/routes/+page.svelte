<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";

  interface Cache {
    remaining: number;
    total: number;
    last_updated: string;
    is_offline: boolean;
  }

  let remaining = $state(0);
  let total = $state(0);
  let isOffline = $state(true);
  let lastUpdated = $state("");

  let percent = $derived(total > 0 ? Math.round((remaining / total) * 100) : 0);

  function formatTime(isoString: string): string {
    if (!isoString) return "--:--";
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (_) {
      return "--:--";
    }
  }

  onMount(async () => {
    // Initial fetch from state
    try {
      const cache = await invoke<Cache>("get_current_quota");
      remaining = cache.remaining;
      total = cache.total;
      isOffline = cache.is_offline;
      lastUpdated = cache.last_updated;
    } catch (e) {
      console.error("Failed to load initial cache", e);
    }

    // Listen for live updates
    const unlisten = await listen<Cache>("quota-update", (event) => {
      remaining = event.payload.remaining;
      total = event.payload.total;
      isOffline = event.payload.is_offline;
      lastUpdated = event.payload.last_updated;
    });

    return () => {
      unlisten();
    };
  });
</script>

<main class="w-full h-full bg-[#1e1e1e] border border-[#333333] rounded-lg p-3 flex flex-col justify-between select-none box-border overflow-hidden {isOffline ? 'opacity-60 grayscale' : ''}">
  <div class="flex justify-between items-center">
    <span class="text-[10px] font-medium tracking-wider text-[#969696] uppercase">Quota</span>
    <span class="w-[6px] h-[6px] rounded-full {isOffline ? 'bg-red-500' : 'bg-green-500'}"></span>
  </div>

  <div class="flex items-baseline gap-1">
    <span class="text-xl font-semibold text-white">{remaining}</span>
    <span class="text-xs text-[#969696]">/ {total}</span>
  </div>

  <div class="h-[4px] bg-[#2d2d2d] rounded-sm overflow-hidden">
    <div class="h-full bg-[#007acc] rounded-sm transition-all duration-300" style="width: {percent}%"></div>
  </div>

  <div class="flex justify-between items-center text-[9px] text-[#969696] uppercase tracking-wide">
    <span>{isOffline ? 'Offline' : 'Active'}</span>
    <span>{formatTime(lastUpdated)}</span>
  </div>
</main>

<style>
  :global(body) {
    margin: 0;
    padding: 0;
    background: transparent !important;
    overflow: hidden;
  }
</style>
