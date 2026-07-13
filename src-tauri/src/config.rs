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
