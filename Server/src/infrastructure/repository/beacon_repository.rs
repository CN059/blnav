//! Beacon 仓储实现

use tokio::sync::RwLock;
use std::collections::HashMap;
use crate::domain::Beacon;
use crate::domain::Location;
use crate::error::Result;

/// Beacon 数据仓储
///
/// 提供Beacon数据的持久化和查询能力
pub struct BeaconRepository {
    /// 内存存储（生产环境应使用数据库）
    data: RwLock<HashMap<String, Beacon>>,
}

impl BeaconRepository {
    /// 创建新的仓储实例
    pub fn new() -> Self {
        let mut data = HashMap::new();
        
        // 初始化默认数据
        let beacons = vec![
            Beacon::new(
                "beacon_001".to_string(),
                "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
                10000,
                12345,
                Location::new(100.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string()),
                -59,
                1000,
                "active".to_string(),
            ),
            Beacon::new(
                "beacon_002".to_string(),
                "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
                10000,
                12346,
                Location::new(500.0, 200.0, 150.0, "1F".to_string(), "area_001".to_string()),
                -59,
                1000,
                "active".to_string(),
            ),
            Beacon::new(
                "beacon_003".to_string(),
                "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
                10000,
                12347,
                Location::new(300.0, 600.0, 150.0, "1F".to_string(), "area_002".to_string()),
                -59,
                1000,
                "active".to_string(),
            ),
            Beacon::new(
                "beacon_004".to_string(),
                "FDA50693-A4E2-4FB1-AFCF-C6EB07647825".to_string(),
                10000,
                12348,
                Location::new(100.0, 800.0, 150.0, "1F".to_string(), "area_002".to_string()),
                -59,
                1000,
                "active".to_string(),
            ),
        ];

        for beacon in beacons {
            data.insert(beacon.id.clone(), beacon);
        }

        Self {
            data: RwLock::new(data),
        }
    }

    /// 获取所有Beacon
    pub async fn find_all(&self) -> Result<Vec<Beacon>> {
        let data = self.data.read().await;
        Ok(data.values().cloned().collect())
    }

    /// 根据ID获取Beacon
    #[allow(dead_code)]
    pub async fn find_by_id(&self, id: &str) -> Result<Option<Beacon>> {
        let data = self.data.read().await;
        Ok(data.get(id).cloned())
    }

    /// 创建Beacon
    #[allow(dead_code)]
    pub async fn create(&self, beacon: Beacon) -> Result<Beacon> {
        beacon.validate()?;
        
        let mut data = self.data.write().await;
        data.insert(beacon.id.clone(), beacon.clone());
        Ok(beacon)
    }

    /// 更新Beacon
    #[allow(dead_code)]
    pub async fn update(&self, beacon: Beacon) -> Result<Beacon> {
        beacon.validate()?;
        
        let mut data = self.data.write().await;
        if !data.contains_key(&beacon.id) {
            return Err(crate::error::AppError::NotFound(
                format!("Beacon with id {} not found", beacon.id),
            ));
        }
        
        data.insert(beacon.id.clone(), beacon.clone());
        Ok(beacon)
    }

    /// 删除Beacon
    #[allow(dead_code)]
    pub async fn delete(&self, id: &str) -> Result<()> {
        let mut data = self.data.write().await;
        if data.remove(id).is_none() {
            return Err(crate::error::AppError::NotFound(
                format!("Beacon with id {} not found", id),
            ));
        }
        Ok(())
    }

    /// 获取Beacon总数
    #[allow(dead_code)]
    pub async fn count(&self) -> Result<usize> {
        let data = self.data.read().await;
        Ok(data.len())
    }
}

impl Default for BeaconRepository {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_find_all() {
        let repo = BeaconRepository::new();
        let beacons = repo.find_all().await.unwrap();
        assert_eq!(beacons.len(), 4);
    }

    #[tokio::test]
    async fn test_find_by_id() {
        let repo = BeaconRepository::new();
        let beacon = repo.find_by_id("beacon_001").await.unwrap();
        assert!(beacon.is_some());
        assert_eq!(beacon.unwrap().id, "beacon_001");
    }

    #[tokio::test]
    async fn test_count() {
        let repo = BeaconRepository::new();
        let count = repo.count().await.unwrap();
        assert_eq!(count, 4);
    }
}
