use std::fs;
use serde::Deserialize;
use reqwest::Client;

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
        if let Ok(body) = res.text().await {
            if let Ok(data) = serde_json::from_str::<QuotaResponseGoogle>(&body) {
                let remaining = (data.quota.total - data.quota.used) as u32;
                let total = data.quota.total as u32;
                return Ok((remaining, total, "local".to_string()));
            } else if let Ok(data) = serde_json::from_str::<QuotaResponseDirect>(&body) {
                return Ok((data.remaining, data.total, "local".to_string()));
            }
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

    if !auth_res.status().is_success() {
        let status = auth_res.status();
        let err_body = auth_res.text().await.unwrap_or_default();
        return Err(format!("OAuth exchange failed ({}): {}", status, err_body));
    }

    let token_data = auth_res.json::<TokenResponse>().await
        .map_err(|e| format!("Failed to parse token response: {}", e))?;

    // Query Quota from CloudCode API
    let quota_url = "https://cloudcode-pa.googleapis.com/v1/quota";
    let quota_res = client.get(quota_url)
        .bearer_auth(token_data.access_token)
        .send()
        .await
        .map_err(|e| format!("Quota request failed: {}", e))?;

    if !quota_res.status().is_success() {
        let status = quota_res.status();
        let err_body = quota_res.text().await.unwrap_or_default();
        return Err(format!("Quota API returned error ({}): {}", status, err_body));
    }

    let body_text = quota_res.text().await
        .map_err(|e| format!("Failed to read quota body: {}", e))?;

    let quota_data: QuotaResponseGoogle = serde_json::from_str(&body_text)
        .map_err(|e| format!("Failed to parse Google quota response: {}, body: {}", e, body_text))?;

    let remaining = (quota_data.quota.total - quota_data.quota.used) as u32;
    let total = quota_data.quota.total as u32;

    Ok((remaining, total, "cloud".to_string()))
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
