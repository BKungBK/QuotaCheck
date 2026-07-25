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

  const isGemini = $derived(pool.label.toLowerCase().includes('gemini'));
  const isClaude = $derived(pool.label.toLowerCase().includes('claude'));
  const isGPT = $derived(pool.label.toLowerCase().includes('gpt') || pool.label.toLowerCase().includes('openai'));

  const statusColorClass = $derived(
    pool.remaining_fraction > 0.5 ? 'status-good' : pool.remaining_fraction >= 0.2 ? 'status-warn' : 'status-danger'
  );
</script>

<div class="pool-row" class:pool-row--desktop={variant === 'desktop'} class:pool-row--mobile={variant === 'mobile'}>
  {#if variant === 'mobile'}
    <div class="mobile-row-header">
      <div class="brand-title-wrap">
        <span 
          class="brand-dot" 
          class:brand-dot--gemini={isGemini} 
          class:brand-dot--claude={isClaude}
          class:brand-dot--gpt={isGPT}
        ></span>
        <span class="pool-label">{pool.label}</span>
      </div>
      <span class="pool-percent {statusColorClass}">{pct}%</span>
    </div>

    <div class="bar-track bar-track--mobile">
      <div
        class="bar-fill {statusColorClass}"
        style="width: {isOffline ? 0 : pool.remaining_fraction * 100}%; background: {color}"
      ></div>
    </div>

    <div class="mobile-row-footer">
      {#if resetText}
        <span class="sub-meta"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="clock-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg> Reset in {resetText}</span>
      {:else}
        <span class="sub-meta sub-meta--ready">Ready & Active</span>
      {/if}
      <span class="details-hint">Tap for details ›</span>
    </div>
  {:else}
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
  {/if}
</div>

<style>
  /* ── Desktop Flat Row ── */
  .pool-row.pool-row--desktop {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 6px 0;
    background: transparent;
    border: none;
    border-radius: 0;
    transition: transform 300ms ease;
  }
  :global(.pools-container > div + div .pool-row.pool-row--desktop) {
    border-top: 1px solid var(--color-separator);
    padding-top: 6px;
    margin-top: 3px;
  }

  .pool-row--desktop .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    line-height: 1.2;
  }
  .pool-row--desktop .pool-label {
    font-size: 0.75rem;
    font-weight: 500;
    letter-spacing: -0.01em;
    color: var(--color-ink-high);
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    min-width: 0;
  }
  .pool-row--desktop .pool-percent {
    font-size: 0.75rem;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--color-ink-high);
    line-height: 1.2;
    flex-shrink: 0;
  }
  .pool-row--desktop .bar-track {
    width: 100%;
    height: 5px;
    background: var(--color-bar-track);
    border-radius: 3px;
    overflow: hidden;
  }
  .pool-row--desktop .bar-fill {
    height: 100%;
    border-radius: 3px;
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
    font-weight: 400;
    color: var(--color-ink-muted);
    letter-spacing: 0.02em;
    font-variant-numeric: tabular-nums;
    line-height: 1.2;
  }

  /* ── Mobile Card View (Impeccable Design System) ── */
  .pool-row.pool-row--mobile {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 14px 16px;
    background: oklch(18% 0.01 260);
    border: 1px solid oklch(26% 0.02 260);
    border-radius: 12px;
    transition: background 200ms ease, transform 150ms ease, border-color 200ms ease;
  }
  .pool-row.pool-row--mobile:active {
    transform: scale(0.985);
    background: oklch(21% 0.015 260);
  }

  .mobile-row-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .brand-title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .brand-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: oklch(65% 0 0);
    flex-shrink: 0;
  }
  .brand-dot--gemini {
    background: oklch(68% 0.18 240);
    box-shadow: 0 0 8px oklch(68% 0.18 240 / 0.4);
  }
  .brand-dot--claude {
    background: oklch(68% 0.18 45);
    box-shadow: 0 0 8px oklch(68% 0.18 45 / 0.4);
  }
  .brand-dot--gpt {
    background: oklch(68% 0.18 150);
    box-shadow: 0 0 8px oklch(68% 0.18 150 / 0.4);
  }

  .pool-row--mobile .pool-label {
    font-size: 0.9375rem;
    font-weight: 600;
    letter-spacing: -0.01em;
    color: oklch(92% 0 0);
  }
  .pool-row--mobile .pool-percent {
    font-size: 1.05rem;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .status-good {
    color: oklch(85% 0.14 145);
  }
  .status-warn {
    color: oklch(82% 0.16 75);
  }
  .status-danger {
    color: oklch(75% 0.2 25);
  }

  .bar-track--mobile {
    width: 100%;
    height: 8px;
    background: oklch(24% 0.01 260);
    border-radius: 4px;
    overflow: hidden;
  }
  .bar-track--mobile .bar-fill {
    height: 100%;
    border-radius: 4px;
    transition: width 500ms ease-out, background 500ms ease;
  }

  .mobile-row-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 0.75rem;
  }
  .pool-row--mobile .sub-meta {
    display: flex;
    align-items: center;
    gap: 4px;
    color: oklch(75% 0 0); /* High contrast for reset text */
    font-weight: 500;
    font-variant-numeric: tabular-nums;
  }
  .sub-meta--ready {
    color: oklch(70% 0.08 145);
  }
  .clock-icon {
    width: 12px;
    height: 12px;
    opacity: 0.8;
  }
  .details-hint {
    color: oklch(60% 0 0);
    font-size: 0.6875rem;
  }
</style>
