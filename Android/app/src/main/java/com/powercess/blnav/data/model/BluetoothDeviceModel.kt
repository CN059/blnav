package com.powercess.blnav.data.model

/**
 * 蓝牙设备数据模型
 *
 * 用于保存扫描到的蓝牙设备信息
 */
data class BluetoothDeviceModel(
    val address: String,
    val name: String,
    val rssi: Int = 0,
    val bondState: Int = 0
) {
    override fun toString(): String {
        return "设备: $name\nMAC: $address\n信号强度: $rssi dBm"
    }
}

