package com.powercess.blnav.common.utils.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 蓝牙权限配置
 *
 * 实现了 PermissionConfig 接口，定义了蓝牙功能所需的全部权限。
 *
 * Android 版本适配：
 * - Android 12 (API 31) 之前：BLUETOOTH + BLUETOOTH_ADMIN + 位置权限
 * - Android 12 (API 31) 及以上：BLUETOOTH_SCAN + BLUETOOTH_CONNECT + 精确位置（部分场景）
 *
 * 使用场景：
 * - 蓝牙设备扫描
 * - 蓝牙设备连接与通信
 * - 蓝牙低功耗（BLE）操作
 *
 * @param context Android 上下文（用于检查设备是否支持蓝牙）
 */
class BluetoothPermissionConfig(
    private val context: Context
) : PermissionConfig {

    override val permissionType: String = "BLUETOOTH"

    override fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 使用新的蓝牙权限
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,      // 扫描蓝牙设备
                Manifest.permission.BLUETOOTH_CONNECT,   // 连接蓝牙设备
                Manifest.permission.ACCESS_FINE_LOCATION // 精确位置（某些场景需要）
            )
        } else {
            // Android 12 以下使用传统蓝牙权限
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    override fun getRationaleMessage(): String {
        return "本应用需要蓝牙和位置权限来实现以下功能：\n\n" +
                "• 扫描并发现附近的蓝牙设备\n" +
                "• 连接蓝牙设备进行数据通信\n" +
                "• 提供基于蓝牙的导航服务\n\n" +
                "我们承诺不会滥用您的权限，仅在必要时使用。\n" +
                "如果拒绝授予权限，应用将无法正常工作。"
    }

    override fun isRequired(): Boolean {
        // 蓝牙权限为必需权限，拒绝后将退出应用
        return true
    }

    override fun isFeatureSupported(): Boolean {
        // 检查设备是否支持蓝牙硬件
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
}
