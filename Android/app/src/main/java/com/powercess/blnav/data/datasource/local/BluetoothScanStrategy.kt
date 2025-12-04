package com.powercess.blnav.data.datasource.local

/**
 * 蓝牙扫描策略
 *
 * ==================== 功能说明 ====================
 *
 * 定义蓝牙设备扫描的规则和缓存策略，用于控制设备数据的更新频率
 * 和缓存机制，使得其他模块能够按需获取实时但不过于频繁的设备数据。
 *
 * ==================== 更新间隔说明 ====================
 *
 * updateInterval: 设备数据的发布间隔（毫秒）
 *
 * - 当设备信息（RSSI、名字、MAC地址等）发生变化时
 * - 如果距上次发布的时间 >= updateInterval，则立即发布新数据
 * - 否则缓冲数据，等待下一个发布时机
 * - 这样可以避免过于频繁的数据流更新，同时保证数据的相对实时性
 *
 * 推荐值范围：
 * - 100ms - 200ms：高频更新，适合对实时性要求高的场景
 * - 500ms - 1000ms：中等更新频率，适合一般定位场景（默认500ms）
 * - 2000ms+：低频更新，适合流量受限或性能受限的场景
 *
 * ==================== 使用示例 ====================
 *
 * ```
 * // 创建扫描策略（每500ms更新一次设备列表）
 * val scanStrategy = BluetoothScanStrategy(updateInterval = 500L)
 *
 * // 传入到蓝牙数据源
 * val bluetoothDataSource = BluetoothLocalDataSource(
 *     context,
 *     filterDataSource,
 *     scanStrategy
 * )
 *
 * // 或使用默认策略（500ms）
 * val bluetoothDataSource = BluetoothLocalDataSource(context, filterDataSource)
 * ```
 *
 * ==================== 设计原理 ====================
 *
 * 1. 缓冲机制：
 *    - 内部缓冲扫描到的设备数据
 *    - 定时检查是否需要发布（超过updateInterval）
 *    - 减少StateFlow频繁更新的开销
 *
 * 2. 解耦设计：
 *    - 将扫描策略从蓝牙数据源中独立出来
 *    - 不同模块可使用不同的策略而无需修改数据源
 *    - 便于测试和维护
 *
 * 3. 性能优化：
 *    - 避免高频率的StateFlow更新
 *    - 减少协程间的数据同步开销
 *    - 支持灵活的更新频率配置
 */
data class BluetoothScanStrategy(
    /**
     * 设备数据更新发布的时间间隔（毫秒）
     * 当扫描到新设备或设备信息更新时，以此间隔发布数据
     * 默认值：500ms
     */
    val updateInterval: Long = 500L
) {
    init {
        require(updateInterval > 0) { "updateInterval 必须大于 0" }
    }
}

