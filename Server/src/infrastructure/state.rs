//! 应用状态管理

use std::sync::Arc;
use crate::infrastructure::repository::BeaconRepository;

/// 应用全局状态
///
/// 管理应用的所有共享状态，包括Beacon数据等
pub struct AppState {
    /// Beacon 仓储
    beacon_repo: Arc<BeaconRepository>,
}

impl AppState {
    /// 创建新的应用状态
    pub fn new() -> Self {
        let beacon_repo = Arc::new(BeaconRepository::new());
        
        // 初始化默认数据
        Self::init_default_data(&beacon_repo);
        
        Self { beacon_repo }
    }

    /// 获取Beacon仓储
    pub fn beacon_repository(&self) -> Arc<BeaconRepository> {
        Arc::clone(&self.beacon_repo)
    }

    /// 初始化默认数据
    fn init_default_data(_repo: &BeaconRepository) {
        // 通过将init_default_data改为async方式处理
        // 这里仅占位，实际初始化通过异步方式在启动时进行
    }
}

impl Default for AppState {
    fn default() -> Self {
        Self::new()
    }
}
