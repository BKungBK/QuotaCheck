<script lang="ts">
  import type { QuotaPool } from '$lib/types';
  import { formatResetTime, barColor } from '$lib/quota-utils';

  let { pool, isOffline, poolsLength, variant = 'desktop' }: {
    pool: QuotaPool;
    isOffline: boolean;
    poolsLength: number;
    variant?: 'desktop' | 'mobile';
  } = $props();

  const pct = $derived(Math.min(100, Math.round(pool.remaining_fraction * 100)));
  const resetText = $derived(formatResetTime(pool.reset_time));
  const color = $derived(barColor(pool.remaining_fraction, isOffline, poolsLength === 0));
</script>

<div class="pool-row" class:pool-row--desktop={variant === 'desktop'} class:pool-row--mobile={variant === 'mobile'}>
  <div class="pool-meta">
    <span class="pool-label">{pool.label}</span>
    <span class="pool-percent">{pct}%</span>
  </div>
  <div class="bar-track">
    <div
      class="bar-fill"
      class:bar-fill--low={pool.remaining_fraction <= 0.2}
      style="width: {isOffline ? 0 : pool.remaining_fraction * 100}%; background: {color}"
    ></div>
  </div>
  {#if resetText}
    <div class="sub-row">
      <span class="sub-meta">reset {resetText}</span>
    </div>
  {/if}
</div>

<style>
  /* ── Desktop Flat Row (Exact Commit 0b425f2) ── */
  .pool-row.pool-row--desktop {
    display: flex;
    flex-direction: column;
    gap: 3px;
    padding: 6px 0;
    background: transparent;
    border: none;
    border-radius: 0;
    transition: transform 300ms ease;
  }
  :global(.pools-container > div + div .pool-row.pool-row--desktop) {
    border-top: 1px solid var(--color-separator);
  }
  .pool-row--desktop .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .pool-row--desktop .pool-label {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--color-ink-high);
    line-height: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
  }
  .pool-row--desktop .pool-percent {
    font-size: 0.75rem;
    font-weight: 600;
    color: var(--color-ink-high);
    line-height: 1;
    flex-shrink: 0;
  }
  .pool-row--desktop .bar-track {
    width: 100%;
    height: 5px;
    background: var(--color-bar-track);
    border-radius: 4px;
    overflow: hidden;
  }
  .pool-row--desktop .bar-fill {
    height: 100%;
    border-radius: 4px;
    will-change: width;
    transition: width 400ms ease, background 600ms ease;
  }
  .pool-row--desktop .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  .pool-row--desktop .sub-meta {
    font-size: 0.5625rem;
    color: var(--color-ink-muted);
    letter-spacing: 0.02em;
  }

  /* ── Mobile Card View ── */
  .pool-row.pool-row--mobile {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 10px;
    background: oklch(20% 0 0 / 0.5);
    border: 1px solid oklch(26% 0 0 / 0.6);
    border-radius: 8px;
  }
  .pool-row--mobile .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .pool-row--mobile .pool-label {
    font-size: 0.875rem;
    font-weight: 600;
    color: var(--color-ink-high);
  }
  .pool-row--mobile .pool-percent {
    font-size: 0.875rem;
    font-weight: 700;
    color: var(--color-accent);
  }
  .pool-row--mobile .bar-track {
    width: 100%;
    height: 6px;
    background: var(--color-bar-track);
    border-radius: 4px;
    overflow: hidden;
  }
  .pool-row--mobile .bar-fill {
    height: 100%;
    border-radius: 6px;
    transition: width 400ms ease, background 600ms ease;
  }
  .pool-row--mobile .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  .pool-row--mobile .sub-meta {
    font-size: 0.6875rem;
    color: var(--color-ink-muted);
  }
</style>
