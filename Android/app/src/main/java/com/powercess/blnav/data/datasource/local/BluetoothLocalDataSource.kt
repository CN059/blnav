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
 * 负责与系统蓝牙API交互，管理蓝牙设备扫描的底层操作
 *
 * ==================== 过滤器集成详细说明 ====================
 *
 * 此数据源已集成蓝牙过滤器功能，用于在扫描时实时过滤设备。
 *
 * 1. 工作流程：
 *    扫描发现设备 → 应用过滤规则检查 → 过滤通过才添加到列表中
 *
 * 2. 初始化方式：
 *
 *    方式A - 带过滤器初始化（推荐用于支持设备过滤）:
 *    ```
 *    val filterDataSource = BluetoothFilterLocalDataSource(context)
 *    val bluetoothDataSource = BluetoothLocalDataSource(context, filterDataSource)
 *    ```
 *
 *    方式B - 不带过滤器初始化（所有设备都会显示）:
 *    ```
 *    val bluetoothDataSource = BluetoothLocalDataSource(context)
 *    ```
 *
 * 3. 扫描时过滤的关键步骤：
 *
 *    a) 系统发现蓝牙设备 → bluetoothReceiver.onReceive()
 *    b) 创建BluetoothDeviceModel对象
 *    c) 调用addDevice(deviceModel)
 *    d) addDevice()内部调用applyFilters(device)进行检查
 *    e) 如果filterDataSource为null → 返回true（允许）
 *    f) 否则调用filterDataSource.shouldFilterDevice()
 *    g) 根据返回值判断是否显示设备
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
    private val filterDataSource: BluetoothFilterLocalDataSource? = null
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    // 扫描状态
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // 发现的设备列表
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = _discoveredDevices.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 用于接收蓝牙扫描结果的广播接收器
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
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

                    device?.let {
                        @Suppress("MissingPermission")
                        val deviceName = it.name ?: "Unknown Device"
                        val deviceModel = BluetoothDeviceModel(
                            address = it.address,
                            name = deviceName,
                            rssi = rssi,
                            bondState = bondState
                        )
                        addDevice(deviceModel)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // 扫描完成
                    _isScanning.value = false
                    AppLogger.debug("BluetoothLocalDataSource", "扫描完成")
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
            AppLogger.error("BluetoothLocalDataSource", "启动扫描失败", e)
            false
        }.also { success ->
            if (success) {
                _isScanning.value = true
                _errorMessage.value = null
                AppLogger.debug("BluetoothLocalDataSource", "开始扫描蓝牙设备")
            } else {
                _errorMessage.value = "无法启动蓝牙扫描"
                AppLogger.error("BluetoothLocalDataSource", "startDiscovery 返回 false")
            }
        }
    }

    /**
     * 停止扫描蓝牙设备
     */
    fun stopScan() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            _isScanning.value = false
            _errorMessage.value = null
            AppLogger.debug("BluetoothLocalDataSource", "停止扫描")
        } catch (e: SecurityException) {
            _errorMessage.value = "无法停止扫描：权限不足"
            AppLogger.error("BluetoothLocalDataSource", "停止扫描失败", e)
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
     * 在添加前会检查设备是否通过过滤规则：
     * - 如果filterDataSource不可用或未配置，所有设备都会被添加
     * - 如果过滤规则判定设备应被过滤，则设备不会被添加
     * - 否则设备会被添加到列表中
     */
    private fun addDevice(device: BluetoothDeviceModel) {
        // 应用过滤规则检查
        if (!applyFilters(device)) {
            AppLogger.debug(
                "BluetoothLocalDataSource",
                "设备被过滤规则阻止: ${device.name} (${device.address})"
            )
            return
        }

        val currentList = _discoveredDevices.value.toMutableList()

        // 如果已存在同样MAC地址的设备，则更新信号强度；否则添加新设备
        val existingIndex = currentList.indexOfFirst { it.address == device.address }
        if (existingIndex >= 0) {
            currentList[existingIndex] = device
        } else {
            currentList.add(device)
        }

        _discoveredDevices.value = currentList
        AppLogger.debug("BluetoothLocalDataSource", "发现设备: ${device.name} (${device.address})")
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
        // 这是必要的，因为BroadcastReceiver的onReceive不是suspend函数
        return try {
            val shouldFilter = runBlocking {
                filterDataSource.shouldFilterDevice(device.name, device.address)
            }
            // shouldFilterDevice返回true表示应过滤，我们返回false（不显示）
            !shouldFilter
        } catch (e: Exception) {
            AppLogger.error("BluetoothLocalDataSource", "应用过滤规则失败", e)
            // 出错时允许设备显示
            true
        }
    }

    /**
     * 清理资源
     * 应该在 Activity/Fragment 销毁时调用
     */
    fun cleanup() {
        stopScan()
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            // 广播接收器未注册，忽略
            AppLogger.warn("BluetoothLocalDataSource", "广播接收器未注册")
        }
    }
}

