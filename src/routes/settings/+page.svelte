<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";

  interface Config {
    refresh_token_override: string;
    antigravity_config_path: string;
    monitor_index: number;
    offset_x: number;
    offset_y: number;
    position_corner: string;
    reset_time_utc: string;
    autostart: boolean;
  }

  let config = $state<Config>({
    refresh_token_override: "",
    antigravity_config_path: "",
    monitor_index: 0,
    offset_x: 20,
    offset_y: 20,
    position_corner: "bottom-right",
    reset_time_utc: "00:00",
    autostart: true,
  });

  let statusMsg = $state("");
  let isSaving = $state(false);

  onMount(async () => {
    try {
      const loaded = await invoke<Config>("get_config");
      config = loaded;
    } catch (e) {
      console.error("Failed to load config", e);
    }
  });

  async function handleSave() {
    isSaving = true;
    statusMsg = "";
    try {
      await invoke("save_config", { newConfig: config });
      statusMsg = "Saved & Applied Successfully!";
      setTimeout(() => { statusMsg = ""; }, 3000);
    } catch (e) {
      statusMsg = `Error: ${e}`;
    } finally {
      isSaving = false;
    }
  }
</script>

<div class="settings-container">
  <h2>QuotaCheck Settings</h2>

  <form onsubmit={(e) => { e.preventDefault(); handleSave(); }}>
    <div class="form-group">
      <label for="corner">Position Corner</label>
      <select id="corner" bind:value={config.position_corner}>
        <option value="bottom-right">Bottom Right</option>
        <option value="bottom-left">Bottom Left</option>
        <option value="top-right">Top Right</option>
        <option value="top-left">Top Left</option>
      </select>
    </div>

    <div class="form-row">
      <div class="form-group half">
        <label for="offset_x">Offset X (px)</label>
        <input id="offset_x" type="number" bind:value={config.offset_x} min="0" max="500" />
      </div>
      <div class="form-group half">
        <label for="offset_y">Offset Y (px)</label>
        <input id="offset_y" type="number" bind:value={config.offset_y} min="0" max="500" />
      </div>
    </div>

    <div class="form-group">
      <label for="monitor">Display Monitor Index</label>
      <select id="monitor" bind:value={config.monitor_index}>
        <option value={0}>Monitor 0 (Primary)</option>
        <option value={1}>Monitor 1 (Secondary)</option>
        <option value={2}>Monitor 2</option>
        <option value={3}>Monitor 3</option>
      </select>
    </div>

    <div class="form-group">
      <label for="reset_time">Daily Reset Time (UTC)</label>
      <input id="reset_time" type="text" bind:value={config.reset_time_utc} placeholder="00:00" />
    </div>

    <div class="form-group checkbox-group">
      <label for="autostart">
        <input id="autostart" type="checkbox" bind:checked={config.autostart} />
        Run at Startup
      </label>
    </div>

    <div class="form-actions">
      <button type="submit" disabled={isSaving}>
        {isSaving ? "Saving..." : "Save & Apply"}
      </button>
      {#if statusMsg}
        <span class="status-msg">{statusMsg}</span>
      {/if}
    </div>
  </form>
</div>

<style>
  :global(body) {
    margin: 0;
    padding: 0;
    font-family: "Inter", system-ui, sans-serif;
    background: oklch(14% 0 0);
    color: oklch(90% 0 0);
    user-select: none;
  }

  .settings-container {
    padding: 20px;
    box-sizing: border-box;
  }

  h2 {
    font-size: 1rem;
    font-weight: 600;
    margin-top: 0;
    margin-bottom: 16px;
    color: oklch(95% 0 0);
    letter-spacing: -0.01em;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .form-row {
    display: flex;
    gap: 12px;
  }
  .form-group.half {
    flex: 1;
  }

  .form-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  label {
    font-size: 0.75rem;
    font-weight: 500;
    color: oklch(70% 0 0);
  }

  input[type="number"],
  input[type="text"],
  select {
    padding: 8px 10px;
    border-radius: 6px;
    border: 1px solid oklch(28% 0 0);
    background: oklch(20% 0 0);
    color: oklch(90% 0 0);
    font-size: 0.8125rem;
    outline: none;
    transition: border-color 0.2s;
  }

  input:focus, select:focus {
    border-color: oklch(55% 0 0);
  }

  .checkbox-group label {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
  }

  .form-actions {
    margin-top: 8px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  button {
    padding: 9px 16px;
    background: oklch(40% 0 0);
    color: oklch(95% 0 0);
    border: none;
    border-radius: 6px;
    font-weight: 600;
    font-size: 0.8125rem;
    cursor: pointer;
    transition: background 0.2s;
  }

  button:hover:not(:disabled) {
    background: oklch(50% 0 0);
  }

  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .status-msg {
    font-size: 0.75rem;
    color: oklch(75% 0.15 140);
  }
</style>
