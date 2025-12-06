mod api;
mod domain;
mod infrastructure;
mod error;
mod config;

use axum::Router;
use std::sync::Arc;
use tower_http::cors::CorsLayer;
use tracing_subscriber;

use infrastructure::state::AppState;
use api::routes;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // 初始化日志
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::INFO)
        .init();

    // 初始化应用状态
    let state = Arc::new(AppState::new());
    tracing::info!("Application state initialized");

    // 构建路由
    let app = Router::new()
        .merge(routes::health::router())
        .merge(routes::beacons::router())
        .layer(CorsLayer::permissive())
        .with_state(state);

    // 启动服务器
    let listener = tokio::net::TcpListener::bind("127.0.0.1:3000")
        .await?;
    
    tracing::info!("Server listening on http://127.0.0.1:3000");
    
    axum::serve(listener, app)
        .await?;

    Ok(())
}
