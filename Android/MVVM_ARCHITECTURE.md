# MVVM 架构实施文档

## 📋 概述

本项目严格遵循 **MVVM（Model-View-ViewModel）架构模式**，确保代码的可维护性、可测试性和关注点分离。

## 🏗️ 架构层次

```
┌─────────────────────────────────────────────────────────┐
│                      View Layer                         │
│  (UI Components - Jetpack Compose)                      │
│  - HomeScreen.kt                                        │
│  - MapScreen.kt                                         │
│  - SettingsScreen.kt                                    │
│  - DraggableMapView.kt                                  │
└─────────────────────────────────────────────────────────┘
                          ↓ 只能访问
┌─────────────────────────────────────────────────────────┐
│                   ViewModel Layer                       │
│  (Business Logic & State Management)                    │
│  - BluetoothViewModel.kt                                │
│  - MapViewModel.kt                                      │
└─────────────────────────────────────────────────────────┘
                          ↓ 只能访问
┌─────────────────────────────────────────────────────────┐
│                  Repository Layer                       │
│  (Data Coordination)                                    │
│  - BluetoothRepository.kt                               │
│  - BluetoothFilterRepository.kt                         │
└─────────────────────────────────────────────────────────┘
                          ↓ 只能访问
┌─────────────────────────────────────────────────────────┐
│                  DataSource Layer                       │
│  (Data Access & Management)                             │
│  - BluetoothLocalDataSource.kt                          │
│  - BluetoothDeviceManagerDataSource.kt                  │
│  - BluetoothFilterLocalDataSource.kt                    │
└─────────────────────────────────────────────────────────┘
```

## ✅ MVVM 原则

### 1. 单向数据流

```kotlin
DataSource -> Repository -> ViewModel -> View
```

### 2. 禁止跨层访问

❌ **错误示例：View直接访问DataSource**
```kotlin
// HomeScreen.kt - 错误！
val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
val devices by deviceManager.managedDevices.collectAsState()
```

✅ **正确示例：View通过ViewModel访问数据**
```kotlin
// HomeScreen.kt - 正确！
val viewModel = remember { BluetoothViewModel(context) }
val devices by viewModel.managedDevices.collectAsState()
```

### 3. 职责分离

| 层次 | 职责 | 禁止事项 |
|------|------|---------|
| **View** | UI渲染、用户交互 | 不能有业务逻辑、不能直接访问Repository/DataSource |
| **ViewModel** | 业务逻辑、状态管理 | 不能持有View引用、不能直接访问DataSource |
| **Repository** | 数据协调、缓存策略 | 不能包含业务逻辑 |
| **DataSource** | 数据获取、持久化 | 不能包含业务逻辑 |

## 📝 实施案例

### 案例1：HomeScreen（蓝牙设备列表）

**数据流向：**
```
BluetoothLocalDataSource（扫描+过滤）
    ↓
BluetoothDeviceManagerDataSource（缓冲+去重）
    ↓
BluetoothRepository.managedDevices
    ↓
BluetoothViewModel.managedDevices
    ↓
HomeScreen订阅并显示
```

**代码实现：**

```kotlin
// HomeScreen.kt (View层)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    // ✅ 通过ViewModel获取数据
    val viewModel = remember { BluetoothViewModel(LocalContext.current) }
    val devices by viewModel.managedDevices.collectAsState()
    
    // UI渲染...
}

// BluetoothViewModel.kt (ViewModel层)
class BluetoothViewModel(context: Context) : ViewModel() {
    private val bluetoothRepository = BluetoothRepository(...)
    
    // ✅ 暴露Repository的数据给View
    val managedDevices: StateFlow<List<BluetoothDeviceModel>> = 
        bluetoothRepository.managedDevices
}

// BluetoothRepository.kt (Repository层)
class BluetoothRepository(...) {
    private val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
    
    // ✅ 暴露DataSource的数据给ViewModel
    val managedDevices: StateFlow<List<BluetoothDeviceModel>>
        get() = deviceManager.managedDevices
}
```

### 案例2：MapScreen（室内地图）

**数据流向：**
```
BluetoothDeviceManagerDataSource
    ↓
BluetoothRepository.managedDevices
    ↓
MapViewModel（转换为地图坐标）
    ↓
MapScreen显示地图点
```

**代码实现：**

```kotlin
// MapScreen.kt (View层)
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    // ✅ 通过MapViewModel获取地图数据
    val viewModel = remember { MapViewModel(LocalContext.current) }
    val mapPoints by viewModel.mapPoints.collectAsState()
    
    DraggableMapView(
        svgFileName = "indoor_map.svg",
        points = mapPoints
    )
}

// MapViewModel.kt (ViewModel层)
class MapViewModel(context: Context) : ViewModel() {
    private val bluetoothRepository = BluetoothRepository(...)
    
    private val _mapPoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val mapPoints: StateFlow<List<MapPoint>> = _mapPoints.asStateFlow()
    
    init {
        // ✅ 将蓝牙设备数据转换为地图坐标
        viewModelScope.launch {
            bluetoothRepository.managedDevices.collect { devices ->
                _mapPoints.value = devices.map { device ->
                    MapPoint(x = ..., y = ..., label = device.name)
                }
            }
        }
    }
}
```

## 🔍 架构验证清单

使用以下清单验证代码是否遵循MVVM架构：

- [ ] View层没有直接访问Repository
- [ ] View层没有直接访问DataSource
- [ ] View层没有直接调用`getInstance()`获取单例
- [ ] ViewModel没有持有View/Context的强引用
- [ ] ViewModel通过Repository访问数据
- [ ] Repository协调多个DataSource
- [ ] DataSource只负责数据获取，没有业务逻辑
- [ ] 数据流是单向的（DataSource -> Repository -> ViewModel -> View）

## 🚫 常见错误

### 错误1：View直接访问DataSource

```kotlin
// ❌ 错误
val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
val devices by deviceManager.managedDevices.collectAsState()

// ✅ 正确
val viewModel = remember { BluetoothViewModel(context) }
val devices by viewModel.managedDevices.collectAsState()
```

### 错误2：ViewModel直接访问DataSource

```kotlin
// ❌ 错误
class MyViewModel : ViewModel() {
    private val dataSource = BluetoothLocalDataSource(context)
}

// ✅ 正确
class MyViewModel(context: Context) : ViewModel() {
    private val repository = BluetoothRepository(...)
}
```

### 错误3：Repository包含业务逻辑

```kotlin
// ❌ 错误 - Repository不应该有复杂的业务逻辑
class BluetoothRepository {
    fun calculateUserPosition(devices: List<Device>): Position {
        // 复杂的定位算法...
    }
}

// ✅ 正确 - 业务逻辑应该在ViewModel中
class MapViewModel : ViewModel() {
    fun calculateUserPosition(devices: List<Device>): Position {
        // 复杂的定位算法...
    }
}
```

## 📚 相关文档

- [Jetpack Compose Architecture Guide](https://developer.android.com/jetpack/compose/architecture)
- [Guide to app architecture](https://developer.android.com/topic/architecture)
- [ViewModel Overview](https://developer.android.com/topic/libraries/architecture/viewmodel)

## 🔄 数据流示例图

```
用户操作（点击扫描按钮）
    ↓
View调用ViewModel方法
    ↓
ViewModel调用Repository方法
    ↓
Repository调用DataSource方法
    ↓
DataSource执行蓝牙扫描
    ↓
DataSource更新StateFlow
    ↓
Repository暴露StateFlow
    ↓
ViewModel暴露StateFlow
    ↓
View订阅StateFlow并更新UI
```

## ✨ 优势

1. **可测试性**：每层可以独立测试
2. **可维护性**：职责清晰，易于修改
3. **可扩展性**：易于添加新功能
4. **解耦合**：各层之间松耦合
5. **生命周期安全**：ViewModel感知生命周期，避免内存泄漏

## 📌 总结

严格遵循MVVM架构能够：
- ✅ 提升代码质量
- ✅ 简化测试流程
- ✅ 提高团队协作效率
- ✅ 降低维护成本
- ✅ 避免常见的架构陷阱

**记住：View -> ViewModel -> Repository -> DataSource，永远不要跨层访问！**

