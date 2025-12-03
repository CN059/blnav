package com.powercess.blnav.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.powercess.blnav.data.datasource.local.BluetoothLocalDataSource
import com.powercess.blnav.data.repository.BluetoothRepository
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙管理ViewModel
 *
 * 负责UI状态管理和业务逻辑，遵循MVVM架构模式
 */
@Suppress("unused")
class BluetoothViewModel(context: Context) : ViewModel() {

    private val bluetoothRepository = BluetoothRepository(
        BluetoothLocalDataSource(context)
    )

    // 暴露给UI的状态流
    val isScanning: StateFlow<Boolean> = bluetoothRepository.isScanning
    val discoveredDevices: StateFlow<List<BluetoothDeviceModel>> = bluetoothRepository.discoveredDevices
    val errorMessage: StateFlow<String?> = bluetoothRepository.errorMessage

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

    /**
     * 清理资源（ViewModel销毁时调用）
     */
    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.cleanup()
    }
}

