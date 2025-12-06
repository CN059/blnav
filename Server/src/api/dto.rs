//! 数据传输对象（DTO）

use serde::{Deserialize, Serialize};
use crate::domain::Beacon;

/// API 响应体
#[derive(Debug, Serialize)]
pub struct ApiResponse<T: Serialize> {
    /// 响应状态码
    pub status: u16,
    /// 请求是否成功
    pub if_success: bool,
    /// 对响应的描述
    pub message: String,
    /// 响应数据
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
}

impl<T: Serialize> ApiResponse<T> {
    /// 创建成功响应
    pub fn success(message: String, data: T) -> Self {
        Self {
            status: 200,
            if_success: true,
            message,
            data: Some(data),
        }
    }
}

/// 创建错误响应
#[derive(Debug, Serialize)]
pub struct ErrorResponse {
    pub status: u16,
    pub if_success: bool,
    pub message: String,
}

impl ErrorResponse {
    /// 创建新的错误响应
    #[allow(dead_code)]
    pub fn new(status: u16, message: String) -> Self {
        Self {
            status,
            if_success: false,
            message,
        }
    }
}

/// Beacon 响应 DTO
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct BeaconDto {
    pub id: String,
    pub uuid: String,
    pub major: i32,
    pub minor: i32,
    pub location: LocationDto,
    pub power: i32,
    pub interval: i32,
    pub status: String,
}

/// Location 响应 DTO
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct LocationDto {
    pub x: f64,
    #[serde(rename = "y")]
    pub y: f64,
    pub z: f64,
    pub floor: String,
    pub area_id: String,
}

impl From<&Beacon> for BeaconDto {
    fn from(beacon: &Beacon) -> Self {
        Self {
            id: beacon.id.clone(),
            uuid: beacon.uuid.clone(),
            major: beacon.major,
            minor: beacon.minor,
            location: LocationDto {
                x: beacon.location.x,
                y: beacon.location.y,
                z: beacon.location.z,
                floor: beacon.location.floor.clone(),
                area_id: beacon.location.area_id.clone(),
            },
            power: beacon.power,
            interval: beacon.interval,
            status: beacon.status.clone(),
        }
    }
}

impl From<Beacon> for BeaconDto {
    fn from(beacon: Beacon) -> Self {
        Self::from(&beacon)
    }
}
