use std::fs;
use serde::Deserialize;
use reqwest::Client;

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

#[allow(dead_code)]
#[derive(Deserialize, Debug)]
struct CockpitAccount {
    email: String,
    #[serde(rename = "refreshToken")]
    refresh_token: String,
}

#[derive(Deserialize, Debug)]
struct CockpitCredentials {
    accounts: std::collections::HashMap<String, CockpitAccount>,
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

#[derive(Deserialize, Debug)]
struct QuotaInfo {
    #[serde(rename = "remainingFraction")]
    remaining_fraction: f64,
}

#[derive(Deserialize, Debug)]
struct ClientModelConfig {
    label: String,
    #[serde(rename = "quotaInfo")]
    quota_info: Option<QuotaInfo>,
}

#[derive(Deserialize, Debug)]
struct CascadeModelConfigData {
    #[serde(rename = "clientModelConfigs")]
    client_model_configs: Vec<ClientModelConfig>,
}

#[derive(Deserialize, Debug)]
struct UserStatusDetail {
    #[serde(rename = "cascadeModelConfigData")]
    cascade_model_config_data: Option<CascadeModelConfigData>,
}

#[derive(Deserialize, Debug)]
struct UserStatusResponse {
    #[serde(rename = "userStatus")]
    user_status: Option<UserStatusDetail>,
}

// ─── Debug logging helper ────────────────────────────────────────────────────

fn append_debug_log(msg: &str) {
    use std::fs::OpenOptions;
    use std::io::Write;
    use std::thread::sleep;
    use std::time::Duration;
    for _ in 0..10 {
        if let Ok(mut f) = OpenOptions::new().append(true).create(true).open("e:\\QuotaCheck\\debug_log.txt") {
            let _ = writeln!(f, "{}", msg);
            return;
        }
        sleep(Duration::from_millis(10));
    }
}

// ─── Process detection ───────────────────────────────────────────────────────

fn extract_arg(cmd: &str, arg: &str) -> Option<String> {
    let idx = cmd.find(arg)?;
    let after = cmd[idx + arg.len()..].trim_start();
    let val: String = after.chars().take_while(|&c| c != ' ' && c != '"').collect();
    if val.is_empty() { None } else { Some(val) }
}

#[cfg(target_os = "windows")]
fn detect_antigravity_process() -> Option<ProcessInfo> {
    use std::os::windows::process::CommandExt;

    let mut cmd = std::process::Command::new("powershell");
    cmd.creation_flags(0x08000000);
    cmd.args(&[
        "-NoProfile",
        "-Command",
        "Get-CimInstance Win32_Process -Filter 'Name LIKE ''%language_server%'' OR Name = ''agy.exe'' OR Name = ''antigravity-cli.exe'' OR Name = ''antigravity_cli.exe''' | Select-Object ProcessId, Name, CommandLine | ConvertTo-Json"
    ]);

    let output = cmd.output().ok()?;
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
    let _ = fs::write("e:\\QuotaCheck\\debug_log.txt", format!("WMI result:\n{}\n", json_str));

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
fn detect_antigravity_process() -> Option<ProcessInfo> { None }

// ─── Port discovery ──────────────────────────────────────────────────────────

#[cfg(target_os = "windows")]
fn get_listening_ports(pid: u32) -> Vec<u16> {
    use std::os::windows::process::CommandExt;
    let mut cmd = std::process::Command::new("powershell");
    cmd.creation_flags(0x08000000);
    cmd.args(&[
        "-NoProfile",
        "-Command",
        &format!("Get-NetTCPConnection -OwningProcess {} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalPort", pid)
    ]);
    let mut ports = Vec::new();
    if let Ok(output) = cmd.output() {
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
fn get_listening_ports(_pid: u32) -> Vec<u16> { Vec::new() }

// ─── API endpoint detection (parallel probing) ───────────────────────────────

async fn probe_port(client: &Client, scheme: &str, port: u16, csrf: &str) -> bool {
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
            code == 200
        }
        Err(_) => false,
    }
}

async fn find_api_endpoint(info: &ProcessInfo) -> Option<(String, u16)> {
    // 1. First try ports the process is actually listening on (fastest)
    let pid_ports = get_listening_ports(info.pid);
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

    let client = Client::builder()
        .danger_accept_invalid_certs(true)
        .timeout(std::time::Duration::from_secs(3))
        .build()
        .ok()?;

    let csrf = &info.csrf_token;

    // Probe https first (primary local server protocol), then http
    let mut tasks = Vec::new();
    for &port in &ports {
        for &scheme in &["https", "http"] {
            let client_ref = client.clone();
            let csrf_owned = csrf.to_string();
            tasks.push(async move {
                if probe_port(&client_ref, scheme, port, &csrf_owned).await {
                    Some((scheme.to_string(), port))
                } else {
                    None
                }
            });
        }
    }

    // Run all probes concurrently and return the first successful one
    let results = futures::future::join_all(tasks).await;
    for r in results {
        if let Some(ep) = r {
            append_debug_log(&format!("Found endpoint: {}://127.0.0.1:{}", ep.0, ep.1));
            return Some(ep);
        }
    }

    append_debug_log("No working endpoint found");
    None
}

// ─── Local language server quota fetch ───────────────────────────────────────

async fn fetch_local_language_server_quota() -> Result<(u32, u32, String), String> {
    append_debug_log("--- fetch_local_language_server_quota start ---");

    let info = detect_antigravity_process()
        .ok_or_else(|| "Antigravity process not detected".to_string())?;

    let (scheme, port) = find_api_endpoint(&info).await
        .ok_or_else(|| "Could not find Antigravity local API port".to_string())?;

    let base_url = format!("{}://127.0.0.1:{}/exa.language_server_pb.LanguageServerService", scheme, port);

    let client = Client::builder()
        .danger_accept_invalid_certs(true)
        .timeout(std::time::Duration::from_secs(6))
        .build()
        .map_err(|e| format!("Failed to build local HTTP client: {}", e))?;

    let meta = serde_json::json!({
        "metadata": {
            "ideName": "antigravity",
            "extensionName": "antigravity",
            "ideVersion": "unknown",
            "locale": "en"
        }
    });

    append_debug_log(&format!("Calling GetUserStatus at {}", base_url));

    let res = if let Some(ref ext_csrf) = info.extension_server_csrf_token {
        let r = client.post(&format!("{}/GetUserStatus", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", ext_csrf)
            .json(&meta)
            .send()
            .await;
        match r {
            Ok(resp) if resp.status().is_success() => Ok(resp),
            _ => {
                append_debug_log("Extension CSRF failed or rejected, retrying with main CSRF");
                client.post(&format!("{}/GetUserStatus", base_url))
                    .header("Connect-Protocol-Version", "1")
                    .header("X-Codeium-Csrf-Token", &info.csrf_token)
                    .json(&meta)
                    .send()
                    .await
            }
        }
    } else {
        client.post(&format!("{}/GetUserStatus", base_url))
            .header("Connect-Protocol-Version", "1")
            .header("X-Codeium-Csrf-Token", &info.csrf_token)
            .json(&meta)
            .send()
            .await
    }.map_err(|e| { append_debug_log(&format!("Request error: {}", e)); format!("GetUserStatus request failed: {}", e) })?;

    let status = res.status();
    append_debug_log(&format!("Response status: {}", status));
    if !status.is_success() {
        return Err(format!("GetUserStatus returned error: {}", status));
    }

    let body = res.text().await.map_err(|e| format!("Failed to read body: {}", e))?;
    append_debug_log(&format!("Response body length: {}", body.len()));

    let status_resp: UserStatusResponse = serde_json::from_str(&body)
        .map_err(|e| { append_debug_log(&format!("JSON parse error: {}", e)); format!("Failed to parse response: {}", e) })?;

    let configs = status_resp.user_status
        .and_then(|us| us.cascade_model_config_data)
        .map(|d| d.client_model_configs)
        .unwrap_or_default();

    append_debug_log(&format!("Model configs count: {}", configs.len()));

    // Prefer Gemini/Flash, fall back to first available
    let quota = configs.iter()
        .filter(|c| {
            let l = c.label.to_lowercase();
            (l.contains("gemini") || l.contains("flash")) && c.quota_info.is_some()
        })
        .chain(configs.iter().filter(|c| c.quota_info.is_some()))
        .next()
        .and_then(|c| c.quota_info.as_ref())
        .ok_or_else(|| "No quota info found".to_string())?;

    let remaining = (quota.remaining_fraction * 100.0).round() as u32;
    append_debug_log(&format!("Quota remaining: {}%", remaining));

    Ok((remaining, 100, "local".to_string()))
}

// ─── Token retrieval for cloud fallback ──────────────────────────────────────

fn get_cached_antigravity_token(custom_path: &str) -> Option<String> {
    // Try custom path first
    if !custom_path.is_empty() {
        if let Ok(content) = fs::read_to_string(custom_path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(token.to_string());
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
                    return Some(acct.refresh_token.clone());
                }
            }
            // Try plain JSON with refresh_token
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(token.to_string());
                }
                if let Some(token) = val["refreshToken"].as_str() {
                    return Some(token.to_string());
                }
            }
        }
    }

    None
}

// ─── Main fetch_quota ────────────────────────────────────────────────────────

pub async fn fetch_quota(config: &super::config::Config) -> Result<(u32, u32, String), String> {
    // Priority 1: Local Loopback Server legacy endpoint
    let legacy_client = Client::builder()
        .timeout(std::time::Duration::from_secs(2))
        .build()
        .unwrap_or_default();

    if let Ok(res) = legacy_client.get("http://localhost:8999/quota").send().await {
        if let Ok(body) = res.text().await {
            if let Ok(data) = serde_json::from_str::<QuotaResponseGoogle>(&body) {
                let remaining = (data.quota.total - data.quota.used) as u32;
                return Ok((remaining, data.quota.total as u32, "local".to_string()));
            } else if let Ok(data) = serde_json::from_str::<QuotaResponseDirect>(&body) {
                return Ok((data.remaining, data.total, "local".to_string()));
            }
        }
    }

    // Priority 2: Direct local language server scraping
    match fetch_local_language_server_quota().await {
        Ok(result) => return Ok(result),
        Err(e) => append_debug_log(&format!("Local scraper failed: {}", e)),
    }

    // Priority 3: Cloud OAuth fallback
    let refresh_token = if !config.refresh_token_override.is_empty() {
        Some(config.refresh_token_override.clone())
    } else {
        get_cached_antigravity_token(&config.antigravity_config_path)
    };

    let token = refresh_token.ok_or_else(|| "No refresh token found".to_string())?;

    let cloud_client = Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {}", e))?;

    // Exchange refresh token for access token
    let token_res = cloud_client.post("https://oauth2.googleapis.com/token")
        .form(&[
            ("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"),
            ("client_secret", "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"),
            ("refresh_token", &token),
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

    // Use GetUserStatus via cloud (same endpoint, bearer auth)
    let meta = serde_json::json!({
        "metadata": {
            "ideName": "antigravity",
            "extensionName": "antigravity",
            "ideVersion": "unknown",
            "locale": "en"
        }
    });

    let quota_res = cloud_client
        .post("https://cloudcode-pa.googleapis.com/exa.language_server_pb.LanguageServerService/GetUserStatus")
        .bearer_auth(&token_data.access_token)
        .header("Connect-Protocol-Version", "1")
        .json(&meta)
        .send()
        .await
        .map_err(|e| format!("Quota request failed: {}", e))?;

    if !quota_res.status().is_success() {
        let status = quota_res.status();
        let body = quota_res.text().await.unwrap_or_default();
        return Err(format!("Quota API returned error ({}): {}", status, body));
    }

    let status_resp: UserStatusResponse = quota_res.json().await
        .map_err(|e| format!("Failed to parse quota response: {}", e))?;

    let configs = status_resp.user_status
        .and_then(|us| us.cascade_model_config_data)
        .map(|d| d.client_model_configs)
        .unwrap_or_default();

    let quota = configs.iter()
        .filter(|c| {
            let l = c.label.to_lowercase();
            (l.contains("gemini") || l.contains("flash")) && c.quota_info.is_some()
        })
        .chain(configs.iter().filter(|c| c.quota_info.is_some()))
        .next()
        .and_then(|c| c.quota_info.as_ref())
        .ok_or_else(|| "No quota info in cloud response".to_string())?;

    let remaining = (quota.remaining_fraction * 100.0).round() as u32;
    Ok((remaining, 100, "cloud".to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_fetch_quota() {
        let config = crate::config::Config::default();
        let res = fetch_quota(&config).await;
        println!("FETCH QUOTA RESULT: {:?}", res);
        assert!(res.is_ok(), "Expected fetch_quota to succeed, got: {:?}", res);
    }
}
