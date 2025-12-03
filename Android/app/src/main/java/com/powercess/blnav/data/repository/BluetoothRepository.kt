package com.powercess.blnav.data.repository

import com.powercess.blnav.data.datasource.local.BluetoothLocalDataSource
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙仓库
 *
 * 负责数据的统一管理和访问，作为数据源和业务逻辑之间的中介
 * 实现仓库模式，便于测试和维护
 */
class BluetoothRepository(private val localDataSource: BluetoothLocalDataSource) {

    /**
     * 获取扫描状态流
     */
    val isScanning: StateFlow<Boolean>
        get() = localDataSource.isScanning

    /**
     * 获取发现的设备列表流
     */
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>>
        get() = localDataSource.discoveredDevices

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

