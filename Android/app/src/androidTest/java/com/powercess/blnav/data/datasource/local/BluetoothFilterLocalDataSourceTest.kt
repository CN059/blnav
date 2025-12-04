package com.powercess.blnav.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 蓝牙过滤器本地数据源测试
 *
 * 验证BluetoothFilterLocalDataSource能否正确地执行CRUD操作
 * 以及正确管理蓝牙设备过滤规则的本地存储
 */
@RunWith(AndroidJUnit4::class)
class BluetoothFilterLocalDataSourceTest {

    private lateinit var context: Context
    private lateinit var dataSource: BluetoothFilterLocalDataSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 清理之前的测试数据
        val sharedPreferences = context.getSharedPreferences("bluetooth_filter_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // 初始化数据源
        dataSource = BluetoothFilterLocalDataSource(context)
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        // 清理测试数据
        dataSource.clearAllFilters()
    }

    /**
     * 测试1: 验证初始化时过滤规则列表为空
     */
    @Test
    fun testInitialFiltersAreEmpty() = runBlocking<Unit> {
        val filters = dataSource.exportFilters()
        assertTrue("初始过滤规则列表应该为空", filters.isEmpty())
    }

    /**
     * 测试2: 验证添加单个过滤规则
     */
    @Test
    fun testAddSingleFilter() = runBlocking<Unit> {
        val filter = createTestFilter(
            id = "filter_1",
            alias = "iPhone 设备",
            filterRule = "^iPhone.*"
        )

        dataSource.addFilter(filter)
        val filters = dataSource.exportFilters()

        assertEquals("应该有1个过滤规则", 1, filters.size)
        assertEquals("过滤规则别名应该匹配", filter.alias, filters[0].alias)
        assertEquals("过滤规则应该匹配", filter.filterRule, filters[0].filterRule)
    }

    /**
     * 测试3: 验证添加多个过滤规则
     */
    @Test
    fun testAddMultipleFilters() = runBlocking<Unit> {
        val filter1 = createTestFilter(
            id = "filter_1",
            alias = "iPhone 白名单",
            filterRule = "^iPhone.*",
            filterType = BluetoothFilterModel.FilterType.WHITELIST
        )

        val filter2 = createTestFilter(
            id = "filter_2",
            alias = "Android 黑名单",
            filterRule = "^Android.*",
            filterType = BluetoothFilterModel.FilterType.BLACKLIST
        )

        dataSource.addFilter(filter1)
        dataSource.addFilter(filter2)

        val filters = dataSource.exportFilters()
        assertEquals("应该有2个过滤规则", 2, filters.size)
    }

    /**
     * 测试4: 验证获取单个过滤规则
     */
    @Test
    fun testGetSingleFilter() = runBlocking<Unit> {
        val filter = createTestFilter(
            id = "filter_test",
            alias = "测试过滤器",
            filterRule = "TEST.*"
        )

        dataSource.addFilter(filter)
        val retrievedFilter = dataSource.getFilter("filter_test")

        assertNotNull("应该能获取到过滤规则", retrievedFilter)
        assertEquals("获取的过滤规则别名应该匹配", filter.alias, retrievedFilter?.alias)
    }

    /**
     * 测试5: 验证获取不存在的过滤规则返回null
     */
    @Test
    fun testGetNonExistentFilter() = runBlocking<Unit> {
        val retrievedFilter = dataSource.getFilter("non_existent_id")
        assertNull("不存在的过滤规则应该返回null", retrievedFilter)
    }

    /**
     * 测试6: 验证更新过滤规则
     */
    @Test
    fun testUpdateFilter() = runBlocking<Unit> {
        val originalFilter = createTestFilter(
            id = "filter_update",
            alias = "原始别名",
            filterRule = "ORIGINAL.*"
        )

        dataSource.addFilter(originalFilter)

        val updatedFilter = originalFilter.copy(
            alias = "更新后的别名",
            filterRule = "UPDATED.*"
        )

        dataSource.updateFilter(updatedFilter)
        val retrievedFilter = dataSource.getFilter("filter_update")

        assertNotNull("应该能获取到更新后的过滤规则", retrievedFilter)
        assertEquals("别名应该被更新", "更新后的别名", retrievedFilter?.alias)
        assertEquals("规则应该被更新", "UPDATED.*", retrievedFilter?.filterRule)
    }

    /**
     * 测试7: 验证删除过滤规则
     */
    @Test
    fun testDeleteFilter() = runBlocking<Unit> {
        val filter = createTestFilter(
            id = "filter_delete",
            alias = "待删除过滤器",
            filterRule = "DELETE.*"
        )

        dataSource.addFilter(filter)
        assertEquals("添加后应该有1个过滤规则", 1, dataSource.exportFilters().size)

        dataSource.deleteFilter("filter_delete")
        assertEquals("删除后应该没有过滤规则", 0, dataSource.exportFilters().size)
    }

    /**
     * 测试8: 验证获取所有启用的过滤规则
     */
    @Test
    fun testGetEnabledFilters() = runBlocking<Unit> {
        val enabledFilter = createTestFilter(
            id = "filter_enabled",
            alias = "启用的过滤器",
            filterRule = "ENABLED.*",
            isEnabled = true
        )

        val disabledFilter = createTestFilter(
            id = "filter_disabled",
            alias = "禁用的过滤器",
            filterRule = "DISABLED.*",
            isEnabled = false
        )

        dataSource.addFilter(enabledFilter)
        dataSource.addFilter(disabledFilter)

        val enabledFilters = dataSource.getEnabledFilters()
        assertEquals("应该只有1个启用的过滤规则", 1, enabledFilters.size)
        assertEquals("启用的过滤规则别名应该匹配", "启用的过滤器", enabledFilters[0].alias)
    }

    /**
     * 测试9: 验证按类型获取过滤规则（白名单）
     */
    @Test
    fun testGetFiltersByTypeWhitelist() = runBlocking<Unit> {
        val whitelistFilter = createTestFilter(
            id = "filter_white",
            alias = "白名单",
            filterRule = "WHITE.*",
            filterType = BluetoothFilterModel.FilterType.WHITELIST
        )

        val blacklistFilter = createTestFilter(
            id = "filter_black",
            alias = "黑名单",
            filterRule = "BLACK.*",
            filterType = BluetoothFilterModel.FilterType.BLACKLIST
        )

        dataSource.addFilter(whitelistFilter)
        dataSource.addFilter(blacklistFilter)

        val whitelistFilters = dataSource.getFiltersByType(BluetoothFilterModel.FilterType.WHITELIST)
        assertEquals("应该只有1个白名单过滤规则", 1, whitelistFilters.size)
        assertEquals("白名单过滤规则别名应该匹配", "白名单", whitelistFilters[0].alias)
    }

    /**
     * 测试10: 验证按类型获取过滤规则（黑名单）
     */
    @Test
    fun testGetFiltersByTypeBlacklist() = runBlocking<Unit> {
        val whitelistFilter = createTestFilter(
            id = "filter_white",
            alias = "白名单",
            filterRule = "WHITE.*",
            filterType = BluetoothFilterModel.FilterType.WHITELIST
        )

        val blacklistFilter = createTestFilter(
            id = "filter_black",
            alias = "黑名单",
            filterRule = "BLACK.*",
            filterType = BluetoothFilterModel.FilterType.BLACKLIST
        )

        dataSource.addFilter(whitelistFilter)
        dataSource.addFilter(blacklistFilter)

        val blacklistFilters = dataSource.getFiltersByType(BluetoothFilterModel.FilterType.BLACKLIST)
        assertEquals("应该只有1个黑名单过滤规则", 1, blacklistFilters.size)
        assertEquals("黑名单过滤规则别名应该匹配", "黑名单", blacklistFilters[0].alias)
    }

    /**
     * 测试11: 验证清空所有过滤规则
     */
    @Test
    fun testClearAllFilters() = runBlocking<Unit> {
        val filter1 = createTestFilter(id = "filter_1", alias = "过滤器1", filterRule = "RULE1.*")
        val filter2 = createTestFilter(id = "filter_2", alias = "过滤器2", filterRule = "RULE2.*")

        dataSource.addFilter(filter1)
        dataSource.addFilter(filter2)
        assertEquals("应该有2个过滤规则", 2, dataSource.exportFilters().size)

        dataSource.clearAllFilters()
        assertEquals("清空后应该没有过滤规则", 0, dataSource.exportFilters().size)
    }

    /**
     * 测试12: 验证批量导入过滤规则
     */
    @Test
    fun testImportFilters() = runBlocking<Unit> {
        val filters = listOf(
            createTestFilter(id = "import_1", alias = "导入过滤器1", filterRule = "IMPORT1.*"),
            createTestFilter(id = "import_2", alias = "导入过滤器2", filterRule = "IMPORT2.*"),
            createTestFilter(id = "import_3", alias = "导入过滤器3", filterRule = "IMPORT3.*")
        )

        dataSource.importFilters(filters)
        val importedFilters = dataSource.exportFilters()

        assertEquals("应该导入3个过滤规则", 3, importedFilters.size)
        assertEquals("第一个过滤规则别名应该匹配", "导入过滤器1", importedFilters[0].alias)
        assertEquals("第二个过滤规则别名应该匹配", "导入过滤器2", importedFilters[1].alias)
        assertEquals("第三个过滤规则别名应该匹配", "导入过滤器3", importedFilters[2].alias)
    }

    /**
     * 测试13: 验证批量导出过滤规则
     */
    @Test
    fun testExportFilters() = runBlocking<Unit> {
        val filter1 = createTestFilter(id = "export_1", alias = "导出过滤器1", filterRule = "EXPORT1.*")
        val filter2 = createTestFilter(id = "export_2", alias = "导出过滤器2", filterRule = "EXPORT2.*")

        dataSource.addFilter(filter1)
        dataSource.addFilter(filter2)

        val exportedFilters = dataSource.exportFilters()
        assertEquals("应该导出2个过滤规则", 2, exportedFilters.size)
    }

    /**
     * 测试14: 验证正则表达式启用标志
     */
    @Test
    fun testRegexEnabledFlag() = runBlocking<Unit> {
        val regexFilter = createTestFilter(
            id = "regex_enabled",
            alias = "正则启用",
            filterRule = "^[0-9A-F]{2}(:[0-9A-F]{2}){5}$",
            enableRegex = true
        )

        dataSource.addFilter(regexFilter)
        val retrievedFilter = dataSource.getFilter("regex_enabled")

        assertNotNull("应该能获取到过滤规则", retrievedFilter)
        assertTrue("正则表达式标志应该为true", retrievedFilter?.enableRegex ?: false)
    }

    /**
     * 测试15: 验证过滤规则的时间戳
     */
    @Test
    fun testFilterTimestamps() = runBlocking<Unit> {
        val filter = createTestFilter(id = "timestamp_test", alias = "时间戳测试", filterRule = "TIME.*")

        val beforeTime = System.currentTimeMillis()
        dataSource.addFilter(filter)
        val afterTime = System.currentTimeMillis()

        val retrievedFilter = dataSource.getFilter("timestamp_test")
        assertNotNull("应该能获取到过滤规则", retrievedFilter)
        assertTrue("创建时间应该大于等于操作前的时间", (retrievedFilter?.createTime ?: 0) >= beforeTime)
        assertTrue("创建时间应该小于等于操作后的时间", (retrievedFilter?.createTime ?: 0) <= afterTime)
    }

    /**
     * 测试16: 验证数据持久化（重新加载验证）
     */
    @Test
    fun testDataPersistence() = runBlocking<Unit> {
        val filter = createTestFilter(
            id = "persistence_test",
            alias = "持久化测试",
            filterRule = "PERSIST.*"
        )

        dataSource.addFilter(filter)

        // 创建新的数据源实例，模拟应用重启
        val newDataSource = BluetoothFilterLocalDataSource(context)
        val retrievedFilter = newDataSource.getFilter("persistence_test")

        assertNotNull("应该能从新的数据源实例中获取到持久化的过滤规则", retrievedFilter)
        assertEquals("持久化的别名应该匹配", "持久化测试", retrievedFilter?.alias)
    }

    /**
     * 测试17: 验证处理具有特殊字符的过滤规则
     */
    @Test
    fun testFilterWithSpecialCharacters() = runBlocking<Unit> {
        val specialFilter = createTestFilter(
            id = "special_chars",
            alias = "特殊字符测试",
            filterRule = "[特殊字符][\\d+*?{}].*",
            description = "包含特殊字符的描述：@#$%^&*()"
        )

        dataSource.addFilter(specialFilter)
        val retrievedFilter = dataSource.getFilter("special_chars")

        assertNotNull("应该能获取到包含特殊字符的过滤规则", retrievedFilter)
        assertEquals("特殊字符规则应该被正确保存", "[特殊字符][\\d+*?{}].*", retrievedFilter?.filterRule)
        assertEquals("特殊字符描述应该被正确保存", "包含特殊字符的描述：@#$%^&*()", retrievedFilter?.description)
    }

    /**
     * 测试18: 验证大量过滤规则的处理能力
     */
    @Test
    fun testHandleManyFilters() = runBlocking<Unit> {
        val filterCount = 100
        val filters = (1..filterCount).map { index ->
            createTestFilter(
                id = "bulk_filter_$index",
                alias = "批量过滤器$index",
                filterRule = "BULK_RULE_$index.*"
            )
        }

        dataSource.importFilters(filters)
        val allFilters = dataSource.exportFilters()

        assertEquals("应该能处理100个过滤规则", filterCount, allFilters.size)
    }

    /**
     * 测试19: 验证更新不存在的过滤规则会记录错误
     */
    @Test
    fun testUpdateNonExistentFilterReturnsError() = runBlocking<Unit> {
        val nonExistentFilter = createTestFilter(
            id = "non_existent",
            alias = "不存在的过滤器",
            filterRule = "NONE.*"
        )

        dataSource.updateFilter(nonExistentFilter)

        // 检查错误信息是否被设置
        val errorMessage = dataSource.errorMessage.value
        assertNotNull("应该设置错误信息", errorMessage)
        assertTrue("错误信息应该包含规则不存在", errorMessage?.contains("过滤规则不存在") ?: false)
    }

    /**
     * 测试20: 验证StateFlow正确发出过滤规则更新
     */
    @Test
    fun testFilterRulesStateFlow() = runBlocking<Unit> {
        var emittedFiltersCount = 0

        // 收集filterRules的值
        val job = launch {
            dataSource.filterRules.collect { _ ->
                emittedFiltersCount++
            }
        }

        // 添加过滤规则
        val filter = createTestFilter(id = "stateflow_test", alias = "StateFlow测试", filterRule = "SF.*")
        dataSource.addFilter(filter)

        // 等待一些时间让StateFlow发出值
        kotlinx.coroutines.delay(100)
        job.cancel()

        assertTrue("StateFlow应该发出过滤规则列表更新", emittedFiltersCount > 0)
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建用于测试的过滤规则
     */
    private fun createTestFilter(
        id: String,
        alias: String,
        filterRule: String,
        enableRegex: Boolean = false,
        filterType: BluetoothFilterModel.FilterType = BluetoothFilterModel.FilterType.WHITELIST,
        isEnabled: Boolean = true,
        description: String = ""
    ): BluetoothFilterModel {
        return BluetoothFilterModel(
            id = id,
            alias = alias,
            filterRule = filterRule,
            enableRegex = enableRegex,
            filterType = filterType,
            isEnabled = isEnabled,
            description = description,
            createTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis()
        )
    }
}

