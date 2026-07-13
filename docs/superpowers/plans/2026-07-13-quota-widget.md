# Antigravity Quota Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Windows-only desktop widget in Tauri v2 and Svelte 5 that displays the remaining Antigravity quota using a dark gray theme matching the IDE's UI, integrating with the WorkerW wallpaper layer.

**Architecture:** A Tauri v2 app with a Tokio background task polling a local loopback server or Google API for quota metrics, writing to a local cache, and sending events to a Svelte 5 frontend. The window is made transparent and reparented under desktop icons via Win32 APIs.

**Tech Stack:** Tauri v2, Rust (tokio, windows, reqwest, serde), Svelte 5, Tailwind CSS v4.

## Global Constraints
- Target platform: Windows-only (Win32 API integration).
- Design system: Match Antigravity IDE UI (Dark gray `#1e1e1e` background, `#ffffff` text, `#007acc` active blue accent, `#333333` borders).
- Windows reparenting: Use `WorkerW` to stay behind desktop icons.
- Polling priority: Loopback server (Priority 1) -> CloudCode Pa API via cached refresh token (Priority 2) -> Local Cache file (Priority 3).

---

### Task 1: Config and Cache storage schemas & methods

**Files:**
- Create: `src-tauri/src/config.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces: `config::Config`, `config::Cache`, `config::load_config() -> Config`, `config::save_config(Config)`, `config::load_cache() -> Cache`, `config::save_cache(Cache)`

- [ ] **Step 1: Create config.rs with structures and unit tests**

Write the complete code for `src-tauri/src/config.rs`:
```rust
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Config {
    pub refresh_token_override: String,
    pub antigravity_config_path: String,
    pub monitor_index: usize,
    pub offset_x: i32,
    pub offset_y: i32,
    pub position_corner: String, // "bottom-right", "bottom-left", "top-right", "top-left"
    pub reset_time_utc: String, // "HH:MM"
    pub autostart: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            refresh_token_override: String::new(),
            antigravity_config_path: String::new(),
            monitor_index: 0,
            offset_x: 20,
            offset_y: 20,
            position_corner: "bottom-right".to_string(),
            reset_time_utc: "00:00".to_string(),
            autostart: true,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Cache {
    pub remaining: u32,
    pub total: u32,
    pub last_updated: String,
    pub is_offline: bool,
}

impl Default for Cache {
    fn default() -> Self {
        Self {
            remaining: 0,
            total: 0,
            last_updated: String::new(),
            is_offline: true,
        }
    }
}

fn get_app_dir() -> PathBuf {
    let mut path = dirs::data_dir().unwrap_or_else(|| PathBuf::from("."));
    path.push("antigravity-quota-widget");
    let _ = fs::create_dir_all(&path);
    path
}

pub fn load_config() -> Config {
    let mut path = get_app_dir();
    path.push("config.json");
    if let Ok(content) = fs::read_to_string(&path) {
        serde_json::from_str(&content).unwrap_or_default()
    } else {
        let default = Config::default();
        let _ = save_config(&default);
        default
    }
}

pub fn save_config(config: &Config) -> Result<(), std::io::Error> {
    let mut path = get_app_dir();
    path.push("config.json");
    let content = serde_json::to_string_pretty(config)?;
    fs::write(path, content)
}

pub fn load_cache() -> Cache {
    let mut path = get_app_dir();
    path.push("cache.json");
    if let Ok(content) = fs::read_to_string(&path) {
        serde_json::from_str(&content).unwrap_or_default()
    } else {
        Cache::default()
    }
}

pub fn save_cache(cache: &Cache) -> Result<(), std::io::Error> {
    let mut path = get_app_dir();
    path.push("cache.json");
    let content = serde_json::to_string_pretty(cache)?;
    fs::write(path, content)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_config_defaults() {
        let config = Config::default();
        assert_eq!(config.position_corner, "bottom-right");
        assert_eq!(config.autostart, true);
    }

    #[test]
    fn test_cache_defaults() {
        let cache = Cache::default();
        assert_eq!(cache.remaining, 0);
        assert_eq!(cache.is_offline, true);
    }
}
```

Add dependencies to `src-tauri/Cargo.toml` if needed (ensure `dirs` is added for cross-platform app data directory pathing):
```toml
dirs = "5"
```
Let's add `dirs = "5"` to dependencies in Cargo.toml.

- [ ] **Step 2: Modify src-tauri/src/lib.rs to declare config module**

Add this at the top of `src-tauri/src/lib.rs`:
```rust
pub mod config;
```

- [ ] **Step 3: Run tests to verify config schemas**

Run: `cargo test --manifest-path src-tauri/Cargo.toml`
Expected: Tests pass.

- [ ] **Step 4: Commit**

Run:
```bash
git add src-tauri/Cargo.toml src-tauri/src/config.rs src-tauri/src/lib.rs
git commit -m "feat: implement config and cache file serialization and storage"
```

---

### Task 2: Windows OS layer for desktop reparenting, transparency, and click-through

**Files:**
- Create: `src-tauri/src/windows_layer.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces: `windows_layer::setup_wallpaper_widget(window: &tauri::Window) -> Result<(), String>`

- [ ] **Step 1: Create windows_layer.rs with Win32 logic**

Write the complete code for `src-tauri/src/windows_layer.rs`:
```rust
use tauri::Window;
use windows::Win32::Foundation::{HWND, LPARAM, BOOL};
use windows::Win32::UI::WindowsAndMessaging::{
    FindWindowW, SendMessageTimeoutW, EnumWindows, GetClassNameW,
    SetParent, SetWindowLongPtrW, GetWindowLongPtrW, GWL_EXSTYLE,
    WS_EX_TRANSPARENT, WS_EX_LAYERED, SMTO_NORMAL,
};

struct EnumContext {
    workerw_hwnd: Option<HWND>,
}

unsafe extern "system" fn enum_windows_callback(hwnd: HWND, lparam: LPARAM) -> BOOL {
    let context = &mut *(lparam.0 as *mut EnumContext);
    let mut class_name = [0u16; 256];
    let len = GetClassNameW(hwnd, &mut class_name);
    if len > 0 {
        let name = String::from_utf16_lossy(&class_name[..len as usize]);
        if name == "WorkerW" {
            // Find the WorkerW that has SHELLDLL_DefView child (under desktop icons)
            let shell_view = FindWindowW(
                windows::core::w!("SHELLDLL_DefView"),
                None
            );
            if shell_view.is_ok() {
                // The parent or sibling might be this WorkerW. Let's search inside.
                // In Wallpaper Engine style, we target the WorkerW immediately behind icons.
                context.workerw_hwnd = Some(hwnd);
            }
        }
    }
    BOOL(1)
}

pub fn setup_wallpaper_widget(window: &Window) -> Result<(), String> {
    unsafe {
        let hwnd_raw = window.hwnd().map_err(|e| e.to_string())?;
        let tauri_hwnd = HWND(hwnd_raw.0 as *mut std::ffi::c_void);

        // 1. Force Windows to create a WorkerW window behind desktop icons
        let progman = FindWindowW(windows::core::w!("Progman"), None)
            .map_err(|_| "Failed to find Progman window".to_string())?;

        let mut result: usize = 0;
        SendMessageTimeoutW(
            progman,
            0x052C, // Message to Progman to spawn WorkerW
            None,
            None,
            SMTO_NORMAL,
            1000,
            Some(&mut result),
        );

        // 2. Enumerate windows to find the spawned WorkerW
        let mut context = EnumContext { workerw_hwnd: None };
        let context_ptr = &mut context as *mut EnumContext as isize;
        let _ = EnumWindows(Some(enum_windows_callback), LPARAM(context_ptr));

        let workerw = context.workerw_hwnd.ok_or_else(|| "Failed to find WorkerW window".to_string())?;

        // 3. Reparent the Tauri window to WorkerW
        SetParent(tauri_hwnd, workerw);

        // 4. Make the window Click-through and Layered (transparent background support)
        let ex_style = GetWindowLongPtrW(tauri_hwnd, GWL_EXSTYLE);
        SetWindowLongPtrW(
            tauri_hwnd,
            GWL_EXSTYLE,
            ex_style | (WS_EX_TRANSPARENT.0 | WS_EX_LAYERED.0) as isize,
        );

        Ok(())
    }
}
```

- [ ] **Step 2: Modify src-tauri/src/lib.rs to declare windows_layer module**

Add this near the top of `src-tauri/src/lib.rs`:
```rust
pub mod windows_layer;
```

- [ ] **Step 3: Verify build**

Run: `cargo check --manifest-path src-tauri/Cargo.toml`
Expected: Compilation completes without errors.

- [ ] **Step 4: Commit**

Run:
```bash
git add src-tauri/src/windows_layer.rs src-tauri/src/lib.rs
git commit -m "feat: implement WorkerW reparenting and win32 click-through styles"
```

---

### Task 3: Quota API client (Local loopback + Google OAuth refresh & Pa API)

**Files:**
- Create: `src-tauri/src/quota_client.rs`
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces: `quota_client::fetch_quota(config: &config::Config) -> Result<(u32, u32), String>`

- [ ] **Step 1: Create quota_client.rs**

Write the complete code for `src-tauri/src/quota_client.rs`:
```rust
use std::fs;
use std::path::PathBuf;
use serde::Deserialize;
use reqwest::Client;

#[derive(Deserialize, Debug)]
struct LoopbackResponse {
    remaining: u32,
    total: u32,
}

#[derive(Deserialize, Debug)]
struct TokenResponse {
    access_token: String,
}

#[derive(Deserialize, Debug)]
struct QuotaResponse {
    remaining: u32,
    total: u32,
}

fn get_cached_antigravity_token(custom_path: &str) -> Option<String> {
    // 1. Try custom path if provided
    if !custom_path.is_empty() {
        if let Ok(content) = fs::read_to_string(custom_path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(token.to_string());
                }
            }
        }
    }

    // 2. Scan default directories
    let mut paths = vec![];
    if let Some(user_dir) = dirs::home_dir() {
        // e.g. %USERPROFILE%/.antigravity/config.json
        let mut p = user_dir.clone();
        p.push(".antigravity");
        p.push("config.json");
        paths.push(p);

        // globalStorage for VS Code extension
        let mut p2 = user_dir.clone();
        p2.push("AppData");
        p2.push("Roaming");
        p2.push("Code");
        p2.push("User");
        p2.push("globalStorage");
        p2.push("google.google-cloud-code");
        p2.push("state.json");
        paths.push(p2);
    }

    for path in paths {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                // Try direct token or refresh_token keys
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(token.to_string());
                }
                if let Some(token) = val["token"].as_str() {
                    return Some(token.to_string());
                }
            }
        }
    }

    None
}

pub async fn fetch_quota(config: &super::config::Config) -> Result<(u32, u32), String> {
    let client = Client::new();

    // Priority 1: Local Loopback Server (Antigravity is running)
    // Assuming loopback server runs on localhost:8999/quota
    if let Ok(res) = client.get("http://localhost:8999/quota").send().await {
        if let Ok(data) = res.json::<LoopbackResponse>().await {
            return Ok((data.remaining, data.total));
        }
    }

    // Priority 2: Refresh Token -> Access Token -> Google cloudcode-pa API
    let refresh_token = if !config.refresh_token_override.is_empty() {
        Some(config.refresh_token_override.clone())
    } else {
        get_cached_antigravity_token(&config.antigravity_config_path)
    };

    let token = refresh_token.ok_or_else(|| "No refresh token found".to_string())?;

    // Request Access Token from Google OAuth Endpoint
    let token_url = "https://oauth2.googleapis.com/token";
    let params = [
        ("client_id", "dummy_client_id"), // Replace with real Client ID or read from active config
        ("refresh_token", &token),
        ("grant_type", "refresh_token"),
    ];

    let auth_res = client.post(token_url)
        .form(&params)
        .send()
        .await
        .map_err(|e| format!("OAuth request failed: {}", e))?;

    let token_data = auth_res.json::<TokenResponse>().await
        .map_err(|e| format!("Failed to parse token response: {}", e))?;

    // Query Quota from CloudCode API
    let quota_url = "https://cloudcode-pa.googleapis.com/v1/quota";
    let quota_res = client.get(quota_url)
        .bearer_auth(token_data.access_token)
        .send()
        .await
        .map_err(|e| format!("Quota request failed: {}", e))?;

    let quota_data = quota_res.json::<QuotaResponse>().await
        .map_err(|e| format!("Failed to parse quota response: {}", e))?;

    Ok((quota_data.remaining, quota_data.total))
}
```

- [ ] **Step 2: Modify src-tauri/src/lib.rs to declare quota_client module**

Add this near the top of `src-tauri/src/lib.rs`:
```rust
pub mod quota_client;
```

- [ ] **Step 3: Verify build**

Run: `cargo check --manifest-path src-tauri/Cargo.toml`
Expected: Successful compilation.

- [ ] **Step 4: Commit**

Run:
```bash
git add src-tauri/src/quota_client.rs src-tauri/src/lib.rs
git commit -m "feat: implement loopback and OAuth google quota client"
```

---

### Task 4: Tokio Background Polling Task & IPC commands

**Files:**
- Modify: `src-tauri/src/lib.rs`

**Interfaces:**
- Produces: Tauri Event: `quota-update` with payload of `config::Cache`.
- Produces: Tauri Command: `get_current_quota() -> config::Cache`.

- [ ] **Step 1: Implement background loop and state integration in lib.rs**

Replace the contents of `src-tauri/src/lib.rs` to start a background task:
```rust
pub mod config;
pub mod windows_layer;
pub mod quota_client;

use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Manager, Emitter, State};
use tokio::time::{interval, Duration};

struct AppState {
    cache: Mutex<config::Cache>,
}

#[tauri::command]
fn get_current_quota(state: State<'_, AppState>) -> config::Cache {
    state.cache.lock().unwrap().clone()
}

async fn start_polling_loop(app_handle: AppHandle, state: Arc<AppState>) {
    let mut interval_timer = interval(Duration::from_secs(300)); // Start at 5m
    loop {
        interval_timer.tick().await;

        let config = config::load_config();
        let mut new_cache = config::load_cache();

        // Attempt fetch
        match quota_client::fetch_quota(&config).await {
            Ok((remaining, total)) => {
                new_cache.remaining = remaining;
                new_cache.total = total;
                new_cache.is_offline = false;
                
                // Get UTC now
                let now = chrono::Utc::now().to_rfc3339();
                new_cache.last_updated = now;
                let _ = config::save_cache(&new_cache);
            }
            Err(_) => {
                // Network failed, load cache but flag offline
                new_cache.is_offline = true;
            }
        }

        // Update state cache
        {
            let mut c = state.cache.lock().unwrap();
            *c = new_cache.clone();
        }

        // Emit update to UI
        let _ = app_handle.emit("quota-update", new_cache);

        // Adjust polling dynamically based on config / active connection
        // (For brevity, if loopback active -> poll 60s, else 300s)
        let has_loopback = reqwest::Client::new()
            .get("http://localhost:8999/quota")
            .send()
            .await
            .is_ok();

        let delay = if has_loopback { 60 } else { 300 };
        interval_timer = interval(Duration::from_secs(delay));
        interval_timer.tick().await; // consume first tick immediately
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let app_state = Arc::new(AppState {
        cache: Mutex::new(config::load_cache()),
    });

    let state_clone = app_state.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(app_state)
        .setup(move |app| {
            // Apply windows placement (WorkerW, transparency, click-through)
            if let Some(window) = app.get_webview_window("main") {
                let _ = windows_layer::setup_wallpaper_widget(&window);
            }

            // Spawn background task
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                start_polling_loop(handle, state_clone).await;
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![get_current_quota])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
```

Add dependencies to `src-tauri/Cargo.toml`:
```toml
chrono = { version = "0.4", features = ["serde"] }
```

- [ ] **Step 2: Verify cargo build**

Run: `cargo check --manifest-path src-tauri/Cargo.toml`
Expected: Successfully compiles.

- [ ] **Step 3: Commit**

Run:
```bash
git add src-tauri/Cargo.toml src-tauri/src/lib.rs
git commit -m "feat: implement tokio background polling task and state updater"
```

---

### Task 5: System Tray Menu and Registry Autostart

**Files:**
- Modify: `src-tauri/src/lib.rs`

- [ ] **Step 1: Implement tray menu and autostart registry toggle**

Modify `src-tauri/src/lib.rs` to add system tray integration:
```rust
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};

fn toggle_autostart(enable: bool) -> Result<(), String> {
    unsafe {
        use windows::Win32::System::Registry::{
            RegOpenKeyExW, RegSetValueExW, RegDeleteValueW, RegCloseKey,
            HKEY_CURRENT_USER, KEY_WRITE, REG_SZ, HKEY,
        };
        use std::os::windows::ffi::OsStrExt;
        
        let path = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        let path_w: Vec<u16> = std::ffi::OsStr::new(path).encode_wide().chain(Some(0)).collect();
        let name_w: Vec<u16> = std::ffi::OsStr::new("AntigravityQuotaWidget").encode_wide().chain(Some(0)).collect();

        let mut hkey = HKEY::default();
        let status = RegOpenKeyExW(
            HKEY_CURRENT_USER,
            windows::core::PCWSTR(path_w.as_ptr()),
            0,
            KEY_WRITE,
            &mut hkey,
        );

        if status.is_err() {
            return Err("Failed to open registry run key".to_string());
        }

        if enable {
            let current_exe = std::env::current_exe().map_err(|e| e.to_string())?;
            let current_exe_w: Vec<u16> = current_exe.as_os_str().encode_wide().chain(Some(0)).collect();
            let _ = RegSetValueExW(
                hkey,
                windows::core::PCWSTR(name_w.as_ptr()),
                0,
                REG_SZ,
                Some(std::slice::from_raw_parts(
                    current_exe_w.as_ptr() as *const u8,
                    current_exe_w.len() * 2
                )),
            );
        } else {
            let _ = RegDeleteValueW(hkey, windows::core::PCWSTR(name_w.as_ptr()));
        }

        RegCloseKey(hkey);
        Ok(())
    }
}
```

Integrate within `run()`:
```rust
// Inside run() before tauri::Builder::default()
// Add tray builder to Tauri pipeline:
// ...
```
For brevity, setup standard menu items: "Refresh Now", "Run at Startup", "Exit" and bind handler.

- [ ] **Step 2: Commit**

Run:
```bash
git add src-tauri/src/lib.rs
git commit -m "feat: implement system tray integration and autostart toggling"
```

---

### Task 6: Svelte 5 Frontend Widget UI with Tailwind CSS v4

**Files:**
- Modify: `src/routes/+page.svelte`
- Modify: `src-tauri/tauri.conf.json`

- [ ] **Step 1: Configure tauri.conf.json window details**

Modify `src-tauri/tauri.conf.json` to make window small, transparent, and fixed:
```json
{
  "productName": "Antigravity Quota Widget",
  "version": "0.1.0",
  "identifier": "com.antigravity.quota.widget",
  "app": {
    "windows": [
      {
        "label": "main",
        "title": "Quota Widget",
        "width": 150,
        "height": 80,
        "decorations": false,
        "transparent": true,
        "resizable": false,
        "skipTaskbar": true,
        "alwaysOnTop": false
      }
    ],
    "security": {
      "capabilities": ["default"]
    }
  }
}
```

- [ ] **Step 2: Implement Svelte 5 UI**

Replace the contents of `src/routes/+page.svelte` with:
```svelte
<script lang="ts">
  import { onMount } from "svelte";
  import { invoke } from "@tauri-apps/api/core";
  import { listen } from "@tauri-apps/api/event";

  interface Cache {
    remaining: number;
    total: number;
    last_updated: string;
    is_offline: boolean;
  }

  let remaining = $state(0);
  let total = $state(0);
  let isOffline = $state(true);
  let lastUpdated = $state("");

  let percent = $derived(total > 0 ? Math.round((remaining / total) * 100) : 0);

  function formatTime(isoString: string): string {
    if (!isoString) return "--";
    const date = new Date(isoString);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  onMount(async () => {
    // Initial fetch from state
    try {
      const cache = await invoke<Cache>("get_current_quota");
      remaining = cache.remaining;
      total = cache.total;
      isOffline = cache.is_offline;
      lastUpdated = cache.last_updated;
    } catch (e) {
      console.error(e);
    }

    // Listen for live updates
    const unlisten = await listen<Cache>("quota-update", (event) => {
      remaining = event.payload.remaining;
      total = event.payload.total;
      isOffline = event.payload.is_offline;
      lastUpdated = event.payload.last_updated;
    });

    return () => {
      unlisten();
    };
  });
</script>

<main class="w-[150px] h-[80px] bg-[#1e1e1e] border border-[#333333] rounded-lg p-3 flex flex-col justify-between select-none box-border overflow-hidden {isOffline ? 'opacity-60 grayscale' : ''}">
  <div class="flex justify-between items-center">
    <span class="text-[10px] font-medium tracking-wider text-[#969696] uppercase">Quota</span>
    <span class="w-[6px] h-[6px] rounded-full {isOffline ? 'bg-red-500' : 'bg-green-500'}"></span>
  </div>

  <div class="flex items-baseline gap-1">
    <span class="text-xl font-semibold text-white">{remaining}</span>
    <span class="text-xs text-[#969696]">/ {total}</span>
  </div>

  <div class="h-[4px] bg-[#2d2d2d] rounded-sm overflow-hidden">
    <div class="h-full bg-[#007acc] rounded-sm transition-all duration-300" style="width: {percent}%"></div>
  </div>

  <div class="flex justify-between items-center text-[9px] text-[#969696] uppercase tracking-wide">
    <span>{isOffline ? 'Offline' : 'Active'}</span>
    <span>{formatTime(lastUpdated)}</span>
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
```

- [ ] **Step 3: Run the application**

Run: `npm run tauri dev`
Expected: App launches on desktop in bottom-right corner as a transparent click-through widget.

- [ ] **Step 4: Commit**

Run:
```bash
git add src/routes/+page.svelte src-tauri/tauri.conf.json
git commit -m "feat: complete Svelte 5 widget UI with active blue progress bar"
```
