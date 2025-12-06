//! 基础设施层模块
//!
//! 包含持久化、状态管理等技术实现

pub mod state;
pub mod repository;

pub use state::AppState;
