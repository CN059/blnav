package com.powercess.blnav.data.datasource.local

import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 蓝牙设备全局管理数据源 - 缓存和发布器
 *
 * 成员：
 *   - _managedDevices: 已发布的设备列表（StateFlow）
 *   - pendingUpdates: 待发布的设备缓冲（Map<address, device>）
 *   - lastPublishTime: 上次发布的时间戳
 *   - scanStrategy: 扫描策略配置
 *
 * 关键方法间的关系：
 *   updateDevice() → checkAndPublishUpdates() → publishUpdates()
 *                                    ↓
 *                          mergeDeviceList() (内部)
 *                                    ↓
 *                          _managedDevices.value 发射新数据
 *
 * 对外服务：
 *   1. updateDevice()/updateDevices(): 接收扫描设备并缓冲
 *   2. managedDevices: 提供实时的设备列表流
 *   3. getDevice(macAddress): 获取特定设备的流
 *   4. checkAndPublishUpdates(): 检查并发布更新（支持外部触发）
 *   5. forcePublish(): 强制立即发布所有缓冲的设备
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
    @Suppress("unused")
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
     * 内部发布方法 - 负责将缓冲的设备发布到StateFlow
     */
    private fun publishUpdates(currentTime: Long) {
        if (pendingUpdates.isEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "⏭️ pendingUpdates为空，跳过发布"
            )
            return
        }

        // 合并设备列表并获取统计信息
        val (mergedDevices, stats) = mergeDeviceList()

        // 更新StateFlow和时间戳
        _managedDevices.value = mergedDevices
        lastPublishTime = currentTime
        pendingUpdates.clear()

        // 输出发布日志
        logPublishSummary(stats)
    }

    /**
     * 合并待发布的设备与已发布的设备列表
     * 返回合并后的列表和统计信息
     */
    private fun mergeDeviceList(): Pair<List<BluetoothDeviceModel>, PublishStats> {
        val currentDevices = _managedDevices.value.toMutableList()
        val addressToIndex = currentDevices.mapIndexed { index, device -> device.address to index }.toMap()

        val stats = PublishStats()
        val updatedAddresses = mutableListOf<String>()
        val addedAddresses = mutableListOf<String>()

        // 遍历待发布设备，进行更新或添加
        pendingUpdates.forEach { (address, device) ->
            val existingIndex = addressToIndex[address]
            if (existingIndex != null) {
                // 更新已有设备
                currentDevices[existingIndex] = device
                stats.updatedCount++
                updatedAddresses.add("${device.name}($address, RSSI=${device.rssi}dBm)")
            } else {
                // 添加新设备
                currentDevices.add(device)
                stats.addedCount++
                addedAddresses.add("${device.name}($address, RSSI=${device.rssi}dBm)")
            }
        }

        stats.totalCount = currentDevices.size
        stats.updatedAddresses = updatedAddresses
        stats.addedAddresses = addedAddresses

        return Pair(currentDevices.toList(), stats)
    }

    /**
     * 输出发布操作的统计日志
     */
    private fun logPublishSummary(stats: PublishStats) {
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "═══════════════════════════════════════════════════"
        )
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "✅ 发布设备更新 - 总数=${stats.totalCount}, 更新=${stats.updatedCount}, 新增=${stats.addedCount}"
        )
        if (stats.updatedAddresses.isNotEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "已更新设备 (${stats.updatedCount}):"
            )
            stats.updatedAddresses.forEach { addr ->
                AppLogger.debug("BluetoothDeviceManagerDataSource", "  ├─ $addr")
            }
        }
        if (stats.addedAddresses.isNotEmpty()) {
            AppLogger.debug(
                "BluetoothDeviceManagerDataSource",
                "新增设备 (${stats.addedCount}):"
            )
            stats.addedAddresses.forEach { addr ->
                AppLogger.debug("BluetoothDeviceManagerDataSource", "  ├─ $addr")
            }
        }
        AppLogger.debug(
            "BluetoothDeviceManagerDataSource",
            "═══════════════════════════════════════════════════"
        )
    }

    /**
     * 内部数据类 - 用于保存发布统计信息
     */
    private data class PublishStats(
        var totalCount: Int = 0,
        var updatedCount: Int = 0,
        var addedCount: Int = 0,
        var updatedAddresses: List<String> = emptyList(),
        var addedAddresses: List<String> = emptyList()
    )

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
    @Suppress("unused")
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

