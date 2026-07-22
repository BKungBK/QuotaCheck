use std::fs;
use serde::Deserialize;

// ─── Structures for legacy/cloud fallback ───────────────────────────────────

#[allow(dead_code)]
#[derive(Deserialize, Debug)]
struct GoogleQuota {
    total: u64,
    used: u64,
    #[serde(rename = "resetTime")]
    reset_time: String,
}

#[derive(Deserialize, Debug)]
struct QuotaResponseGoogle {
    quota: GoogleQuota,
}

#[derive(Deserialize, Debug)]
struct QuotaResponseDirect {
    remaining: u32,
    total: u32,
}

#[derive(Deserialize, Debug)]
struct TokenResponse {
    access_token: String,
}

#[derive(Deserialize, Debug, Clone)]
struct CloudQuotaInfo {
    #[serde(default, rename = "remainingFraction")]
    remaining_fraction: Option<f64>,
    #[serde(default, rename = "resetTime")]
    reset_time: Option<String>,
}

#[derive(Deserialize, Debug)]
struct CloudModelConfig {
    #[serde(default, rename = "quotaInfo")]
    quota_info: Option<CloudQuotaInfo>,
    #[serde(default, rename = "displayName")]
    display_name: Option<String>,
}

#[derive(Deserialize, Debug)]
struct FetchAvailableModelsResponse {
    #[serde(default)]
    models: std::collections::HashMap<String, CloudModelConfig>,
}

#[allow(dead_code)]
#[derive(Deserialize, Debug, Clone)]
struct CockpitAccount {
    #[serde(default)]
    email: Option<String>,
    #[serde(default, rename = "refreshToken")]
    refresh_token: Option<String>,
    #[serde(default, rename = "accessToken")]
    access_token: Option<String>,
    #[serde(default, rename = "expiresAt")]
    expires_at: Option<String>,
    #[serde(default, rename = "projectId")]
    project_id: Option<String>,
}

#[derive(Deserialize, Debug)]
struct CockpitCredentials {
    #[serde(default)]
    accounts: std::collections::HashMap<String, CockpitAccount>,
}

use std::sync::OnceLock;
use tokio::sync::Mutex;
use chrono::{DateTime, Utc};

struct CloudCache {
    access_token: Option<String>,
    expires_at: Option<DateTime<Utc>>,
    project_id: Option<String>,
}

fn get_cloud_cache() -> &'static Mutex<CloudCache> {
    static CACHE: OnceLock<Mutex<CloudCache>> = OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(CloudCache {
        access_token: None,
        expires_at: None,
        project_id: None,
    }))
}

// ─── Structures for local language server scraping ──────────────────────────

#[derive(Deserialize, Debug)]
struct WmiProcess {
    #[serde(rename = "ProcessId")]
    process_id: u32,
    #[serde(rename = "Name")]
    name: String,
    #[serde(rename = "CommandLine")]
    command_line: Option<String>,
}

#[derive(Debug)]
struct ProcessInfo {
    csrf_token: String,
    extension_server_csrf_token: Option<String>,
    extension_port: u16,
    pid: u32,
}

#[derive(Deserialize, Debug, Clone)]
struct QuotaInfo {
    #[serde(default, rename = "remainingFraction")]
    remaining_fraction: Option<f64>,
    #[serde(default, rename = "resetTime")]
    reset_time: Option<String>,
}

#[derive(Deserialize, Debug)]
struct ClientModelConfig {
    #[serde(default)]
    label: String,
    #[serde(default, rename = "quotaInfo")]
    quota_info: Option<QuotaInfo>,
}

#[derive(Deserialize, Debug)]
struct CascadeModelConfigData {
    #[serde(default, rename = "clientModelConfigs")]
    client_model_configs: Vec<ClientModelConfig>,
}

#[derive(Deserialize, Debug)]
struct UserStatusDetail {
    #[serde(default, rename = "cascadeModelConfigData")]
    cascade_model_config_data: Option<CascadeModelConfigData>,
}

#[derive(Deserialize, Debug)]
struct UserStatusResponse {
    #[serde(default, rename = "userStatus")]
    user_status: Option<UserStatusDetail>,
}

#[derive(Deserialize, Debug, Default)]
struct RetrieveUserQuotaSummaryResponse {
    #[serde(default)]
    pools: Option<Vec<BackendQuotaPool>>,
}

#[derive(Deserialize, Debug, Clone, Default)]
struct BackendQuotaPool {
    #[serde(default)]
    label: Option<String>,
    #[serde(default, rename = "remainingFraction")]
    remaining_fraction: Option<f64>,
    #[serde(default, rename = "resetTime")]
    reset_time: Option<String>,
}

// ─── Debug logging helper ────────────────────────────────────────────────────

fn append_debug_log(msg: &str) {
    if !cfg!(debug_assertions) { return; }
    use std::fs::OpenOptions;
    use std::io::Write;
    let log_path = std::env::temp_dir().join("antigravity_quota_widget_debug.log");
    if let Ok(mut f) = OpenOptions::new().append(true).create(true).open(&log_path) {
        let _ = writeln!(f, "{}", msg);
    }
}

// ─── Endpoint cache ─────────────────────────────────────────────────────────

#[derive(Clone, Debug)]
struct CachedEndpoint {
    pid: u32,
    scheme: String,
    port: u16,
    csrf_token: String,
    extension_server_csrf_token: Option<String>,
}

fn get_endpoint_cache() -> &'static Mutex<Option<CachedEndpoint>> {
    static CACHE: OnceLock<Mutex<Option<CachedEndpoint>>> = OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(None))
}

// ─── Process detection ───────────────────────────────────────────────────────

fn extract_arg(cmd: &str, arg: &str) -> Option<String> {
    let idx = cmd.find(arg)?;
    let after = cmd[idx + arg.len()..].trim_start();
    let val: String = after.chars().take_while(|&c| c != ' ' && c != '"').collect();
    if val.is_empty() { None } else { Some(val) }
}

#[cfg(target_os = "windows")]
async fn detect_antigravity_process() -> Option<ProcessInfo> {
    let mut cmd = tokio::process::Command::new("powershell");
    cmd.creation_flags(0x08000000);
    cmd.args(&[
        "-NoProfile",
        "-Command",
        "Get-CimInstance Win32_Process -Filter 'Name LIKE ''%language_server%'' OR Name = ''agy.exe'' OR Name = ''antigravity-cli.exe'' OR Name = ''antigravity_cli.exe''' | Select-Object ProcessId, Name, CommandLine | ConvertTo-Json"
    ]);

    let output = cmd.output().await.ok()?;
    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        append_debug_log(&format!("WMI query failed: {}", stderr));
        return None;
    }

    let json_str = String::from_utf8_lossy(&output.stdout);
    let json_str = json_str.trim();
    if json_str.is_empty() {
        append_debug_log("WMI: no processes found");
        return None;
    }

    // Reset debug log with WMI results
    if cfg!(debug_assertions) {
        let log_path = std::env::temp_dir().join("antigravity_quota_widget_debug.log");
        let _ = fs::write(&log_path, format!("WMI result:\n{}\n", json_str));
    }

    let processes: Vec<WmiProcess> = if json_str.starts_with('[') {
        serde_json::from_str(json_str).ok()?
    } else {
        let p: WmiProcess = serde_json::from_str(json_str).ok()?;
        vec![p]
    };

    for proc in processes {
        let cmd_line = proc.command_line.as_deref().unwrap_or("");
        if cmd_line.is_empty() { continue; }

        let name_lower = proc.name.to_lowercase();
        let cmd_lower = cmd_line.to_lowercase();

        // Match IDE language server: must contain "language_server" in name and
        // either --app_data_dir with "antigravity" anywhere OR path contains "antigravity"
        let is_ide = name_lower.contains("language_server") && (
            cmd_lower.contains("antigravity")
        );

        let is_cli = matches!(name_lower.as_str(), "agy.exe" | "antigravity-cli.exe" | "antigravity_cli.exe");

        if is_ide || is_cli {
            let csrf_token = extract_arg(cmd_line, "--csrf_token").unwrap_or_default();
            let extension_server_csrf_token = extract_arg(cmd_line, "--extension_server_csrf_token");
            let extension_port = extract_arg(cmd_line, "--extension_server_port")
                .and_then(|p| p.parse::<u16>().ok())
                .unwrap_or(0);

            append_debug_log(&format!(
                "Detected: PID={}, name={}, csrf={:.8}…, ext_port={}",
                proc.process_id, proc.name, csrf_token, extension_port
            ));

            return Some(ProcessInfo {
                csrf_token,
                extension_server_csrf_token,
                extension_port,
                pid: proc.process_id,
            });
        }
    }

    append_debug_log("No matching Antigravity process found");
    None
}

#[cfg(not(target_os = "windows"))]
async fn detect_antigravity_process() -> Option<ProcessInfo> { None }

// ─── Port discovery ──────────────────────────────────────────────────────────

#[cfg(target_os = "windows")]
async fn get_listening_ports(pid: u32) -> Vec<u16> {
    let mut cmd = tokio::process::Command::new("powershell");
    cmd.creation_flags(0x08000000);
    cmd.args(&[
        "-NoProfile",
        "-Command",
        &format!("Get-NetTCPConnection -OwningProcess {} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort", pid)
    ]);
    let mut ports = Vec::new();
    if let Ok(output) = cmd.output().await {
        if output.status.success() {
            for line in String::from_utf8_lossy(&output.stdout).lines() {
                if let Ok(p) = line.trim().parse::<u16>() {
                    ports.push(p);
                }
            }
        }
    }
    ports
}

#[cfg(not(target_os = "windows"))]
async fn get_listening_ports(_pid: u32) -> Vec<u16> { Vec::new() }

// ─── API endpoint detection (parallel probing) ───────────────────────────────

async fn probe_port(client: &reqwest::Client, scheme: &str, port: u16, csrf: &str) -> bool {
    let url = format!("{}://127.0.0.1:{}/exa.language_server_pb.LanguageServerService/GetUnleashData", scheme, port);
    match client.post(&url)
        .header("Connect-Protocol-Version", "1")
        .header("X-Codeium-Csrf-Token", csrf)
        .header("Content-Type", "application/json")
        .body("{}")
        .send()
        .await
    {
        Ok(resp) => {
            let code = resp.status().as_u16();
            if code == 400 && scheme == "http" {
                if let Ok(body) = resp.text().await {
                    if body.contains("HTTPS") || body.contains("HTTP request") {
                        return false;
                    }
                }
            }
            code == 200 || code == 400 || code == 401 || code == 403
        }
        Err(_) => false,
    }
}

async fn find_api_endpoint(local_client: &reqwest::Client, info: &ProcessInfo) -> Option<(String, u16)> {
    // 1. First try ports the process is actually listening on (fastest)
    let pid_ports = get_listening_ports(info.pid).await;
    append_debug_log(&format!("PID {} listening ports: {:?}", info.pid, pid_ports));

    // 2. Build candidate list: PID ports first, then extension_port range, then fallbacks
    let mut candidates = pid_ports.clone();
    if info.extension_port > 0 {
        for off in 0..20u16 {
            candidates.push(info.extension_port.saturating_add(off));
        }
    }
    candidates.extend_from_slice(&[53835, 53836, 53837, 53838, 53845, 53849]);

    // Deduplicate
    let mut seen = std::collections::HashSet::new();
    let ports: Vec<u16> = candidates.into_iter().filter(|p| seen.insert(*p)).collect();
    append_debug_log(&format!("Probing ports: {:?}", ports));

    let csrf = &info.csrf_token;

    use futures::stream::{FuturesUnordered, StreamExt};
    let mut select_probes = FuturesUnordered::new();

    // Probe https first (primary local server protocol), then http
    for &port in &ports {
        for &scheme in &["https", "http"] {
            let client_ref = local_client.clone();
            let csrf_owned = csrf.to_string();
            select_probes.push(async move {
                if probe_port(&client_ref, scheme, port, &csrf_owned).await {
                    Some((scheme.to_string(), port))
                } else {
                    None
                }
            });
        }
    }

    // Run probes concurrently, returning early on the first successful one
    while let Some(res) = select_probes.next().await {
        if let Some(ep) = res {
            append_debug_log(&format!("Found endpoint: {}://127.0.0.1:{}", ep.0, ep.1));
            return Some(ep);
        }
    }

    append_debug_log("No working endpoint found");
    None
}

// ─── Local language server quota fetch ───────────────────────────────────────

async fn fetch_local_language_server_quota(local_client: &reqwest::Client) -> Result<(Vec<super::config::QuotaPool>, String), String> {
    append_debug_log("--- fetch_local_language_server_quota start ---");

    let cached_opt = {
        let guard = get_endpoint_cache().lock().await;
        guard.clone()
    };

    let (info, scheme, port) = if let Some(cached) = cached_opt {
        append_debug_log(&format!("Probing cached endpoint: {}://127.0.0.1:{}", cached.scheme, cached.port));
        if probe_port(local_client, &cached.scheme, cached.port, &cached.csrf_token).await {
            append_debug_log("Cached endpoint probe succeeded!");
            let info = ProcessInfo {
                csrf_token: cached.csrf_token.clone(),
                extension_server_csrf_token: cached.extension_server_csrf_token.clone(),
                extension_port: cached.port,
                pid: cached.pid,
            };
            (info, cached.scheme, cached.port)
        } else {
            append_debug_log("Cached endpoint probe failed, clearing cache and running full detect");
            {
                let mut guard = get_endpoint_cache().lock().await;
                *guard = None;
            }
            let info = detect_antigravity_process().await
                .ok_or_else(|| "Antigravity process not detected".to_string())?;
            let (scheme, port) = find_api_endpoint(local_client, &info).await
                .ok_or_else(|| "Could not find Antigravity local API port".to_string())?;
            
            {
                let mut guard = get_endpoint_cache().lock().await;
                *guard = Some(CachedEndpoint {
                    pid: info.pid,
                    scheme: scheme.clone(),
                    port,
                    csrf_token: info.csrf_token.clone(),
                    extension_server_csrf_token: info.extension_server_csrf_token.clone(),
                });
            }
            (info, scheme, port)
        }
    } else {
        let info = detect_antigravity_process().await
            .ok_or_else(|| "Antigravity process not detected".to_string())?;
        let (scheme, port) = find_api_endpoint(local_client, &info).await
            .ok_or_else(|| "Could not find Antigravity local API port".to_string())?;

        {
            let mut guard = get_endpoint_cache().lock().await;
            *guard = Some(CachedEndpoint {
                pid: info.pid,
                scheme: scheme.clone(),
                port,
                csrf_token: info.csrf_token.clone(),
                extension_server_csrf_token: info.extension_server_csrf_token.clone(),
            });
        }
        (info, scheme, port)
    };

    let base_url = format!("{}://127.0.0.1:{}/exa.language_server_pb.LanguageServerService", scheme, port);

    let meta = serde_json::json!({
        "metadata": {
            "ideName": "antigravity",
            "extensionName": "antigravity",
            "ideVersion": "unknown",
            "locale": "en"
        }
    });

    let mut retrieve_summary_success = false;
    let mut pools_result = Vec::new();

    append_debug_log(&format!("Calling RetrieveUserQuotaSummary at {}", base_url));

    let res_summary = if let Some(ref ext_csrf) = info.extension_server_csrf_token {
        let r = local_client.post(&format!("{}/RetrieveUserQuotaSummary", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", ext_csrf)
            .json(&meta)
            .send()
            .await;
        match r {
            Ok(resp) if resp.status().is_success() => Ok(resp),
            _ => {
                append_debug_log("Extension CSRF failed for RetrieveUserQuotaSummary, retrying with main CSRF");
                local_client.post(&format!("{}/RetrieveUserQuotaSummary", base_url))
                    .header("Connect-Protocol-Version", "1")
                    .header("X-Codeium-Csrf-Token", &info.csrf_token)
                    .json(&meta)
                    .send()
                    .await
            }
        }
    } else {
        local_client.post(&format!("{}/RetrieveUserQuotaSummary", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", &info.csrf_token)
            .json(&meta)
            .send()
            .await
    };

    if let Ok(resp) = res_summary {
        if resp.status().is_success() {
            if let Ok(body) = resp.text().await {
                if let Ok(parsed) = serde_json::from_str::<RetrieveUserQuotaSummaryResponse>(&body) {
                    if let Some(pools) = parsed.pools {
                        if !pools.is_empty() {
                            for p in pools {
                                if let (Some(lbl), Some(rem)) = (p.label, p.remaining_fraction) {
                                    pools_result.push(super::config::QuotaPool {
                                        label: lbl,
                                        remaining_fraction: rem,
                                        reset_time: p.reset_time,
                                    });
                                }
                            }
                            if !pools_result.is_empty() {
                                retrieve_summary_success = true;
                                append_debug_log(&format!("Successfully parsed RetrieveUserQuotaSummary with {} pools", pools_result.len()));
                            }
                        }
                    }
                } else {
                    append_debug_log("Failed to parse RetrieveUserQuotaSummaryResponse json");
                }
            }
        } else {
            append_debug_log(&format!("RetrieveUserQuotaSummary returned non-200: {}", resp.status()));
        }
    } else {
        append_debug_log("RetrieveUserQuotaSummary request failed");
    }

    if retrieve_summary_success {
        return Ok((pools_result, "local".to_string()));
    }

    // Fallback: query GetUserStatus and merge manually
    append_debug_log("Falling back to GetUserStatus with manual merging");
    
    let res_status = if let Some(ref ext_csrf) = info.extension_server_csrf_token {
        let r = local_client.post(&format!("{}/GetUserStatus", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", ext_csrf)
            .json(&meta)
            .send()
            .await;
        match r {
            Ok(resp) if resp.status().is_success() => Ok(resp),
            _ => {
                append_debug_log("Extension CSRF failed or rejected, retrying with main CSRF");
                local_client.post(&format!("{}/GetUserStatus", base_url))
                    .header("Connect-Protocol-Version", "1")
                    .header("X-Codeium-Csrf-Token", &info.csrf_token)
                    .json(&meta)
                    .send()
                    .await
            }
        }
    } else {
        local_client.post(&format!("{}/GetUserStatus", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", &info.csrf_token)
            .json(&meta)
            .send()
            .await
    }.map_err(|e| { append_debug_log(&format!("Request error: {}", e)); format!("GetUserStatus request failed: {}", e) })?;

    let status = res_status.status();
    if !status.is_success() {
        return Err(format!("GetUserStatus returned error: {}", status));
    }

    let body = res_status.text().await.map_err(|e| format!("Failed to read body: {}", e))?;
    let status_resp: UserStatusResponse = serde_json::from_str(&body)
        .map_err(|e| { append_debug_log(&format!("JSON parse error: {}", e)); format!("Failed to parse response: {}", e) })?;

    let configs = status_resp.user_status
        .and_then(|us| us.cascade_model_config_data)
        .map(|d| d.client_model_configs)
        .unwrap_or_default();

    let mut gemini_min: Option<(f64, Option<String>)> = None;
    let mut claude_min: Option<(f64, Option<String>)> = None;

    for c in configs {
        let label_lower = c.label.to_lowercase();
        if let Some(ref q) = c.quota_info {
            if let Some(rem_frac) = q.remaining_fraction {
                let reset = q.reset_time.clone();
                if label_lower.starts_with("gemini") {
                    if gemini_min.as_ref().map(|(min_frac, _)| rem_frac < *min_frac).unwrap_or(true) {
                        gemini_min = Some((rem_frac, reset));
                    }
                } else if label_lower.starts_with("claude") || label_lower.starts_with("gpt-oss") {
                    if claude_min.as_ref().map(|(min_frac, _)| rem_frac < *min_frac).unwrap_or(true) {
                        claude_min = Some((rem_frac, reset));
                    }
                }
            }
        }
    }

    let mut fallback_pools = Vec::new();
    if let Some((frac, reset)) = gemini_min {
        fallback_pools.push(super::config::QuotaPool {
            label: "Gemini".to_string(),
            remaining_fraction: frac,
            reset_time: reset,
        });
    }
    if let Some((frac, reset)) = claude_min {
        fallback_pools.push(super::config::QuotaPool {
            label: "Claude".to_string(),
            remaining_fraction: frac,
            reset_time: reset,
        });
    }

    if fallback_pools.is_empty() {
        return Err("No matching models found in manual fallback".to_string());
    }

    Ok((fallback_pools, "local".to_string()))
}

// ─── Token retrieval for cloud fallback ──────────────────────────────────────

fn get_cached_antigravity_token(custom_path: &str) -> Option<CockpitAccount> {
    // Try custom path first
    if !custom_path.is_empty() {
        if let Ok(content) = fs::read_to_string(custom_path) {
            if let Ok(acct) = serde_json::from_str::<CockpitAccount>(&content) {
                return Some(acct);
            }
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(CockpitAccount {
                        email: None,
                        refresh_token: Some(token.to_string()),
                        access_token: None,
                        expires_at: None,
                        project_id: None,
                    });
                }
            }
        }
    }

    let home = dirs::home_dir();
    let appdata = dirs::data_dir(); // %APPDATA% on Windows

    // Build search list — order from most likely to least likely
    let mut paths: Vec<std::path::PathBuf> = Vec::new();

    // %APPDATA%\Antigravity\...
    if let Some(ref d) = appdata {
        paths.push(d.join("Antigravity").join("credentials.json"));
        paths.push(d.join("Antigravity").join("config.json"));
        paths.push(d.join("antigravity").join("credentials.json"));
        paths.push(d.join("antigravity").join("config.json"));
        // VS Code-style storage
        paths.push(d.join("Code").join("User").join("globalStorage").join("antigravity.antigravity").join("token.json"));
    }

    // ~/.antigravity_cockpit/credentials.json (original)
    if let Some(ref h) = home {
        paths.push(h.join(".antigravity_cockpit").join("credentials.json"));
        paths.push(h.join(".antigravity").join("config.json"));
        // Antigravity IDE app data
        paths.push(h.join("AppData").join("Roaming").join("Antigravity").join("credentials.json"));
        paths.push(h.join("AppData").join("Roaming").join("antigravity-ide").join("credentials.json"));
    }

    for path in &paths {
        if let Ok(content) = fs::read_to_string(path) {
            // Try CockpitCredentials format
            if let Ok(creds) = serde_json::from_str::<CockpitCredentials>(&content) {
                if let Some(acct) = creds.accounts.values().next() {
                    return Some(acct.clone());
                }
            }
            // Try plain JSON with refresh_token / accessToken / refreshToken
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                let refresh_token = val["refresh_token"]
                    .as_str()
                    .or_else(|| val["refreshToken"].as_str())
                    .map(String::from);
                
                let access_token = val["access_token"]
                    .as_str()
                    .or_else(|| val["accessToken"].as_str())
                    .map(String::from);

                let expires_at = val["expires_at"]
                    .as_str()
                    .or_else(|| val["expiresAt"].as_str())
                    .map(String::from);

                let project_id = val["project_id"]
                    .as_str()
                    .or_else(|| val["projectId"].as_str())
                    .map(String::from);

                if refresh_token.is_some() || access_token.is_some() {
                    return Some(CockpitAccount {
                        email: val["email"].as_str().map(String::from),
                        refresh_token,
                        access_token,
                        expires_at,
                        project_id,
                    });
                }
            }
        }
    }

    None
}

// ─── Main fetch_quota ────────────────────────────────────────────────────────

pub async fn fetch_quota(client: &reqwest::Client, local_client: &reqwest::Client, config: &super::config::Config) -> Result<(Vec<super::config::QuotaPool>, String), String> {
    // Priority 1: Local Loopback Server legacy endpoint
    if let Ok(res) = client.get("http://localhost:8999/quota").send().await {
        if let Ok(body) = res.text().await {
            if let Ok(data) = serde_json::from_str::<QuotaResponseGoogle>(&body) {
                let remaining = (data.quota.total - data.quota.used) as f64;
                let fraction = if data.quota.total > 0 { remaining / (data.quota.total as f64) } else { 0.0 };
                return Ok((vec![super::config::QuotaPool {
                    label: "Gemini".to_string(),
                    remaining_fraction: fraction,
                    reset_time: Some(data.quota.reset_time),
                }], "local".to_string()));
            } else if let Ok(data) = serde_json::from_str::<QuotaResponseDirect>(&body) {
                let fraction = if data.total > 0 { (data.remaining as f64) / (data.total as f64) } else { 0.0 };
                return Ok((vec![super::config::QuotaPool {
                    label: "Gemini".to_string(),
                    remaining_fraction: fraction,
                    reset_time: None,
                }], "local".to_string()));
            }
        }
    }

    // Priority 2: Direct local language server scraping
    match fetch_local_language_server_quota(local_client).await {
        Ok(result) => return Ok(result),
        Err(e) => append_debug_log(&format!("Local scraper failed: {}", e)),
    }

    // Priority 3: Cloud OAuth fallback
    let mut access_token: Option<String> = None;
    let mut project_id: Option<String> = None;
    let mut refresh_token: Option<String> = None;

    // Check custom path override first
    if !config.refresh_token_override.is_empty() {
        refresh_token = Some(config.refresh_token_override.clone());
    } else {
        // Retrieve credentials from file
        if let Some(acct) = get_cached_antigravity_token(&config.antigravity_config_path) {
            refresh_token = acct.refresh_token;
            project_id = acct.project_id;

            if let (Some(token), Some(expiry_str)) = (acct.access_token, acct.expires_at) {
                if let Ok(expiry) = chrono::DateTime::parse_from_rfc3339(&expiry_str) {
                    let now = chrono::Utc::now();
                    // If valid for more than 5 minutes, reuse
                    if expiry.with_timezone(&chrono::Utc) > now + chrono::Duration::minutes(5) {
                        access_token = Some(token);
                    }
                }
            }
        }
    }

    // Check in-memory CLOUD_CACHE if not using override
    if config.refresh_token_override.is_empty() {
        let cache = get_cloud_cache().lock().await;
        if access_token.is_none() {
            if let (Some(token), Some(expiry)) = (&cache.access_token, cache.expires_at) {
                let now = chrono::Utc::now();
                if expiry > now + chrono::Duration::minutes(5) {
                    access_token = Some(token.clone());
                }
            }
        }
        if project_id.is_none() {
            project_id = cache.project_id.clone();
        }
    }

    // Refresh token if we don't have a valid access token in memory/cache
    if access_token.is_none() {
        let r_token = refresh_token.ok_or_else(|| "No refresh token found".to_string())?;
        
        let token_res = client.post("https://oauth2.googleapis.com/token")
            .form(&[
                ("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"),
                ("client_secret", "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"),
                ("refresh_token", &r_token),
                ("grant_type", "refresh_token"),
            ])
            .send()
            .await
            .map_err(|e| format!("OAuth request failed: {}", e))?;

        if !token_res.status().is_success() {
            let status = token_res.status();
            let body = token_res.text().await.unwrap_or_default();
            return Err(format!("OAuth exchange failed ({}): {}", status, body));
        }

        let token_data = token_res.json::<TokenResponse>().await
            .map_err(|e| format!("Failed to parse token response: {}", e))?;
        
        access_token = Some(token_data.access_token);
        
        // Cache refreshed access token in memory
        let mut cache = get_cloud_cache().lock().await;
        cache.access_token = access_token.clone();
        // Assume access token is valid for 55 minutes
        cache.expires_at = Some(chrono::Utc::now() + chrono::Duration::minutes(55));
    }

    let access_token_str = access_token.as_ref().ok_or_else(|| "No access token".to_string())?;

    // If project_id is still unknown, try project discovery via loadCodeAssist
    if project_id.is_none() {
        let load_res = client
            .post("https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist")
            .bearer_auth(access_token_str)
            .header("User-Agent", "antigravity/1.104.0 windows/amd64")
            .header("Client-Metadata", "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"WINDOWS\",\"pluginType\":\"GEMINI\"}")
            .json(&serde_json::json!({
                "metadata": { "ideType": "ANTIGRAVITY", "platform": "WINDOWS", "pluginType": "GEMINI" }
            }))
            .send()
            .await;

        if let Ok(resp) = load_res {
            if resp.status().is_success() {
                if let Ok(load_json) = resp.json::<serde_json::Value>().await {
                    if let Some(p) = load_json["cloudaicompanionProject"].as_str() {
                        project_id = Some(p.to_string());
                    }
                }
            }
        }
    }

    // Fallback project discovery via ResourceManager if loadCodeAssist failed
    if project_id.is_none() {
        let rm_res = client
            .get("https://cloudresourcemanager.googleapis.com/v1/projects")
            .bearer_auth(access_token_str)
            .send()
            .await;

        if let Ok(resp) = rm_res {
            if resp.status().is_success() {
                if let Ok(rm_json) = resp.json::<serde_json::Value>().await {
                    if let Some(projects) = rm_json["projects"].as_array() {
                        for p in projects {
                            let mut matches = false;
                            if let Some(p_id) = p["projectId"].as_str() {
                                if p_id.starts_with("gen-lang-client") {
                                    project_id = Some(p_id.to_string());
                                    matches = true;
                                }
                            }
                            if !matches {
                                if let Some(labels) = p["labels"].as_object() {
                                    if labels.contains_key("generative-language") {
                                        if let Some(p_id) = p["projectId"].as_str() {
                                            project_id = Some(p_id.to_string());
                                            matches = true;
                                        }
                                    }
                                }
                            }
                            if matches {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // Cache project_id in memory if discovered
    if project_id.is_some() {
        let mut cache = get_cloud_cache().lock().await;
        cache.project_id = project_id.clone();
    }

    // Call quota API with project parameter or {}
    let req_body = match &project_id {
        Some(p) => serde_json::json!({ "project": p }),
        None => serde_json::json!({}),
    };

    let mut cloud_summary_success = false;
    let mut cloud_pools_result = Vec::new();

    // Call retrieveUserQuotaSummary
    let quota_res_summary = client
        .post("https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary")
        .bearer_auth(access_token_str)
        .header("User-Agent", "antigravity/1.104.0 windows/amd64")
        .header("Client-Metadata", "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"WINDOWS\",\"pluginType\":\"GEMINI\"}")
        .json(&req_body)
        .send()
        .await;

    if let Ok(resp) = quota_res_summary {
        if resp.status().is_success() {
            if let Ok(body) = resp.text().await {
                if let Ok(parsed) = serde_json::from_str::<RetrieveUserQuotaSummaryResponse>(&body) {
                    if let Some(pools) = parsed.pools {
                        if !pools.is_empty() {
                            for p in pools {
                                if let (Some(lbl), Some(rem)) = (p.label, p.remaining_fraction) {
                                    cloud_pools_result.push(super::config::QuotaPool {
                                        label: lbl,
                                        remaining_fraction: rem,
                                        reset_time: p.reset_time,
                                    });
                                }
                            }
                            if !cloud_pools_result.is_empty() {
                                cloud_summary_success = true;
                            }
                        }
                    }
                }
            }
        }
    }

    if cloud_summary_success {
        return Ok((cloud_pools_result, "cloud".to_string()));
    }

    // Fallback to fetchAvailableModels
    let quota_res = client
        .post("https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels")
        .bearer_auth(access_token_str)
        .header("User-Agent", "antigravity/1.104.0 windows/amd64")
        .header("Client-Metadata", "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"WINDOWS\",\"pluginType\":\"GEMINI\"}")
        .json(&req_body)
        .send()
        .await
        .map_err(|e| format!("Quota request failed: {}", e))?;

    if !quota_res.status().is_success() {
        let status = quota_res.status();
        let body = quota_res.text().await.unwrap_or_default();
        return Err(format!("Quota API returned error ({}): {}", status, body));
    }

    let quota_res_data: FetchAvailableModelsResponse = quota_res.json().await
        .map_err(|e| format!("Failed to parse quota response: {}", e))?;

    let mut gemini_min: Option<(f64, Option<String>)> = None;
    let mut claude_min: Option<(f64, Option<String>)> = None;

    for (k, v) in quota_res_data.models {
        let label_lower = k.to_lowercase();
        let display_lower = v.display_name.as_ref().map(|d| d.to_lowercase()).unwrap_or_default();
        if let Some(ref q) = v.quota_info {
            if let Some(rem_frac) = q.remaining_fraction {
                let reset = q.reset_time.clone();
                if label_lower.contains("gemini") || display_lower.contains("gemini") {
                    if gemini_min.as_ref().map(|(min_frac, _)| rem_frac < *min_frac).unwrap_or(true) {
                        gemini_min = Some((rem_frac, reset));
                    }
                } else if label_lower.contains("claude") || display_lower.contains("claude") || label_lower.contains("gpt-oss") || display_lower.contains("gpt-oss") {
                    if claude_min.as_ref().map(|(min_frac, _)| rem_frac < *min_frac).unwrap_or(true) {
                        claude_min = Some((rem_frac, reset));
                    }
                }
            }
        }
    }

    let mut cloud_fallback_pools = Vec::new();
    if let Some((frac, reset)) = gemini_min {
        cloud_fallback_pools.push(super::config::QuotaPool {
            label: "Gemini".to_string(),
            remaining_fraction: frac,
            reset_time: reset,
        });
    }
    if let Some((frac, reset)) = claude_min {
        cloud_fallback_pools.push(super::config::QuotaPool {
            label: "Claude".to_string(),
            remaining_fraction: frac,
            reset_time: reset,
        });
    }

    if cloud_fallback_pools.is_empty() {
        return Err("No matching models found in cloud manual fallback".to_string());
    }

    Ok((cloud_fallback_pools, "cloud".to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    #[ignore]
    async fn test_fetch_quota() {
        let config = crate::config::Config::default();
        let client = reqwest::Client::new();
        let local_client = reqwest::Client::builder()
            .danger_accept_invalid_certs(true)
            .build()
            .unwrap();
        let res = fetch_quota(&client, &local_client, &config).await;
        println!("FETCH QUOTA RESULT: {:?}", res);
        assert!(res.is_ok(), "Expected fetch_quota to succeed, got: {:?}", res);
    }
}
