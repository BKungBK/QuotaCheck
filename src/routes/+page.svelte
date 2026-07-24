<script lang="ts">
  import { onMount } from "svelte";
  import DesktopWidget from "$lib/components/DesktopWidget.svelte";
  import MobileApp from "$lib/components/MobileApp.svelte";

  let isMobilePlatform = $state(false);

  onMount(() => {
    const ua = navigator.userAgent.toLowerCase();
    // Strictly check for mobile device User-Agent (Android / iOS / Mobile device)
    // Do NOT check screen width because PC Desktop widget window is 300px wide!
    isMobilePlatform = ua.includes("android") || ua.includes("iphone") || ua.includes("ipad") || ua.includes("ipod") || (ua.includes("mobile") && !ua.includes("windows"));
  });
</script>

{#if isMobilePlatform}
  <MobileApp />
{:else}
  <DesktopWidget />
{/if}