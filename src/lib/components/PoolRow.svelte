<script lang="ts">
  import { flip } from 'svelte/animate';
  import type { QuotaPool } from '$lib/types';
  import { formatResetTime, barColor } from '$lib/quota-utils';

  let { pool, isOffline, poolsLength }: {
    pool: QuotaPool;
    isOffline: boolean;
    poolsLength: number;
  } = $props();

  const pct = $derived(Math.min(100, Math.round(pool.remaining_fraction * 100)));
  const resetText = $derived(formatResetTime(pool.reset_time));
  const color = $derived(barColor(pool.remaining_fraction, isOffline, poolsLength === 0));
</script>

<div class="pool-row" animate:flip={{ duration: 300 }}>
  <div class="pool-meta">
    <span class="pool-label">{pool.label}</span>
    <span class="pool-percent">{pct}%</span>
  </div>
  <div class="bar-track">
    <div
      class="bar-fill"
      class:bar-fill--low={pool.remaining_fraction <= 0.2}
      style="width: {pool.remaining_fraction * 100}%; background: {color}"
    ></div>
  </div>
  {#if resetText}
    <div class="sub-row">
      <span class="sub-meta">reset {resetText}</span>
    </div>
  {/if}
</div>

<style>
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
</style>
