//! Beacon 处理程序

use axum::{
    extract::State,
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use std::sync::Arc;

use crate::api::dto::{ApiResponse, BeaconDto};
use crate::infrastructure::AppState;

/// 获取所有Beacon设备
pub async fn get_all_beacons(
    State(state): State<Arc<AppState>>,
) -> impl IntoResponse {
    let repo = state.beacon_repository();
    
    match repo.find_all().await {
        Ok(beacons) => {
            let beacon_dtos: Vec<BeaconDto> = beacons.iter().map(BeaconDto::from).collect();
            let response = ApiResponse::success(
                "获取所有beacon设备成功".to_string(),
                beacon_dtos,
            );
            (StatusCode::OK, Json(response)).into_response()
        }
        Err(e) => {
            tracing::error!("Failed to fetch beacons: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({
                    "status": 500,
                    "if_success": false,
                    "message": "Failed to fetch beacons"
                })),
            ).into_response()
        }
    }
}
