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
 * - enableRegex: 是否启用正则表达式匹配（false = 模糊匹配，true = 正则表达式）
 * - filterType: 过滤类型（WHITELIST = 白名单，BLACKLIST = 黑名单）
 * - isEnabled: 是否启用当前规则（禁用的规则不会被应用）
 * - description: 规则的详细描述（可选）
 * - createTime: 规则创建时间戳（自动设置）
 * - updateTime: 规则最后更新时间戳（自动设置）
 */
@Suppress("unused")
class BluetoothViewModel(context: Context) : ViewModel() {

    private val bluetoothRepository = BluetoothRepository(
        BluetoothLocalDataSource(context)
    )

    private val filterRepository = BluetoothFilterRepository(
        BluetoothFilterLocalDataSource(context)
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

