//! 健康检查处理程序

use axum::{http::StatusCode, response::IntoResponse, Json};
use serde::Serialize;

#[derive(Serialize)]
pub struct HealthResponse {
    pub status: String,
    pub message: String,
}

/// 健康检查端点处理程序
pub async fn health_check() -> impl IntoResponse {
    let response = HealthResponse {
        status: "healthy".to_string(),
        message: "Server is running".to_string(),
    };
    (StatusCode::OK, Json(response))
}
