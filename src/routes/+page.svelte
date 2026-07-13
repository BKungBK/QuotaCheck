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

  let percent = $derived(total > 0 ? Math.round((remaining / total) * 100) : 0);

  let timeAgo = $state("Never");
  $effect(() => {
    const updateTime = () => {
      if (!lastUpdated) {
        timeAgo = "Never";
        return;
      }
      const diffMs = Date.now() - new Date(lastUpdated).getTime();
      const diffSecs = Math.floor(diffMs / 1000);
      if (diffSecs < 10) {
        timeAgo = "Just now";
      } else if (diffSecs < 60) {
        timeAgo = `${diffSecs}s ago`;
      } else {
        const mins = Math.floor(diffSecs / 60);
        if (mins < 60) {
          timeAgo = `${mins}m ago`;
        } else {
          const hrs = Math.floor(mins / 60);
          timeAgo = `${hrs}h ago`;
        }
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

    return () => {
      unlisten();
    };
  });
</script>

<main class="w-full h-full p-4 flex flex-col justify-between box-border bg-[#0d0e11]/90 backdrop-blur-md border border-[#21262d] rounded-2xl select-none overflow-hidden relative">
  <!-- Top bar -->
  <div class="flex justify-between items-center w-full z-10">
    <div class="flex items-center gap-1.5">
      <svg class="w-3.5 h-3.5 text-[#58a6ff]" viewBox="0 0 16 16" fill="currentColor">
        <path d="M8 0a8 8 0 100 16A8 8 0 008 0zm0 14.5a6.5 6.5 0 110-13 6.5 6.5 0 010 13z"/>
        <path d="M8 3a5 5 0 00-5 5h2a3 3 0 013-3V3z"/>
      </svg>
      <span class="text-[10px] font-semibold tracking-wider text-[#8b949e] uppercase">Antigravity</span>
    </div>

    <!-- Status badge -->
    {#if isOffline}
      <div class="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-[#311c1e] border border-[#f85149]/20 text-[9px] text-[#f85149] font-medium">
        <span class="w-1 h-1 rounded-full bg-[#f85149] animate-pulse"></span>
        Offline
      </div>
    {:else if source === "local"}
      <div class="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-[#102a1b] border border-[#39d353]/20 text-[9px] text-[#39d353] font-medium">
        <span class="w-1 h-1 rounded-full bg-[#39d353]"></span>
        Local
      </div>
    {:else}
      <div class="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-[#132641] border border-[#58a6ff]/20 text-[9px] text-[#58a6ff] font-medium">
        <span class="w-1 h-1 rounded-full bg-[#58a6ff]"></span>
        Cloud
      </div>
    {/if}
  </div>

  <!-- Circle Gauge Center -->
  <div class="flex justify-center items-center relative my-auto">
    <svg class="w-28 h-28 transform -rotate-90">
      <defs>
        <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>
      <!-- Track -->
      <circle
        cx="56"
        cy="56"
        r="48"
        class="stroke-[#161b22]"
        stroke-width="7"
        fill="transparent"
      />
      <!-- Progress Fill -->
      {#if !isOffline && total > 0}
        <circle
          cx="56"
          cy="56"
          r="48"
          class="stroke-[#58a6ff] transition-all duration-500 ease-out"
          stroke-width="7"
          fill="transparent"
          stroke-dasharray="301.6"
          stroke-dashoffset={301.6 - (301.6 * percent) / 100}
          stroke-linecap="round"
          filter="url(#glow)"
        />
      {/if}
    </svg>

    <!-- Absolute Center Text -->
    <div class="absolute flex flex-col items-center justify-center text-center">
      <span class="text-2xl font-bold tracking-tight text-white">{remaining}</span>
      <span class="text-[9px] font-semibold text-[#8b949e] uppercase mt-0.5">/ {total}</span>
    </div>
  </div>

  <!-- Bottom Details Grid -->
  <div class="grid grid-cols-2 gap-2 border-t border-[#21262d] pt-2.5 w-full z-10">
    <div class="flex flex-col">
      <span class="text-[7.5px] font-bold text-[#8b949e] tracking-wider uppercase">Source</span>
      <span class="text-[10px] font-medium text-white mt-0.5">
        {isOffline ? "Disconnected" : source === "local" ? "Local Agent" : "Cloud Pa API"}
      </span>
    </div>
    <div class="flex flex-col items-end">
      <span class="text-[7.5px] font-bold text-[#8b949e] tracking-wider uppercase">Updated</span>
      <span class="text-[10px] font-medium text-white mt-0.5">{timeAgo}</span>
    </div>
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
