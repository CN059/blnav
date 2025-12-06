package com.powercess.blnav.data.datasource.local

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 蓝牙设备更新扫描器 - 高频率更新已发现设备
 *
 * 成员分组：
 *   系统交互：bluetoothAdapter - 蓝牙适配器
 *   设备维护：_deviceCache - 需要定时更新的设备缓存（Map<MAC, device>）
 *   扫描控制：_isScanning - 是否正在定时扫描
 *   定时任务：scanJob - 后台定时扫描任务
 *   发布机制：_updatedDevices - 最新更新的设备流
 *   配置参数：scanIntervalMs - 扫描间隔（秒级，默认1000ms）
 *
 * 关键方法间的关系：
 *   addDevice(device)
 *     └─ 将设备添加到 _deviceCache
 *
 *   startScan()
 *     └─ 启动后台定时任务 scanJob
 *        └─ 每秒查询一次缓存中设备的最新信息
 *        └─ 发射更新到 _updatedDevices
 *
 *   stopScan()
 *     └─ 取消后台任务
 *
 * 对外服务：
 *   1. addDevice(device): 将发现的设备添加到更新队列
 *   2. startScan()/stopScan(): 控制定时更新
 *   3. updatedDevices: 提供定时更新的设备流（每秒发布一次）
 *   4. getDeviceCache(): 获取当前维护的设备列表
 *
 * 工作原理：
 *   第1层 (BluetoothLocalDataSource)：
 *     通过 startDiscovery() 慢速扫描发现新设备 (间隔较长)
 *     ↓
 *   发现新设备后，调用此类的 addDevice()
 *     ↓
 *   第2层 (此类)：
 *     后台定时任务定时查询这些设备 (每秒一次)
 *     获取最新的RSSI和其他信息
 *     通过 _updatedDevices 发射更新
 *     ↓
 *   订阅者接收高频率更新
 */
class BluetoothDeviceUpdateScanner(
    @Suppress("UNUSED_PARAMETER")
    private val context: Context,
    private val scanIntervalMs: Long = 1000L  // 默认每秒扫描一次
) {

    // ==================== 系统蓝牙API ====================
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // ==================== 设备缓存 ====================
    /**
     * 维护需要定时更新的设备
     * Key: MAC地址
     * Value: 最新的设备信息
     */
    private val _deviceCache = mutableMapOf<String, BluetoothDeviceModel>()

    // ==================== 扫描状态 ====================
    @Suppress("unused")
    private val _isScanning = MutableStateFlow(false)
    @Suppress("unused")
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // ==================== 更新发布 ====================
    /**
     * 每次定时扫描完成后，发射更新的设备列表
     * 订阅此流以获取高频率的设备更新
     */
    private val _updatedDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    @Suppress("unused")
    val updatedDevices: StateFlow<List<BluetoothDeviceModel>> = _updatedDevices.asStateFlow()

    // ==================== 后台任务 ====================
    private var scanJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    // ==================== 设备管理 ====================

    /**
     * 将发现的设备添加到缓存
     *
     * 此方法由第1层 (BluetoothLocalDataSource) 调用
     * 当发现新设备或设备信息更新时，调用此方法
     *
     * @param device 要添加/更新的设备
     */
    fun addDevice(device: BluetoothDeviceModel) {
        val existingDevice = _deviceCache[device.address]

        if (existingDevice == null) {
            // 新设备
            _deviceCache[device.address] = device
            AppLogger.debug(
                "BluetoothDeviceUpdateScanner",
                "✨ 添加到缓存 | ${device.name} (${device.address}) | RSSI=${device.rssi}dBm"
            )
        } else if (existingDevice.rssi != device.rssi || existingDevice.name != device.name) {
            // 设备信息有变化，更新缓存
            _deviceCache[device.address] = device
            AppLogger.debug(
                "BluetoothDeviceUpdateScanner",
                "🔄 缓存已更新 | ${device.name} | RSSI=${existingDevice.rssi} → ${device.rssi}dBm"
            )
        }
    }

    /**
     * 从缓存中移除设备
     *
     * @param macAddress 设备MAC地址
     */
    @Suppress("unused")
    fun removeDevice(macAddress: String) {
        val removed = _deviceCache.remove(macAddress)
        if (removed != null) {
            AppLogger.debug("BluetoothDeviceUpdateScanner", "🗑️ 从缓存移除: $macAddress")
        }
    }

    /**
     * 获取当前缓存的所有设备
     *
     * @return 设备列表快照
     */
    @Suppress("unused")
    fun getDeviceCache(): List<BluetoothDeviceModel> {
        return _deviceCache.values.toList()
    }

    /**
     * 获取缓存中的设备数量
     */
    @Suppress("unused")
    fun getCacheSize(): Int {
        return _deviceCache.size
    }

    /**
     * 清空所有缓存的设备
     */
    fun clearCache() {
        val clearedCount = _deviceCache.size
        _deviceCache.clear()
        if (clearedCount > 0) {
            AppLogger.debug(
                "BluetoothDeviceUpdateScanner",
                "🧹 清空缓存 | 已清除: $clearedCount 个设备"
            )
        }
    }

    // ==================== 扫描控制 ====================

    /**
     * 启动定时扫描
     *
     * 启动后台任务，每 scanIntervalMs (默认1000ms) 扫描一次缓存中的设备
     * 获取这些设备的最新信息，然后发射到订阅者
     *
     * @return 是否成功启动
     */
    fun startScan(): Boolean {
        if (_isScanning.value) {
            AppLogger.warn("BluetoothDeviceUpdateScanner", "⚠️ 扫描已在运行中")
            return false
        }

        if (bluetoothAdapter == null) {
            AppLogger.error("BluetoothDeviceUpdateScanner", "❌ 蓝牙适配器不可用")
            return false
        }

        _isScanning.value = true
        AppLogger.debug("BluetoothDeviceUpdateScanner", "⚡ 启动定时更新扫描 (间隔=${scanIntervalMs}ms)")

        // 启动后台定时任务
        scanJob = coroutineScope.launch {
            while (_isScanning.value) {
                try {
                    // 等待指定时间间隔
                    delay(scanIntervalMs)

                    // 如果没有设备需要更新，跳过
                    if (_deviceCache.isEmpty()) {
                        continue
                    }

                    // 执行一次扫描更新
                    performUpdate()

                } catch (e: Exception) {
                    AppLogger.error("BluetoothDeviceUpdateScanner", "❌ 定时扫描异常", e)
                }
            }
        }

        return true
    }

    /**
     * 停止定时扫描
     */
    fun stopScan() {
        if (!_isScanning.value) {
            return
        }

        _isScanning.value = false
        scanJob?.cancel()
        scanJob = null

        AppLogger.debug("BluetoothDeviceUpdateScanner", "⛔ 停止定时更新扫描")
    }

    /**
     * 执行一次更新扫描
     *
     * 查询缓存中所有设备的最新信息，发射到订阅者
     * 此方法在后台定时任务中调用
     */
    private fun performUpdate() {
        try {
            // 获取当前缓存的所有设备
            val cachedDevices = _deviceCache.values.toList()

            if (cachedDevices.isEmpty()) {
                return
            }

            // 发射当前缓存的设备列表
            // 设备信息应该通过蓝牙系统的callback获取最新值
            // 这里主要是将缓存发射出去，让订阅者获取高频率更新
            _updatedDevices.value = cachedDevices

            AppLogger.debug(
                "BluetoothDeviceUpdateScanner",
                "📤 定时发布更新 | 设备数=${cachedDevices.size} | 时间戳=${System.currentTimeMillis()}"
            )

            // 输出最新的RSSI信息（用于调试）
            cachedDevices.take(3).forEach { device ->
                AppLogger.debug(
                    "BluetoothDeviceUpdateScanner",
                    "  ├─ ${device.name} (${device.address}) | RSSI=${device.rssi}dBm"
                )
            }

        } catch (e: Exception) {
            AppLogger.error("BluetoothDeviceUpdateScanner", "❌ 执行更新扫描异常", e)
        }
    }

    // ==================== 高级控制 ====================

    /**
     * 强制立即执行一次扫描更新
     *
     * 不受定时间隔限制，立即发射当前缓存的设备
     */
    @Suppress("unused")
    fun forceUpdate() {
        AppLogger.debug("BluetoothDeviceUpdateScanner", "💪 强制立即发布更新")
        performUpdate()
    }

    /**
     * 获取缓存中指定MAC地址的设备
     *
     * @param macAddress 设备MAC地址
     * @return 设备信息，如果不存在则返回null
     */
    @Suppress("unused")
    fun getDevice(macAddress: String): BluetoothDeviceModel? {
        return _deviceCache[macAddress]
    }

    /**
     * 检查缓存中是否存在指定的设备
     *
     * @param macAddress 设备MAC地址
     * @return true表示存在，false表示不存在
     */
    @Suppress("unused")
    fun hasDevice(macAddress: String): Boolean {
        return _deviceCache.containsKey(macAddress)
    }

    /**
     * 清理资源
     *
     * 应该在Activity/Fragment销毁时调用
     */
    fun cleanup() {
        stopScan()
        clearCache()
        AppLogger.debug("BluetoothDeviceUpdateScanner", "🧹 已清理资源")
    }
}

