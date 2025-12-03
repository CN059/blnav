/// HTTP 服务器实现
/// 
/// 使用 tokio 和 hyper 构建 HTTP 服务器
/// 职责：
/// - 启动 HTTP 服务器
/// - 路由请求到对应的处理器
/// - 返回 HTTP 响应

use std::convert::Infallible;
use std::net::SocketAddr;
use std::sync::Arc;

use hyper::{Body, Method, Request, Response, Server, StatusCode};
use tokio::sync::RwLock;

use crate::network::handlers::PositioningHandler;
use crate::network::request::LocationRequest;
use crate::network::response::HttpResponse;

/// HTTP 服务器
pub struct HttpServer {
    /// 监听地址
    addr: SocketAddr,
    
    /// 定位处理器
    handler: Arc<RwLock<PositioningHandler>>,
}

impl HttpServer {
    /// 创建新的 HTTP 服务器
    pub fn new(addr: SocketAddr, handler: PositioningHandler) -> Self {
        HttpServer {
            addr,
            handler: Arc::new(RwLock::new(handler)),
        }
    }

    /// 启动 HTTP 服务器
    pub async fn start(self) -> Result<(), Box<dyn std::error::Error>> {
        let handler = self.handler.clone();

        // 构建路由
        let make_svc = hyper::service::make_service_fn(move |_conn| {
            let handler = handler.clone();
            async move {
                Ok::<_, Infallible>(hyper::service::service_fn(move |req| {
                    let handler = handler.clone();
                    handle_request(req, handler)
                }))
            }
        });

        let server = Server::bind(&self.addr).serve(make_svc);

        println!("🌐 HTTP 服务器启动: http://{}", self.addr);
        println!("📡 POST /locate - 接收定位请求");

        server.await?;

        Ok(())
    }

    /// 获取处理器引用（用于配置更新）
    pub fn handler(&self) -> Arc<RwLock<PositioningHandler>> {
        self.handler.clone()
    }
}

/// 处理 HTTP 请求
async fn handle_request(
    req: Request<Body>,
    handler: Arc<RwLock<PositioningHandler>>,
) -> Result<Response<Body>, Infallible> {
    match (req.method().clone(), req.uri().path()) {
        // 健康检查端点
        (Method::GET, "/health") => {
            let handler = handler.read().await;
            let response = serde_json::json!({
                "status": "ok",
                "beacon_count": handler.beacon_count(),
                "rssi_model": handler.rssi_model_description(),
            });

            Ok(Response::builder()
                .status(StatusCode::OK)
                .header("Content-Type", "application/json")
                .body(Body::from(response.to_string()))
                .unwrap())
        }

        // 定位请求端点
        (Method::POST, "/locate") => {
            handle_locate_request(req, handler).await
        }

        // 404 - 未找到
        _ => {
            let response = serde_json::json!({
                "status": "error",
                "message": "未找到该端点",
                "path": req.uri().path(),
            });

            Ok(Response::builder()
                .status(StatusCode::NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(Body::from(response.to_string()))
                .unwrap())
        }
    }
}

/// 处理定位请求
async fn handle_locate_request(
    req: Request<Body>,
    handler: Arc<RwLock<PositioningHandler>>,
) -> Result<Response<Body>, Infallible> {
    // 读取请求体
    let bytes = match hyper::body::to_bytes(req.into_body()).await {
        Ok(b) => b,
        Err(e) => {
            let response = HttpResponse::bad_request(format!("无法读取请求体: {}", e));
            let json = response.to_json().unwrap_or_else(|_| "{}".to_string());
            return Ok(Response::builder()
                .status(StatusCode::BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Body::from(json))
                .unwrap());
        }
    };

    // 解析 JSON 请求
    let request: LocationRequest = match serde_json::from_slice(&bytes) {
        Ok(r) => r,
        Err(e) => {
            let response = HttpResponse::bad_request(format!("JSON 格式错误: {}", e));
            let json = response.to_json().unwrap_or_else(|_| "{}".to_string());
            return Ok(Response::builder()
                .status(StatusCode::BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(Body::from(json))
                .unwrap());
        }
    };

    // 获取处理器并处理请求
    let handler_lock = handler.read().await;
    let response = handler_lock.handle_positioning_request(request).await;

    // 返回响应
    let json = response.to_json().unwrap_or_else(|_| "{}".to_string());
    let status = StatusCode::from_u16(response.code).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);

    Ok(Response::builder()
        .status(status)
        .header("Content-Type", "application/json; charset=utf-8")
        .body(Body::from(json))
        .unwrap())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::algorithms::{Beacon, BeaconSet, RSSIModel, DistanceUnit};

    #[test]
    fn test_http_server_creation() {
        let addr: SocketAddr = "127.0.0.1:3000".parse().unwrap();

        let mut beacons = BeaconSet::new();
        beacons.add_beacon(Beacon::new(
            "B1".to_string(),
            "Beacon1".to_string(),
            0.0,
            0.0,
            100.0,
        ));

        let model = RSSIModel::log_distance(-50.0, -40.0, DistanceUnit::Centimeter);
        let handler = PositioningHandler::new(beacons, model);
        let _server = HttpServer::new(addr, handler);

        // 只验证创建成功
        assert_eq!(_server.addr, addr);
    }
}
