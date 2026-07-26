<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import type { Config } from "$lib/types";

  let config = $state<Config>({
    refresh_token_override: "",
    antigravity_config_path: "",
    monitor_index: 0,
    offset_x: 20,
    offset_y: 20,
    position_corner: "bottom-right",
    reset_time_utc: "00:00",
    autostart: true,
    preferred_account: "",
    mask_account_email: false,
    quota_source_mode: "auto",
    display_mode: "summary",
  });

  let statusMsg = $state("");
  let isSaving = $state(false);
  let monitorCount = $state(1);

  onMount(async () => {
    try {
      config = await invoke<Config>("get_config");
    } catch (e) {
      console.error("Failed to load config", e);
    }

    try {
      const count = await invoke<number>("get_monitor_count");
      monitorCount = Math.max(1, count);
    } catch (e) {
      console.error("Failed to load monitor count", e);
    }
  });

  async function handleSave() {
    isSaving = true;
    statusMsg = "";

    try {
      await invoke("save_config", { newConfig: config });
      statusMsg = "Saved & Applied Successfully!";
      setTimeout(() => {
        statusMsg = "";
      }, 3000);
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
        {#each Array(Math.max(monitorCount, (config.monitor_index ?? 0) + 1)) as _, i (i)}
          <option value={i}>
            Monitor {i} {i === 0 ? "(Primary)" : i === 1 ? "(Secondary)" : ""}
          </option>
        {/each}
      </select>
    </div>

    <div class="form-group">
      <label for="reset_time">Daily Reset Time (UTC)</label>
      <input id="reset_time" type="text" bind:value={config.reset_time_utc} placeholder="00:00" />
    </div>

    <div class="form-group">
      <label for="quota_source_mode">Quota Source Mode</label>
      <select id="quota_source_mode" bind:value={config.quota_source_mode}>
        <option value="auto">Auto (Local First -> Cloud Fallback)</option>
        <option value="local">Local Language Server Only</option>
        <option value="cloud">Cloud OAuth API Only</option>
      </select>
    </div>

    <div class="form-group">
      <label for="display_mode">Display Mode</label>
      <select id="display_mode" bind:value={config.display_mode}>
        <option value="summary">Summary (Gemini & Claude Merged)</option>
        <option value="detailed">Detailed (All Individual Models)</option>
      </select>
    </div>

    <div class="form-group">
      <label for="preferred_account">Preferred Account Email (Optional)</label>
      <input id="preferred_account" type="text" bind:value={config.preferred_account} placeholder="user@gmail.com" />
    </div>

    <div class="form-group checkbox-group">
      <label for="mask_account_email">
        <input id="mask_account_email" type="checkbox" bind:checked={config.mask_account_email} />
        Mask Email on Widget (Privacy)
      </label>
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
    background: var(--color-bg);
    color: var(--color-ink-high);
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
    color: var(--color-ink-high);
    letter-spacing: 0;
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
    color: var(--color-ink-mid);
  }

  input[type="number"],
  input[type="text"],
  select {
    padding: 8px 10px;
    border-radius: 6px;
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-ink-high);
    font-size: 0.8125rem;
    outline: none;
    transition: border-color 0.2s;
  }

  input:focus,
  select:focus {
    border-color: var(--color-accent);
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
    background: var(--color-surface-variant);
    color: var(--color-ink-high);
    border: none;
    border-radius: 6px;
    font-weight: 600;
    font-size: 0.8125rem;
    cursor: pointer;
    transition: background 0.2s;
  }

  button:hover:not(:disabled) {
    background: var(--color-ink-dim);
  }

  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .status-msg {
    font-size: 0.75rem;
    color: var(--color-dot-live);
  }
</style>
