@file:OptIn(ExperimentalMaterial3Api::class)

package com.powercess.blnav.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

    val filterRules by (filterRulesFlow?.collectAsState(emptyList()) ?: remember {
        mutableStateOf(
            emptyList()
        )
    })

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
            // 过滤规则统计卡片
            FilterStatisticsCard(filterRules = filterRules)

            Spacer(modifier = Modifier.height(16.dp))

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
                            onToggleEnabled = { newState ->
                                viewModel?.updateFilterRule(
                                    filter.copy(isEnabled = newState)
                                )
                            },
                            onEdit = {
                                // 将过滤规则传递给编辑对话框
                                viewModel?.updateFilterRule(it)
                            },
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
 *
 * 显示单个过滤规则的详细信息，包括：
 * - 规则别名、匹配类型、过滤类型等标签
 * - 规则的详细内容和描述
 * - 启用/禁用、编辑和删除操作按钮
 *
 * 提供三个操作时的确认或编辑对话框：
 * 1. 切换启用状态时显示确认提示
 * 2. 编辑规则时显示编辑对话框
 * 3. 删除规则时显示确认提示
 */
@Composable
private fun FilterRuleCard(
    filter: BluetoothFilterModel,
    onToggleEnabled: (Boolean) -> Unit = {},
    onEdit: (BluetoothFilterModel) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    // 用于控制状态切换确认对话框的显示
    var showToggleConfirmDialog by remember { mutableStateOf(false) }
    var pendingToggleState by remember { mutableStateOf(false) }

    // 用于控制编辑对话框的显示
    var showEditDialog by remember { mutableStateOf(false) }

    // 用于控制删除确认对话框的显示
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
            // 规则标题和操作按钮
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 显示匹配类型标签
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (filter.matchType == BluetoothFilterModel.MatchType.DEVICE_NAME) "设备名" else "MAC",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(4.dp, 2.dp)
                            )
                        }

                        // 显示正则表达式标签
                        if (filter.enableRegex) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "正则",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(4.dp, 2.dp)
                                )
                            }
                        }

                        // 显示过滤类型
                        Text(
                            text = if (filter.filterType == BluetoothFilterModel.FilterType.WHITELIST) "白名单" else "黑名单",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 操作按钮区域 - 编辑和删除
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    // 编辑按钮
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // 删除按钮
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
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
            }

            // 规则详情
            Text(
                text = "规则: ${filter.filterRule}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 描述
            if (filter.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = filter.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 启用状态指示器和切换按钮
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 可交互的Checkbox - 点击时弹出确认对话框
                Checkbox(
                    checked = filter.isEnabled,
                    onCheckedChange = { newState ->
                        pendingToggleState = newState
                        showToggleConfirmDialog = true
                    },
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
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "创建于: ${
                        android.text.format.DateFormat.format(
                            "MM-dd HH:mm",
                            filter.createTime
                        )
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 切换启用/禁用状态时的确认对话框
    if (showToggleConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showToggleConfirmDialog = false },
            title = {
                Text(
                    if (pendingToggleState) "启用规则？" else "禁用规则？"
                )
            },
            text = {
                Text(
                    if (pendingToggleState)
                        "确认要启用规则 \"${filter.alias}\" 吗？启用后此规则将立即生效。"
                    else
                        "确认要禁用规则 \"${filter.alias}\" 吗？禁用后此规则将不再生效。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showToggleConfirmDialog = false
                        onToggleEnabled(pendingToggleState)
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                Button(onClick = { showToggleConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑规则时的对话框
    if (showEditDialog) {
        EditFilterRuleDialog(
            filter = filter,
            onDismiss = { showEditDialog = false },
            onSave = { editedFilter ->
                showEditDialog = false
                onEdit(editedFilter)
            }
        )
    }

    // 删除规则时的确认对话框
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text("删除规则？")
            },
            text = {
                Text(
                    "确认要删除规则 \"${filter.alias}\" 吗？此操作不可撤销，删除后此规则将被永久移除。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 编辑过滤规则对话框
 *
 * 用于编辑现有的过滤规则。支持修改规则的所有属性：
 * - 别名、规则内容、描述
 * - 匹配类型（设备名/MAC地址）
 * - 过滤类型（白名单/黑名单）
 * - 正则表达式开关
 *
 * 注意：规则的ID和创建时间保持不变，只更新updateTime
 */
@Composable
private fun EditFilterRuleDialog(
    filter: BluetoothFilterModel,
    onDismiss: () -> Unit,
    onSave: (BluetoothFilterModel) -> Unit
) {
    var alias by remember { mutableStateOf(filter.alias) }
    var filterRule by remember { mutableStateOf(filter.filterRule) }
    var enableRegex by remember { mutableStateOf(filter.enableRegex) }
    var filterType by remember { mutableStateOf(filter.filterType) }
    var description by remember { mutableStateOf(filter.description) }
    var matchType by remember { mutableStateOf(filter.matchType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("编辑过滤规则")
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
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

                    // 匹配类型选择
                    Text(
                        text = "匹配类型",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BluetoothFilterModel.MatchType.DEVICE_NAME to "设备名称",
                            BluetoothFilterModel.MatchType.MAC_ADDRESS to "MAC地址"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = matchType == type,
                                onClick = { matchType = type },
                                label = { Text(label) }
                            )
                        }
                    }

                    // 规则输入（根据匹配类型显示提示信息）
                    OutlinedTextField(
                        value = filterRule,
                        onValueChange = { filterRule = it },
                        label = {
                            Text(
                                when (matchType) {
                                    BluetoothFilterModel.MatchType.DEVICE_NAME -> "设备名称或模式"
                                    BluetoothFilterModel.MatchType.MAC_ADDRESS -> "MAC地址 (XX:XX:XX:XX:XX:XX)"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // 正则表达式开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                    Text(
                        text = "过滤类型",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                        val editedFilter = filter.copy(
                            alias = alias,
                            filterRule = filterRule,
                            matchType = matchType,
                            enableRegex = enableRegex,
                            filterType = filterType,
                            description = description,
                            updateTime = System.currentTimeMillis()
                        )
                        onSave(editedFilter)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
    var matchType by remember { mutableStateOf(BluetoothFilterModel.MatchType.DEVICE_NAME) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("添加过滤规则")
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
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

                    // 匹配类型选择
                    Text(
                        text = "匹配类型",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            BluetoothFilterModel.MatchType.DEVICE_NAME to "设备名称",
                            BluetoothFilterModel.MatchType.MAC_ADDRESS to "MAC地址"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = matchType == type,
                                onClick = { matchType = type },
                                label = { Text(label) }
                            )
                        }
                    }

                    // 规则输入（根据匹配类型显示提示信息）
                    OutlinedTextField(
                        value = filterRule,
                        onValueChange = { filterRule = it },
                        label = {
                            Text(
                                when (matchType) {
                                    BluetoothFilterModel.MatchType.DEVICE_NAME -> "设备名称或模式"
                                    BluetoothFilterModel.MatchType.MAC_ADDRESS -> "MAC地址 (XX:XX:XX:XX:XX:XX)"
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    // 正则表达式开关
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                    Text(
                        text = "过滤类型",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
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
                            matchType = matchType,
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

/**
 * 过滤规则统计卡片
 */
@Composable
private fun FilterStatisticsCard(filterRules: List<BluetoothFilterModel>) {
    val enabledCount = filterRules.count { it.isEnabled }
    val whitelistCount =
        filterRules.count { it.filterType == BluetoothFilterModel.FilterType.WHITELIST }
    val blacklistCount =
        filterRules.count { it.filterType == BluetoothFilterModel.FilterType.BLACKLIST }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatisticItem(label = "总规则数", value = filterRules.size.toString())
            StatisticItem(label = "已启用", value = enabledCount.toString())
            StatisticItem(label = "白名单", value = whitelistCount.toString())
            StatisticItem(label = "黑名单", value = blacklistCount.toString())
        }
    }
}

/**
 * 统计项目
 */
@Composable
private fun RowScope.StatisticItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
