package com.powercess.blnav.data.datasource.local

import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 蓝牙设备全局管理数据源
 *
 * ==================== 功能说明 ====================
 *
 * 为其他模块提供统一、实时但可控的蓝牙设备数据访问接口。
 * 基于扫描策略进行缓存管理，避免频繁的数据流更新，同时保证数据相对实时性。
 *
 * ==================== 主要特性 ====================
 *
 * 1. 缓存管理：
 *    - 内部维护设备数据缓存
 *    - 按照扫描策略的updateInterval定时发布更新
 *    - 减少StateFlow更新频率，提高性能
 *
 * 2. 实时更新：
 *    - 扫描到的设备信息（RSSI、名字、MAC地址等）立即缓冲
 *    - 当满足发布条件时，立即通知订阅者
 *    - 支持多个模块同时订阅同一个数据源
 *
 * 3. 解耦设计：
 *    - 独立于BluetoothLocalDataSource的扫描逻辑
 *    - 其他模块只需依赖此数据源，无需关心扫描细节
 *    - 便于服务器上传、UI更新、本地存储等操作
 *
 * ==================== 使用示例 ====================
 *
 * ```
 * // 获取全局蓝牙设备管理数据源
 * val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
 *
 * // 订阅设备列表更新
 * deviceManager.managedDevices.collect { devices ->
 *     // 设备列表已更新，可以上传至服务器进行定位
 *     sendToLocationServer(devices)
 * }
 *
 * // 获取当前缓存的设备列表
 * val currentDevices = deviceManager.getManagedDevices()
 *
 * // 订阅指定MAC地址的设备更新
 * deviceManager.getDevice("AA:BB:CC:DD:EE:FF").collect { device ->
 *     if (device != null) {
 *         updateDeviceUI(device)
 *     }
 * }
 * ```
 *
 * ==================== 数据流向 ====================
 *
 * BluetoothLocalDataSource
 *     ↓ (扫描发现设备)
 * BluetoothLocalDataSource.onDeviceDiscovered(device)
 *     ↓ (调用)
 * BluetoothDeviceManagerDataSource.updateDevice(device)
 *     ↓ (缓冲和定时检查)
 * BluetoothDeviceManagerDataSource.publishUpdates()
 *     ↓ (如果超过updateInterval)
 * managedDevices StateFlow 发射新数据
 *     ↓
 * 其他模块订阅者接收更新（例如定位服务）
 *
 * ==================== 内部缓存机制 ====================
 *
 * 1. 缓冲存储：
 *    - pendingUpdates: 存储待发布的设备更新
 *    - managedDevices: 已发布的设备列表
 *
 * 2. 发布策略：
 *    - 每次updateDevice()调用时检查是否需要发布
 *    - 如果距上次发布 >= updateInterval，立即发布所有待更新
 *    - 否则继续缓冲
 *
 * 3. 性能优化：
 *    - 避免重复的MAC地址存储
 *    - 只发布有变化的设备集合
 *    - 支持外部控制发布时机（手动发布）
 */
class BluetoothDeviceManagerDataSource(
    private val scanStrategy: BluetoothScanStrategy = BluetoothScanStrategy()
) {

    companion object {
        private var instance: BluetoothDeviceManagerDataSource? = null
        private val lock = Any()

        /**
         * 获取全局单例实例
         *
         * 推荐在Application或依赖注入框架中初始化一次，然后通过此方法获取
         * 如果还未初始化，则使用默认扫描策略创建
         */
        fun getInstance(): BluetoothDeviceManagerDataSource {
            return instance ?: synchronized(lock) {
                instance ?: BluetoothDeviceManagerDataSource().also { instance = it }
            }
        }

        /**
         * 使用指定的扫描策略初始化单例实例
         *
         * 应该在应用启动时调用一次，之后的getInstance()调用都会返回这个实例
         * 如果已经初始化过，此方法会直接返回已有实例（不会重新初始化）
         */
        fun initializeWith(scanStrategy: BluetoothScanStrategy): BluetoothDeviceManagerDataSource {
            return synchronized(lock) {
                instance ?: BluetoothDeviceManagerDataSource(scanStrategy).also { instance = it }
            }
        }

        /**
         * 重置单例实例（主要用于测试）
         */
        fun resetInstance() {
            synchronized(lock) {
                instance = null
            }
        }
    }

    // 已发布的管理设备列表（外部可订阅）
    private val _managedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val managedDevices: StateFlow<List<BluetoothDeviceModel>> = _managedDevices.asStateFlow()

    // 缓冲的待发布设备更新（内部使用）
    private val pendingUpdates = mutableMapOf<String, BluetoothDeviceModel>()

    // 上次发布的时间戳
    private var lastPublishTime = 0L

    /**
     * 更新设备信息
     *
     * 此方法由BluetoothLocalDataSource调用，用于向管理器提交扫描到的设备。
     * 内部会根据扫描策略决定是否立即发布或缓冲等待。
     *
     * @param device 要更新的蓝牙设备信息
     */
    fun updateDevice(device: BluetoothDeviceModel) {
        val timestamp = System.currentTimeMillis()

        // 将设备加入待发布缓冲
        val isNewDevice = !pendingUpdates.containsKey(device.address)
        pendingUpdates[device.address] = device

        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "[${timestamp}] ⏱️ updateDevice: ${if (isNewDevice) "新设备" else "更新设备"} ${device.name} (${device.address}) | RSSI=${device.rssi}dBm | 缓冲=${pendingUpdates.size}"
        )

        // 检查是否需要发布更新
        checkAndPublishUpdates()
    }

    /**
     * 批量更新设备信息
     *
     * 用于一次性提交多个设备，例如在扫描完成时提交全部设备。
     *
     * @param devices 要更新的蓝牙设备列表
     */
    fun updateDevices(devices: List<BluetoothDeviceModel>) {
        devices.forEach { device ->
            pendingUpdates[device.address] = device
        }
        checkAndPublishUpdates()
    }

    /**
     * 检查并发布更新
     *
     * 如果距上次发布已经超过updateInterval，则立即发布所有待更新的设备；
     * 否则继续缓冲，等待下一个发布时机。
     *
     * 此方法由updateDevice()自动调用，也可外部手动调用以强制发布。
     *
     * ==================== 修复说明 ====================
     *
     * 添加详细的调试日志，用于追踪时序问题：
     * - 跟踪updateInterval的检查
     * - 记录缓冲中的设备数量
     * - 记录是否触发发布
     */
    fun checkAndPublishUpdates() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastPublish = currentTime - lastPublishTime

        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "📊 检查发布条件: 时间差=${timeSinceLastPublish}ms / 要求=${scanStrategy.updateInterval}ms | 缓冲设备=${pendingUpdates.size} | 已管理=${_managedDevices.value.size}"
        )

        // 检查是否已达到发布间隔或这是第一次发布
        if (lastPublishTime == 0L || timeSinceLastPublish >= scanStrategy.updateInterval) {
            val reason = if (lastPublishTime == 0L) "首次发布" else "时间充足(${timeSinceLastPublish}ms >= ${scanStrategy.updateInterval}ms)"
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "✅ 触发发布 ($reason) | 缓冲设备=${pendingUpdates.size}"
            )
            publishUpdates(currentTime)
        } else {
            val remainingTime = scanStrategy.updateInterval - timeSinceLastPublish
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "⏳ 缓冲等待中... (还需${remainingTime}ms) | 缓冲设备=${pendingUpdates.size}"
            )
        }
    }

    /**
     * 立即强制发布所有缓冲的更新
     *
     * 不受updateInterval限制，立即将所有待更新的设备发布出去。
     * 用于特殊场景，例如扫描完成时需要立即同步数据。
     */
    fun forcePublish() {
        val timestamp = System.currentTimeMillis()
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "[${timestamp}] 💪 强制发布: 缓冲=${pendingUpdates.size}, 已管理=${_managedDevices.value.size}"
        )
        publishUpdates(timestamp)
    }

    /**
     * 内部发布方法
     *
     * 将缓冲中的设备与已发布的列表合并，生成新的设备列表并发射。
     *
     * ==================== 修复说明 ====================
     *
     * 这个方法是动态更新的关键。修复点：
     * 1. 使用addressToIndex映射直接查找索引，避免重复的indexOfFirst()
     * 2. 清晰的合并逻辑：更新已有设备，添加新设备
     * 3. 强制更新StateFlow的值，即使内容相似也会触发订阅者更新
     * 4. 详细的调试日志记录设备变化
     */
    private fun publishUpdates(currentTime: Long) {
        if (pendingUpdates.isEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "⏭️ pendingUpdates为空，跳过发布 [时间: $currentTime]"
            )
            return
        }

        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "🔄 开始处理发布 [时间: $currentTime] | 缓冲设备数: ${pendingUpdates.size}"
        )

        // 合并已发布的设备和新的更新
        val currentDevices = _managedDevices.value.toMutableList()

        // 创建已发布设备的地址→索引映射
        val addressToIndex = mutableMapOf<String, Int>()
        currentDevices.forEachIndexed { index, device ->
            addressToIndex[device.address] = index
        }

        var updatedCount = 0
        var addedCount = 0
        val updatedAddresses = mutableListOf<String>()
        val addedAddresses = mutableListOf<String>()

        // 遍历待发布的设备，进行更新或添加
        pendingUpdates.forEach { (address, device) ->
            val existingIndex = addressToIndex[address]
            if (existingIndex != null) {
                // 更新已有设备
                currentDevices[existingIndex] = device
                updatedCount++
                updatedAddresses.add("${device.name}($address, RSSI=${device.rssi}dBm)")
                AppLogger.debug(
                    "BluetoothDeviceManagerDataSource",
                    "更新设备: ${device.name} | MAC: $address | RSSI: ${device.rssi}dBm"
                )
            } else {
                // 添加新设备
                currentDevices.add(device)
                addedCount++
                addedAddresses.add("${device.name}($address, RSSI=${device.rssi}dBm)")
                AppLogger.debug(
                    "BluetoothDeviceManagerDataSource",
                    "新增设备: ${device.name} | MAC: $address | RSSI: ${device.rssi}dBm"
                )
            }
        }

        // 强制更新StateFlow值，触发订阅者更新
        // 即使列表内容相同，重新赋值也会触发collectAsState()
        _managedDevices.value = currentDevices.toList()

        lastPublishTime = currentTime
        pendingUpdates.clear()

        // 详细的发布汇总日志
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "═══════════════════════════════════════════════════"
        )
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "发布设备更新汇总 - 总数=${currentDevices.size}, 更新=$updatedCount, 新增=$addedCount"
        )
        if (updatedAddresses.isNotEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "已更新设备 ($updatedCount):"
            )
            updatedAddresses.forEach { addr ->
                AppLogger.debug(
                    "BluetoothDeviceManagerDataSource",
                    "  ├─ $addr"
                )
            }
        }
        if (addedAddresses.isNotEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "新增设备 ($addedCount):"
            )
            addedAddresses.forEach { addr ->
                AppLogger.debug(
                    "BluetoothDeviceManagerDataSource",
                    "  ├─ $addr"
                )
            }
        }
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "═══════════════════════════════════════════════════"
        )
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "✅ 发布完成 | StateFlow已更新 | 订阅者将接收新数据"
        )
    }

    /**
     * 获取当前缓存的所有管理设备
     *
     * @return 已发布的蓝牙设备列表（快照）
     */
    fun getManagedDevices(): List<BluetoothDeviceModel> {
        return _managedDevices.value
    }

    /**
     * 根据MAC地址获取指定设备的实时流
     *
     * 返回一个Flow，只包含指定MAC地址的设备。
     * 当该设备信息更新时，会自动发射新数据。
     *
     * @param macAddress 设备的MAC地址
     * @return Flow<BluetoothDeviceModel?>，未找到则为null
     */
    fun getDevice(macAddress: String): kotlinx.coroutines.flow.Flow<BluetoothDeviceModel?> {
        return managedDevices.let { flow ->
            object : kotlinx.coroutines.flow.Flow<BluetoothDeviceModel?> {
                override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<BluetoothDeviceModel?>) {
                    flow.collect { devices ->
                        collector.emit(devices.find { it.address == macAddress })
                    }
                }
            }
        }
    }

    /**
     * 清空所有缓存的设备数据
     *
     * 用于重新扫描或停止服务时清理状态。
     */
    fun clearAll() {
        val beforeCount = _managedDevices.value.size
        val pendingCount = pendingUpdates.size

        _managedDevices.value = emptyList()
        pendingUpdates.clear()
        lastPublishTime = 0L

        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "🧹 清空所有设备数据 | 已清除=$beforeCount | 缓冲=$pendingCount | 时间=${System.currentTimeMillis()}"
        )
    }

    /**
     * 获取当前统计信息
     *
     * @return 包含设备数、缓冲数等信息的字符串
     */
    fun getStatistics(): String {
        val managedCount = _managedDevices.value.size
        val pendingCount = pendingUpdates.size
        return "已管理设备: $managedCount, 待发布更新: $pendingCount, 更新间隔: ${scanStrategy.updateInterval}ms"
    }

    /**
     * 获取当前扫描策略的更新间隔（毫秒）
     *
     * @return 更新间隔时间，单位毫秒
     */
    fun getUpdateInterval(): Long {
        return scanStrategy.updateInterval
    }
}

