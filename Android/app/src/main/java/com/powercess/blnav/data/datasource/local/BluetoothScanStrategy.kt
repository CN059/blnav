package com.powercess.blnav.data.datasource.local

/**
 * 蓝牙扫描策略配置
 *
 * 成员：
 *   - updateInterval: 设备数据的发布间隔（毫秒），默认500ms
 *
 * 用途：
 *   定义设备数据的缓冲和发布策略，避免过于频繁的数据流更新
 *   当扫描到设备信息变化时，缓冲数据并按此间隔定时发布
 *
 * 工作原理：
 *   - 设备信息变化 → 加入缓冲
 *   - 检查时间 ≥ updateInterval → 立即发布缓冲的所有设备
 *   - 否则 → 继续缓冲等待
 */
data class BluetoothScanStrategy(
    /**
     * 设备数据更新发布的时间间隔（毫秒）
     *
     * 推荐值：
     *   - 100-200ms: 高频更新（对RSSI变化敏感，功耗较高）
     *   - 500ms: 中等更新频率（平衡实时性和功耗，默认值）
     *   - 1000ms+: 低频更新（节省功耗，实时性一般）
     */
    val updateInterval: Long = 500L
) {
    init {
        require(updateInterval > 0) { "updateInterval 必须大于 0" }
    }
}

