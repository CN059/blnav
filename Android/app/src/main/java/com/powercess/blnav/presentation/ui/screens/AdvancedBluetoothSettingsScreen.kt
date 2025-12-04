@file:OptIn(ExperimentalMaterial3Api::class)

package com.powercess.blnav.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powercess.blnav.data.model.BluetoothFilterModel
import com.powercess.blnav.presentation.viewmodel.BluetoothViewModel


/**
 * 高级蓝牙设置页面
 */
@Composable
fun AdvancedBluetoothSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: BluetoothViewModel? = null,
    onNavigateBack: () -> Unit = {}
) {
    var showAddFilterDialog by remember { mutableStateOf(false) }

    val filterRulesFlow by remember {
        derivedStateOf {
            viewModel?.filterRules
        }
    }

    val filterRules by (filterRulesFlow?.collectAsState(emptyList()) ?: remember { mutableStateOf(emptyList()) })

    Column(
        modifier = modifier
            .fillMaxSize()
            .fillMaxHeight()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = {
                Text("高级蓝牙设置")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
            }
        )

        // 内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // 过滤规则标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "蓝牙设备过滤规则",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FloatingActionButton(
                    onClick = { showAddFilterDialog = true },
                    modifier = Modifier.size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加规则",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 过滤规则列表或空状态
            if (filterRules.isEmpty()) {
                EmptyFilterRulesPlaceholder()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterRules) { filter ->
                        FilterRuleCard(
                            filter = filter,
                            onDelete = {
                                viewModel?.deleteFilterRule(filter.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加规则对话框
    if (showAddFilterDialog) {
        AddFilterRuleDialog(
            onDismiss = { showAddFilterDialog = false },
            onAdd = { filter ->
                showAddFilterDialog = false
                viewModel?.addFilterRule(filter)
            }
        )
    }
}

/**
 * 空规则占位符
 */
@Composable
private fun EmptyFilterRulesPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无过滤规则",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击\"＋\"按钮添加蓝牙设备过滤规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 过滤规则卡片
 */
@Composable
private fun FilterRuleCard(
    filter: BluetoothFilterModel,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (filter.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 规则标题和启用状态
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = filter.alias,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "类型: ${if (filter.filterType == BluetoothFilterModel.FilterType.WHITELIST) "白名单" else "黑名单"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 删除按钮
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 规则详情
            Text(
                text = "规则: ${filter.filterRule}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 正则表达式标签
            if (filter.enableRegex) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "正则表达式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp, 2.dp)
                    )
                }
            }

            // 描述
            if (filter.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = filter.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 启用状态指示器
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = filter.isEnabled,
                    onCheckedChange = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (filter.isEnabled) "已启用" else "已禁用",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (filter.isEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 添加过滤规则对话框
 */
@Composable
private fun AddFilterRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (BluetoothFilterModel) -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var filterRule by remember { mutableStateOf("") }
    var enableRegex by remember { mutableStateOf(false) }
    var filterType by remember { mutableStateOf(BluetoothFilterModel.FilterType.WHITELIST) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加过滤规则")
        },
        text = {
            LazyColumn {
                item {
                    // 别名输入
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("规则别名") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // 规则输入
                    OutlinedTextField(
                        value = filterRule,
                        onValueChange = { filterRule = it },
                        label = { Text("过滤规则") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // 正则表达式开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableRegex,
                            onCheckedChange = { enableRegex = it },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "启用正则表达式",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // 规则类型选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BluetoothFilterModel.FilterType.WHITELIST to "白名单",
                            BluetoothFilterModel.FilterType.BLACKLIST to "黑名单"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = filterType == type,
                                onClick = { filterType = type },
                                label = { Text(label) }
                            )
                        }
                    }

                    // 描述输入
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述（可选）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (alias.isNotBlank() && filterRule.isNotBlank()) {
                        val newFilter = BluetoothFilterModel(
                            id = "filter_${System.currentTimeMillis()}",
                            alias = alias,
                            filterRule = filterRule,
                            enableRegex = enableRegex,
                            filterType = filterType,
                            description = description
                        )
                        onAdd(newFilter)
                    }
                }
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

