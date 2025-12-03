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

/**
 * 首页 - 蓝牙设备管理页面
 * 显示网络请求结果和蓝牙设备信息
 */
@Composable
fun HomeScreen(
    networkResult: String,
    modifier: Modifier = Modifier
) {
    // 获取上下文用于ViewModel创建
    val context = LocalContext.current

    // 创建或获取ViewModel实例
    val bluetoothViewModel = remember { BluetoothViewModel(context) }

    // 订阅扫描状态、设备列表和错误信息
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val discoveredDevices by bluetoothViewModel.discoveredDevices.collectAsState()
    val errorMessage by bluetoothViewModel.errorMessage.collectAsState()

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
                Text(
                    text = "蓝牙设备列表 (${discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (discoveredDevices.isEmpty()) {
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
                    // 使用 LazyColumn 显示设备列表（避免长列表性能问题）
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredDevices) { device ->
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

