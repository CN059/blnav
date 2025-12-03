use blunav::algorithms::{Beacon, BeaconSet, RSSIModel, DistanceUnit};
use blunav::network::{HttpServer, PositioningHandler};
use std::net::SocketAddr;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("=== Blunav 室内定位系统 ===");
    println!("启动 HTTP 服务器...\n");

    // 1. 创建信标配置
    println!("步骤 1: 配置信标位置");
    let mut beacons = BeaconSet::new();
    
    beacons.add_beacon(Beacon::new(
        "20:A7:16:5E:C5:D6".to_string(),
        "RFstar_C5D6".to_string(),
        764.0,
        216.0,
        63.0,
    ));
    
    beacons.add_beacon(Beacon::new(
        "20:A7:16:61:0C:F1".to_string(),
        "RFstar_0CF1".to_string(),
        0.0,
        152.0,
        157.0,
    ));
    
    beacons.add_beacon(Beacon::new(
        "20:A7:16:60:FB:FC".to_string(),
        "RFstar_FBFC".to_string(),
        309.0,
        748.0,
        63.0,
    ));
    
    println!("  ✓ 已添加 {} 个信标\n", beacons.len());

    // 2. 创建 RSSI 模型
    println!("步骤 2: 配置 RSSI 距离模型");
    let model = RSSIModel::from_python_fit(-49.656, -43.284, 4.328, DistanceUnit::Centimeter);
    println!("  ✓ RSSI 模型已配置\n");

    // 3. 创建定位处理器
    println!("步骤 3: 创建定位处理器");
    let handler = PositioningHandler::new(beacons, model);
    println!("  ✓ 定位处理器已准备就绪\n");

    // 4. 创建 HTTP 服务器
    println!("步骤 4: 启动 HTTP 服务器");
    let addr: SocketAddr = "127.0.0.1:3000".parse()?;
    let server = HttpServer::new(addr, handler);
    
    println!("  ✓ 服务器已启动，监听地址: {}\n", addr);
    println!("=== 服务器就绪 ===");
    println!("可用端点:");
    println!("  GET  /health           - 服务器健康检查");
    println!("  POST /locate           - 定位请求\n");
    println!("示例请求:");
    println!("curl -X POST http://127.0.0.1:3000/locate \\");
    println!("  -H 'Content-Type: application/json' \\");
    println!("  -d '{{\n");
    println!("    \"client_id\": \"device_001\",\n");
    println!("    \"signals\": [\n");
    println!("      {{\"beacon_id\": \"20:A7:16:5E:C5:D6\", \"rssi\": -52}},\n");
    println!("      {{\"beacon_id\": \"20:A7:16:61:0C:F1\", \"rssi\": -77}},\n");
    println!("      {{\"beacon_id\": \"20:A7:16:60:FB:FC\", \"rssi\": -86}}\n");
    println!("    ],\n");
    println!("    \"options\": {{\n");
    println!("      \"algorithm\": \"auto\",\n");
    println!("      \"min_confidence\": 0.0\n");
    println!("    }}\n");
    println!("  }}'\n");
    println!("按 Ctrl+C 停止服务器\n");

    // 5. 启动服务器
    server.start().await?;
    
    Ok(())
}
