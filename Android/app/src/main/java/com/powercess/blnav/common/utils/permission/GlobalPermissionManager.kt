package com.powercess.blnav.common.permission

import android.app.AlertDialog
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.powercess.blnav.common.logger.AppLogger

class GlobalPermissionManager(
    private val activity: ComponentActivity
) {
    companion object {
        private const val TAG = "GlobalPermissionManager"
    }

    /** 已注册的权限配置列表 */
    private val permissionConfigs = mutableListOf<PermissionConfig>()

    /** 权限请求结果回调 */
    private var onAllPermissionsResult: ((Boolean, List<PermissionResult>) -> Unit)? = null

    /** 当前正在请求的权限配置索引 */
    private var currentConfigIndex = 0

    /** 所有权限请求的结果集合 */
    private val permissionResults = mutableListOf<PermissionResult>()

    /**
     * 权限请求启动器
     * 使用 ActivityResultContracts.RequestMultiplePermissions 一次请求多个权限
     */
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResult(permissions)
        }

    /**
     * 注册权限配置
     *
     * 将需要的权限配置添加到管理器中。
     * 建议在 Activity 的 onCreate 中调用。
     *
     * @param config 权限配置对象
     * @return GlobalPermissionManager 返回自身，支持链式调用
     */
    fun registerPermission(config: PermissionConfig): GlobalPermissionManager {
        permissionConfigs.add(config)
        AppLogger.debug(TAG, "注册权限配置: ${config.permissionType}")
        return this
    }

    /**
     * 批量注册多个权限配置
     *
     * @param configs 权限配置列表
     * @return GlobalPermissionManager 返回自身，支持链式调用
     */
    @Suppress("unused")
    fun registerPermissions(vararg configs: PermissionConfig): GlobalPermissionManager {
        configs.forEach { registerPermission(it) }
        return this
    }

    /**
     * 请求所有已注册的权限
     *
     * 核心方法：按顺序检查并请求所有已注册的权限配置。
     * - 如果权限已授予，跳过
     * - 如果设备不支持，记录并跳过
     * - 如果缺少权限，弹出系统权限请求对话框
     * - 必需权限被拒绝时，自动退出应用
     *
     * @param onResult 权限请求完成后的回调
     *                 参数1: Boolean - 所有必需权限是否已授予
     *                 参数2: List<PermissionResult> - 各权限的详细结果
     */
    fun requestAllPermissions(onResult: (Boolean, List<PermissionResult>) -> Unit) {
        if (permissionConfigs.isEmpty()) {
            AppLogger.warn(TAG, "未注册任何权限配置")
            onResult(true, emptyList())
            return
        }

        this.onAllPermissionsResult = onResult
        currentConfigIndex = 0
        permissionResults.clear()

        AppLogger.debug(TAG, "开始请求所有权限，共 ${permissionConfigs.size} 组")
        requestNextPermission()
    }

    /**
     * 请求下一个权限配置
     *
     * 内部方法：递归请求每一组权限，直到所有权限都处理完毕。
     */
    private fun requestNextPermission() {
        if (currentConfigIndex >= permissionConfigs.size) {
            // 所有权限请求完毕，执行回调
            onAllRequestsCompleted()
            return
        }

        val config = permissionConfigs[currentConfigIndex]
        AppLogger.debug(TAG, "正在请求权限: ${config.permissionType}")

        // 检查设备是否支持相关功能
        if (!config.isFeatureSupported()) {
            AppLogger.warn(TAG, "${config.permissionType} 功能不被设备支持")
            permissionResults.add(
                PermissionResult(
                    permissionType = config.permissionType,
                    status = PermissionStatus.FEATURE_NOT_SUPPORTED,
                    allGranted = false,
                    grantedPermissions = emptyList(),
                    deniedPermissions = config.getRequiredPermissions().toList(),
                    permanentlyDeniedPermissions = emptyList()
                )
            )

            // 如果是必需权限，退出应用
            if (config.isRequired()) {
                val message = "设备不支持 ${config.permissionType} 功能，应用无法运行。\n\n" +
                        config.getRationaleMessage()
                showExitDialog(config.permissionType, message)
                return
            }

            currentConfigIndex++
            requestNextPermission()
            return
        }

        // 检查权限状态
        val requiredPermissions = config.getRequiredPermissions()
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        requiredPermissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(
                    activity,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                granted.add(permission)
            } else {
                denied.add(permission)
            }
        }

        if (denied.isEmpty()) {
            // 权限已全部授予
            AppLogger.debug(TAG, "${config.permissionType} 权限已全部授予")
            permissionResults.add(
                PermissionResult(
                    permissionType = config.permissionType,
                    status = PermissionStatus.GRANTED,
                    allGranted = true,
                    grantedPermissions = granted,
                    deniedPermissions = emptyList(),
                    permanentlyDeniedPermissions = emptyList()
                )
            )
            currentConfigIndex++
            requestNextPermission()
        } else {
            // 需要请求权限
            AppLogger.debug(TAG, "${config.permissionType} 缺少权限: ${denied.joinToString()}")

            // 检查是否应该显示权限说明
            val shouldShowRationale = denied.any { permission ->
                activity.shouldShowRequestPermissionRationale(permission)
            }

            if (shouldShowRationale) {
                // 用户之前拒绝过，显示说明对话框
                showRationaleDialog(config)
            } else {
                // 直接请求权限
                permissionLauncher.launch(denied.toTypedArray())
            }
        }
    }

    /**
     * 处理权限请求结果
     *
     * @param permissions 系统返回的权限结果 Map
     */
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val config = permissionConfigs[currentConfigIndex]
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()
        val permanentlyDenied = mutableListOf<String>()

        permissions.entries.forEach { entry ->
            if (entry.value) {
                granted.add(entry.key)
            } else {
                denied.add(entry.key)
                // 检查是否为永久拒绝（用户勾选了"不再询问"）
                if (!activity.shouldShowRequestPermissionRationale(entry.key)) {
                    permanentlyDenied.add(entry.key)
                }
            }
            AppLogger.debug(TAG, "权限 ${entry.key}: ${if (entry.value) "已授予" else "被拒绝"}")
        }

        val allGranted = denied.isEmpty()

        // 确定权限状态
        val status = when {
            allGranted -> PermissionStatus.GRANTED
            permanentlyDenied.isNotEmpty() -> PermissionStatus.PERMANENTLY_DENIED
            else -> PermissionStatus.DENIED
        }

        permissionResults.add(
            PermissionResult(
                permissionType = config.permissionType,
                status = status,
                allGranted = allGranted,
                grantedPermissions = granted,
                deniedPermissions = denied,
                permanentlyDeniedPermissions = permanentlyDenied
            )
        )

        // 如果是必需权限且被拒绝，显示退出对话框
        if (!allGranted && config.isRequired()) {
            showExitDialog(config.permissionType, config.getRationaleMessage())
            return
        }

        // 继续请求下一组权限
        currentConfigIndex++
        requestNextPermission()
    }

    /**
     * 所有权限请求完成
     */
    private fun onAllRequestsCompleted() {
        AppLogger.debug(TAG, "所有权限请求完成")

        // 检查是否所有必需权限都已授予
        val allRequiredGranted = permissionResults.all { result ->
            val config = permissionConfigs.find { it.permissionType == result.permissionType }
            !config!!.isRequired() || result.allGranted
        }

        AppLogger.debug(TAG, "必需权限状态: ${if (allRequiredGranted) "全部授予" else "部分拒绝"}")

        // 执行回调
        onAllPermissionsResult?.invoke(allRequiredGranted, permissionResults.toList())
    }

    /**
     * 显示权限说明对话框
     *
     * @param config 权限配置
     */
    private fun showRationaleDialog(config: PermissionConfig) {
        AlertDialog.Builder(activity)
            .setTitle("需要权限")
            .setMessage(config.getRationaleMessage())
            .setPositiveButton("授予权限") { _, _ ->
                // 用户同意，发起权限请求
                val denied = config.getRequiredPermissions().filter { permission ->
                    ContextCompat.checkSelfPermission(
                        activity,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                }
                permissionLauncher.launch(denied.toTypedArray())
            }
            .setNegativeButton("拒绝") { _, _ ->
                // 用户拒绝
                AppLogger.warn(TAG, "用户拒绝了 ${config.permissionType} 权限说明")
                handlePermissionResult(
                    config.getRequiredPermissions().associateWith { false }
                )
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 显示退出对话框
     *
     * 当必需权限被拒绝时调用，提示用户并退出应用。
     *
     * @param permissionType 权限类型
     * @param rationaleMessage 权限说明信息
     */
    private fun showExitDialog(permissionType: String, rationaleMessage: String) {
        AppLogger.error(TAG, "必需权限 $permissionType 被拒绝，准备退出应用")

        AlertDialog.Builder(activity)
            .setTitle("${permissionType}权限不足")
            .setMessage(rationaleMessage)
            .setPositiveButton("退出应用") { _, _ ->
                AppLogger.debug(TAG, "用户确认退出应用")
                activity.finishAffinity() // 退出应用
            }
            .setNegativeButton("去设置") { _, _ ->
                // 跳转到应用设置页面
                openAppSettings()
                activity.finishAffinity() // 仍然退出应用
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 打开应用设置页面
     *
     * 引导用户手动授予权限。
     */
    private fun openAppSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", activity.packageName, null)
            )
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            AppLogger.debug(TAG, "跳转到应用设置页面")
        } catch (e: Exception) {
            AppLogger.error(TAG, "无法打开应用设置页面", e)
        }
    }

    /**
     * 检查特定权限类型是否已授予
     *
     * @param permissionType 权限类型（如 "BLUETOOTH"）
     * @return true = 已授予，false = 未授予或不存在
     */
    @Suppress("unused")
    fun isPermissionGranted(permissionType: String): Boolean {
        val config = permissionConfigs.find { it.permissionType == permissionType }
            ?: return false

        return config.getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取已注册的权限配置列表
     *
     * @return 权限配置列表（只读）
     */
    @Suppress("unused")
    fun getRegisteredPermissions(): List<PermissionConfig> {
        return permissionConfigs.toList()
    }
}

/**
 * ComponentActivity 扩展函数：快速创建全局权限管理器
 *
 * 使用示例：
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private lateinit var permissionManager: GlobalPermissionManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         permissionManager = createGlobalPermissionManager()
 *             .registerPermission(BluetoothPermissionConfig(this))
 *             .registerPermission(CameraPermissionConfig(this))
 *
 *         permissionManager.requestAllPermissions { allGranted, results ->
 *             if (allGranted) {
 *                 // 所有必需权限已授予，启动应用功能
 *             } else {
 *                 // 部分权限被拒绝（可选权限），可继续运行
 *             }
 *         }
 *     }
 * }
 * ```
 */
fun ComponentActivity.createGlobalPermissionManager(): GlobalPermissionManager {
    return GlobalPermissionManager(this)
}

