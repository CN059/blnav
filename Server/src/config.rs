//! 应用配置模块
//!
//! 提供应用的全局配置

/// 服务器配置
#[allow(dead_code)]
pub struct ServerConfig {
    pub host: String,
    pub port: u16,
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            host: "127.0.0.1".to_string(),
            port: 3000,
        }
    }
}

/// 应用配置
#[allow(dead_code)]
pub struct AppConfig {
    pub server: ServerConfig,
    pub log_level: String,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            server: ServerConfig::default(),
            log_level: "info".to_string(),
        }
    }
}

impl AppConfig {
    #[allow(dead_code)]
    pub fn new() -> Self {
        Self::default()
    }

    #[allow(dead_code)]
    pub fn addr(&self) -> String {
        format!("{}:{}", self.server.host, self.server.port)
    }
}
