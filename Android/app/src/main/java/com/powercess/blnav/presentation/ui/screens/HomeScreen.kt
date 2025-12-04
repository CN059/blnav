package com.powercess.blnav.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.powercess.blnav.data.model.BluetoothDeviceModel
import com.powercess.blnav.presentation.viewmodel.BluetoothViewModel
import com.powercess.blnav.data.datasource.local.BluetoothDeviceManagerDataSource

/**
 * 首页 - 蓝牙设备管理页面
 * 显示网络请求结果和蓝牙设备信息
 *
 * ==================== 数据源说明 ====================
 *
 * 此页面现在使用全局BluetoothDeviceManagerDataSource作为实时设备数据源：
 *
 * 1. 数据特性：
 *    - 已通过过滤规则检查的设备
 *    - 已自动去重（相同MAC地址的设备会更新）
 *    - 按500ms间隔定时更新（缓冲机制）
 *    - 包含完整信息：MAC地址、设备名、RSSI信号强度
 *
 * 2. 工作流程：
 *    a) BluetoothLocalDataSource 扫描蓝牙设备
 *    b) 应用过滤规则检查
 *    c) 自动同步到全局管理器（BluetoothDeviceManagerDataSource）
 *    d) 管理器内部缓冲设备更新
 *    e) 每500ms发布一次设备列表
 *    f) 此页面订阅并实时显示
 *
 * 3. 性能优势：
 *    - UI更新频率受控（最多2次/秒，而不是数百次/秒）
 *    - 缓冲机制避免高频StateFlow更新
 *    - CPU和内存开销大幅降低
 *
 * 4. 数据一致性：
 *    - 所有显示的设备都是已通过当前活跃过滤规则的
 *    - RSSI、设备名、MAC地址都是最新的
 *    - 可直接用于定位、统计、上传等场景
 */
@Composable
fun HomeScreen(
    networkResult: String,
    modifier: Modifier = Modifier
) {
    // 获取上下文用于ViewModel创建
    val context = LocalContext.current

    // ==================== 关键：先创建ViewModel以初始化全局管理器 ====================
    // 创建或获取ViewModel实例（用于扫描控制）
    // 重要：ViewModel的构造函数会初始化BluetoothLocalDataSource，
    // 后者会通过initializeWith()创建并初始化全局BluetoothDeviceManagerDataSource
    // 因此必须先创建ViewModel，再获取管理器实例
    val bluetoothViewModel = remember { BluetoothViewModel(context) }

    // 订阅扫描状态和错误信息（来自ViewModel）
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val errorMessage by bluetoothViewModel.errorMessage.collectAsState()

    // ==================== 现在获取全局设备管理器 ====================
    // 获取全局设备管理器实例（已过滤、按500ms间隔更新的设备数据源）
    // 此时管理器已由ViewModel初始化，getInstance()会返回同一实例
    val deviceManager = remember { BluetoothDeviceManagerDataSource.getInstance() }

    // 订阅全局管理器的设备列表（已过滤、已去重、按策略定时发布）
    // 这个数据源被所有需要蓝牙设备信息的模块共享使用
    // 包含：MAC地址、设备名、RSSI信号强度
    val managedDevices by deviceManager.managedDevices.collectAsState()

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            // ViewModel 会在 Compose 销毁时自动调用 onCleared
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = "蓝牙设备管理",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 网络请求结果卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "网络请求结果",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = networkResult,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 蓝牙扫描控制卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "蓝牙扫描",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 扫描状态显示
                Text(
                    text = if (isScanning) "🔄 扫描中..." else "⏸ 已停止",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isScanning) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 扫描按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { bluetoothViewModel.startScan() },
                        enabled = !isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("开启扫描")
                    }

                    Button(
                        onClick = { bluetoothViewModel.stopScan() },
                        enabled = isScanning,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("停止扫描")
                    }

                    Button(
                        onClick = { bluetoothViewModel.clearDevices() },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF757575)
                        )
                    ) {
                        Text("清除数据")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 错误信息显示
        if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "⚠️ 错误信息",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B0000)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 蓝牙设备列表卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 设备列表标题，显示设备数量和数据源信息
                Text(
                    text = "蓝牙设备列表 (${managedDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                // 数据源说明
                Text(
                    text = "✓ 已过滤 · 实时更新 · 每500ms刷新",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (managedDevices.isEmpty()) {
                    Text(
                        text = "暂无设备\n点击\"开启扫描\"按钮开始扫描蓝牙设备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                } else {
                    // ==================== 使用全局管理器的设备列表 ====================
                    // managedDevices 是从BluetoothDeviceManagerDataSource获取的
                    // 特点：
                    // - 已通过过滤规则检查
                    // - 已去重（相同MAC的设备会更新）
                    // - 按500ms定时发布（避免高频更新）
                    // - 包含最新的RSSI、设备名、MAC地址等信息

                    // 使用 LazyColumn 显示设备列表（避免长列表性能问题）
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(managedDevices) { device ->
                            BluetoothDeviceItem(device)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单个蓝牙设备项
 */
@Composable
fun BluetoothDeviceItem(device: BluetoothDeviceModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 设备名称
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📱",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // MAC 地址
            Text(
                text = "地址: ${device.address}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 信号强度
            Text(
                text = "信号强度: ${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 配对状态
            Text(
                text = "配对状态: " + when (device.bondState) {
                    0 -> "未配对"
                    1 -> "配对中"
                    2 -> "已配对"
                    else -> "未知"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

