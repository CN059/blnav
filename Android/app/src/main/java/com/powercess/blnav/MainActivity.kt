package com.powercess.blnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.common.utils.permission.BluetoothPermissionConfig
import com.powercess.blnav.common.utils.permission.GlobalPermissionManager
import com.powercess.blnav.common.utils.permission.createGlobalPermissionManager
import com.powercess.blnav.presentation.ui.MainScreen
import com.powercess.blnav.presentation.ui.theme.BlnavTheme
class MainActivity : ComponentActivity() {
    private lateinit var globalPermissionManager: GlobalPermissionManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        globalPermissionManager = createGlobalPermissionManager()
            .registerPermission(BluetoothPermissionConfig(this))
        globalPermissionManager.requestAllPermissions { allRequiredGranted, results ->
            if (allRequiredGranted) {
                AppLogger.debug("MainActivity", "所有必需权限已授予")
                results.forEach { result ->
                    AppLogger.debug(
                        "MainActivity",
                        "权限 ${result.permissionType}: " +
                                if (result.allGranted) "完全授予" else "部分拒绝"
                    )
                    if (result.deniedPermissions.isNotEmpty()) {
                        AppLogger.warn(
                            "MainActivity",
                            "  被拒绝的权限: ${result.deniedPermissions.joinToString()}"
                        )
                    }
                }
            } else {
                AppLogger.warn("MainActivity", "部分可选权限被拒绝")
                results.filter { !it.allGranted }.forEach { result ->
                    AppLogger.warn(
                        "MainActivity",
                        "${result.permissionType} 权限未完全授予，相关功能可能受限"
                    )
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            BlnavTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}