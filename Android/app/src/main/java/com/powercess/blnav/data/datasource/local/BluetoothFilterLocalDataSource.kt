package com.powercess.blnav.data.datasource.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.powercess.blnav.common.logger.AppLogger
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 蓝牙设备过滤器本地数据源
 *
 * 负责与系统持久化存储的API交互，管理蓝牙设备过滤规则的本地数据
 *
 * ==================== 功能说明 ====================
 *
 * 1. CRUD操作：
 *    - addFilter(filter): 添加新的过滤规则
 *    - updateFilter(filter): 更新已存在的过滤规则
 *    - deleteFilter(filterId): 删除指定ID的过滤规则
 *    - getFilter(filterId): 获取单个过滤规则
 *    - clearAllFilters(): 清空所有过滤规则
 *
 * 2. 查询操作：
 *    - getEnabledFilters(): 获取所有启用的过滤规则
 *    - getFiltersByType(filterType): 按类型获取过滤规则
 *    - filterByDeviceName(deviceName): 按设备名进行过滤
 *    - filterByMacAddress(macAddress): 按MAC地址进行过滤
 *    - shouldFilterDevice(deviceName, macAddress): 检查设备是否应过滤
 *
 * 3. 导入导出：
 *    - importFilters(filters): 批量导入过滤规则
 *    - exportFilters(): 导出所有过滤规则
 *
 * 4. 数据持久化：
 *    - 所有修改都自动持久化到SharedPreferences
 *    - 支持JSON格式的序列化/反序列化
 *    - 应用启动时自动从本地存储加载规则
 *
 * ==================== 白名单和黑名单逻辑 ====================
 *
 * 白名单模式（WHITELIST）：
 *   - 只有匹配白名单规则的设备才被允许
 *   - 不在白名单中的设备将被过滤（阻止）
 *   - 用于只连接指定设备的场景
 *
 * 黑名单模式（BLACKLIST）：
 *   - 匹配黑名单规则的设备将被过滤（阻止）
 *   - 其他设备都被允许连接
 *   - 用于阻止特定设备的场景
 *
 * ==================== 正则表达式支持 ====================
 *
 * enableRegex = false：使用简单的字符串匹配
 *   - 例如：过滤规则 "iPhone" 将匹配 "iPhone 12 Pro"
 *
 * enableRegex = true：使用正则表达式匹配
 *   - 例如：过滤规则 "^(iPhone|iPad).*" 将匹配 iPhone 或 iPad 开头的设备
 *   - 提供更灵活的匹配方式
 *
 * ==================== 数据流 ====================
 *
 * filterRules StateFlow：
 *   - 暴露当前所有过滤规则的状态流
 *   - UI可以通过 collectAsState() 订阅此流
 *   - 任何修改都会自动触发UI更新
 *
 * errorMessage StateFlow：
 *   - 暴露错误信息的状态流
 *   - 用于显示给用户的错误提示
 */
class BluetoothFilterLocalDataSource(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(FILTER_PREFERENCES_NAME, Context.MODE_PRIVATE)

    // 过滤规则列表
    private val _filterRules = MutableStateFlow<List<BluetoothFilterModel>>(emptyList())
    val filterRules: StateFlow<List<BluetoothFilterModel>> = _filterRules.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadAllFilters()
    }

    /**
     * 从本地存储加载所有过滤规则
     */
    private fun loadAllFilters() {
        try {
            val filterJsonString = sharedPreferences.getString(KEY_FILTERS, "[]")
            val jsonArray = JSONArray(filterJsonString ?: "[]")
            val filters = mutableListOf<BluetoothFilterModel>()

            for (i in 0 until jsonArray.length()) {
                val filter = parseFilterFromJson(jsonArray.getJSONObject(i))
                filters.add(filter)
            }

            _filterRules.value = filters
            AppLogger.debug(
                "BluetoothFilterLocalDataSource",
                "加载 ${filters.size} 个过滤规则"
            )
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "加载过滤规则失败", e)
            _errorMessage.value = "加载过滤规则失败: ${e.message}"
        }
    }

    /**
     * 保存过滤规则到本地存储
     */
    private suspend fun saveFilters() = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            _filterRules.value.forEach { filter ->
                jsonArray.put(filterToJson(filter))
            }

            sharedPreferences.edit {
                putString(KEY_FILTERS, jsonArray.toString())
            }

            AppLogger.debug(
                "BluetoothFilterLocalDataSource",
                "保存 ${_filterRules.value.size} 个过滤规则"
            )
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "保存过滤规则失败", e)
            _errorMessage.value = "保存过滤规则失败: ${e.message}"
        }
    }

    /**
     * 添加新的过滤规则
     */
    suspend fun addFilter(filter: BluetoothFilterModel) {
        try {
            val currentFilters = _filterRules.value.toMutableList()
            currentFilters.add(filter)
            _filterRules.value = currentFilters
            saveFilters()
            AppLogger.debug("BluetoothFilterLocalDataSource", "添加过滤规则: ${filter.alias}")
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "添加过滤规则失败", e)
            _errorMessage.value = "添加过滤规则失败: ${e.message}"
        }
    }

    /**
     * 删除指定ID的过滤规则
     */
    suspend fun deleteFilter(filterId: String) {
        try {
            val currentFilters = _filterRules.value.toMutableList()
            currentFilters.removeAll { it.id == filterId }
            _filterRules.value = currentFilters
            saveFilters()
            AppLogger.debug("BluetoothFilterLocalDataSource", "删除过滤规则: $filterId")
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "删除过滤规则失败", e)
            _errorMessage.value = "删除过滤规则失败: ${e.message}"
        }
    }

    /**
     * 更新指定ID的过滤规则
     */
    suspend fun updateFilter(filter: BluetoothFilterModel) {
        try {
            val currentFilters = _filterRules.value.toMutableList()
            val index = currentFilters.indexOfFirst { it.id == filter.id }
            if (index != -1) {
                currentFilters[index] = filter.copy(updateTime = System.currentTimeMillis())
                _filterRules.value = currentFilters
                saveFilters()
                AppLogger.debug("BluetoothFilterLocalDataSource", "更新过滤规则: ${filter.alias}")
            } else {
                throw Exception("过滤规则不存在: ${filter.id}")
            }
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "更新过滤规则失败", e)
            _errorMessage.value = "更新过滤规则失败: ${e.message}"
        }
    }

    /**
     * 获取单个过滤规则
     */
    suspend fun getFilter(filterId: String): BluetoothFilterModel? = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.find { it.id == filterId }
    }

    /**
     * 获取所有启用的过滤规则
     */
    suspend fun getEnabledFilters(): List<BluetoothFilterModel> = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.filter { it.isEnabled }
    }

    /**
     * 获取指定类型的过滤规则
     */
    suspend fun getFiltersByType(
        filterType: BluetoothFilterModel.FilterType
    ): List<BluetoothFilterModel> = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.filter { it.filterType == filterType }
    }

    /**
     * 清空所有过滤规则
     */
    suspend fun clearAllFilters() {
        try {
            _filterRules.value = emptyList()
            saveFilters()
            AppLogger.debug("BluetoothFilterLocalDataSource", "清空所有过滤规则")
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "清空过滤规则失败", e)
            _errorMessage.value = "清空过滤规则失败: ${e.message}"
        }
    }

    /**
     * 批量导入过滤规则
     */
    suspend fun importFilters(filters: List<BluetoothFilterModel>) {
        try {
            _filterRules.value = filters
            saveFilters()
            AppLogger.debug("BluetoothFilterLocalDataSource", "导入 ${filters.size} 个过滤规则")
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "导入过滤规则失败", e)
            _errorMessage.value = "导入过滤规则失败: ${e.message}"
        }
    }

    /**
     * 批量导出过滤规则
     */
    suspend fun exportFilters(): List<BluetoothFilterModel> = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.toList()
    }

    /**
     * 按蓝牙设备名进行过滤
     *
     * 只返回matchType为DEVICE_NAME且启用的过滤规则
     *
     * @param deviceName 蓝牙设备名称
     * @return 匹配的过滤规则列表
     */
    suspend fun filterByDeviceName(deviceName: String): List<BluetoothFilterModel> = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.filter { filter ->
            // 只处理设备名类型的规则
            if (filter.matchType != BluetoothFilterModel.MatchType.DEVICE_NAME) return@filter false
            if (!filter.isEnabled) return@filter false

            if (filter.enableRegex) {
                try {
                    Regex(filter.filterRule).containsMatchIn(deviceName)
                } catch (e: Exception) {
                    AppLogger.error("BluetoothFilterLocalDataSource", "正则表达式错误: ${filter.filterRule}", e)
                    false
                }
            } else {
                deviceName.contains(filter.filterRule, ignoreCase = true)
            }
        }
    }

    /**
     * 按MAC地址进行过滤
     *
     * 只返回matchType为MAC_ADDRESS且启用的过滤规则
     *
     * @param macAddress MAC地址 (格式: XX:XX:XX:XX:XX:XX)
     * @return 匹配的过滤规则列表
     */
    suspend fun filterByMacAddress(macAddress: String): List<BluetoothFilterModel> = withContext(Dispatchers.IO) {
        return@withContext _filterRules.value.filter { filter ->
            // 只处理MAC地址类型的规则
            if (filter.matchType != BluetoothFilterModel.MatchType.MAC_ADDRESS) return@filter false
            if (!filter.isEnabled) return@filter false

            if (filter.enableRegex) {
                try {
                    Regex(filter.filterRule).containsMatchIn(macAddress)
                } catch (e: Exception) {
                    AppLogger.error("BluetoothFilterLocalDataSource", "正则表达式错误: ${filter.filterRule}", e)
                    false
                }
            } else {
                macAddress.equals(filter.filterRule, ignoreCase = true)
            }
        }
    }

    /**
     * 检查蓝牙设备是否应该被过滤
     *
     * @param deviceName 蓝牙设备名称
     * @param macAddress MAC地址
     * @return true 表示应该过滤（被阻止），false 表示允许
     */
    suspend fun shouldFilterDevice(deviceName: String, macAddress: String): Boolean = withContext(Dispatchers.IO) {
        val enabledFilters = getEnabledFilters()
        if (enabledFilters.isEmpty()) return@withContext false

        val whitelistFilters = enabledFilters.filter { it.filterType == BluetoothFilterModel.FilterType.WHITELIST }
        val blacklistFilters = enabledFilters.filter { it.filterType == BluetoothFilterModel.FilterType.BLACKLIST }

        // 如果存在白名单，检查设备是否在白名单中
        if (whitelistFilters.isNotEmpty()) {
            val inWhitelist = whitelistFilters.any { filter ->
                matchesFilter(deviceName, macAddress, filter)
            }
            if (!inWhitelist) {
                return@withContext true  // 不在白名单中，应过滤
            }
        }

        // 检查是否在黑名单中
        if (blacklistFilters.isNotEmpty()) {
            val inBlacklist = blacklistFilters.any { filter ->
                matchesFilter(deviceName, macAddress, filter)
            }
            if (inBlacklist) {
                return@withContext true  // 在黑名单中，应过滤
            }
        }

        return@withContext false  // 允许
    }

    /**
     * 检查设备是否匹配过滤规则
     *
     * 严格按照matchType进行过滤，确保蓝牙名和MAC地址的过滤规则不混淆
     *
     * @param deviceName 蓝牙设备名称
     * @param macAddress MAC地址
     * @param filter 过滤规则
     * @return 是否匹配
     */
    private fun matchesFilter(deviceName: String, macAddress: String, filter: BluetoothFilterModel): Boolean {
        return try {
            when (filter.matchType) {
                BluetoothFilterModel.MatchType.DEVICE_NAME -> {
                    // 只按蓝牙设备名进行匹配
                    if (filter.enableRegex) {
                        Regex(filter.filterRule).containsMatchIn(deviceName)
                    } else {
                        deviceName.contains(filter.filterRule, ignoreCase = true)
                    }
                }
                BluetoothFilterModel.MatchType.MAC_ADDRESS -> {
                    // 只按MAC地址进行匹配
                    if (filter.enableRegex) {
                        Regex(filter.filterRule).containsMatchIn(macAddress)
                    } else {
                        macAddress.equals(filter.filterRule, ignoreCase = true)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.error("BluetoothFilterLocalDataSource", "匹配过滤规则失败", e)
            false
        }
    }

    /**
     * 将过滤规则转换为JSON对象
     */
    private fun filterToJson(filter: BluetoothFilterModel): JSONObject {
        return JSONObject().apply {
            put("id", filter.id)
            put("alias", filter.alias)
            put("filterRule", filter.filterRule)
            put("matchType", filter.matchType.name)
            put("enableRegex", filter.enableRegex)
            put("filterType", filter.filterType.name)
            put("isEnabled", filter.isEnabled)
            put("description", filter.description)
            put("createTime", filter.createTime)
            put("updateTime", filter.updateTime)
        }
    }

    /**
     * 从JSON对象解析过滤规则
     */
    private fun parseFilterFromJson(jsonObject: JSONObject): BluetoothFilterModel {
        return BluetoothFilterModel(
            id = jsonObject.getString("id"),
            alias = jsonObject.getString("alias"),
            filterRule = jsonObject.getString("filterRule"),
            matchType = BluetoothFilterModel.MatchType.valueOf(
                jsonObject.optString("matchType", BluetoothFilterModel.MatchType.DEVICE_NAME.name)
            ),
            enableRegex = jsonObject.getBoolean("enableRegex"),
            filterType = BluetoothFilterModel.FilterType.valueOf(
                jsonObject.getString("filterType")
            ),
            isEnabled = jsonObject.getBoolean("isEnabled"),
            description = jsonObject.optString("description", ""),
            createTime = jsonObject.getLong("createTime"),
            updateTime = jsonObject.getLong("updateTime")
        )
    }

    companion object {
        private const val FILTER_PREFERENCES_NAME = "bluetooth_filter_preferences"
        private const val KEY_FILTERS = "filter_rules"
    }
}

