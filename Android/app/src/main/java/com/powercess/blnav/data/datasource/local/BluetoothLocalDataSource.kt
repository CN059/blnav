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

/**
 * 蓝牙本地数据源
 *
 * 负责与系统蓝牙API交互，管理蓝牙设备扫描的底层操作
 */
class BluetoothLocalDataSource(private val context: Context) {

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
     */
    private fun addDevice(device: BluetoothDeviceModel) {
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

