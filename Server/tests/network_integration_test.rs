/// 网络服务演示和集成测试

#[cfg(test)]
mod network_integration_tests {
    use blunav::algorithms::{Beacon, BeaconSet, RSSIModel, DistanceUnit};
    use blunav::network::{
        HttpServer, PositioningHandler, LocationRequest, BeaconSignal,
    };
    use std::net::SocketAddr;

    /// 创建演示用的信标集合
    fn create_demo_beacons() -> BeaconSet {
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
        
        beacons
    }

    /// 创建演示用的 RSSI 模型
    fn create_demo_rssi_model() -> RSSIModel {
        RSSIModel::from_python_fit(-49.656, -43.284, 4.328, DistanceUnit::Centimeter)
    }

    #[tokio::test]
    async fn test_positioning_handler_basic_workflow() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        // 创建定位请求
        let request = LocationRequest::new(
            "device_001".to_string(),
            vec![
                BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52),
                BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77),
                BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86),
            ],
        );

        // 处理请求
        let response = handler.handle_positioning_request(request).await;

        // 验证响应
        assert_eq!(response.code, 200);
        assert_eq!(response.status, "success");
        assert!(response.result.is_some());

        let result = response.result.unwrap();
        println!("定位结果: ({:.2}, {:.2}, {:.2})", result.x, result.y, result.z);
        println!("置信度: {:.1}%", result.confidence * 100.0);
        println!("误差: {:.2}", result.error);
    }

    #[tokio::test]
    async fn test_positioning_handler_with_options() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        let mut request = LocationRequest::new(
            "device_002".to_string(),
            vec![
                BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52),
                BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77),
                BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86),
            ],
        );

        // 设置定位选项
        request.options.algorithm = "least_squares".to_string();
        request.options.enable_kalman_filter = true;
        request.options.min_confidence = 0.2;  // 降低最小置信度阈值

        let response = handler.handle_positioning_request(request).await;

        assert_eq!(response.code, 200);
        assert!(response.result.is_some());
    }

    #[tokio::test]
    async fn test_positioning_handler_invalid_request() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        // 请求信标过少
        let request = LocationRequest::new(
            "device_003".to_string(),
            vec![BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52)],
        );

        let response = handler.handle_positioning_request(request).await;

        assert_eq!(response.code, 400);
        assert_eq!(response.status, "bad_request");
        assert!(response.error_details.is_some());
    }

    #[tokio::test]
    async fn test_positioning_handler_multiple_requests() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        // 多个不同的客户端请求
        let requests = vec![
            LocationRequest::new(
                "client_1".to_string(),
                vec![
                    BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52),
                    BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77),
                    BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86),
                ],
            ),
            LocationRequest::new(
                "client_2".to_string(),
                vec![
                    BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -48),
                    BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -70),
                    BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -80),
                ],
            ),
        ];

        for request in requests {
            let response = handler.handle_positioning_request(request).await;
            assert_eq!(response.code, 200);
            assert!(response.result.is_some());
        }
    }

    #[test]
    fn test_http_server_initialization() {
        let addr: SocketAddr = "127.0.0.1:3000".parse().unwrap();
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        let _server = HttpServer::new(addr, handler);
        // 验证创建成功
    }

    #[tokio::test]
    async fn test_request_with_timestamps() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_millis() as u64;

        let mut request = LocationRequest::new(
            "device_time".to_string(),
            vec![
                BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52)
                    .with_timestamp(now),
                BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77)
                    .with_timestamp(now),
                BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86)
                    .with_timestamp(now),
            ],
        );

        request.request_timestamp_ms = Some(now);

        let response = handler.handle_positioning_request(request).await;
        assert_eq!(response.code, 200);
    }

    #[tokio::test]
    async fn test_request_with_beacon_names() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        let request = LocationRequest::new(
            "device_named".to_string(),
            vec![
                BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52)
                    .with_name("RFstar_C5D6".to_string()),
                BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77)
                    .with_name("RFstar_0CF1".to_string()),
                BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86)
                    .with_name("RFstar_FBFC".to_string()),
            ],
        );

        let response = handler.handle_positioning_request(request).await;
        assert_eq!(response.code, 200);
        assert!(response.result.is_some());
    }

    #[tokio::test]
    async fn test_algorithm_selection() {
        let beacons = create_demo_beacons();
        let model = create_demo_rssi_model();
        let handler = PositioningHandler::new(beacons, model);

        let algorithms = vec!["basic", "weighted", "least_squares", "auto"];

        for algo in algorithms {
            let mut request = LocationRequest::new(
                format!("device_{}", algo),
                vec![
                    BeaconSignal::new("20:A7:16:5E:C5:D6".to_string(), -52),
                    BeaconSignal::new("20:A7:16:61:0C:F1".to_string(), -77),
                    BeaconSignal::new("20:A7:16:60:FB:FC".to_string(), -86),
                ],
            );

            request.options.algorithm = algo.to_string();
            let response = handler.handle_positioning_request(request).await;
            
            assert_eq!(response.code, 200, "Algorithm {} failed", algo);
            assert!(response.result.is_some());
        }
    }
}
