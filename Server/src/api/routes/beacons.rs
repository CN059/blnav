//! Beacon 相关路由

use axum::{
    routing::get,
    Router,
};
use std::sync::Arc;

use crate::api::handlers::get_all_beacons;
use crate::infrastructure::AppState;

/// 构建Beacon路由
pub fn router() -> Router<Arc<AppState>> {
    Router::new()
        .route("/api/all_beacons", get(get_all_beacons))
}
