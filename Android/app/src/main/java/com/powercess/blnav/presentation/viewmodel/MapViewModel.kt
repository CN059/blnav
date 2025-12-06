package com.powercess.blnav.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powercess.blnav.data.datasource.local.BluetoothFilterLocalDataSource
import com.powercess.blnav.data.datasource.local.BluetoothLocalDataSource
import com.powercess.blnav.data.repository.BluetoothFilterRepository
import com.powercess.blnav.data.repository.BluetoothRepository
import com.powercess.blnav.presentation.ui.components.MapPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 地图页面ViewModel
 *
 * 遵循MVVM架构原则：
 * - View层（MapScreen）只与ViewModel交互
 * - ViewModel通过Repository获取数据
 * - Repository协调DataSource层
 *
 * 职责：
 * 1. 管理地图上的定位点数据（蓝牙信标位置）
 * 2. 提供地图相关的业务逻辑
 * 3. 将蓝牙设备数据转换为地图坐标点
 */
class MapViewModel(context: Context) : ViewModel() {

    // 初始化DataSource和Repository（遵循MVVM架构）
    private val filterDataSource = BluetoothFilterLocalDataSource(context)
    private val bluetoothLocalDataSource = BluetoothLocalDataSource(context, filterDataSource)
    private val filterRepository = BluetoothFilterRepository(filterDataSource)
    private val bluetoothRepository = BluetoothRepository(
        bluetoothLocalDataSource,
        filterRepository
    )

    // UI状态：地图上的定位点列表
    private val _mapPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val mapPoints: StateFlow<List<MapPoint>> = _mapPoints.asStateFlow()

    // UI状态：当前用户位置（如果支持定位）
    private val _currentPosition = MutableStateFlow<MapPoint?>(null)
    val currentPosition: StateFlow<MapPoint?> = _currentPosition.asStateFlow()

    // UI状态：错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // 初始化时订阅蓝牙设备数据，转换为地图点
        observeBluetoothDevices()
    }

    /**
     * 订阅蓝牙设备数据并转换为地图坐标点
     * 这里演示如何将蓝牙设备的RSSI信号强度转换为地图位置
     */
    private fun observeBluetoothDevices() {
        viewModelScope.launch {
            try {
                // 使用managedDevices而不是discoveredDevices，获得更稳定的数据
                bluetoothRepository.managedDevices.collect { devices ->
                    // 将蓝牙设备转换为地图点
                    // 这里使用示例算法，实际应用中可能需要：
                    // 1. 三角定位算法（基于多个信标的RSSI）
                    // 2. 指纹定位（基于预先采集的RSSI地图）
                    // 3. 卡尔曼滤波（平滑位置变化）

                    val points = devices.mapIndexed { index, device ->
                        // 示例：根据设备索引分配不同位置
                        // 实际使用时应该根据设备的MAC地址映射到预设的信标位置
                        val x = when (index % 3) {
                            0 -> 0.3f
                            1 -> 0.7f
                            else -> 0.5f
                        }
                        val y = when (index / 3) {
                            0 -> 0.3f
                            1 -> 0.5f
                            else -> 0.7f
                        }

                        MapPoint(
                            x = x,
                            y = y,
                            label = device.name,
                            color = androidx.compose.ui.graphics.Color(
                                // 根据信号强度设置颜色（RSSI越高越绿，越低越红）
                                when {
                                    device.rssi > -60 -> 0xFF4CAF50 // 绿色（强信号）
                                    device.rssi > -80 -> 0xFFFFC107 // 黄色（中等信号）
                                    else -> 0xFFF44336 // 红色（弱信号）
                                }
                            )
                        )
                    }

                    _mapPoints.value = points
                }
            } catch (e: Exception) {
                _errorMessage.value = "获取设备位置失败: ${e.message}"
            }
        }
    }

    /**
     * 设置预定义的信标位置
     * @param beaconPositions 信标MAC地址到地图坐标的映射
     */
    @Suppress("unused")
    fun setBeaconPositions(beaconPositions: Map<String, Pair<Float, Float>>) {
        viewModelScope.launch {
            try {
                bluetoothRepository.managedDevices.collect { devices ->
                    val points = devices.mapNotNull { device ->
                        // 查找设备对应的预设位置
                        beaconPositions[device.address]?.let { (x, y) ->
                            MapPoint(
                                x = x,
                                y = y,
                                label = device.name,
                                color = androidx.compose.ui.graphics.Color(
                                    when {
                                        device.rssi > -60 -> 0xFF4CAF50
                                        device.rssi > -80 -> 0xFFFFC107
                                        else -> 0xFFF44336
                                    }
                                )
                            )
                        }
                    }
                    _mapPoints.value = points
                }
            } catch (e: Exception) {
                _errorMessage.value = "设置信标位置失败: ${e.message}"
            }
        }
    }

    /**
     * 添加自定义地图点（如目标位置、路径点等）
     */
    @Suppress("unused")
    fun addCustomPoint(point: MapPoint) {
        _mapPoints.value = _mapPoints.value + point
    }

    /**
     * 清除所有地图点
     */
    @Suppress("unused")
    fun clearMapPoints() {
        _mapPoints.value = emptyList()
    }

    /**
     * 更新当前用户位置
     * 可以基于蓝牙信标进行室内定位计算
     */
    @Suppress("unused")
    fun updateCurrentPosition(x: Float, y: Float) {
        _currentPosition.value = MapPoint(
            x = x,
            y = y,
            label = "当前位置",
            color = androidx.compose.ui.graphics.Color(0xFF2196F3) // 蓝色
        )
    }

    /**
     * 清除错误信息
     */
    @Suppress("unused")
    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel被销毁时的清理工作
        // Repository会自动处理资源释放
    }
}

