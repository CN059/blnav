/// HTTP 请求处理器
/// 
/// 职责：
/// - 验证请求数据
/// - 调用定位算法
/// - 返回定位结果

use crate::network::request::LocationRequest;
use crate::network::response::{HttpResponse, PositioningResult};
use crate::algorithms::{Beacon, BeaconSet, LocationAlgorithm, RSSIModel, SignalReadings};

/// 定位处理器配置
pub struct PositioningHandler {
    /// 已配置的信标集合
    beacons: BeaconSet,
    
    /// RSSI 模型
    rssi_model: RSSIModel,
}

impl PositioningHandler {
    /// 创建新的处理器
    pub fn new(beacons: BeaconSet, rssi_model: RSSIModel) -> Self {
        PositioningHandler {
            beacons,
            rssi_model,
        }
    }

    /// 处理定位请求
    pub async fn handle_positioning_request(&self, request: LocationRequest) -> HttpResponse {
        // 1. 验证请求
        if let Err(e) = request.validate() {
            return HttpResponse::bad_request(e);
        }

        if let Err(e) = request.options.validate() {
            return HttpResponse::bad_request(e);
        }

        // 2. 转换信号数据格式
        let mut signals = SignalReadings::new();
        for signal in &request.signals {
            signals.add(signal.beacon_id.clone(), signal.rssi);
        }

        // 3. 选择定位算法
        let algorithm_name = &request.options.algorithm;
        let beacons_ref: Vec<Beacon> = self.beacons.all_cloned();

        let positioning_result = match algorithm_name.as_str() {
            "basic" => {
                LocationAlgorithm::trilateration_basic(&beacons_ref, &signals, &self.rssi_model)
            }
            "weighted" => {
                LocationAlgorithm::trilateration_weighted(&beacons_ref, &signals, &self.rssi_model)
            }
            "least_squares" => {
                LocationAlgorithm::trilateration_least_squares(&beacons_ref, &signals, &self.rssi_model)
            }
            "auto" => {
                // 自动选择最佳算法
                match beacons_ref.len() {
                    0..=2 => None,
                    3 => LocationAlgorithm::trilateration_weighted(&beacons_ref, &signals, &self.rssi_model),
                    _ => LocationAlgorithm::trilateration_least_squares(&beacons_ref, &signals, &self.rssi_model),
                }
            }
            _ => None,
        };

        // 4. 检查定位结果
        let mut result = match positioning_result {
            Some(loc_result) => {
                // 检查置信度是否满足要求
                if loc_result.confidence < request.options.min_confidence {
                    return HttpResponse::positioning_failed(
                        request.client_id,
                        format!(
                            "置信度 {:.1}% 低于要求 {:.1}%",
                            loc_result.confidence * 100.0,
                            request.options.min_confidence * 100.0
                        ),
                    );
                }

                PositioningResult::new(
                    loc_result.x,
                    loc_result.y,
                    loc_result.z,
                    loc_result.confidence,
                    loc_result.error,
                    loc_result.method,
                    loc_result.beacon_count,
                )
            }
            None => {
                return HttpResponse::positioning_failed(
                    request.client_id,
                    "无法执行定位计算".to_string(),
                )
            }
        };

        // 5. 添加时间戳
        result = result.with_timestamp(
            request.request_timestamp_ms.unwrap_or_else(|| {
                std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .unwrap_or_default()
                    .as_millis() as u64
            })
        );

        // 6. 返回成功响应
        HttpResponse::success(request.client_id, result)
    }

    /// 获取当前配置的信标数量
    pub fn beacon_count(&self) -> usize {
        self.beacons.len()
    }

    /// 获取 RSSI 模型描述
    pub fn rssi_model_description(&self) -> String {
        self.rssi_model.description()
    }

    /// 更新信标配置
    pub fn update_beacons(&mut self, beacons: BeaconSet) {
        self.beacons = beacons;
    }

    /// 更新 RSSI 模型
    pub fn update_rssi_model(&mut self, model: RSSIModel) {
        self.rssi_model = model;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::algorithms::{Beacon, BeaconSet, RSSIModel, DistanceUnit};

    #[tokio::test]
    async fn test_positioning_handler_creation() {
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

        assert_eq!(handler.beacon_count(), 1);
    }

    #[tokio::test]
    async fn test_positioning_handler_request_validation() {
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

        // 创建无效请求（少于 3 个信标）
        let request = LocationRequest::new(
            "client1".to_string(),
            vec![crate::network::request::BeaconSignal::new("B1".to_string(), -50)],
        );

        let response = handler.handle_positioning_request(request).await;
        assert_eq!(response.code, 400);
    }
}
