package com.powercess.blnav.data.repository

import com.powercess.blnav.data.datasource.local.BluetoothDeviceManagerDataSource
import com.powercess.blnav.data.datasource.local.BluetoothLocalDataSource
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙仓库
 *
 * 负责数据的统一管理和访问，作为数据源和业务逻辑之间的中介
 * 实现仓库模式，便于测试和维护
 *
 * ==================== MVVM架构说明 ====================
 *
 * 此Repository作为ViewModel和DataSource之间的中介层：
 * - ViewModel通过Repository访问数据，不直接访问DataSource
 * - Repository协调多个DataSource（本地/远程）
 * - 提供统一的数据访问接口
 *
 * ==================== 过滤器集成 ====================
 *
 * BluetoothRepository支持可选的BluetoothFilterRepository集成：
 *
 * 1. 如果在创建时传递filterRepository，蓝牙设备扫描会自动应用过滤规则
 * 2. 扫描时会根据当前启用的过滤规则过滤设备
 * 3. 支持白名单和黑名单模式
 * 4. 支持按设备名和MAC地址进行过滤
 *
 * ==================== 设备管理器集成 ====================
 *
 * 通过managedDevices属性访问全局设备管理器的数据：
 * - 已过滤的设备列表
 * - 已去重（相同MAC地址自动更新）
 * - 按500ms间隔定时发布（避免高频更新）
 */
class BluetoothRepository(
    private val localDataSource: BluetoothLocalDataSource,
    val filterRepository: BluetoothFilterRepository? = null
) {

    // 全局设备管理器实例（由LocalDataSource初始化）
    private val deviceManager: BluetoothDeviceManagerDataSource by lazy {
        BluetoothDeviceManagerDataSource.getInstance()
    }

    /**
     * 获取扫描状态流
     */
    val isScanning: StateFlow<Boolean>
        get() = localDataSource.isScanning

    /**
     * 获取发现的设备列表流（直接来自扫描结果）
     */
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>>
        get() = localDataSource.discoveredDevices

    /**
     * 获取管理的设备列表流（来自全局设备管理器）
     *
     * 特性：
     * - 已通过过滤规则检查
     * - 已自动去重（相同MAC地址的设备会更新）
     * - 按500ms间隔定时更新（避免高频刷新）
     * - 包含完整信息：MAC地址、设备名、RSSI信号强度
     *
     * 推荐用于：
     * - UI显示（避免频繁重绘）
     * - 定位计算（稳定的数据源）
     * - 数据上传（批量处理）
     */
    val managedDevices: StateFlow<List<BluetoothDeviceModel>>
        get() = deviceManager.managedDevices

    /**
     * 获取错误信息流
     */
    val errorMessage: StateFlow<String?>
        get() = localDataSource.errorMessage

    /**
     * 检查是否拥有扫描权限
     */
    fun hasBluetoothScanPermission(): Boolean {
        return localDataSource.hasBluetoothScanPermission()
    }

    /**
     * 检查蓝牙是否可用
     */
    fun isBluetoothAvailable(): Boolean {
        return localDataSource.isBluetoothAvailable()
    }

    /**
     * 检查蓝牙是否已启用
     */
    fun isBluetoothEnabled(): Boolean {
        return localDataSource.isBluetoothEnabled()
    }

    /**
     * 开始扫描蓝牙设备
     */
    fun startScan(): Boolean {
        return localDataSource.startScan()
    }

    /**
     * 停止扫描蓝牙设备
     */
    fun stopScan() {
        localDataSource.stopScan()
    }

    /**
     * 清除已发现的设备列表
     */
    fun clearDevices() {
        localDataSource.clearDevices()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        localDataSource.cleanup()
    }
}

