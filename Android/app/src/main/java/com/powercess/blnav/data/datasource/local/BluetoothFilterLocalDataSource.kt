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
     * 将过滤规则转换为JSON对象
     */
    private fun filterToJson(filter: BluetoothFilterModel): JSONObject {
        return JSONObject().apply {
            put("id", filter.id)
            put("alias", filter.alias)
            put("filterRule", filter.filterRule)
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

