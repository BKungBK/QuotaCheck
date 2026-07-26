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
    gap: 4px;
    padding: 6px 0;
    background: transparent;
    border: none;
  }

  .pool-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    line-height: 1.2;
  }

  .bar-track {
    width: 100%;
    height: 5px;
    background: var(--color-bar-track);
    border-radius: 3px;
    overflow: hidden;
  }

  .sub-row {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }

  .skeleton-text {
    height: 10px;
    border-radius: 4px;
    background: linear-gradient(
      90deg,
      var(--color-shimmer-base) 25%,
      var(--color-shimmer-highlight) 37%,
      var(--color-shimmer-base) 63%
    );
    background-size: 400% 100%;
    animation: shimmer 1.4s linear infinite;
  }

  .skeleton-bar {
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

  .name { width: 55px; }
  .name--short { width: 50px; }
  .percent { width: 25px; }
  .sub { width: 85px; height: 8px; margin-top: 1px; }
  .sub--short { width: 60px; height: 8px; margin-top: 1px; }

  @keyframes shimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
  }
</style>
