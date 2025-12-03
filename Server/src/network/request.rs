/// 客户端请求数据模型

use serde::{Deserialize, Serialize};

/// 单个信标的信号测量数据
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct BeaconSignal {
    /// 信标唯一标识（MAC 地址或 UUID）
    pub beacon_id: String,
    
    /// 信标友好名称
    pub beacon_name: Option<String>,
    
    /// RSSI 信号强度 (dBm)
    pub rssi: i16,
    
    /// 信号时间戳（毫秒，可选）
    pub timestamp_ms: Option<u64>,
    
    /// 其他自定义参数（扩展用）
    #[serde(default)]
    pub extra: Option<serde_json::Value>,
}

impl BeaconSignal {
    /// 创建新的信标信号
    pub fn new(beacon_id: String, rssi: i16) -> Self {
        BeaconSignal {
            beacon_id,
            beacon_name: None,
            rssi,
            timestamp_ms: None,
            extra: None,
        }
    }

    /// 添加信标名称
    pub fn with_name(mut self, name: String) -> Self {
        self.beacon_name = Some(name);
        self
    }

    /// 添加时间戳
    pub fn with_timestamp(mut self, timestamp_ms: u64) -> Self {
        self.timestamp_ms = Some(timestamp_ms);
        self
    }

    /// 添加额外参数
    pub fn with_extra(mut self, extra: serde_json::Value) -> Self {
        self.extra = Some(extra);
        self
    }
}

/// HTTP POST 请求体 - 客户端上传的定位请求
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LocationRequest {
    /// 客户端唯一标识
    pub client_id: String,
    
    /// 设备唯一标识（可选）
    pub device_id: Option<String>,
    
    /// 所有接收到的信标信号
    pub signals: Vec<BeaconSignal>,
    
    /// 请求时间戳（可选）
    pub request_timestamp_ms: Option<u64>,
    
    /// 特殊参数（定位算法选择、置信度要求等）
    #[serde(default)]
    pub options: LocationRequestOptions,
}

impl LocationRequest {
    /// 创建新的定位请求
    pub fn new(client_id: String, signals: Vec<BeaconSignal>) -> Self {
        LocationRequest {
            client_id,
            device_id: None,
            signals,
            request_timestamp_ms: None,
            options: LocationRequestOptions {
                algorithm: "auto".to_string(),
                enable_kalman_filter: true,
                enable_smoothing: false,
                min_confidence: 0.0,
                extra: None,
            },
        }
    }

    /// 验证请求的有效性
    pub fn validate(&self) -> Result<(), String> {
        if self.client_id.is_empty() {
            return Err("client_id 不能为空".to_string());
        }
        
        if self.signals.is_empty() {
            return Err("signals 不能为空".to_string());
        }
        
        if self.signals.len() < 3 {
            return Err("至少需要 3 个信标信号".to_string());
        }
        
        // 检查信号数据
        for (idx, signal) in self.signals.iter().enumerate() {
            if signal.beacon_id.is_empty() {
                return Err(format!("信号 {} 的 beacon_id 不能为空", idx));
            }
            if signal.rssi > 0 {
                return Err(format!("信号 {} 的 RSSI 应为负数", idx));
            }
        }
        
        Ok(())
    }
}

/// 定位请求选项
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct LocationRequestOptions {
    /// 定位算法选择
    /// "basic" - 基础三边定位
    /// "weighted" - 加权三边定位
    /// "least_squares" - 最小二乘法
    /// "auto" - 自动选择（默认）
    #[serde(default = "default_algorithm")]
    pub algorithm: String,
    
    /// 是否启用卡尔曼滤波
    #[serde(default = "default_enable_filter")]
    pub enable_kalman_filter: bool,
    
    /// 是否需要平滑处理
    #[serde(default = "default_enable_smoothing")]
    pub enable_smoothing: bool,
    
    /// 最小置信度要求 (0.0-1.0)
    #[serde(default = "default_min_confidence")]
    pub min_confidence: f64,
    
    /// 其他自定义选项
    #[serde(default)]
    pub extra: Option<serde_json::Value>,
}

fn default_algorithm() -> String {
    "auto".to_string()
}

fn default_enable_filter() -> bool {
    true
}

fn default_enable_smoothing() -> bool {
    false
}

fn default_min_confidence() -> f64 {
    0.0
}

impl LocationRequestOptions {
    /// 验证选项的有效性
    pub fn validate(&self) -> Result<(), String> {
        if self.min_confidence < 0.0 || self.min_confidence > 1.0 {
            return Err("min_confidence 必须在 0.0-1.0 之间".to_string());
        }
        
        match self.algorithm.as_str() {
            "basic" | "weighted" | "least_squares" | "auto" => Ok(()),
            _ => Err(format!("不支持的算法: {}", self.algorithm)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_beacon_signal_creation() {
        let signal = BeaconSignal::new("B1".to_string(), -50);
        assert_eq!(signal.beacon_id, "B1");
        assert_eq!(signal.rssi, -50);
    }

    #[test]
    fn test_location_request_validation() {
        let mut request = LocationRequest::new(
            "client1".to_string(),
            vec![
                BeaconSignal::new("B1".to_string(), -50),
                BeaconSignal::new("B2".to_string(), -60),
                BeaconSignal::new("B3".to_string(), -70),
            ],
        );
        
        // 应该通过验证
        assert!(request.validate().is_ok());
        
        // 移除一个信号，应该失败
        request.signals.pop();
        assert!(request.validate().is_err());
    }

    #[test]
    fn test_location_request_options() {
        let opts = LocationRequestOptions {
            algorithm: "weighted".to_string(),
            enable_kalman_filter: true,
            min_confidence: 0.8,
            ..Default::default()
        };
        
        assert!(opts.validate().is_ok());
        
        let invalid_opts = LocationRequestOptions {
            min_confidence: 1.5,
            ..Default::default()
        };
        assert!(invalid_opts.validate().is_err());
    }
}
