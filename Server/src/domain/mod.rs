//! 领域模型模块
//!
//! 包含应用的核心业务模型，独立于技术实现

pub mod beacon;
pub mod location;

pub use beacon::Beacon;
pub use location::Location;
