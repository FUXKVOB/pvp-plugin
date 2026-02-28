use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub server_path: String,
    pub plugin_path: String,
    pub max_players: u32,
    pub enable_tournaments: bool,
    pub enable_cosmetics: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            server_path: "./test-server".to_string(),
            plugin_path: ".".to_string(),
            max_players: 100,
            enable_tournaments: true,
            enable_cosmetics: true,
        }
    }
}

impl Config {
    pub fn load() -> anyhow::Result<Self> {
        let path = Self::config_path();
        if path.exists() {
            let content = fs::read_to_string(&path)?;
            Ok(serde_json::from_str(&content)?)
        } else {
            Ok(Self::default())
        }
    }

    pub fn save(&self) -> anyhow::Result<()> {
        let path = Self::config_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(self)?;
        fs::write(&path, content)?;
        Ok(())
    }

    fn config_path() -> PathBuf {
        dirs::config_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("pvpkits-manager")
            .join("config.json")
    }
}
