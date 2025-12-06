//! 地理位置模型

use serde::{Deserialize, Serialize};

/// 地理位置信息
///
/// 表示一个三维空间中的位置，包含楼层和区域信息
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Location {
    /// X坐标（单位：米）
    pub x: f64,
    /// Y坐标（单位：米）
    #[serde(rename = "y")]
    pub y: f64,
    /// Z坐标/高度（单位：米）
    pub z: f64,
    /// 楼层
    pub floor: String,
    /// 区域ID
    pub area_id: String,
}

impl Location {
    /// 创建新的位置
    pub fn new(x: f64, y: f64, z: f64, floor: String, area_id: String) -> Self {
        Self { x, y, z, floor, area_id }
    }

    /// 验证位置数据的有效性
    #[allow(dead_code)]
    pub fn validate(&self) -> crate::error::Result<()> {
        if self.floor.is_empty() {
            return Err(crate::error::AppError::ValidationError(
                "Floor cannot be empty".to_string(),
            ));
        }

        if self.area_id.is_empty() {
            return Err(crate::error::AppError::ValidationError(
                "Area ID cannot be empty".to_string(),
            ));
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_location_creation() {
        let loc = Location::new(100.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string());
        assert_eq!(loc.x, 100.0);
        assert_eq!(loc.y, 200.0);
        assert_eq!(loc.z, 150.0);
    }

    #[test]
    fn test_location_validation() {
        let loc = Location::new(100.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string());
        assert!(loc.validate().is_ok());

        let invalid_loc = Location::new(100.0, 200.0, 150.0, "".to_string(), "area_001".to_string());
        assert!(invalid_loc.validate().is_err());
    }
}
