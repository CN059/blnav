use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Serialize;

/// 应用错误类型
#[derive(Debug)]
pub enum AppError {
    /// 业务逻辑错误
    #[allow(dead_code)]
    BusinessError(String),
    /// 数据库错误
    #[allow(dead_code)]
    DatabaseError(String),
    /// 验证错误
    ValidationError(String),
    /// 资源未找到
    #[allow(dead_code)]
    NotFound(String),
    /// 内部服务器错误
    #[allow(dead_code)]
    InternalError(String),
}

/// 错误响应体
#[derive(Serialize)]
pub struct ErrorResponse {
    pub status: u16,
    pub if_success: bool,
    pub message: String,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, message) = match self {
            AppError::BusinessError(msg) => (StatusCode::BAD_REQUEST, msg),
            AppError::ValidationError(msg) => (StatusCode::BAD_REQUEST, msg),
            AppError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            AppError::DatabaseError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
            AppError::InternalError(msg) => (StatusCode::INTERNAL_SERVER_ERROR, msg),
        };

        let error_response = ErrorResponse {
            status: status.as_u16(),
            if_success: false,
            message,
        };

        (status, Json(error_response)).into_response()
    }
}

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            AppError::BusinessError(msg) => write!(f, "Business Error: {}", msg),
            AppError::DatabaseError(msg) => write!(f, "Database Error: {}", msg),
            AppError::ValidationError(msg) => write!(f, "Validation Error: {}", msg),
            AppError::NotFound(msg) => write!(f, "Not Found: {}", msg),
            AppError::InternalError(msg) => write!(f, "Internal Error: {}", msg),
        }
    }
}

impl std::error::Error for AppError {}

/// 结果类型别名
pub type Result<T> = std::result::Result<T, AppError>;
