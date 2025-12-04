package com.powercess.blnav.data.repository

import com.powercess.blnav.data.datasource.local.BluetoothFilterLocalDataSource
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.flow.StateFlow

/**
 * 蓝牙过滤规则仓库
 *
 * 负责过滤规则数据的统一管理和访问
 * 作为数据源和业务逻辑之间的中介
 *
 * ==================== 关键特性 ====================
 *
 * 1. 明确的过滤目标类型（MatchType）：
 *    - DEVICE_NAME: 按蓝牙设备名称进行过滤
 *    - MAC_ADDRESS: 按MAC地址进行过滤
 *    每个规则都明确指定其过滤目标，避免混淆
 *
 * 2. 灵活的过滤类型（FilterType）：
 *    - WHITELIST: 白名单，只允许匹配的设备
 *    - BLACKLIST: 黑名单，阻止匹配的设备
 *
 * 3. 强大的匹配方式：
 *    - enableRegex = false: 使用简单的字符串匹配
 *    - enableRegex = true: 使用正则表达式进行高级匹配
 *
 * 4. 完整的生命周期管理：
 *    - 创建、更新、删除过滤规则
 *    - 启用/禁用规则
 *    - 导入/导出规则
 *    - 持久化到本地存储
 *
 * ==================== 使用示例 ====================
 *
 * // 示例1: 创建设备名白名单规则
 * val iPhoneWhitelist = BluetoothFilterModel(
 *     id = "device_name_whitelist_1",
 *     alias = "允许iPhone设备",
 *     filterRule = "iPhone",
 *     targetType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *     enableRegex = false,
 *     filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *     description = "白名单：只允许设备名包含'iPhone'的设备"
 * )
 * repository.addFilter(iPhoneWhitelist)
 *
 * // 示例2: 创建MAC地址黑名单规则
 * val macBlacklist = BluetoothFilterModel(
 *     id = "mac_blacklist_1",
 *     alias = "禁止特定MAC地址",
 *     filterRule = "AA:BB:CC:DD:EE:FF",
 *     targetType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
 *     enableRegex = false,
 *     filterType = BluetoothFilterModel.FilterType.BLACKLIST,
 *     description = "黑名单：禁止MAC地址为AA:BB:CC:DD:EE:FF的设备"
 * )
 * repository.addFilter(macBlacklist)
 *
 * // 示例3: 创建使用正则表达式的设备名规则
 * val appleDevicesRegex = BluetoothFilterModel(
 *     id = "device_name_regex_1",
 *     alias = "Apple设备白名单（正则表达式）",
 *     filterRule = "^(iPhone|iPad|Apple Watch).*",
 *     targetType = BluetoothFilterModel.MatchType.DEVICE_NAME,
 *     enableRegex = true,
 *     filterType = BluetoothFilterModel.FilterType.WHITELIST,
 *     description = "白名单：只允许Apple品牌的设备"
 * )
 * repository.addFilter(appleDevicesRegex)
 *
 * // 示例4: 按设备名进行过滤查询
 * val deviceNameMatches = repository.filterByDeviceName("iPhone 12 Pro")
 * // 只返回targetType为DEVICE_NAME的匹配规则
 *
 * // 示例5: 按MAC地址进行过滤查询
 * val macMatches = repository.filterByMacAddress("00:1A:7D:DA:71:13")
 * // 只返回targetType为MAC_ADDRESS的匹配规则
 *
 * // 示例6: 检查设备是否应该被过滤
 * val shouldFilter = repository.shouldFilterDevice("iPhone 12", "00:1A:7D:DA:71:13")
 * // 返回true表示设备应该被过滤（阻止），false表示允许
 *
 * ==================== 重要说明 ====================
 *
 * - 每个规则都有明确的targetType，确保不会混淆不同的过滤目标
 * - 设备名过滤规则只会在filterByDeviceName()中被应用
 * - MAC地址过滤规则只会在filterByMacAddress()中被应用
 * - shouldFilterDevice()方法会综合考虑两种类型的规则
 * - 所有操作都是异步的（suspend函数），不会阻塞UI线程
 * - 修改会自动持久化到本地存储
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

    /**
     * 按蓝牙设备名进行过滤
     */
    suspend fun filterByDeviceName(deviceName: String): List<BluetoothFilterModel> {
        return filterDataSource.filterByDeviceName(deviceName)
    }

    /**
     * 按MAC地址进行过滤
     */
    suspend fun filterByMacAddress(macAddress: String): List<BluetoothFilterModel> {
        return filterDataSource.filterByMacAddress(macAddress)
    }

    /**
     * 检查蓝牙设备是否应该被过滤
     *
     * @param deviceName 蓝牙设备名称
     * @param macAddress MAC地址
     * @return true 表示应该过滤（被阻止），false 表示允许
     */
    suspend fun shouldFilterDevice(deviceName: String, macAddress: String): Boolean {
        return filterDataSource.shouldFilterDevice(deviceName, macAddress)
    }
}

