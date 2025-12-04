package com.powercess.blnav.data.repository

import com.powercess.blnav.data.datasource.local.BluetoothFilterLocalDataSource
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙过滤规则仓库
 *
 * 负责过滤规则数据的统一管理和访问
 * 作为数据源和业务逻辑之间的中介
 */
class BluetoothFilterRepository(private val filterDataSource: BluetoothFilterLocalDataSource) {

    /**
     * 获取过滤规则列表流
     */
    val filterRules: StateFlow<List<BluetoothFilterModel>>
        get() = filterDataSource.filterRules

    /**
     * 获取错误信息流
     */
    val errorMessage: StateFlow<String?>
        get() = filterDataSource.errorMessage

    /**
     * 添加过滤规则
     */
    suspend fun addFilter(filter: BluetoothFilterModel) {
        filterDataSource.addFilter(filter)
    }

    /**
     * 删除过滤规则
     */
    suspend fun deleteFilter(filterId: String) {
        filterDataSource.deleteFilter(filterId)
    }

    /**
     * 更新过滤规则
     */
    suspend fun updateFilter(filter: BluetoothFilterModel) {
        filterDataSource.updateFilter(filter)
    }

    /**
     * 获取单个过滤规则
     */
    suspend fun getFilter(filterId: String): BluetoothFilterModel? {
        return filterDataSource.getFilter(filterId)
    }

    /**
     * 获取所有启用的过滤规则
     */
    suspend fun getEnabledFilters(): List<BluetoothFilterModel> {
        return filterDataSource.getEnabledFilters()
    }

    /**
     * 按类型获取过滤规则
     */
    suspend fun getFiltersByType(filterType: BluetoothFilterModel.FilterType): List<BluetoothFilterModel> {
        return filterDataSource.getFiltersByType(filterType)
    }

    /**
     * 清空所有过滤规则
     */
    suspend fun clearAllFilters() {
        filterDataSource.clearAllFilters()
    }

    /**
     * 批量导入过滤规则
     */
    suspend fun importFilters(filters: List<BluetoothFilterModel>) {
        filterDataSource.importFilters(filters)
    }

    /**
     * 导出所有过滤规则
     */
    suspend fun exportFilters(): List<BluetoothFilterModel> {
        return filterDataSource.exportFilters()
    }
}

