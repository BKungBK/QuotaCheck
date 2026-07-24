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
  let tokenStatusMsg = $state("");
  let isSaving = $state(false);
  let isSavingToken = $state(false);
  let monitorCount = $state(1);
  let isMobilePlatform = $state(false);
  let refreshTokenInput = $state("");

  onMount(async () => {
    const ua = navigator.userAgent.toLowerCase();
    isMobilePlatform = ua.includes("android") || ua.includes("iphone") || ua.includes("ipad") || ua.includes("mobile") || (window.innerWidth <= 600 && window.innerHeight > window.innerWidth);

    try {
      const loaded = await invoke<Config>("get_config");
      config = loaded;
      if (config.refresh_token_override) {
        refreshTokenInput = config.refresh_token_override;
      }
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

  async function handleSaveToken() {
    if (!refreshTokenInput.trim()) return;
    isSavingToken = true;
    tokenStatusMsg = "";
    try {
      await invoke("plugin:quota|saveRefreshToken", { token: refreshTokenInput.trim() });
      tokenStatusMsg = "Token saved to Android Secure Storage!";
    } catch (_e) {
      config.refresh_token_override = refreshTokenInput.trim();
      await invoke("save_config", { newConfig: config });
      tokenStatusMsg = "Token saved to Config!";
    } finally {
      isSavingToken = false;
      setTimeout(() => { tokenStatusMsg = ""; }, 3000);
    }
  }

  async function handleSave() {
    isSaving = true;
    statusMsg = "";
    try {
      if (refreshTokenInput.trim()) {
        config.refresh_token_override = refreshTokenInput.trim();
      }
      await invoke("save_config", { newConfig: config });
      statusMsg = "Saved & Applied Successfully!";
      setTimeout(() => { statusMsg = ""; }, 3000);
    } catch (e) {
      statusMsg = `Error: ${e}`;
    } finally {
      isSaving = false;
    }
  }

  function goBack() {
    window.location.href = "/";
  }
</script>

{#if isMobilePlatform}
  <!-- 📱 Dedicated Mobile Settings View -->
  <div class="settings-container mobile-view">
    <div class="header-nav">
      <button type="button" class="btn-back" onclick={goBack}>
        ← Back
      </button>
      <h2>QuotaCheck Settings</h2>
    </div>

    <div class="token-card">
      <div class="card-header">
        <h3>🔑 OAuth Refresh Token</h3>
      </div>
      <p class="card-desc">
        Paste Refresh Token from <code>C:\Users\KK\.antigravity_cockpit\credentials.json</code> on your PC.
      </p>

      <div class="token-input-group">
        <input
          type="password"
          placeholder="Paste Refresh Token (1//0...)"
          bind:value={refreshTokenInput}
        />
        <button type="button" class="btn-primary" onclick={handleSaveToken} disabled={isSavingToken}>
          {isSavingToken ? "Saving..." : "Save & Sync"}
        </button>
      </div>
      {#if tokenStatusMsg}
        <span class="status-msg green">{tokenStatusMsg}</span>
      {/if}
    </div>

    <form onsubmit={(e) => { e.preventDefault(); handleSave(); }}>
      <div class="form-group">
        <label for="m_quota_source_mode">Quota Source Mode</label>
        <select id="m_quota_source_mode" bind:value={config.quota_source_mode}>
          <option value="auto">Auto (Local First → Cloud Fallback)</option>
          <option value="local">Local Language Server Only</option>
          <option value="cloud">Cloud OAuth API Only</option>
        </select>
      </div>

      <div class="form-group">
        <label for="m_display_mode">Display Mode</label>
        <select id="m_display_mode" bind:value={config.display_mode}>
          <option value="summary">Summary (Gemini & Claude Merged)</option>
          <option value="detailed">Detailed (All Individual Models)</option>
        </select>
      </div>

      <div class="form-actions">
        <button type="submit" class="btn-primary" disabled={isSaving}>
          {isSaving ? "Saving..." : "Save All Settings"}
        </button>
        {#if statusMsg}
          <span class="status-msg">{statusMsg}</span>
        {/if}
      </div>
    </form>
  </div>
{:else}
  <!-- 🖥️ Original PC Desktop Settings View (Exact commit 0b425f2) -->
  <div class="settings-container desktop-view">
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
          {#each Array(Math.max(monitorCount, (config.monitor_index ?? 0) + 1)) as _, i}
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
          <option value="auto">Auto (Local First → Cloud Fallback)</option>
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
{/if}

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

  .settings-container.mobile-view {
    max-width: 500px;
    margin: 0 auto;
    padding: 16px;
  }

  .header-nav {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  .btn-back {
    padding: 6px 12px;
    background: oklch(22% 0 0);
    border: 1px solid oklch(30% 0 0);
    border-radius: 6px;
    color: oklch(90% 0 0);
    font-size: 0.8125rem;
    font-weight: 600;
    cursor: pointer;
  }

  h2 {
    font-size: 1rem;
    font-weight: 600;
    margin-top: 0;
    margin-bottom: 16px;
    color: oklch(95% 0 0);
    letter-spacing: -0.01em;
  }

  .mobile-view h2 {
    margin-bottom: 0;
    font-size: 1.125rem;
  }

  .token-card {
    background: oklch(18% 0 0);
    border: 1px solid oklch(28% 0 0);
    border-radius: 10px;
    padding: 14px;
    margin-bottom: 20px;
  }

  .token-card h3 {
    margin: 0 0 6px 0;
    font-size: 0.9375rem;
    color: oklch(95% 0 0);
  }

  .card-desc {
    margin: 0 0 12px 0;
    font-size: 0.75rem;
    color: oklch(65% 0 0);
    line-height: 1.4;
  }

  .card-desc code {
    background: oklch(12% 0 0);
    padding: 2px 5px;
    border-radius: 4px;
    color: oklch(80% 0.1 230);
  }

  .token-input-group {
    display: flex;
    gap: 8px;
  }

  .token-input-group input {
    flex: 1;
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
  input[type="password"],
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

  .btn-primary {
    background: oklch(48% 0.16 230);
    color: #fff;
  }

  button:hover:not(:disabled) {
    background: oklch(50% 0 0);
  }

  .btn-primary:hover:not(:disabled) {
    background: oklch(55% 0.16 230);
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
