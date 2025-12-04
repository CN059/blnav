package com.powercess.blnav.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powercess.blnav.data.datasource.local.BluetoothLocalDataSource
import com.powercess.blnav.data.datasource.local.BluetoothFilterLocalDataSource
import com.powercess.blnav.data.repository.BluetoothRepository
import com.powercess.blnav.data.repository.BluetoothFilterRepository
import com.powercess.blnav.data.model.BluetoothDeviceModel
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 蓝牙管理ViewModel
 *
 * 负责UI状态管理和业务逻辑，遵循MVVM架构模式
 *
 * ==================== 蓝牙设备扫描与过滤集成 ====================
 *
 * 此ViewModel已完整集成蓝牙设备扫描和过滤功能：
 *
 * 1. 工作流程：
 *    - 用户启动扫描 → 系统扫描蓝牙设备 → 应用过滤规则 → 只显示通过规则的设备
 *
 * 2. 过滤器的三层架构：
 *
 *    a) BluetoothFilterLocalDataSource（过滤器数据源层）
 *       - 管理过滤规则的CRUD操作
 *       - 负责过滤规则的本地持久化
 *       - 提供shouldFilterDevice()方法进行过滤判断
 *
 *    b) BluetoothLocalDataSource（蓝牙扫描层，已集成过滤）
 *       - 与系统蓝牙API交互
 *       - 在扫描发现设备时，调用过滤器检查设备是否应显示
 *       - applyFilters()方法在设备被添加到列表前进行检查
 *
 *    c) BluetoothRepository（仓库层）
 *       - 作为UI和数据源之间的中介
 *       - 暴露isScanning、discoveredDevices等状态流
 *
 * 3. 扫描流程（带过滤）：
 *
 *    startScan()被调用
 *      ↓
 *    系统蓝牙模块开始扫描
 *      ↓
 *    发现设备 → bluetoothReceiver.onReceive()
 *      ↓
 *    创建BluetoothDeviceModel
 *      ↓
 *    调用addDevice(model)
 *      ↓
 *    调用applyFilters(device) ← 关键：过滤检查发生在这里
 *      ↓
 *    shouldFilterDevice(name, mac)
 *      ↓
 *    判断设备是否应被过滤
 *      ↓
 *    返回true=过滤，false=允许
 *      ↓
 *    过滤通过的设备被添加到discoveredDevices列表
 *      ↓
 *    UI订阅discoveredDevices并显示设备列表
 *
 * 4. 过滤规则示例：
 *
 *    示例1 - 创建白名单规则（只允许iPhone）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "filter_1",
 *        alias = "允许iPhone设备",
 *        filterRule = "iPhone",
 *        matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *        enableRegex = false,
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *        isEnabled = true,
 *        description = "只允许设备名包含iPhone的设备"
 *    )
 *    viewModel.addFilterRule(filter)
 *    ```
 *    结果：扫描后的设备列表中只会显示设备名包含"iPhone"的设备
 *
 *    示例2 - 创建黑名单规则（禁止特定MAC地址）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "filter_2",
 *        alias = "禁止特定设备",
 *        filterRule = "AA:BB:CC:DD:EE:FF",
 *        matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
 *        enableRegex = false,
 *        filterType = BluetoothFilterModel.FilterType.BLACKLIST,
 *        isEnabled = true,
 *        description = "禁止MAC地址为AA:BB:CC:DD:EE:FF的设备"
 *    )
 *    viewModel.addFilterRule(filter)
 *    ```
 *    结果：扫描后的设备列表中不会显示MAC地址为"AA:BB:CC:DD:EE:FF"的设备
 *
 *    示例3 - 使用正则表达式（匹配Apple所有产品）:
 *    ```
 *    val filter = BluetoothFilterModel(
 *        id = "filter_3",
 *        alias = "Apple设备白名单",
 *        filterRule = "^(iPhone|iPad|Apple Watch|AirPods).*",
 *        matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *        enableRegex = true,  // 启用正则表达式
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *        isEnabled = true,
 *        description = "只允许Apple品牌的设备"
 *    )
 *    viewModel.addFilterRule(filter)
 *    ```
 *    结果：扫描后的设备列表中只会显示Apple产品的设备
 *
 * 5. 过滤规则类型说明：
 *
 *    matchType（匹配类型）：
 *    - DEVICE_NAME: 按蓝牙设备名称进行过滤
 *    - MAC_ADDRESS: 按MAC地址进行过滤
 *
 *    filterType（过滤类型）：
 *    - WHITELIST: 白名单模式，只允许匹配的设备
 *    - BLACKLIST: 黑名单模式，阻止匹配的设备
 *
 *    enableRegex（正则表达式）：
 *    - false: 使用简单的字符串匹配（包含判断）
 *    - true: 使用正则表达式进行高级模式匹配
 *
 * 6. 过滤逻辑详解：
 *
 *    情况1 - 只有白名单规则：
 *    - 设备必须匹配至少一条白名单规则才会显示
 *    - 不匹配任何白名单规则的设备会被过滤隐藏
 *
 *    情况2 - 只有黑名单规则：
 *    - 匹配任何黑名单规则的设备会被过滤隐藏
 *    - 不匹配黑名单的设备会显示
 *
 *    情况3 - 同时有白名单和黑名单：
 *    - 优先检查白名单（必须先匹配白名单）
 *    - 然后检查黑名单（即使在白名单中也不能在黑名单中）
 *    - 最终只显示"在白名单中且不在黑名单中"的设备
 *
 *    情况4 - 没有任何规则或所有规则都禁用：
 *    - 所有扫描到的设备都会显示
 *
 * 7. 实时更新和动态过滤：
 *
 *    在扫描过程中可以随时添加、更新或删除过滤规则：
 *    ```
 *    // 正在扫描时添加新规则
 *    viewModel.addFilterRule(newFilter)
 *    // 之后发现的新设备会使用新规则进行过滤
 *
 *    // 禁用某个规则
 *    viewModel.updateFilterRule(existingFilter.copy(isEnabled = false))
 *    // 该规则不再被应用，之前被过滤的设备可能会重新出现（如果其他规则允许）
 *    ```
 *
 * 8. UI集成示例：
 *
 *    ```
 *    // 在UI中使用设备列表
 *    @Composable
 *    fun DeviceListScreen(viewModel: BluetoothViewModel) {
 *        val devices by viewModel.discoveredDevices.collectAsState()
 *        val isScanning by viewModel.isScanning.collectAsState()
 *
 *        LazyColumn {
 *            items(devices) { device ->
 *                // 这些设备已经通过了所有的过滤规则检查
 *                DeviceItem(device)
 *            }
 *        }
 *    }
 *    ```
 *
 * ==================== 过滤功能使用指南 ====================
 *
 * 1. 添加过滤规则：
 *    val filter = BluetoothFilterModel(
 *        id = "filter_1",
 *        alias = "iPhone白名单",
 *        filterRule = "iPhone",
 *        enableRegex = false,
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST
 *    )
 *    viewModel.addFilterRule(filter)
 *
 * 2. 按设备名进行过滤：
 *    val matchedFilters = viewModel.filterDevicesByName("iPhone 12 Pro")
 *
 * 3. 按MAC地址进行过滤：
 *    val matchedFilters = viewModel.filterDevicesByMacAddress("00:1A:7D:DA:71:13")
 *
 * 4. 检查设备是否应该被过滤：
 *    viewModel.shouldFilterDevice("iPhone 12", "00:1A:7D:DA:71:13")
 *
 * 5. 使用正则表达式过滤：
 *    val filter = BluetoothFilterModel(
 *        id = "filter_regex",
 *        alias = "Apple设备过滤",
 *        filterRule = "^(iPhone|iPad|Apple).*",
 *        enableRegex = true,
 *        filterType = BluetoothFilterModel.FilterType.WHITELIST
 *    )
 *    viewModel.addFilterRule(filter)
 *
 * 白名单模式：只允许匹配的设备连接
 * 黑名单模式：不允许匹配的设备连接
 *
 * ==================== 过滤规则字段说明 ====================
 *
 * - id: 规则的唯一标识符，不能重复
 * - alias: 规则的别名，用于UI显示和用户识别
 * - filterRule: 过滤规则字符串（设备名称或MAC地址的匹配规则）
 * - matchType: 匹配类型（DEVICE_NAME 或 MAC_ADDRESS）
 * - enableRegex: 是否启用正则表达式匹配（false = 模糊匹配，true = 正则表达式）
 * - filterType: 过滤类型（WHITELIST = 白名单，BLACKLIST = 黑名单）
 * - isEnabled: 是否启用当前规则（禁用的规则不会被应用）
 * - description: 规则的详细描述（可选）
 * - createTime: 规则创建时间戳（自动设置）
 * - updateTime: 规则最后更新时间戳（自动设置）
 *
 * ==================== 新增：全局设备管理器（BluetoothDeviceManagerDataSource）====================
 *
 * 为了支持其他模块（如定位服务、数据统计等）直接访问蓝牙设备数据，新增了全局设备管理器。
 *
 * 1. 架构图：
 *
 *    ┌────────────────────────────────────────────────────────────┐
 *    │  其他模块（定位、统计、上传等）                            │
 *    │  val mgr = BluetoothDeviceManagerDataSource.getInstance()  │
 *    │  mgr.managedDevices.collect { devices -> ... }             │
 *    └────────────────────────────────────────────────────────────┘
 *                              ↑ 订阅
 *    ┌────────────────────────────────────────────────────────────┐
 *    │  BluetoothDeviceManagerDataSource（全局单例）              │
 *    │  - 缓冲设备数据                                            │
 *    │  - 按500ms间隔发布                                         │
 *    │  - StateFlow<List<BluetoothDeviceModel>>                   │
 *    └────────────────────────────────────────────────────────────┘
 *                              ↑ updateDevice()
 *    ┌────────────────────────────────────────────────────────────┐
 *    │  BluetoothLocalDataSource（此ViewModel使用）               │
 *    │  - 系统蓝牙扫描                                            │
 *    │  - 应用过滤规则                                            │
 *    │  - 同步到管理器                                            │
 *    └────────────────────────────────────────────────────────────┘
 *
 * 2. 使用示例：
 *
 *    // 在定位服务中
 *    val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
 *    deviceManager.managedDevices.collect { devices ->
 *        // 设备已通过过滤、已去重、已整合RSSI/名字/MAC
 *        uploadBeaconsToLocationServer(devices)
 *    }
 *
 *    // 在统计模块中
 *    deviceManager.managedDevices.collect { devices ->
 *        analyzeSignalStrength(devices)  // 分析信号强度
 *        trackDeviceTypes(devices)        // 统计设备类型
 *    }
 *
 * 3. 关键特性：
 *
 *    - 全局单例：所有模块共享同一个管理器
 *    - 缓冲机制：避免高频更新（数百/秒 → 2/秒）
 *    - 定时发布：按500ms间隔发布设备列表
 *    - 解耦设计：其他模块无需创建BluetoothLocalDataSource
 *    - 完整数据：设备名、MAC地址、RSSI信号强度
 *
 * 4. 初始化流程：
 *
 *    - BluetoothLocalDataSource 创建时自动初始化管理器
 *    - 其他模块通过 getInstance() 获取同一实例
 *    - 数据自动同步，无需手动操作
 *
 * 详见BluetoothScanStrategy、BluetoothDeviceManagerDataSource、BLUETOOTH_MANAGER_INTEGRATION_GUIDE.md
 */
@Suppress("unused")
class BluetoothViewModel(context: Context) : ViewModel() {

    // 初始化过滤器数据源
    private val filterDataSource = BluetoothFilterLocalDataSource(context)

    // 创建蓝牙本地数据源，并传递过滤器数据源
    // 这样扫描时会自动应用过滤规则
    private val bluetoothLocalDataSource = BluetoothLocalDataSource(context, filterDataSource)

    // 初始化过滤规则仓库
    private val filterRepository = BluetoothFilterRepository(filterDataSource)

    // 创建蓝牙仓库，并传递过滤规则仓库
    private val bluetoothRepository = BluetoothRepository(
        bluetoothLocalDataSource,
        filterRepository
    )

    // 暴露给UI的状态流
    val isScanning: StateFlow<Boolean> = bluetoothRepository.isScanning
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = bluetoothRepository.discoveredDevices
    val errorMessage: StateFlow<String?> = bluetoothRepository.errorMessage

    // 过滤规则相关
    val filterRules: StateFlow<List<BluetoothFilterModel>> = filterRepository.filterRules
    val filterErrorMessage: StateFlow<String?> = filterRepository.errorMessage

    /**
     * 检查是否拥有扫描权限
     */
    @Suppress("unused")
    fun hasBluetoothScanPermission(): Boolean {
        return bluetoothRepository.hasBluetoothScanPermission()
    }

    /**
     * 检查蓝牙是否可用
     */
    @Suppress("unused")
    fun isBluetoothAvailable(): Boolean {
        return bluetoothRepository.isBluetoothAvailable()
    }

    /**
     * 检查蓝牙是否已启用
     */
    @Suppress("unused")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothRepository.isBluetoothEnabled()
    }

    /**
     * 开始扫描蓝牙设备
     */
    fun startScan(): Boolean {
        return bluetoothRepository.startScan()
    }

    /**
     * 停止扫描蓝牙设备
     */
    fun stopScan() {
        bluetoothRepository.stopScan()
    }

    /**
     * 清除已发现的设备列表
     */
    fun clearDevices() {
        bluetoothRepository.clearDevices()
    }

    // ==================== 过滤规则管理方法 ====================

    /**
     * 添加过滤规则
     */
    fun addFilterRule(filter: BluetoothFilterModel) {
        viewModelScope.launch {
            filterRepository.addFilter(filter)
        }
    }

    /**
     * 删除过滤规则
     */
    fun deleteFilterRule(filterId: String) {
        viewModelScope.launch {
            filterRepository.deleteFilter(filterId)
        }
    }

    /**
     * 更新过滤规则
     */
    fun updateFilterRule(filter: BluetoothFilterModel) {
        viewModelScope.launch {
            filterRepository.updateFilter(filter)
        }
    }

    /**
     * 获取启用的过滤规则
     */
    fun getEnabledFilterRules(): List<BluetoothFilterModel> {
        return filterRules.value.filter { it.isEnabled }
    }

    /**
     * 获取所有过滤规则
     */
    fun getAllFilterRules(): List<BluetoothFilterModel> {
        return filterRules.value
    }

    /**
     * 检查设备是否应该被过滤
     *
     * @param deviceName 蓝牙设备名称
     * @param macAddress MAC地址
     * @return true 表示应过滤，false 表示允许
     */
    fun shouldFilterDevice(deviceName: String, macAddress: String) {
        viewModelScope.launch {
            val shouldFilter = filterRepository.shouldFilterDevice(deviceName, macAddress)
            if (shouldFilter) {
                // TODO 可以在这里添加日志或其他处理逻辑
            }
        }
    }

    /**
     * 按设备名进行过滤
     */
    fun filterDevicesByName(deviceName: String): List<BluetoothFilterModel> {
        val matchedFilters = filterRules.value.filter { filter ->
            // 只处理设备名类型的规则
            if (filter.matchType != BluetoothFilterModel.MatchType.DEVICE_NAME) return@filter false
            if (!filter.isEnabled) return@filter false

            if (filter.enableRegex) {
                try {
                    Regex(filter.filterRule).containsMatchIn(deviceName)
                } catch (e: Exception) {
                    false
                }
            } else {
                deviceName.contains(filter.filterRule, ignoreCase = true)
            }
        }
        return matchedFilters
    }

    /**
     * 按MAC地址进行过滤
     */
    fun filterDevicesByMacAddress(macAddress: String): List<BluetoothFilterModel> {
        val matchedFilters = filterRules.value.filter { filter ->
            // 只处理MAC地址类型的规则
            if (filter.matchType != BluetoothFilterModel.MatchType.MAC_ADDRESS) return@filter false
            if (!filter.isEnabled) return@filter false

            if (filter.enableRegex) {
                try {
                    Regex(filter.filterRule).containsMatchIn(macAddress)
                } catch (e: Exception) {
                    false
                }
            } else {
                macAddress.equals(filter.filterRule, ignoreCase = true)
            }
        }
        return matchedFilters
    }

    /**
     * 清理资源（ViewModel销毁时调用）
     */
    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.cleanup()
    }
}

