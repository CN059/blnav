/// HTTP 响应数据模型

use serde::{Deserialize, Serialize};

/// HTTP 响应状态码
#[derive(Clone, Debug, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub enum ResponseStatus {
    /// 成功
    #[serde(rename = "success")]
    Success = 200,
    
    /// 请求数据格式错误
    #[serde(rename = "bad_request")]
    BadRequest = 400,
    
    /// 定位失败
    #[serde(rename = "positioning_failed")]
    PositioningFailed = 420,
    
    /// 服务器内部错误
    #[serde(rename = "server_error")]
    ServerError = 500,
}

impl ResponseStatus {
    pub fn as_u16(&self) -> u16 {
        *self as u16
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            ResponseStatus::Success => "success",
            ResponseStatus::BadRequest => "bad_request",
            ResponseStatus::PositioningFailed => "positioning_failed",
            ResponseStatus::ServerError => "server_error",
        }
    }
}

/// 定位结果数据
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct PositioningResult {
    /// X 坐标
    pub x: f64,
    
    /// Y 坐标
    pub y: f64,
    
    /// Z 坐标（高度）
    pub z: f64,
    
    /// 定位置信度 (0.0-1.0)
    pub confidence: f64,
    
    /// 估计误差（单位与坐标相同）
    pub error: f64,
    
    /// 使用的算法
    pub algorithm: String,
    
    /// 参与定位的信标数量
    pub beacon_count: usize,
    
    /// 时间戳（毫秒）
    pub timestamp_ms: Option<u64>,
}

impl PositioningResult {
    /// 创建新的定位结果
    pub fn new(x: f64, y: f64, z: f64, confidence: f64, error: f64, algorithm: String, beacon_count: usize) -> Self {
        PositioningResult {
            x,
            y,
            z,
            confidence,
            error,
            algorithm,
            beacon_count,
            timestamp_ms: None,
        }
    }

    /// 添加时间戳
    pub fn with_timestamp(mut self, timestamp_ms: u64) -> Self {
        self.timestamp_ms = Some(timestamp_ms);
        self
    }

    /// 判断是否是高质量结果（置信度 > 0.7 且误差 < 100）
    pub fn is_high_quality(&self) -> bool {
        self.confidence > 0.7 && self.error < 100.0
    }
}

/// HTTP 响应体 - 通用格式
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct HttpResponse {
    /// 状态码
    pub status: String,
    
    /// HTTP 状态码
    pub code: u16,
    
    /// 响应消息
    pub message: String,
    
    /// 客户端 ID（对应请求）
    pub client_id: Option<String>,
    
    /// 定位结果（仅在成功时包含）
    pub result: Option<PositioningResult>,
    
    /// 错误详情（仅在失败时包含）
    pub error_details: Option<String>,
    
    /// 响应时间戳（毫秒）
    pub response_timestamp_ms: Option<u64>,
}

impl HttpResponse {
    /// 成功响应
    pub fn success(
        client_id: String,
        result: PositioningResult,
    ) -> Self {
        HttpResponse {
            status: ResponseStatus::Success.as_str().to_string(),
            code: ResponseStatus::Success.as_u16(),
            message: "定位成功".to_string(),
            client_id: Some(client_id),
            result: Some(result),
            error_details: None,
            response_timestamp_ms: Some(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            ),
        }
    }

    /// 请求错误响应
    pub fn bad_request(error_msg: String) -> Self {
        HttpResponse {
            status: ResponseStatus::BadRequest.as_str().to_string(),
            code: ResponseStatus::BadRequest.as_u16(),
            message: "请求格式错误".to_string(),
            client_id: None,
            result: None,
            error_details: Some(error_msg),
            response_timestamp_ms: Some(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            ),
        }
    }

    /// 定位失败响应
    pub fn positioning_failed(client_id: String, reason: String) -> Self {
        HttpResponse {
            status: ResponseStatus::PositioningFailed.as_str().to_string(),
            code: ResponseStatus::PositioningFailed.as_u16(),
            message: "定位失败".to_string(),
            client_id: Some(client_id),
            result: None,
            error_details: Some(reason),
            response_timestamp_ms: Some(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            ),
        }
    }

    /// 服务器错误响应
    pub fn server_error(error_msg: String) -> Self {
        HttpResponse {
            status: ResponseStatus::ServerError.as_str().to_string(),
            code: ResponseStatus::ServerError.as_u16(),
            message: "服务器错误".to_string(),
            client_id: None,
            result: None,
            error_details: Some(error_msg),
            response_timestamp_ms: Some(
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            ),
        }
    }

    /// 转换为 JSON 字符串
    pub fn to_json(&self) -> Result<String, serde_json::Error> {
        serde_json::to_string(self)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_positioning_result() {
        let result = PositioningResult::new(368.0, 339.0, 94.0, 0.85, 20.0, "basic".to_string(), 3);
        assert_eq!(result.x, 368.0);
        assert_eq!(result.confidence, 0.85);
        assert!(result.is_high_quality());
    }

    #[test]
    fn test_http_response_success() {
        let result = PositioningResult::new(368.0, 339.0, 94.0, 0.85, 20.0, "basic".to_string(), 3);
        let response = HttpResponse::success("client1".to_string(), result);
        
        assert_eq!(response.code, 200);
        assert_eq!(response.status, "success");
        assert_eq!(response.client_id, Some("client1".to_string()));
        assert!(response.result.is_some());
    }

    #[test]
    fn test_http_response_error() {
        let response = HttpResponse::bad_request("Invalid input".to_string());
        assert_eq!(response.code, 400);
        assert_eq!(response.status, "bad_request");
    }

    #[test]
    fn test_response_to_json() {
        let result = PositioningResult::new(368.0, 339.0, 94.0, 0.85, 20.0, "basic".to_string(), 3);
        let response = HttpResponse::success("client1".to_string(), result);
        let json = response.to_json();
        assert!(json.is_ok());
    }
}
