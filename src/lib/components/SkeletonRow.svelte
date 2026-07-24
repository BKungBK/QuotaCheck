<script lang="ts">
  let { short = false }: { short?: boolean } = $props();
</script>

<div class="pool-row skeleton">
  <div class="pool-meta">
    <div class="skeleton-text name" class:name--short={short}></div>
    <div class="skeleton-text percent"></div>
  </div>
  <div class="bar-track">
    <div class="bar-fill skeleton-bar"></div>
  </div>
  <div class="sub-row">
    <div class="skeleton-text sub" class:sub--short={short}></div>
  </div>
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
  .bar-track {
    width: 100%;
    height: 8px;
    background: var(--color-bar-track);
    border-radius: 6px;
    overflow: hidden;
  }
  .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
  /* Skeleton shimmer — relies on --color-shimmer-* tokens from parent :root */
  .skeleton-text {
    height: 0.75rem;
    border-radius: 4px;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 50%,
      var(--color-shimmer-base) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.6s ease-in-out infinite;
  }
  .skeleton-bar {
    width: 60%;
    height: 100%;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 50%,
      var(--color-shimmer-base) 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.6s ease-in-out infinite;
  }
  .name        { width: 70%; }
  .name--short { width: 45%; }
  .percent     { width: 2rem; }
  .sub         { width: 55%; }
  .sub--short  { width: 35%; }

  @keyframes shimmer {
    0%   { background-position: -200% 0; }
    100% { background-position:  200% 0; }
  }
</style>
