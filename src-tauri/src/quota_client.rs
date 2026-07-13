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

    // 2. Scan default .antigravity_cockpit credentials
    if let Some(user_dir) = dirs::home_dir() {
        let mut p = user_dir.clone();
        p.push(".antigravity_cockpit");
        p.push("credentials.json");
        if let Ok(content) = fs::read_to_string(p) {
            if let Ok(credentials) = serde_json::from_str::<CockpitCredentials>(&content) {
                if let Some(account) = credentials.accounts.values().next() {
                    return Some(account.refresh_token.clone());
                }
            }
        }
    }

    // 3. Fallback scan fallback directories
    let mut paths = vec![];
    if let Some(user_dir) = dirs::home_dir() {
        let mut p = user_dir.clone();
        p.push(".antigravity");
        p.push("config.json");
        paths.push(p);
    }

    for path in paths {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(val) = serde_json::from_str::<serde_json::Value>(&content) {
                if let Some(token) = val["refresh_token"].as_str() {
                    return Some(token.to_string());
                }
            }
        }
    }

    None
}

pub async fn fetch_quota(config: &super::config::Config) -> Result<(u32, u32, String), String> {
    let client = Client::new();

    // Priority 1: Local Loopback Server (Antigravity is running)
    if let Ok(res) = client.get("http://localhost:8999/quota").send().await {
        if let Ok(data) = res.json::<LoopbackResponse>().await {
            return Ok((data.remaining, data.total, "local".to_string()));
        }
    }

    // Priority 2: Refresh Token -> Access Token -> Google cloudcode-pa API
    let refresh_token = if !config.refresh_token_override.is_empty() {
        Some(config.refresh_token_override.clone())
    } else {
        get_cached_antigravity_token(&config.antigravity_config_path)
    };

    let token = refresh_token.ok_or_else(|| "No refresh token found".to_string())?;

    // Request Access Token from Google OAuth Endpoint using real client details
    let token_url = "https://oauth2.googleapis.com/token";
    let params = [
        ("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"),
        ("client_secret", "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"),
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

    Ok((quota_data.remaining, quota_data.total, "cloud".to_string()))
}
