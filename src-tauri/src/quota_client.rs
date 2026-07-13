use std::fs;
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
        ("client_id", "dummy_client_id"), // Replace with real Client ID if needed
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
