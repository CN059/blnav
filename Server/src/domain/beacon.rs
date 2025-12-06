//! Beacon 蓝牙信标模型

use serde::{Deserialize, Serialize};
use crate::domain::location::Location;

/// Beacon 设备信息
///
/// 表示一个BLE信标设备的完整信息
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Beacon {
    /// 设备唯一ID
    pub id: String,
    /// UUID
    pub uuid: String,
    /// Major值
    pub major: i32,
    /// Minor值
    pub minor: i32,
    /// 位置信息
    pub location: Location,
    /// 发射功率（dBm）
    pub power: i32,
    /// 广播间隔（毫秒）
    pub interval: i32,
    /// 设备状态
    pub status: String,
}

impl Beacon {
    /// 创建新的Beacon
    pub fn new(
        id: String,
        uuid: String,
        major: i32,
        minor: i32,
        location: Location,
        power: i32,
        interval: i32,
        status: String,
    ) -> Self {
        Self {
            id,
            uuid,
            major,
            minor,
            location,
            power,
            interval,
            status,
        }
    }

    /// 验证Beacon数据的有效性
    #[allow(dead_code)]
    pub fn validate(&self) -> crate::error::Result<()> {
        if self.id.is_empty() {
            return Err(crate::error::AppError::ValidationError(
                "Beacon ID cannot be empty".to_string(),
            ));
        }

        if self.uuid.is_empty() {
            return Err(crate::error::AppError::ValidationError(
                "UUID cannot be empty".to_string(),
            ));
        }

        if self.power < -100 || self.power > 0 {
            return Err(crate::error::AppError::ValidationError(
                "Power must be between -100 and 0 dBm".to_string(),
            ));
        }

        if self.interval <= 0 {
            return Err(crate::error::AppError::ValidationError(
                "Interval must be greater than 0".to_string(),
            ));
        }

        self.location.validate()?;
        Ok(())
    }

    /// 检查设备是否活跃
    #[allow(dead_code)]
    pub fn is_active(&self) -> bool {
        self.status.to_lowercase() == "active"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_test_beacon() -> Beacon {
        let loc = Location::new(100.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string());
        Beacon::new(
            "beacon_001".to_string(),
            "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
            10000,
            12345,
            loc,
            -59,
            1000,
            "active".to_string(),
        )
    }

    #[test]
    fn test_beacon_creation() {
        let beacon = create_test_beacon();
        assert_eq!(beacon.id, "beacon_001");
        assert!(beacon.is_active());
    }

    #[test]
    fn test_beacon_validation() {
        let beacon = create_test_beacon();
        assert!(beacon.validate().is_ok());

        let loc = Location::new(100.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string());
        let invalid_beacon = Beacon::new(
            "".to_string(),
            "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
            10000,
            12345,
            loc,
            -59,
            1000,
            "active".to_string(),
        );
        assert!(invalid_beacon.validate().is_err());
    }
}
