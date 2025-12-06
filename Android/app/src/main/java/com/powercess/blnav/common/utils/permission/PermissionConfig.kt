package com.powercess.blnav.common.permission
/**
 * 权限配置接口
 *
 * 定义了权限请求的标准契约，所有具体的权限配置（如蓝牙、相机、存储等）都需要实现此接口。
 *
 * 设计理念：
 * - 统一权限管理的入口
 * - 支持不同 Android 版本的权限适配
 * - 便于扩展新的权限类型
 *
 * @property permissionType 权限类型标识（如 "BLUETOOTH", "CAMERA", "STORAGE"）
 */
interface PermissionConfig {
    /**
     * 权限类型名称（用于日志和调试）
     */
    val permissionType: String

    /**
     * 获取当前 Android 版本所需的权限列表
     *
     * @return 权限字符串数组
     */
    fun getRequiredPermissions(): Array<String>

    /**
     * 权限说明（解释为什么需要这些权限）
     *
     * 用于：
     * - 首次请求权限时的说明对话框
     * - 权限被拒绝后的提示信息
     *
     * @return 用户友好的权限说明文本
     */
    fun getRationaleMessage(): String

    /**
     * 是否为必需权限（如果为 true，拒绝后将退出应用）
     *
     * @return true = 必需，false = 可选
     */
    fun isRequired(): Boolean

    /**
     * 检查设备是否支持相关功能（如蓝牙、NFC 等）
     *
     * @return true = 支持，false = 不支持
     */
    fun isFeatureSupported(): Boolean
}

/**
 * 权限请求状态
 */
enum class PermissionStatus {
    /** 权限已授予 */
    GRANTED,

    /** 权限被拒绝（用户可再次请求） */
    DENIED,

    /** 权限被永久拒绝（用户勾选了"不再询问"） */
    PERMANENTLY_DENIED,

    /** 设备不支持相关功能 */
    FEATURE_NOT_SUPPORTED
}

/**
 * 权限请求结果回调
 *
 * @param permissionType 权限类型
 * @param status 权限状态（使用枚举表示更精确的状态）
 * @param allGranted 是否全部授予（快捷判断字段）
 * @param grantedPermissions 已授予的权限列表
 * @param deniedPermissions 被拒绝的权限列表
 * @param permanentlyDeniedPermissions 被永久拒绝的权限列表
 */
data class PermissionResult(
    val permissionType: String,
    val status: PermissionStatus,
    val allGranted: Boolean,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val permanentlyDeniedPermissions: List<String> = emptyList()
)


