package com.powercess.blnav.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.powercess.blnav.presentation.ui.components.DraggableMapView
import com.powercess.blnav.presentation.viewmodel.MapViewModel

/**
 * 地图页 - 室内导航地图
 * 用于显示室内地图和导航路径
 *
 * ==================== MVVM架构说明 ====================
 *
 * 遵循严格的MVVM架构：
 * - View层（本文件）：只负责UI渲染，通过ViewModel获取数据
 * - ViewModel层：MapViewModel，管理地图相关业务逻辑
 * - Model/Repository层：通过Repository访问蓝牙设备数据
 *
 * 功能特性：
 * - 显示SVG格式的室内地图
 * - 支持拖动和缩放地图
 * - 在地图上显示定位点（蓝牙信标位置）
 * - 提供虚拟坐标系统（0-1范围）方便定位
 * - 根据蓝牙设备的RSSI信号强度显示位置和颜色
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    // 获取Context用于创建ViewModel
    val context = LocalContext.current

    // 创建MapViewModel（遵循MVVM架构）
    val viewModel = remember(context) { MapViewModel(context) }

    // 订阅ViewModel的状态
    val mapPoints by viewModel.mapPoints.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 地图标题
        Text(
            text = "室内地图",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 错误信息提示
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // SVG地图区域（可拖动和缩放）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            DraggableMapView(
                modifier = Modifier.fillMaxSize(),
                svgFileName = "indoor_map.svg",
                points = buildList {
                    // 添加蓝牙信标点
                    addAll(mapPoints)
                    // 添加当前位置点（如果存在）
                    currentPosition?.let { add(it) }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 导航信息卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "操作说明",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 单指拖动：平移地图\n• 双指捏合：缩放地图\n• 彩色圆点：蓝牙信标位置\n• 蓝色圆点：当前位置",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "检测到 ${mapPoints.size} 个蓝牙信标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

