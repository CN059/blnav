//! 健康检查路由

use axum::{
    routing::get,
    Router,
};
use std::sync::Arc;

use crate::api::handlers::health_check;
use crate::infrastructure::AppState;

/// 构建健康检查路由
pub fn router() -> Router<Arc<AppState>> {
    Router::new()
        .route("/health", get(health_check))
}
