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
 * 蓝牙本地数据源
 *
 * 负责与系统蓝牙API交互，管理蓝牙设备扫描的底层操作和定时发布机制
 *
 * ==================== 核心功能 ====================
 *
 * 1. 蓝牙设备扫描：负责系统级的扫描和设备发现
 * 2. 过滤规则应用：在扫描时实时过滤设备
 * 3. 缓存管理：根据扫描策略定时发布设备数据更新
 * 4. 全局同步：通过BluetoothDeviceManagerDataSource向其他模块提供数据
 *
 * ==================== 过滤器集成详细说明 ====================
 *
 * 此数据源已集成蓝牙过滤器功能，用于在扫描时实时过滤设备。
 *
 * 1. 工作流程：
 *    扫描发现设备 → 应用过滤规则检查 → 更新缓存 → 按策略定时发布 → 管理器同步 → 其他模块使用
 *
 * 2. 初始化方式：
 *
 *    方式A - 完整初始化（推荐）:
 *    ```
 *    val scanStrategy = BluetoothScanStrategy(updateInterval = 500L)
 *    val filterDataSource = BluetoothFilterLocalDataSource(context)
 *    val bluetoothDataSource = BluetoothLocalDataSource(
 *        context,
 *        filterDataSource,
 *        scanStrategy
 *    )
 *    ```
 *
 *    方式B - 使用默认扫描策略（500ms间隔）:
 *    ```
 *    val bluetoothDataSource = BluetoothLocalDataSource(
 *        context,
 *        BluetoothFilterLocalDataSource(context)
 *    )
 *    ```
 *
 *    方式C - 不带过滤器初始化:
 *    ```
 *    val bluetoothDataSource = BluetoothLocalDataSource(context)
 *    ```
 *
 * 3. 获取管理的设备数据：
 *
 *    ```
 *    // 获取全局设备管理器，其中包含所有扫描到的设备
 *    val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
 *
 *    // 订阅设备列表实时更新（按500ms间隔）
 *    deviceManager.managedDevices.collect { devices ->
 *        // 发送到服务器进行定位等操作
 *        sendDevicesLocateServer(devices)
 *    }
 *    ```
 *
 * 4. 扫描时缓存和发布的关键步骤：
 *
 *    a) 系统发现蓝牙设备 → bluetoothReceiver.onReceive()
 *    b) 创建BluetoothDeviceModel对象
 *    c) 调用addDevice(deviceModel)
 *    d) addDevice()内部调用applyFilters(device)进行检查
 *    e) 过滤通过 → updateManager(device) 添加到缓冲
 *    f) 检查是否已超过updateInterval时间
 *    g) 如果是，立即发布所有缓冲的设备到管理器
 *    h) 如果否，继续缓冲，等待下次发布机会
 *    i) 管理器发射设备列表更新
 *    j) 订阅方（如定位模块）接收最新设备列表
 *
 * 4. 过滤规则示例：
 *
 *    示例1 - 白名单（仅允许iPhone）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "whitelist_1",
 *        alias = "允许iPhone设备",
 *        filterRule = "iPhone",
 *        matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *        isEnabled = true
 *    )
 *    filterDataSource.addFilter(filter)
 *    ```
 *    扫描结果：只有设备名包含"iPhone"的设备会被显示
 *
 *    示例2 - 黑名单（禁止特定MAC地址）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "blacklist_1",
 *        alias = "禁止某设备",
 *        filterRule = "AA:BB:CC:DD:EE:FF",
 *        matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
 *        filterType = BluetoothFilterModel.FilterType.BLACKLIST,
 *        isEnabled = true
 *    )
 *    filterDataSource.addFilter(filter)
 *    ```
 *    扫描结果：除了指定MAC地址外的所有设备都会被显示
 *
 *    示例3 - 正则表达式（匹配Apple设备）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "regex_1",
 *        alias = "Apple设备",
 *        filterRule = "^(iPhone|iPad|Apple Watch).*",
 *        matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *        enableRegex = true,
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *        isEnabled = true
 *    )
 *    filterDataSource.addFilter(filter)
 *    ```
 *    扫描结果：仅显示iPhone、iPad或Apple Watch
 *
 * 5. 过滤逻辑详解：
 *
 *    白名单模式：
 *    - 存在白名单规则 → 只有匹配白名单的设备才显示
 *    - 不匹配白名单 → 设备被过滤（隐藏）
 *
 *    黑名单模式：
 *    - 匹配黑名单规则 → 设备被过滤（隐藏）
 *    - 不匹配黑名单 → 设备被允许（显示）
 *
 *    混合模式（同时有白名单和黑名单）：
 *    - 优先检查白名单 → 如果有白名单规则，必须先匹配白名单
 *    - 然后检查黑名单 → 即使匹配白名单，也不能同时匹配黑名单
 *
 * 6. 实时更新过滤规则：
 *
 *    在扫描过程中随时可以修改或添加过滤规则，新规则会立即应用：
 *    ```
 *    // 正在扫描时，添加新的过滤规则
 *    filterDataSource.addFilter(newFilter)
 *    // 之后发现的新设备会使用新规则进行过滤
 *    ```
 *
 * 7. 性能考虑：
 *
 *    - 每次发现设备都会调用applyFilters()进行检查
 *    - 规则越多，过滤的时间越长
 *    - 建议仅启用必要的规则以优化性能
 *    - 可通过isEnabled标志快速禁用特定规则而不删除它
 *
 * ==================== 默认行为 ====================
 *
 * - 无过滤器时：所有扫描到的设备都会被显示
 * - 无启用规则时：所有扫描到的设备都会被显示
 * - 过滤异常时：出于安全考虑，设备会被允许显示（不会因过滤器出错导致设备被隐藏）
 */
class BluetoothLocalDataSource(
    private val context: Context,
    private val filterDataSource: BluetoothFilterLocalDataSource? = null,
    scanStrategy: BluetoothScanStrategy = BluetoothScanStrategy()
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // 设备管理器（全局单例，使用指定的扫描策略初始化）
    // 如果已经初始化过会直接返回已有实例，否则用传入的策略创建新实例
    private val deviceManager = BluetoothDeviceManagerDataSource.initializeWith(scanStrategy)

    // 扫描状态
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 是否应该继续扫描（标志位）
    // 用途：控制扫描是否应该在完成后自动重启
    // true  = 扫描完成后自动重启（持续扫描）
    // false = 扫描完成后停止（一次性扫描）
    private var shouldContinueScanning = false

    // 跟踪的目标设备集合（感兴趣的设备）
    // 用途：存储用户关注的设备MAC地址，这些设备会被持续监控和更新
    // 当扫描到这些设备时，会使用高频率更新（每500ms或1秒）而不是标准的updateInterval
    private val trackedDevices = mutableSetOf<String>()

    // 上一次对跟踪设备进行高频率更新的时间戳（毫秒）
    // 用途：记录最后一次发布跟踪设备数据的时间，用于控制高频率更新的间隔
    // 比如每500ms更新一次，就需要检查距上次更新是否已经过了500ms
    private var lastTrackedDeviceUpdateTime = 0L

    // 高频率更新的间隔时间（毫秒）
    // 用途：控制对跟踪设备的更新频率，这个值应该比普通的updateInterval更小
    // 建议值：500毫秒（0.5秒）或1000毫秒（1秒）
    // 例如：高频率更新是500ms，而普通设备更新是标准的updateInterval（默认500ms）
    private val trackedDeviceUpdateInterval = 500L // 可根据需要调整为500L或1000L

    // 本地发现的蓝牙设备列表（实时更新）
    // 用途：存储此次扫描周期中发现的所有设备
    // 这个列表会在每个新的扫描周期开始时被清空
    // 同时会向全局设备管理器发送这些设备进行定时发布
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = _discoveredDevices.asStateFlow()

    // 错误信息
    // 用途：存储扫描过程中发生的错误，比如权限不足、蓝牙未启用等
    // 这个值会在扫描成功启动时被清空
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 用于接收蓝牙扫描结果的广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    try {
                        // 发现新设备
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<BluetoothDevice>(
                            BluetoothDevice.EXTRA_DEVICE
                        )
                        val rssi = intent.getShortExtra(
                            BluetoothDevice.EXTRA_RSSI,
                            Short.MIN_VALUE
                        ).toInt()
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE
                        )

                        if (device == null) {
                            AppLogger.warn(
                                "BluetoothLocalDataSource",
                                "⚠️ 收到ACTION_FOUND但设备为null"
                            )
                            return
                        }

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
                        AppLogger.error(
                            "BluetoothLocalDataSource",
                            "❌ 处理ACTION_FOUND时发生异常",
                            e
                        )
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // 扫描完成，发布缓冲的设备
                    val discoveredCount = _discoveredDevices.value.size
                    val managedCount = deviceManager.getManagedDevices().size

                    AppLogger.debug(
                        "BluetoothLocalDataSource",
                        "🏁 扫描完成 | 本地: $discoveredCount | 全局: $managedCount"
                    )

                    // 立即发布缓冲中的所有设备
                    deviceManager.forcePublish()

                    // 如果应该继续扫描，自动重新启动
                    if (shouldContinueScanning) {
                        try {
                            bluetoothAdapter?.startDiscovery()
                            _isScanning.value = true
                            AppLogger.debug(
                                "BluetoothLocalDataSource",
                                "🔄 重新启动扫描"
                            )
                        } catch (e: SecurityException) {
                            AppLogger.error(
                                "BluetoothLocalDataSource",
                                "❌ 重新启动失败",
                                e
                            )
                            shouldContinueScanning = false
                            _isScanning.value = false
                        } catch (e: Exception) {
                            AppLogger.error(
                                "BluetoothLocalDataSource",
                                "❌ 重新启动失败",
                                e
                            )
                            shouldContinueScanning = false
                            _isScanning.value = false
                        }
                    } else {
                        _isScanning.value = false
                    }
                }
            }
        }
    }

    /**
     * 检查是否拥有扫描权限
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

    /**
     * 开始扫描蓝牙设备
     */
    fun startScan(): Boolean {
        // 检查权限
        if (!hasBluetoothScanPermission()) {
            _errorMessage.value = "缺少蓝牙扫描权限，请在设置中授予权限"
            AppLogger.error("BluetoothLocalDataSource", "缺少蓝牙扫描权限")
            return false
        }

        // 检查蓝牙是否可用
        if (!isBluetoothAvailable()) {
            _errorMessage.value = "您的设备不支持蓝牙功能"
            AppLogger.error("BluetoothLocalDataSource", "设备不支持蓝牙")
            return false
        }

        // 检查蓝牙是否已启用
        if (!isBluetoothEnabled()) {
            _errorMessage.value = "蓝牙功能未启用，请先启用蓝牙"
            AppLogger.error("BluetoothLocalDataSource", "蓝牙未启用")
            return false
        }

        // 清除之前的设备列表
        _discoveredDevices.value = listOf()
        _errorMessage.value = null

        // 设置持续扫描标志位
        shouldContinueScanning = true

        // 注册广播接收器
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(bluetoothReceiver, filter)

        // 开始扫描
        return try {
            bluetoothAdapter?.startDiscovery() ?: false
        } catch (e: SecurityException) {
            _errorMessage.value = "无法启动扫描：权限不足"
            AppLogger.error("BluetoothLocalDataSource", "❌ 启动扫描失败", e)
            shouldContinueScanning = false
            false
        }.also { success ->
            if (success) {
                _isScanning.value = true
                _errorMessage.value = null
                AppLogger.debug(
                    "BluetoothLocalDataSource",
                    "🚀 开始扫描"
                )
            } else {
                _errorMessage.value = "无法启动蓝牙扫描"
                AppLogger.error("BluetoothLocalDataSource", "❌ startDiscovery 返回 false")
                shouldContinueScanning = false
            }
        }
    }

    /**
     * 停止扫描蓝牙设备
     *
     * 停止扫描时会立即发布所有缓冲中的设备到管理器，
     * 确保最后的设备数据不会被遗留在缓冲中。
     */
    fun stopScan() {
        // 设置停止扫描标志位，防止扫描完成时自动重启
        shouldContinueScanning = false

        try {
            bluetoothAdapter?.cancelDiscovery()
            _isScanning.value = false
            _errorMessage.value = null
            // 立即发布缓冲中的所有设备
            deviceManager.forcePublish()
            AppLogger.debug(
                "BluetoothLocalDataSource",
                "🛑 停止扫描"
            )
        } catch (e: SecurityException) {
            _errorMessage.value = "无法停止扫描：权限不足"
            AppLogger.error("BluetoothLocalDataSource", "❌ 停止扫描失败", e)
        }
    }

    /**
     * 清除已发现的设备列表
     */
    fun clearDevices() {
        _discoveredDevices.value = listOf()
        _errorMessage.value = null
        AppLogger.debug("BluetoothLocalDataSource", "已清除设备列表")
    }

    /**
     * 添加设备到列表（避免重复）
     *
     * 此方法执行以下步骤：
     * 1. 应用过滤规则检查设备是否应被过滤
     * 2. 将设备添加到本地缓存列表
     * 3. 向全局设备管理器发送更新，管理器根据扫描策略定时发布
     *
     * 在添加前会检查设备是否通过过滤规则：
     * - 如果filterDataSource不可用或未配置，所有设备都会被添加
     * - 如果过滤规则判定设备应被过滤，则设备不会被添加
     * - 否则设备会被添加到列表中
     *
     * ==================== 缓存和发布机制 ====================
     *
     * 设备添加后，会立即发送到设备管理器。设备管理器内部会：
     * - 缓冲设备到pendingUpdates中
     * - 检查距上次发布是否已经超过updateInterval时间
     * - 如果是，立即发布所有缓冲的设备；否则继续等待
     * - 这样可以避免过于频繁的StateFlow更新
     */
    private fun addDevice(device: BluetoothDeviceModel) {
        // ==================== 第一步：应用过滤规则 ====================
        // 检查设备是否应该被过滤隐藏
        if (!applyFilters(device)) {
            // 设备被过滤，直接返回，不继续处理
            return
        }

        // ==================== 第二步：更新本地设备列表 ====================
        // 获取当前的设备列表，并转换为可修改的列表
        val currentList = _discoveredDevices.value.toMutableList()

        // 检查设备是否已经在列表中（通过MAC地址查找）
        val existingIndex = currentList.indexOfFirst { it.address == device.address }

        // 标记该设备是否被跟踪 - 用于后续决定是否输出日志
        val isTracked = trackedDevices.contains(device.address)

        if (existingIndex >= 0) {
            // ==================== 情况A：设备已存在，更新其信息 ====================
            val oldDevice = currentList[existingIndex]
            currentList[existingIndex] = device

            // 只有跟踪的设备才输出日志，使用DEBUG级别
            if (isTracked) {
                AppLogger.debug(
                    "BluetoothLocalDataSource",
                    "🔄 更新 | ${device.name} | MAC: ${device.address} | RSSI: ${device.rssi}dBm (${oldDevice.rssi}dBm)"
                )
            }
        } else {
            // ==================== 情况B：全新设备，添加到列表 ====================
            currentList.add(device)

            // 只有跟踪的设备才输出日志，使用INFO级别表示新设备
            if (isTracked) {
                AppLogger.info(
                    "BluetoothLocalDataSource",
                    "✨ 新发现 | ${device.name} | MAC: ${device.address} | RSSI: ${device.rssi}dBm"
                )
            }
        }

        // ==================== 第三步：发布设备列表到UI ====================
        _discoveredDevices.value = currentList

        // ==================== 第四步：发送到全局设备管理器 ====================
        deviceManager.updateDevice(device)

        // ==================== 第五步：对跟踪的设备执行高频率更新 ====================
        // 只有跟踪的设备才执行高频率更新和相关日志
        if (isTracked) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastUpdate = currentTime - lastTrackedDeviceUpdateTime

            // 如果距上次更新已经过了trackedDeviceUpdateInterval时间或这是第一次更新，则执行高频率更新
            if (lastTrackedDeviceUpdateTime == 0L || timeSinceLastUpdate >= trackedDeviceUpdateInterval) {
                // 立即强制发布所有缓冲的设备到全局管理器
                deviceManager.forcePublish()
                lastTrackedDeviceUpdateTime = currentTime

                // 输出高频率更新的日志
                AppLogger.debug(
                    "BluetoothLocalDataSource",
                    "⚡ 高频更新 | ${device.name} | RSSI: ${device.rssi}dBm | 发布 (${trackedDeviceUpdateInterval}ms)"
                )
            }
        }
    }

    /**
     * 应用过滤规则到设备
     *
     * ==================== 过滤逻辑 ====================
     *
     * 1. 如果没有配置过滤器数据源，返回true（允许所有设备）
     * 2. 调用filterDataSource.shouldFilterDevice()检查设备是否应被过滤
     * 3. shouldFilterDevice返回true表示应过滤，我们返回false（不显示）
     * 4. shouldFilterDevice返回false表示允许，我们返回true（显示）
     *
     * @param device 要检查的蓝牙设备
     * @return true 表示设备应该被显示，false 表示设备应被过滤隐藏
     */
    private fun applyFilters(device: BluetoothDeviceModel): Boolean {
        // 如果没有配置过滤数据源，允许所有设备
        if (filterDataSource == null) {
            return true
        }

        // 使用runBlocking从suspend函数获取同步结果
        return try {
            val shouldFilter = runBlocking {
                filterDataSource.shouldFilterDevice(device.name, device.address)
            }

            if (shouldFilter) {
                // 只有被跟踪的设备被过滤时才输出日志
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

    // ...existing code...

    /**
     * 添加设备到跟踪列表
     *
     * 对于在跟踪列表中的设备，会执行高频率更新：
     * • 每500ms（或自定义的trackedDeviceUpdateInterval）更新一次该设备的信息
     * • 设备的RSSI、名称等信息会被频繁发布，不受普通updateInterval的限制
     * • 这对于监控感兴趣设备的实时信息非常有用（如室内定位中的参考信标）
     *
     * 使用场景：
     * • 室内定位应用中需要持续监控的参考信标
     * • 需要实时追踪的特定蓝牙设备
     * • 性能关键的感兴趣设备
     *
     * @param macAddress 要跟踪的设备MAC地址
     *
     * 示例：
     * ```
     * // 添加MAC地址为AA:BB:CC:DD:EE:FF的设备到跟踪列表
     * bluetoothDataSource.addTrackedDevice("AA:BB:CC:DD:EE:FF")
     * // 之后，每当这个设备被扫描到时，就会每500ms进行一次高频率更新
     * ```
     */
    fun addTrackedDevice(macAddress: String) {
        // 将MAC地址添加到跟踪集合中
        trackedDevices.add(macAddress)

        AppLogger.info(
            "BluetoothLocalDataSource",
            "📌 跟踪设备: $macAddress"
        )
    }

    /**
     * 从跟踪列表中移除设备
     *
     * 移除后，该设备的更新将恢复为普通的updateInterval速率
     *
     * @param macAddress 要移除的设备MAC地址
     *
     * 示例：
     * ```
     * // 移除跟踪的设备
     * bluetoothDataSource.removeTrackedDevice("AA:BB:CC:DD:EE:FF")
     * // 之后，这个设备的更新速率会降低到普通的updateInterval（如500ms）
     * ```
     */
    fun removeTrackedDevice(macAddress: String) {
        // 从跟踪集合中移除指定的MAC地址
        val removed = trackedDevices.remove(macAddress)

        if (removed) {
            AppLogger.info(
                "BluetoothLocalDataSource",
                "📍 停止跟踪: $macAddress"
            )
        }
    }

    /**
     * 获取当前跟踪的所有设备MAC地址
     *
     * @return 包含所有被跟踪的设备MAC地址的Set
     *
     * 示例：
     * ```
     * val trackedMacs = bluetoothDataSource.getTrackedDevices()
     * trackedMacs.forEach { mac ->
     *     Log.d("Bluetooth", "跟踪设备: $mac")
     * }
     * ```
     */
    fun getTrackedDevices(): Set<String> {
        // 返回一个不可修改的副本，防止外部代码直接修改内部集合
        return trackedDevices.toSet()
    }

    /**
     * 检查指定的设备是否在跟踪列表中
     *
     * @param macAddress 设备的MAC地址
     * @return true 表示该设备在跟踪列表中，false 表示不在
     *
     * 示例：
     * ```
     * if (bluetoothDataSource.isTrackedDevice("AA:BB:CC:DD:EE:FF")) {
     *     Log.d("Bluetooth", "这是一个被跟踪的设备")
     * }
     * ```
     */
    fun isTrackedDevice(macAddress: String): Boolean {
        return trackedDevices.contains(macAddress)
    }

    /**
     * 清空所有跟踪的设备
     *
     * 调用此方法后，所有设备的更新速率将恢复为普通的updateInterval
     * 高频率更新功能将被禁用
     *
     * 示例：
     * ```
     * // 停止跟踪所有设备
     * bluetoothDataSource.clearTrackedDevices()
     * // 现在所有设备都会使用普通的updateInterval（如500ms）进行更新
     * ```
     */
    fun clearTrackedDevices() {
        val removedCount = trackedDevices.size
        // 清空跟踪集合
        trackedDevices.clear()
        // 重置高频率更新的时间戳
        lastTrackedDeviceUpdateTime = 0L

        if (removedCount > 0) {
            AppLogger.info(
                "BluetoothLocalDataSource",
                "🧹 清空所有跟踪设备 | 已清除: $removedCount 个"
            )
        }
    }

    /**
     * 配置高频率更新的间隔时间（仅在运行时有效，需要在startScan前调用以确保效果最佳）
     *
     * 注意：此方法修改的是trackedDeviceUpdateInterval，但该变量在初始化时已设置为500L
     * 如需在扫描进行中动态调整，建议建立新的配置系统
     *
     * 建议值：
     * - 500L - 高频率更新（0.5秒），适合对延迟敏感的应用
     * - 1000L - 标准频率（1秒），适合普通应用
     * - 2000L - 低频率（2秒），适合功耗敏感的应用
     *
     * 注：由于trackedDeviceUpdateInterval是val，无法直接修改
     * 如需动态调整，可以考虑将其改为var，或创建新的配置机制
     */

    /**
     * 获取跟踪设备的高频率更新间隔
     *
     * @return 高频率更新的间隔时间（毫秒）
     *
     * 示例：
     * ```
     * val interval = bluetoothDataSource.getTrackedDeviceUpdateInterval()
     * Log.d("Bluetooth", "高频率更新间隔: ${interval}ms")
     * ```
     */
    fun getTrackedDeviceUpdateInterval(): Long {
        return trackedDeviceUpdateInterval
    }

    /**
     * 清理资源
     *
     * 应该在 Activity/Fragment 销毁时调用。
     * 会停止扫描、卸载广播接收器，并清理设备管理器中的缓存数据。
     */
    fun cleanup() {
        // 设置停止扫描标志位
        shouldContinueScanning = false

        stopScan()
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            // 广播接收器未注册，忽略
            AppLogger.warn("BluetoothLocalDataSource", "⚠️ 广播接收器未注册")
        }
        // 清理全局设备管理器的缓存数据
        deviceManager.clearAll()
        // 清空所有跟踪设备
        trackedDevices.clear()
        lastTrackedDeviceUpdateTime = 0L

        AppLogger.debug(
            "BluetoothLocalDataSource",
            "🧹 已清理资源"
        )
    }
}

