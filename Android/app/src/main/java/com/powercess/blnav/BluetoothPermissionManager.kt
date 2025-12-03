package com.powercess.blnav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 蓝牙权限管理器
 *
 * 功能说明：
 * - 处理 Android 不同版本的蓝牙权限请求
 * - Android 12 (API 31) 之前：需要 BLUETOOTH、BLUETOOTH_ADMIN 和位置权限
 * - Android 12 (API 31) 及以上：需要 BLUETOOTH_SCAN、BLUETOOTH_CONNECT 权限
 *
 * 使用方法：
 * 1. 在 Activity 的 onCreate 中创建实例
 * 2. 调用 requestBluetoothPermissions() 请求权限
 * 3. 在回调中处理权限授予或拒绝的情况
 *
 * 注意事项：
 * - 必须在 Activity 的 onCreate 方法中初始化（在 super.onCreate 之后）
 * - 权限请求结果通过回调函数返回
 * - 建议在实际使用蓝牙功能前先检查权限状态
 * - 这个类传入的参数ComponentActivity意味着，这个权限管理器要在Activity中使用。任意一个Activity都可以。都会授予整个App蓝牙权限。
 */

// 这里在类里面写的那两个东西就是构造函数的参数直接作为类的属性
class BluetoothPermissionManager(
    private val activity: ComponentActivity,
    // onPermissionResult 是一个 函数类型（function type），表示接受一个 Boolean，不返回值（Unit 相当于 Java 的 void）的函数
    // 实际上这个函数类型就是一个回调函数，用于通知调用者权限请求的结果
    // Unit不能省略，因为它表示这个函数没有返回值。如果省略了，Kotlin 会认为这是一个普通的属性，而不是函数类型
    private val onPermissionResult: (Boolean) -> Unit  // 权限结果回调：true = 全部授权，false = 有权限被拒绝
) {

    /** 权限请求启动器
     使用 ActivityResultContracts.RequestMultiplePermissions 可以一次请求多个权限*/
    // 权限启动器是一个 ActivityResultLauncher 对象，用于发起权限请求并处理结果回调。
    // 也就是说，真正向系统请求权限的操作是通过这个启动器来完成的。这个东西发送请求，然后系统受理请求，弹出弹窗，然后这个权限启动器获取用户的选择结果，然后回调给我们处理结果。
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        // registerForActivityResult 是 Android Activity Result API 的核心方法，用于注册一个异步结果回调。
        // 它不会立刻请求权限，而是返回一个 ActivityResultLauncher 对象。
        // 真正触发权限弹窗的是 permissionLauncher.launch(...)（在 requestBluetoothPermissions() 中调用）。
        // 系统弹窗后，用户操作（允许/拒绝）会自动回调传入的 lambda。

        //  MainActivity 调用 requestBluetoothPermissions() → 内部调用 permissionLauncher.launch() → 系统弹窗；

        // permissionLauncher 是 ActivityResultLauncher<Array<String>> 的一个实例。
        // 它不是“权限申请的核心管理者”，而是 “一个用于启动特定 Activity Result 流程的轻量级启动器”。
        // 它本身不会申请权限，只是封装了“如何启动权限请求” + “如何接收结果” 的管道。

        // registerForActivityResult 的签名里的含义是：
        //    contract：告诉系统 “你想执行哪一类操作”（比如：请求权限、选择图片、拍照等）。
        //    callback：告诉系统 “操作完成后，把结果交给我处理”。
        // RequestMultiplePermissions() 是一个 ActivityResultContract 对象，用于定义协议，不是回调。
        activity.registerForActivityResult(
            // 这个函数返回的类型是 ActivityResultContract<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>
            //    ActivityResultContract<I, O> 是契约类型，用于定义输入/输出。
            //    registerForActivityResult(contract) { result: O -> ... } 的回调参数类型是 O，即 Map<String, Boolean>。
            //    ActivityResultLauncher.launch(I) 没有返回值（返回 Unit），因为它是异步启动，结果通过回调传递。
            ActivityResultContracts.RequestMultiplePermissions()
            //    没有“函数返回结果”
            //    结果通过 callback 传递
            //    ActivityResultContract 不是函数，而是一个对象，描述“如何启动 + 如何解析结果”
        ) {
            // 回调 lambda 的输入 permissions 从哪里来？
            //    它不是由 ActivityResultContracts.RequestMultiplePermissions() “返回”的。
            //    而是 Activity Result API 框架在收到系统回调后，调用 contract.parseResult(...) 生成的。
            //    最终作为参数传入注册的 lambda。
            permissions ->
            // 回调处理：检查是否所有权限都被授予
            // permissions 是一个 Map<String, Boolean>，key 是权限名，value 是是否授予
            val allGranted = permissions.entries.all { it.value }

            // 打印权限请求结果（便于调试）
            permissions.entries.forEach { entry ->
                android.util.Log.d(
                    "BluetoothPermission",
                    "权限 ${entry.key}: ${if (entry.value) "已授予" else "被拒绝"}"
                )
            }

            // 通过回调函数通知调用者
            onPermissionResult(allGranted)
        }

    /**
     * 获取当前 Android 版本需要的蓝牙权限列表
     *
     * Android 版本适配说明：
     * - API < 23 (Android 6.0)：不需要运行时权限
     * - API 23-30：需要位置权限才能扫描蓝牙设备
     * - API >= 31 (Android 12)：使用新的蓝牙专用权限
     */
    private fun getRequiredPermissions(): Array<String> {
        // 这个函数根据不同的 Android 版本返回不同的权限数组

        // Build是 android.os 包下的一个类，包含了设备的构建信息。这是一个提供设备和系统信息的静态类
        // Version 是 Build 类的一个静态内部类，包含了与 Android 版本相关的信息
        // SDK_INT 是 Version 类的一个静态常量，表示当前设备运行的 Android SDK 版本号（整数形式）
        // VERSION_CODES 是 Version 类的一个静态内部类，定义了各个 Android 版本对应的 SDK 版本号常量

        // 右边的VERSION_CODES.S 是 Android 12 的版本代号，表示 SDK 31
        // 也就是说如果当前设备的SDK版本号大于等于31，就说明运行的是Android 12或更高版本，那么就返回新的蓝牙权限数组
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 这里返回的是一个数组，包含了三个权限字符串
            // 问题：什么是Manifest,有什么作用？
            //  Manifest的成员permission是什么，除此之外，它还管理哪些成员？
            //  这些定义好的权限在整个安卓生态里是如何处理的？
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,      // 扫描蓝牙设备
                Manifest.permission.BLUETOOTH_CONNECT,   // 连接蓝牙设备
                Manifest.permission.ACCESS_FINE_LOCATION // 精确位置（某些场景仍需要）
            )
        } else {
            // Android 12 以下：使用传统权限组合
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * 检查是否已拥有所有必需的蓝牙权限
     *
     * @return true = 已拥有全部权限，false = 缺少一个或多个权限
     */
    fun hasAllPermissions(): Boolean {
        // 首先我们调用本类的私有方法 getRequiredPermissions() 获取当前需要的权限列表
        val requiredPermissions = getRequiredPermissions()
        // 下面的代码就是逐个检查权限是否被授予
        //  all 是 Kotlin 标准库的一个扩展函数，用于检查集合中的所有元素是否都满足给定的条件。
        //  参数是一个 Lambda 表达式，表示检查条件，->运算符是什么意思?前面的内容是什么？后面的内容又是什么？整个的运行情况，所有的可能的情况又是怎样的？
        // <参数名>:<参数类型> -> 返回值表达式
        // 这个.all 仿佛自动的把里面的每个参数传递给了 Lambda 表达式的参数 permission，然后lambda表达式的后面的返回值表达式可以调用permission进行一些逻辑操作，对吗？
        //  lambda表达式后面的返回值表达式不需要用花括号框起来？
        return requiredPermissions.all { permission ->
            /** ContextCompat 是 AndroidX 提供的一个兼容性类，包含了一些静态方法，用于简化对上下文相关功能的访问
               checkSelfPermission 是 ContextCompat 类的一个静态方法，用于检查应用是否拥有某个权限
             它接受两个参数：Context 对象（这里是 activity）和权限名称字符串（permission）
               返回值是一个整数，表示权限状态。
             PackageManager.PERMISSION_GRANTED 是 PackageManager 类的一个常量，表示权限已被授予
               也就是说，这一行代码的作用是检查指定的权限是否被授予，返回 true 或 false */
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
        // 所以说，上面的那个all函数执行过程中，一旦检测到有一个权限没有被授予（也就是返回false），整个all函数就会立即返回false，不会继续检查剩下的权限了。只有当所有权限都被授予时，all函数才会返回true。对吗？
    }

    /**
     * # 请求蓝牙相关权限
     * ## 这个方法是公开的，可以在Activity中调用它来发起权限请求。也就是核心权限获取方法
     *
     * 流程说明：
     * 1. 先检查是否已拥有权限
     * 2. 如果已拥有，直接回调 true
     * 3. 如果缺少权限，弹出系统权限请求对话框
     * 4. 用户授权/拒绝后，通过 onPermissionResult 回调返回结果
     */
    fun requestBluetoothPermissions() {
        if (hasAllPermissions()) {
            // 已经拥有全部权限，直接回调成功
            // android.util.Log 是 Android 提供的一个日志工具类，内部方法d,v,i,w,e分别表示不同级别的日志输出
            android.util.Log.d("BluetoothPermission", "已拥有全部蓝牙权限")
            // 这个onPermissionResult是构造函数传入的一个回调函数，什么意思？
            //  作用是通知调用者权限已经就绪，可以执行蓝牙操作
            onPermissionResult(true)// 同步回调，不走 launcher
            // 如果权限已存在：直接同步调用 onPermissionResult(true)，不涉及 permissionLauncher
        } else {
            // 缺少权限，发起权限请求
            val permissionsToRequest = getRequiredPermissions()
            android.util.Log.d(
                "BluetoothPermission",
                "请求蓝牙权限: ${permissionsToRequest.joinToString()}"
            )
            // joinToString 的作用是把数组转换成一个字符串，元素之间用逗号分隔，便于日志输出
            // 通过 permissionLauncher 发起权限请求
            permissionLauncher.launch(permissionsToRequest)// 异步启动，结果走注册的 lambda
            // 如果权限缺失：调用 launcher.launch(...) → 系统弹窗 → 用户操作 →
            //  框架回调你注册的 lambda → lambda 再调用 onPermissionResult(allGranted)
        }
    }

    /**
     * 检查是否应该显示权限说明（用户之前拒绝过权限）
     *
     * 使用场景：
     * - 当用户第一次拒绝权限后，再次请求时可以先显示说明对话框
     * - 解释为什么需要这些权限，提高用户授权意愿
     *
     * @return true = 应该显示说明，false = 不需要显示
     */
    fun shouldShowRationale(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * 获取缺少的权限列表（便于调试和日志）
     *
     * @return 当前缺少的权限名称列表
     */
    fun getMissingPermissions(): List<String> {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查当前设备是否支持蓝牙功能
     *
     * @return true = 设备支持蓝牙，false = 设备不支持蓝牙
     */
    fun isBluetoothSupported(): Boolean {
        // activity 是 ComponentActivity（Jetpack 的 Activity 基类） 类型，来源是构造函数传入的参数，作用是提供Android上下文环境
        // 那么这里的上下文环境是什么？为什么一定需要从activity获取设备是否支持蓝牙？难道说这个“是否支持蓝牙”这个属性是所有ComponentActivity的共有属性吗？

        // packageManager 是 Context 类的一个属性，ComponentActivity 继承自 Context，因此可以直接访问 packageManager。
        // packageManager 提供了访问设备上已安装应用程序包的信息的能力。作用就是管理应用包信息、权限、硬件特性等
        // 所以是不是可以理解为packageManager是管理这个app的一个管理器，而activity是管理app里面显示一个页面的管理器？

        // hasSystemFeature 是 PackageManager 类的一个方法，用于检查设备是否支持特定的硬件或软件特性。
        // 传入的参数是 硬件特性名称 ，是一个常量

        // 总的来说就是，我们把蓝牙功能特性常量发送给调用这个蓝牙权限管理器的activity组件，然后这个组建调用它的全局app包管理器的hasSystemFeature方法，来检查这个设备是否支持蓝牙功能，对吗？
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    }
}

/**
 * 扩展函数：为 ComponentActivity 提供便捷的蓝牙权限管理方法
 *
 * 使用示例：
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private lateinit var bluetoothPermissionManager: BluetoothPermissionManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         bluetoothPermissionManager = createBluetoothPermissionManager { allGranted ->
 *             if (allGranted) {
 *                 // 权限已授予，可以开始蓝牙操作
 *             } else {
 *                 // 权限被拒绝，显示提示信息
 *             }
 *         }
 *
 *         // 请求权限
 *         bluetoothPermissionManager.requestBluetoothPermissions()
 *     }
 * }
 * ```
 */
// 这是 Kotlin 的 扩展函数（Extension Function），为 ComponentActivity 添加新方法。
// 语法糖，让创建更简洁（this 就是调用者 Activity）。
fun ComponentActivity.createBluetoothPermissionManager(
    onPermissionResult: (Boolean) -> Unit
): BluetoothPermissionManager {
    return BluetoothPermissionManager(this, onPermissionResult)
}

