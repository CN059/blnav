package com.powercess.blnav.data.datasource.local

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * 蓝牙本地数据源 - 第1层扫描引擎（设备发现）
 *
 * 成员分组：
 *   系统交互层：bluetoothManager, bluetoothAdapter
 *   扫描管理：_isScanning, shouldContinueScanning, _discoveredDevices, _errorMessage
 *   第2层关联：updateScanner - 关联的高频更新扫描器
 *   广播接收：bluetoothReceiver（接收ACTION_FOUND和ACTION_DISCOVERY_FINISHED）
 *   过滤器：filterDataSource
 *
 * 关键方法间的关系：
 *   startScan()
 *     ├─ 权限校验 → isBluetoothAvailable/isBluetoothEnabled
 *     ├─ 启动 updateScanner.startScan() (第2层定时更新)
 *     ├─ 注册广播接收器
 *     └─ 调用 bluetoothAdapter.startDiscovery() (第1层低频发现)
 *
 *   bluetoothReceiver.onReceive()
 *     ├─ ACTION_FOUND → 调用 handleDeviceFound(device)
 *     │   └─ addDevice() 流程：
 *     │       ├─ applyFilters(device) → 过滤检查
 *     │       ├─ updateLocalDeviceList() → 本地列表更新
 *     │       ├─ updateScanner.addDevice() → 添加到第2层扫描器 ✨ 新增
 *     │       └─ deviceManager.updateDevice() → 发布初始信息
 *     │
 *     └─ ACTION_DISCOVERY_FINISHED → 调用 handleDiscoveryFinished()
 *         ├─ 发布最后的设备
 *         └─ shouldContinueScanning 判断是否重启
 *
 * 对外服务：
 *   1. startScan()/stopScan(): 启动/停止蓝牙扫描
 *   2. isScanning: 扫描状态流
 *   3. discoveredDevices: 本地发现的设备列表
 *   4. errorMessage: 错误信息提示
 *   5. getUpdateScanner(): 获取第2层扫描器
 *
 * 双层扫描机制：
 *   第1层 (此类 - 发现层，低频)：
 *     └─ startDiscovery() 每 20-30 秒进行一次设备发现
 *     └─ 发现新设备后立即通知第2层
 *
 *   第2层 (BluetoothDeviceUpdateScanner - 更新层，高频)：
 *     └─ 每秒定时扫描一次已发现的设备
 *     └─ 获取最新RSSI信息
 *     └─ 发射到订阅者
 *
 * 结果：
 *   ✅ 发现新设备：第1层低频发现，响应快
 *   ✅ 更新RSSI：第2层高频更新，数据新鲜
 */
class BluetoothLocalDataSource(
    private val context: Context,
    private val filterDataSource: BluetoothFilterLocalDataSource? = null,
    scanStrategy: BluetoothScanStrategy = BluetoothScanStrategy()
) {

    // ==================== 系统蓝牙API访问 ====================
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // ==================== 双层扫描系统 ====================
    // 第2层：高频更新扫描器（每秒扫描一次缓存中的设备）
    private val updateScanner = BluetoothDeviceUpdateScanner(context, scanIntervalMs = 1000L)

    // 第1层相关的全局设备管理（保留用于兼容性）
    private val deviceManager = BluetoothDeviceManagerDataSource.initializeWith(scanStrategy)

    // ==================== 扫描状态管理 ====================
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var shouldContinueScanning = false

    // ==================== 本地发现设备缓存 ====================
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = _discoveredDevices.asStateFlow()

    // ==================== 错误信息 ====================
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ==================== 设备跟踪 - 用于高频率更新 ====================
    private val trackedDevices = mutableSetOf<String>()
    private var lastTrackedDeviceUpdateTime = 0L
    private val trackedDeviceUpdateInterval = 500L

    // ==================== 广播接收器 - 接收扫描结果 ====================
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> handleDeviceFound(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> handleDiscoveryFinished()
            }
        }
    }

    // ==================== 权限和状态检查 ====================

    /**
     * 检查是否拥有蓝牙扫描权限
     */
    fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查蓝牙是否可用
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * 检查蓝牙是否已启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    // ==================== 扫描控制 ====================

    /**
     * 开始蓝牙扫描
     *
     * 工作流程：
     *   1. 检查权限、蓝牙可用性和启用状态
     *   2. 清除之前的设备列表和错误信息
     *   3. 启动第2层定时更新扫描器 ✨ 关键
     *   4. 注册广播接收器
     *   5. 调用 startDiscovery() 启动第1层低频发现
     *
     * @return 是否成功启动扫描
     */
    fun startScan(): Boolean {
        // 权限检查
        if (!hasBluetoothScanPermission()) {
            _errorMessage.value = "缺少蓝牙扫描权限，请在设置中授予权限"
            AppLogger.error("BluetoothLocalDataSource", "缺少蓝牙扫描权限")
            return false
        }

        // 蓝牙可用性检查
        if (!isBluetoothAvailable()) {
            _errorMessage.value = "您的设备不支持蓝牙功能"
            AppLogger.error("BluetoothLocalDataSource", "设备不支持蓝牙")
            return false
        }

        // 蓝牙启用状态检查
        if (!isBluetoothEnabled()) {
            _errorMessage.value = "蓝牙功能未启用，请先启用蓝牙"
            AppLogger.error("BluetoothLocalDataSource", "蓝牙未启用")
            return false
        }

        // 初始化扫描
        clearDevicesAndErrors()
        shouldContinueScanning = true

        // ✨ 启动第2层定时更新扫描器（高频率）
        if (!updateScanner.startScan()) {
            _errorMessage.value = "无法启动定时更新扫描器"
            AppLogger.error("BluetoothLocalDataSource", "启动第2层扫描器失败")
            return false
        }

        // 注册广播接收器
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(bluetoothReceiver, filter)

        // 启动第1层低频扫描
        return try {
            bluetoothAdapter?.startDiscovery() ?: false
        } catch (e: SecurityException) {
            handleScanError("无法启动扫描：权限不足", e)
            false
        }.also { success ->
            if (success) {
                _isScanning.value = true
                _errorMessage.value = null
                AppLogger.debug("BluetoothLocalDataSource", "🚀 开始扫描 (第1层+第2层)")
            } else {
                handleScanError("无法启动蓝牙扫描", null)
                updateScanner.stopScan()  // 停止第2层
            }
        }
    }

    /**
     * 停止蓝牙扫描
     *
     * 停止扫描时会：
     *   1. 停止第1层的系统级扫描
     *   2. 停止第2层的定时更新任务
     *   3. 立即发布所有缓冲的设备
     */
    fun stopScan() {
        shouldContinueScanning = false

        try {
            bluetoothAdapter?.cancelDiscovery()
            _isScanning.value = false
            _errorMessage.value = null

            // 停止第2层定时更新
            updateScanner.stopScan()

            deviceManager.forcePublish()
            AppLogger.debug("BluetoothLocalDataSource", "🛑 停止扫描 (第1层+第2层)")
        } catch (e: SecurityException) {
            handleScanError("无法停止扫描：权限不足", e)
        }
    }

    // ==================== 设备处理 ====================

    /**
     * 处理设备发现事件
     *
     * 从 ACTION_FOUND intent 中提取设备信息并调用 addDevice()
     */
    private fun handleDeviceFound(intent: Intent) {
        try {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

            if (device == null) {
                AppLogger.warn("BluetoothLocalDataSource", "⚠️ 收到ACTION_FOUND但设备为null")
                return
            }

            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

            @Suppress("MissingPermission")
            val deviceName = device.name ?: "Unknown Device"

            val deviceModel = BluetoothDeviceModel(
                address = device.address,
                name = deviceName,
                rssi = rssi,
                bondState = bondState
            )

            addDevice(deviceModel)
        } catch (e: Exception) {
            AppLogger.error("BluetoothLocalDataSource", "❌ 处理ACTION_FOUND时发生异常", e)
        }
    }

    /**
     * 处理扫描完成事件
     *
     * 扫描完成时：
     *   1. 立即发布缓冲的设备
     *   2. 如果 shouldContinueScanning 为真，则自动重启扫描
     */
    private fun handleDiscoveryFinished() {
        val discoveredCount = _discoveredDevices.value.size
        val managedCount = deviceManager.getManagedDevices().size

        AppLogger.debug(
            "BluetoothLocalDataSource",
            "🏁 扫描完成 | 本地: $discoveredCount | 全局: $managedCount"
        )

        // 立即发布缓冲的设备
        deviceManager.forcePublish()

        // 如果需要继续扫描，自动重启
        if (shouldContinueScanning) {
            restartScan()
        } else {
            _isScanning.value = false
        }
    }

    /**
     * 重启扫描
     *
     * 在扫描完成后自动重启，用于实现持续扫描
     */
    private fun restartScan() {
        try {
            if (hasBluetoothScanPermission()) {
                @Suppress("MissingPermission")
                bluetoothAdapter?.startDiscovery()
                _isScanning.value = true
                AppLogger.debug("BluetoothLocalDataSource", "🔄 重新启动扫描")
            } else {
                handleScanError("重新启动失败：权限不足", null)
            }
        } catch (e: Exception) {
            handleScanError("重新启动失败", e)
            shouldContinueScanning = false
        }
    }

    /**
     * 添加设备到列表
     *
     * 工作流程：
     *   1. 应用过滤规则检查
     *   2. 更新本地设备列表（添加或更新）
     *   3. 发送到全局设备管理器
     *   4. ✨ 添加到第2层定时更新扫描器
     *   5. 如果设备被跟踪，执行高频率更新
     *
     * @param device 要添加的蓝牙设备
     */
    private fun addDevice(device: BluetoothDeviceModel) {
        // 第一步：过滤检查
        if (!applyFilters(device)) {
            return
        }

        // 第二步：更新本地列表
        updateLocalDeviceList(device)

        // 第三步：发送到全局管理器
        deviceManager.updateDevice(device)

        // 第四步：✨ 添加到第2层定时更新扫描器（这是关键！）
        updateScanner.addDevice(device)

        // 第五步：跟踪设备高频率更新
        updateTrackedDeviceHighFrequency(device)
    }

    /**
     * 更新本地设备列表
     *
     * 添加新设备或更新已有设备的信息
     */
    private fun updateLocalDeviceList(device: BluetoothDeviceModel) {
        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == device.address }
        val isTracked = trackedDevices.contains(device.address)

        if (existingIndex >= 0) {
            // 更新已有设备
            val oldDevice = currentList[existingIndex]
            currentList[existingIndex] = device
            if (isTracked) {
                AppLogger.debug(
                    "BluetoothLocalDataSource",
                    "🔄 更新 | ${device.name} | MAC: ${device.address} | RSSI: ${device.rssi}dBm (${oldDevice.rssi}dBm)"
                )
            }
        } else {
            // 添加新设备
            currentList.add(device)
            if (isTracked) {
                AppLogger.info(
                    "BluetoothLocalDataSource",
                    "✨ 新发现 | ${device.name} | MAC: ${device.address} | RSSI: ${device.rssi}dBm"
                )
            }
        }

        _discoveredDevices.value = currentList
    }

    /**
     * 对跟踪的设备执行高频率更新
     *
     * 对于被跟踪的设备，每 trackedDeviceUpdateInterval 毫秒执行一次强制发布，
     * 而不是等待普通的 updateInterval
     */
    private fun updateTrackedDeviceHighFrequency(device: BluetoothDeviceModel) {
        if (!trackedDevices.contains(device.address)) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastTrackedDeviceUpdateTime

        if (lastTrackedDeviceUpdateTime == 0L || timeSinceLastUpdate >= trackedDeviceUpdateInterval) {
            deviceManager.forcePublish()
            lastTrackedDeviceUpdateTime = currentTime

            AppLogger.debug(
                "BluetoothLocalDataSource",
                "⚡ 高频更新 | ${device.name} | RSSI: ${device.rssi}dBm | 发布 (${trackedDeviceUpdateInterval}ms)"
            )
        }
    }

    /**
     * 应用过滤规则到设备
     *
     * @param device 要检查的蓝牙设备
     * @return true 表示设备应该被显示，false 表示设备应被过滤隐藏
     */
    private fun applyFilters(device: BluetoothDeviceModel): Boolean {
        // 没有配置过滤器时，允许所有设备
        if (filterDataSource == null) {
            return true
        }

        return try {
            val shouldFilter = runBlocking {
                filterDataSource.shouldFilterDevice(device.name, device.address)
            }

            if (shouldFilter) {
                if (trackedDevices.contains(device.address)) {
                    AppLogger.debug(
                        "BluetoothLocalDataSource",
                        "❌ 被过滤 | ${device.name} | MAC: ${device.address}"
                    )
                }
                false
            } else {
                true
            }
        } catch (e: Exception) {
            AppLogger.error(
                "BluetoothLocalDataSource",
                "❌ 过滤规则异常，允许显示: ${device.name} (${device.address})",
                e
            )
            true
        }
    }

    // ==================== 设备跟踪管理 ====================

    /**
     * 添加设备到跟踪列表
     *
     * 跟踪的设备会以高频率进行更新（每 trackedDeviceUpdateInterval 毫秒），
     * 而不是等待普通的 updateInterval
     *
     * @param macAddress 要跟踪的设备MAC地址
     */
    @Suppress("unused")
    fun addTrackedDevice(macAddress: String) {
        trackedDevices.add(macAddress)
        AppLogger.info("BluetoothLocalDataSource", "📌 跟踪设备: $macAddress")
    }

    /**
     * 从跟踪列表中移除设备
     */
    @Suppress("unused")
    fun removeTrackedDevice(macAddress: String) {
        val removed = trackedDevices.remove(macAddress)
        if (removed) {
            AppLogger.info("BluetoothLocalDataSource", "📍 停止跟踪: $macAddress")
        }
    }

    /**
     * 获取所有被跟踪的设备MAC地址
     */
    @Suppress("unused")
    fun getTrackedDevices(): Set<String> {
        return trackedDevices.toSet()
    }

    /**
     * 检查设备是否在跟踪列表中
     */
    @Suppress("unused")
    fun isTrackedDevice(macAddress: String): Boolean {
        return trackedDevices.contains(macAddress)
    }

    /**
     * 清空所有跟踪的设备
     */
    @Suppress("unused")
    fun clearTrackedDevices() {
        val removedCount = trackedDevices.size
        trackedDevices.clear()
        lastTrackedDeviceUpdateTime = 0L

        if (removedCount > 0) {
            AppLogger.info(
                "BluetoothLocalDataSource",
                "🧹 清空所有跟踪设备 | 已清除: $removedCount 个"
            )
        }
    }

    /**
     * 获取跟踪设备的高频率更新间隔
     */
    @Suppress("unused")
    fun getTrackedDeviceUpdateInterval(): Long {
        return trackedDeviceUpdateInterval
    }

    /**
     * 获取第2层高频率更新扫描器
     *
     * 用于订阅高频率的设备更新流
     *
     * 使用示例：
     * ```
     * val updateScanner = bluetoothDataSource.getUpdateScanner()
     * lifecycleScope.launch {
     *     updateScanner.updatedDevices.collect { devices ->
     *         // 每秒获取一次最新的设备列表
     *         updateUI(devices)
     *     }
     * }
     * ```
     *
     * @return BluetoothDeviceUpdateScanner 实例
     */
    @Suppress("unused")
    fun getUpdateScanner(): BluetoothDeviceUpdateScanner {
        return updateScanner
    }

    // ==================== 辅助方法 ====================

    /**
     * 清除已发现的设备列表和错误信息
     */
    private fun clearDevicesAndErrors() {
        _discoveredDevices.value = emptyList()
        _errorMessage.value = null
    }

    /**
     * 清除已发现的设备列表
     */
    fun clearDevices() {
        clearDevicesAndErrors()
        AppLogger.debug("BluetoothLocalDataSource", "已清除设备列表")
    }

    /**
     * 处理扫描错误
     */
    private fun handleScanError(message: String, exception: Exception?) {
        _errorMessage.value = message
        shouldContinueScanning = false
        _isScanning.value = false

        if (exception != null) {
            AppLogger.error("BluetoothLocalDataSource", message, exception)
        } else {
            AppLogger.error("BluetoothLocalDataSource", message)
        }
    }

    /**
     * 清理资源
     *
     * 应该在 Activity/Fragment 销毁时调用
     */
    fun cleanup() {
        shouldContinueScanning = false
        stopScan()

        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            AppLogger.warn("BluetoothLocalDataSource", "⚠️ 广播接收器未注册")
        }

        deviceManager.clearAll()

        // 清理第2层扫描器
        updateScanner.cleanup()

        trackedDevices.clear()
        lastTrackedDeviceUpdateTime = 0L

        AppLogger.debug("BluetoothLocalDataSource", "🧹 已清理资源 (第1层+第2层)")
    }
}

