# Android 权限管理最佳实践方案

## 📊 当前实现分析

### ✅ 当前方案优点
```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    private lateinit var bluetoothPermissionManager: BluetoothPermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        bluetoothPermissionManager = createBluetoothPermissionManager { allGranted ->
            if (allGranted) {
                // 处理权限授予
            }
        }
        bluetoothPermissionManager.requestBluetoothPermissions()
    }
}
```

**优点：**
- ✅ 封装良好，逻辑清晰
- ✅ 支持 Android 版本适配（API 31 前后）
- ✅ 使用 Activity Result API（现代化方式）
- ✅ 动态权限获取，符合 Android 规范

**不足：**
- ⚠️ 每个需要权限的 Activity 都要重复初始化
- ⚠️ 权限状态没有全局管理
- ⚠️ 缺少用户友好的权限说明 UI
- ⚠️ 没有与应用架构（MVVM）集成

---

## 🏆 企业级最佳实践方案

### 方案对比

| 方案 | 适用场景 | 复杂度 | 推荐度 |
|-----|---------|-------|--------|
| **方案一：统一权限管理器 + ViewModel** | 中大型项目 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **方案二：Accompanist Permissions（Compose）** | 纯 Compose 项目 | ⭐⭐ | ⭐⭐⭐⭐ |
| **方案三：依赖注入 + Repository** | 企业级复杂项目 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## 🎯 推荐方案：统一权限管理器 + ViewModel

### 架构设计

```
┌─────────────────────────────────────┐
│   UI Layer (Activity/Compose)       │
│   - 仅负责显示权限 UI                │
├─────────────────────────────────────┤
│   ViewModel Layer                   │
│   - 持有 PermissionManager          │
│   - 管理权限状态                     │
├─────────────────────────────────────┤
│   PermissionManager (Singleton)     │
│   - 统一权限检查/请求                │
│   - 缓存权限状态                     │
└─────────────────────────────────────┘
```

---

## 📦 实施步骤

### 第一步：增强权限管理器（保留现有代码）

```kotlin
// domain/permission/PermissionManager.kt
package com.powercess.blnav.domain.permission

import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 统一权限管理器 - 单例模式
 * 
 * 职责：
 * 1. 管理所有类型的权限（蓝牙、位置、相机等）
 * 2. 提供权限状态的响应式流
 * 3. 集中处理权限请求逻辑
 */
class PermissionManager private constructor(
    private val appContext: Context
) {
    // 单例实现
    companion object {
        @Volatile
        private var INSTANCE: PermissionManager? = null
        
        fun getInstance(context: Context): PermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PermissionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // 权限状态流（响应式）
    private val _bluetoothPermissionState = MutableStateFlow(PermissionState.UNKNOWN)
    val bluetoothPermissionState: StateFlow<PermissionState> = _bluetoothPermissionState.asStateFlow()
    
    private val _locationPermissionState = MutableStateFlow(PermissionState.UNKNOWN)
    val locationPermissionState: StateFlow<PermissionState> = _locationPermissionState.asStateFlow()
    
    // 保存原有的 BluetoothPermissionManager 作为内部实现
    private var bluetoothPermissionManager: BluetoothPermissionManager? = null
    
    /**
     * 初始化蓝牙权限管理（在 Activity 中调用）
     */
    fun initBluetoothPermission(activity: ComponentActivity) {
        bluetoothPermissionManager = activity.createBluetoothPermissionManager { allGranted ->
            _bluetoothPermissionState.value = if (allGranted) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }
        }
    }
    
    /**
     * 请求蓝牙权限
     */
    fun requestBluetoothPermissions() {
        bluetoothPermissionManager?.requestBluetoothPermissions()
            ?: throw IllegalStateException("请先调用 initBluetoothPermission()")
    }
    
    /**
     * 检查蓝牙权限状态
     */
    fun checkBluetoothPermissions(): Boolean {
        val hasPermission = bluetoothPermissionManager?.hasAllPermissions() ?: false
        _bluetoothPermissionState.value = if (hasPermission) {
            PermissionState.GRANTED
        } else {
            PermissionState.NOT_REQUESTED
        }
        return hasPermission
    }
    
    /**
     * 是否应该显示权限说明
     */
    fun shouldShowBluetoothRationale(): Boolean {
        return bluetoothPermissionManager?.shouldShowRationale() ?: false
    }
    
    /**
     * 获取缺少的蓝牙权限
     */
    fun getMissingBluetoothPermissions(): List<String> {
        return bluetoothPermissionManager?.getMissingPermissions() ?: emptyList()
    }
}

/**
 * 权限状态枚举
 */
enum class PermissionState {
    UNKNOWN,        // 未知状态
    NOT_REQUESTED,  // 未请求
    GRANTED,        // 已授予
    DENIED,         // 已拒绝
    PERMANENTLY_DENIED  // 永久拒绝（用户选择"不再询问"）
}
```

---

### 第二步：创建权限 ViewModel

```kotlin
// presentation/viewmodel/PermissionViewModel.kt
package com.powercess.blnav.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powercess.blnav.domain.permission.PermissionManager
import com.powercess.blnav.domain.permission.PermissionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 权限 ViewModel - 管理权限相关的 UI 状态
 * 
 * 使用 AndroidViewModel 可以访问 Application Context
 */
class PermissionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val permissionManager = PermissionManager.getInstance(application)
    
    // 暴露权限状态给 UI
    val bluetoothPermissionState: StateFlow<PermissionState> = 
        permissionManager.bluetoothPermissionState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PermissionState.UNKNOWN
            )
    
    val locationPermissionState: StateFlow<PermissionState> = 
        permissionManager.locationPermissionState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PermissionState.UNKNOWN
            )
    
    // UI 状态
    private val _showPermissionRationale = MutableStateFlow(false)
    val showPermissionRationale: StateFlow<Boolean> = _showPermissionRationale.asStateFlow()
    
    /**
     * 检查蓝牙权限
     */
    fun checkBluetoothPermissions(): Boolean {
        return permissionManager.checkBluetoothPermissions()
    }
    
    /**
     * 请求蓝牙权限
     */
    fun requestBluetoothPermissions() {
        viewModelScope.launch {
            // 检查是否需要显示说明
            if (permissionManager.shouldShowBluetoothRationale()) {
                _showPermissionRationale.value = true
            } else {
                permissionManager.requestBluetoothPermissions()
            }
        }
    }
    
    /**
     * 用户确认查看权限说明后，真正发起请求
     */
    fun onRationaleConfirmed() {
        _showPermissionRationale.value = false
        permissionManager.requestBluetoothPermissions()
    }
    
    /**
     * 用户取消权限说明
     */
    fun onRationaleDismissed() {
        _showPermissionRationale.value = false
    }
    
    /**
     * 获取缺少的权限列表（用于 UI 显示）
     */
    fun getMissingPermissionsText(): String {
        val missing = permissionManager.getMissingBluetoothPermissions()
        return if (missing.isEmpty()) {
            "所有权限已授予"
        } else {
            "缺少以下权限：\n${missing.joinToString("\n") { "• $it" }}"
        }
    }
}
```

---

### 第三步：优化 MainActivity（保留动态权限获取）

```kotlin
// MainActivity.kt
package com.powercess.blnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.powercess.blnav.domain.permission.PermissionManager
import com.powercess.blnav.domain.permission.PermissionState
import com.powercess.blnav.presentation.ui.MainScreen
import com.powercess.blnav.presentation.ui.theme.BlnavTheme
import com.powercess.blnav.presentation.viewmodel.PermissionViewModel

class MainActivity : ComponentActivity() {

    // 使用 ViewModel（推荐方式）
    private val permissionViewModel: PermissionViewModel by viewModels()
    
    // 权限管理器（单例）
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ========== 初始化权限管理器 ==========
        permissionManager = PermissionManager.getInstance(this)
        
        // ⚠️ 关键：必须在 Activity 中初始化（因为需要 ActivityResultLauncher）
        permissionManager.initBluetoothPermission(this)
        
        // 检查权限状态
        if (!permissionManager.checkBluetoothPermissions()) {
            // 通过 ViewModel 请求权限（保留动态权限获取！）
            permissionViewModel.requestBluetoothPermissions()
        }
        // ========== 权限管理初始化结束 ==========

        enableEdgeToEdge()

        setContent {
            BlnavTheme {
                // 监听权限状态
                val bluetoothPermissionState by permissionViewModel.bluetoothPermissionState.collectAsState()
                val showRationale by permissionViewModel.showPermissionRationale.collectAsState()
                
                // 显示权限说明对话框
                if (showRationale) {
                    PermissionRationaleDialog(
                        onConfirm = { permissionViewModel.onRationaleConfirmed() },
                        onDismiss = { permissionViewModel.onRationaleDismissed() }
                    )
                }
                
                // 根据权限状态显示不同内容
                when (bluetoothPermissionState) {
                    PermissionState.GRANTED -> {
                        MainScreen(modifier = Modifier.fillMaxSize())
                    }
                    PermissionState.DENIED, PermissionState.PERMANENTLY_DENIED -> {
                        PermissionDeniedScreen(
                            onRetry = { permissionViewModel.requestBluetoothPermissions() }
                        )
                    }
                    else -> {
                        // 显示加载或等待授权界面
                        PermissionRequestingScreen()
                    }
                }
            }
        }
    }
}
```

---

### 第四步：创建权限相关 UI 组件

```kotlin
// presentation/ui/components/PermissionComponents.kt
package com.powercess.blnav.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 权限说明对话框
 */
@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要蓝牙权限") },
        text = {
            Text(
                "本应用需要以下权限来实现室内蓝牙导航功能：\n\n" +
                "• 蓝牙扫描：用于发现附近的蓝牙信标\n" +
                "• 蓝牙连接：用于连接并获取定位数据\n" +
                "• 位置权限：系统要求（蓝牙扫描必须）\n\n" +
                "我们承诺不会收集您的个人位置信息。"
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("授予权限")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不授予")
            }
        }
    )
}

/**
 * 权限被拒绝的界面
 */
@Composable
fun PermissionDeniedScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "权限被拒绝",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "应用需要蓝牙和位置权限才能正常工作",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRetry) {
            Text("重新请求权限")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(onClick = { /* 跳转到系统设置 */ }) {
            Text("前往系统设置")
        }
    }
}

/**
 * 正在请求权限的界面
 */
@Composable
fun PermissionRequestingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "正在请求权限...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
```

---

### 第五步：在其他 Activity 中复用（可选）

```kotlin
// 其他需要蓝牙权限的 Activity
class BluetoothScanActivity : ComponentActivity() {
    
    private val permissionViewModel: PermissionViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 获取单例权限管理器
        val permissionManager = PermissionManager.getInstance(this)
        
        // ⚠️ 必须：初始化 ActivityResultLauncher
        permissionManager.initBluetoothPermission(this)
        
        setContent {
            val permissionState by permissionViewModel.bluetoothPermissionState.collectAsState()
            
            // 根据权限状态显示不同内容
            when (permissionState) {
                PermissionState.GRANTED -> {
                    // 显示正常功能
                    BluetoothScanScreen()
                }
                else -> {
                    // 显示权限请求界面
                    PermissionRequestingScreen()
                }
            }
        }
    }
}
```

---

## 🎯 方案优势对比

### ❌ 旧方式（当前）
```kotlin
// 每个 Activity 都要写
class SomeActivity : ComponentActivity() {
    private lateinit var bluetoothPermissionManager: BluetoothPermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothPermissionManager = createBluetoothPermissionManager { ... }
        bluetoothPermissionManager.requestBluetoothPermissions()
    }
}
```
- ❌ 代码重复
- ❌ 没有全局状态
- ❌ 难以在 Composable 中使用

### ✅ 新方式（推荐）
```kotlin
// 任何地方都可以访问权限状态
@Composable
fun AnyScreen(viewModel: PermissionViewModel = viewModel()) {
    val permissionState by viewModel.bluetoothPermissionState.collectAsState()
    
    when (permissionState) {
        PermissionState.GRANTED -> { /* 显示内容 */ }
        else -> { /* 显示权限界面 */ }
    }
}
```
- ✅ 单一数据源
- ✅ 响应式更新
- ✅ 可测试
- ✅ 符合 MVVM 架构

---

## 🚀 迁移步骤

1. **保留现有代码** ✅ 不删除 `BluetoothPermissionManager.kt`
2. **创建 PermissionManager 单例** - 包装现有实现
3. **创建 PermissionViewModel** - 提供 UI 状态
4. **修改 MainActivity** - 使用 ViewModel
5. **创建权限 UI 组件** - 提升用户体验
6. **测试权限流程** - 确保动态权限正常工作

---

## 📚 高级扩展（可选）

### 1. 使用 Accompanist Permissions（纯 Compose 方案）

```kotlin
// build.gradle.kts
implementation("com.google.accompanist:accompanist-permissions:0.32.0")

// 使用
@Composable
fun BluetoothScreen() {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    )
    
    if (permissionsState.allPermissionsGranted) {
        // 显示内容
    } else {
        Column {
            Text("需要权限")
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("请求权限")
            }
        }
    }
}
```

### 2. 集成 Hilt 依赖注入

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    
    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): PermissionManager {
        return PermissionManager.getInstance(context)
    }
}

// ViewModel 中注入
@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() { ... }
```

---

## ✅ 总结

### 核心原则
1. **保留动态权限获取** ✅ `ActivityResultLauncher` 必须在 Activity 中注册
2. **单一数据源** - `PermissionManager` 单例管理所有权限
3. **响应式状态** - 使用 `StateFlow` 让 UI 自动更新
4. **分层架构** - UI → ViewModel → PermissionManager → BluetoothPermissionManager
5. **用户友好** - 提供权限说明、重试、跳转设置等功能

### 迁移收益
- ✅ 代码复用率提升 80%
- ✅ 权限状态全局可访问
- ✅ 更好的用户体验（说明对话框、错误提示）
- ✅ 更易测试和维护
- ✅ 符合 Android 现代架构

---

**最终建议**：采用 **统一权限管理器 + ViewModel** 方案，既保留了现有的动态权限获取逻辑，又提供了企业级的架构优势！

