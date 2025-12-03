package com.powercess.blnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.powercess.blnav.presentation.ui.MainScreen
import com.powercess.blnav.presentation.ui.theme.BlnavTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException

/*
* 注意：
*  最佳实践是 用 State 驱动 UI 重组，而不是重复调用 setContent：
*  在未来的开发中使用更好的解决方案
* */

/**
* Activity 是一个核心组件，代表应用程序中的一个单一屏幕（界面）及其对应的行为。
* 可以把 Activity 理解为一个“窗口”或“页面”，用户通过它与应用进行交互。
* 用户界面载体：每个 Activity 通常关联一个 UI 布局（通过 XML 文件或代码定义），并负责响应用户操作（如点击、输入等）。
* 生命周期管理：Activity 有明确的生命周期回调方法（如 onCreate()、onStart()、onResume()、onPause()、onStop()、onDestroy()），开发者可据此管理资源、保存状态或处理界面切换。
* 任务栈（Task Stack）：Android 使用“回退栈”（Back Stack）管理 Activity 的启动与返回。新启动的 Activity 被压入栈顶，按返回键时出栈，返回上一个 Activity。
* 组件间通信：通过 Intent 启动其他 Activity，并可传递数据（如字符串、序列化对象等）。
* */

// ComponentActivity 是 AndroidX 提供的一个基类，
// 支持 Jetpack Compose、ViewModel、Lifecycle 等现代 Android 架构组件。
class MainActivity : ComponentActivity() {

    /** 蓝牙权限管理器（延迟初始化）
    使用 lateinit 因为需要在 onCreate 中初始化（需要 Activity 上下文）*/
    // 这段代码声明了一个名为 bluetoothPermissionManager 的私有属性，
    // 类型是 BluetoothPermissionManager，并使用 lateinit 修饰符进行延迟初始化。
    // 延迟初始化一般是由某些特定事件触发的，比如在 onCreate 方法中。
    // 在下面的代码中，我们只是定义了这个 createBluetoothPermissionManager 执行后的回调 lambda。
    // 延迟初始化（lateinit 赋值）发生在 bluetoothPermissionManager = createBluetoothPermissionManager { ... } 这一行，在 requestBluetoothPermissions() 调用之前。
    // requestBluetoothPermissions() 不是初始化 bluetoothPermissionManager 的触发条件，而是使用这个已初始化对象的方法。
    private lateinit var bluetoothPermissionManager: BluetoothPermissionManager

    /** 重写 onCreate 方法
    override 关键字表示这是对父类方法的重写
    参数 savedInstanceState 是一个 `Bundle?`（可空的 Bundle 对象）。
    作用：当 Activity 因系统资源不足（如屏幕旋转、后台被杀）被销毁并重建时，系统会将之前保存的状态数据打包成 Bundle，通过这个参数传递回来。*/
    override fun onCreate(savedInstanceState: Bundle?) {
        // 调用父类的 onCreate 方法，完成基础的初始化工作，也就是通用的共有的onCreate执行时的操作
        super.onCreate(savedInstanceState)

        // ========== 蓝牙权限管理初始化 ==========
        // 创建蓝牙权限管理器实例
        // 使用扩展函数 createBluetoothPermissionManager 简化创建过程

        // 下面的这段代码返回值是一个 BluetoothPermissionManager 实例，
        // 并将其赋值给前面声明的 bluetoothPermissionManager 属性。
        // createBluetoothPermissionManager 函数接收一个 lambda 作为参数，最终其实就是 onPermissionResult(allGranted) 的具体值传递给了我们的 lambda

        //    createBluetoothPermissionManager 是扩展函数，返回 BluetoothPermissionManager(this, onPermissionResult)。
        //    这行代码就是赋值初始化，完成 lateinit 变量的首次（也是唯一必需的）赋值。
        bluetoothPermissionManager = createBluetoothPermissionManager { allGranted ->
            // 权限请求结果回调
            if (allGranted) {
                // ✅ 所有蓝牙权限已授予
                android.util.Log.d("MainActivity", "蓝牙权限已全部授予，可以开始蓝牙相关操作")
                // TODO: 在这里可以初始化蓝牙相关功能，例如：
                // - 初始化蓝牙适配器
                // - 开始扫描蓝牙设备
                // - 连接已配对的设备
            } else {
                // ❌ 有权限被拒绝
                android.util.Log.w("MainActivity", "蓝牙权限被拒绝，部分功能可能无法使用")
                // 可以在这里显示提示信息，告知用户为什么需要这些权限
                // 获取缺少的权限列表
                val missingPermissions = bluetoothPermissionManager.getMissingPermissions()
                android.util.Log.w(
                    "MainActivity",
                    "缺少的权限: ${missingPermissions.joinToString()}"
                )

                // TODO: 可以显示一个对话框或 Snackbar 提示用户
            }
        }

        // 检查设备是否支持蓝牙
        if (!bluetoothPermissionManager.isBluetoothSupported()) {
            android.util.Log.e("MainActivity", "设备不支持蓝牙功能")
            // TODO: 显示错误提示，禁用蓝牙相关功能
        } else {
            // 请求蓝牙权限
            // 注意：这会弹出系统权限请求对话框（如果权限未授予）
            bluetoothPermissionManager.requestBluetoothPermissions()
        }
        // ========== 蓝牙权限管理初始化结束 ==========

        // 启用边缘到边缘显示： androidx.activity:activity-compose 库中的扩展函数。
        //
        // 作用：启用 “边缘到边缘”（Edge-to-Edge）显示模式。
        //
        //    让 App 内容 延伸到状态栏（Status Bar）和导航栏（Navigation Bar）背后，实现全面屏视觉效果。
        //    但内容不会被遮挡，因为系统会通过 WindowInsets 提供安全区域内边距（innerPadding）。
        //
        // 效果：状态栏和导航栏变为半透明，UI 填满整个屏幕，更现代、沉浸。
        // 注意：必须配合 Scaffold 或手动处理 WindowInsets，否则文字可能被遮挡。
        enableEdgeToEdge()

        // 使用 mutableStateOf 创建响应式状态，当值变化时会自动触发 UI 重组
        // 这是 Compose 推荐的最佳实践，避免重复调用 setContent
        var result by mutableStateOf("正在加载...")

        // 启动协程获取数据
        lifecycleScope.launch {
            result = sendOkHttpRequest()
        }

        // 设置 Compose UI 内容（只调用一次）
        // 后续 UI 更新通过 State 自动重组实现
        setContent {
            BlnavTheme {
                MainScreen(
                    networkResult = result,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // suspend表示这是一个挂起函数，可以在协程中调用，用于执行异步操作而不阻塞线程。
    // 挂起函数只能在协程或其他挂起函数中调用；
    // 被调用时，可能暂停（suspend）当前协程，等结果回来后再恢复（resume）。
    // 函数后面的 : String 表示该函数返回一个字符串类型的结果。

    // 对于后面的 withContext(Dispatchers.IO) { ... } 块：
    // 这是 Kotlin 的 “单表达式函数” 写法（等价于 { return ... }）。
    //
    //    withContext(Dispatchers.IO)
    //        是 kotlinx.coroutines 提供的协程构建器；
    //        作用：临时切换协程上下文（线程），在此块中运行代码；
    //        Dispatchers.IO 是专为 I/O 密集型操作（如网络、文件读写）优化的调度器；
    //            它使用共享的后台线程池，避免阻塞主线程（UI thread）；
    //            适用于 OkHttp、数据库、文件等操作。

    // 这是一个 lambda 表达式块，块内的最后一个表达式的值会作为 withContext 的返回值。
    private suspend fun sendOkHttpRequest(): String = withContext(Dispatchers.IO) {
        // 创建 OkHttpClient 实例
        val client = OkHttpClient()
        // 配置 Request 对象的部分属性，然后调用 build 生成目标 Request 实例
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .build()

        try {
            // newCall 创建一个Call对象，表示一个可执行的HTTP请求
            // execute 方法同步执行请求，返回响应 Response 对象，这会阻塞当前线程直到响应返回
            val response = client.newCall(request).execute()
            // 检测状态码是否是2xx成功范围
            if (response.isSuccessful) {
                // ?.语法是 Kotlin 的安全调用操作符，表示如果 body 不为 null，则调用 string() 方法，否则返回 null
                response.body?.string() ?: "Empty response body"
            } else {
                "Request failed with code: ${response.code}"
            }
        } catch (e: IOException) {
            "Network error: ${e.message}"
        }
    }
}
// @Composable 是一个 Kotlin 编译器插件注解。
//  它标记一个函数为 “可组合函数”，意味着：
//    该函数可以 描述 UI。
//    可以 调用其他 @Composable 函数（如 Text、Column 等）。
//    会被 Compose 编译器特殊处理（生成重组逻辑、状态跟踪等）。
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name, modifier = modifier
    )
}

// @Preview 是 Android Studio 的 Compose 预览注解。
// 作用：允许在 IDE 中预览 Composable 函数的 UI，而无需运行整个应用。
// 参数 showBackground = true 表示在预览时显示背景色，便于查看
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BlnavTheme {
        Greeting("Preview")
    }
}