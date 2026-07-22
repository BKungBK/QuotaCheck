pub mod config;
pub mod windows_layer;
pub mod quota_client;

use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Manager, Emitter, State};
use tokio::time::{sleep, Duration};
use tokio::sync::mpsc;
use tauri::menu::{Menu, MenuItem, CheckMenuItem};
use tauri::tray::TrayIconBuilder;
#[cfg(target_os = "windows")]
use windows::Win32::UI::HiDpi::{SetProcessDpiAwarenessContext, DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2};

struct AppState {
    cache: Mutex<config::Cache>,
    refresh_trigger: mpsc::Sender<()>,
    client: reqwest::Client,
    local_client: reqwest::Client,
}

#[tauri::command]
fn get_current_quota(state: State<'_, AppState>) -> config::Cache {
    state.cache.lock().unwrap().clone()
}

#[tauri::command]
fn get_config() -> config::Config {
    config::load_config()
}

#[tauri::command]
async fn save_config(app_handle: AppHandle, new_config: config::Config) -> Result<(), String> {
    config::save_config(&new_config).map_err(|e| e.to_string())?;
    let _ = toggle_autostart(new_config.autostart);
    if let Some(window) = app_handle.get_webview_window("main") {
        windows_layer::setup_with_retry(&window).await;
    }
    let _ = app_handle.emit("config-updated", new_config);
    Ok(())
}

#[tauri::command]
async fn manual_refresh_trigger(app_handle: AppHandle, state: State<'_, AppState>) -> Result<(), String> {
    let _ = app_handle.emit("refresh-started", ());
    let _ = state.refresh_trigger.send(()).await;
    Ok(())
}

fn is_near_reset(reset_time_str: &str) -> bool {
    use chrono::TimeZone;
    let now = chrono::Utc::now();
    let parts: Vec<&str> = reset_time_str.split(':').collect();
    if parts.len() != 2 {
        return false;
    }
    if let (Ok(h), Ok(m)) = (parts[0].parse::<u32>(), parts[1].parse::<u32>()) {
        let today_reset = now.date_naive().and_hms_opt(h, m, 0)
            .map(|dt| chrono::Utc.from_utc_datetime(&dt))
            .unwrap_or(now);
        
        let diff = today_reset.signed_duration_since(now).num_minutes();
        if diff >= -5 && diff <= 15 {
            return true;
        }
        let tomorrow_reset = today_reset + chrono::Duration::days(1);
        let diff_tomorrow = tomorrow_reset.signed_duration_since(now).num_minutes();
        if diff_tomorrow >= -5 && diff_tomorrow <= 15 {
            return true;
        }
    }
    false
}

struct LoopbackCache {
    result: bool,
    last_check: Option<chrono::DateTime<chrono::Utc>>,
}

fn get_loopback_cache() -> &'static Mutex<LoopbackCache> {
    static CACHE: std::sync::OnceLock<Mutex<LoopbackCache>> = std::sync::OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(LoopbackCache {
        result: false,
        last_check: None,
    }))
}

async fn check_has_loopback(client: &reqwest::Client) -> bool {
    let now = chrono::Utc::now();
    {
        if let Ok(cache) = get_loopback_cache().lock() {
            if let Some(last) = cache.last_check {
                if (now - last).num_seconds() < 300 {
                    return cache.result;
                }
            }
        }
    }

    let loopback_client = reqwest::Client::builder()
        .timeout(Duration::from_millis(500))
        .build()
        .unwrap_or_else(|_| client.clone());

    let result = loopback_client
        .get("http://localhost:8999/quota")
        .send()
        .await
        .is_ok();

    if let Ok(mut cache) = get_loopback_cache().lock() {
        cache.result = result;
        cache.last_check = Some(now);
    }

    result
}

async fn start_polling_loop(app_handle: AppHandle, state: Arc<AppState>, mut rx: mpsc::Receiver<()>) {
    let mut heavy_usage_until: Option<chrono::DateTime<chrono::Utc>> = None;

    loop {
        let config = config::load_config();
        let mut new_cache = config::load_cache();

        match quota_client::fetch_quota(&state.client, &state.local_client, &config).await {
            Ok((pools, src)) => {
                new_cache.pools = pools;
                new_cache.is_offline = false;
                new_cache.error_reason = None;
                new_cache.source = src;
                new_cache.last_updated = chrono::Utc::now().to_rfc3339();
            }
            Err(err) => {
                new_cache.is_offline = true;
                if err.contains("not detected") || err.contains("not found") {
                    new_cache.error_reason = Some("process_not_found".to_string());
                } else {
                    new_cache.error_reason = Some("offline".to_string());
                }
                new_cache.source = String::new();
            }
        }
        let _ = config::save_cache(&new_cache);

        {
            let mut c = state.cache.lock().unwrap();
            *c = new_cache.clone();
        }

        let _ = app_handle.emit("quota-update", new_cache);

        let has_loopback = check_has_loopback(&state.client).await;

        let near_reset = is_near_reset(&config.reset_time_utc);
        let now = chrono::Utc::now();
        let is_heavy = heavy_usage_until.map(|until| now < until).unwrap_or(false);

        let delay_secs = if is_heavy || near_reset {
            30
        } else if has_loopback {
            60
        } else {
            300
        };

        tokio::select! {
            _ = sleep(Duration::from_secs(delay_secs)) => {}
            res = rx.recv() => {
                if res.is_some() {
                    heavy_usage_until = Some(chrono::Utc::now() + chrono::Duration::minutes(5));
                } else {
                    break;
                }
            }
        }
    }
}

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
            Some(0),
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
                Some(0),
                REG_SZ,
                Some(std::slice::from_raw_parts(
                    current_exe_w.as_ptr() as *const u8,
                    current_exe_w.len() * 2
                )),
            );
        } else {
            let _ = RegDeleteValueW(hkey, windows::core::PCWSTR(name_w.as_ptr()));
        }

        let _ = RegCloseKey(hkey);
        Ok(())
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    #[cfg(target_os = "windows")]
    unsafe {
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    }

    let (tx, rx) = mpsc::channel(10);

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
        .unwrap_or_default();

    let local_client = reqwest::Client::builder()
        .danger_accept_invalid_certs(true)
        .timeout(Duration::from_secs(6))
        .build()
        .unwrap_or_default();

    let app_state = Arc::new(AppState {
        cache: Mutex::new(config::load_cache()),
        refresh_trigger: tx,
        client,
        local_client,
    });

    let state_clone = app_state.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(app_state)
        .setup(move |app| {
            if let Some(window) = app.get_webview_window("main") {
                windows_layer::init_wallpaper_widget(window);
            }

            let refresh = MenuItem::with_id(app, "refresh", "Refresh Now", true, None::<&str>).unwrap();
            let settings = MenuItem::with_id(app, "settings", "Settings", true, None::<&str>).unwrap();
            
            let cfg = config::load_config();
            let _ = toggle_autostart(cfg.autostart);
            let autostart = CheckMenuItem::with_id(
                app,
                "autostart",
                "Run at Startup",
                true,
                cfg.autostart,
                None::<&str>,
            ).unwrap();
            
            let exit = MenuItem::with_id(app, "exit", "Exit", true, None::<&str>).unwrap();

            let menu = Menu::with_items(app, &[&refresh, &settings, &autostart, &exit]).unwrap();
            let refresh_tx = state_clone.refresh_trigger.clone();

            let _tray = TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .menu(&menu)
                .on_menu_event(move |app_handle, event| {
                    match event.id.as_ref() {
                        "refresh" => {
                            let _ = app_handle.emit("refresh-started", ());
                            let tx = refresh_tx.clone();
                            tauri::async_runtime::spawn(async move {
                                let _ = tx.send(()).await;
                            });
                        }
                        "settings" => {
                            if let Some(window) = app_handle.get_webview_window("settings") {
                                let _ = window.show();
                                let _ = window.set_focus();
                            } else {
                                let _ = tauri::WebviewWindowBuilder::new(
                                    app_handle,
                                    "settings",
                                    tauri::WebviewUrl::App("/settings".into()),
                                )
                                .title("QuotaCheck Settings")
                                .inner_size(360.0, 520.0)
                                .resizable(false)
                                .build();
                            }
                        }
                        "autostart" => {
                            let mut config = config::load_config();
                            config.autostart = !config.autostart;
                            let _ = config::save_config(&config);
                            let _ = toggle_autostart(config.autostart);
                            
                            if let Some(menu) = app_handle.menu() {
                                if let Some(tauri::menu::MenuItemKind::Check(menu_item)) = menu.get("autostart") {
                                    let _ = menu_item.set_checked(config.autostart);
                                }
                            }
                        }
                        "exit" => {
                            app_handle.exit(0);
                        }
                        _ => {}
                    }
                })
                .build(app)
                .unwrap();

            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                start_polling_loop(handle, state_clone, rx).await;
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![get_current_quota, get_config, save_config, manual_refresh_trigger])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
