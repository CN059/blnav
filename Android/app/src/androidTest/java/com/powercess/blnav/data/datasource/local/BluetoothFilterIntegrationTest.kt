package com.powercess.blnav.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 蓝牙过滤器集成测试
 *
 * 演示如何使用BluetoothFilterLocalDataSource和BluetoothFilterModel
 * 进行蓝牙设备名和MAC地址的过滤
 */
@RunWith(AndroidJUnit4::class)
class BluetoothFilterIntegrationTest {

    private lateinit var context: Context
    private lateinit var dataSource: BluetoothFilterLocalDataSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val sharedPreferences = context.getSharedPreferences("bluetooth_filter_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        dataSource = BluetoothFilterLocalDataSource(context)
    }

    @After
    fun tearDown() = runBlocking {
        dataSource.clearAllFilters()
    }

    /**
     * 集成测试1: 演示按设备名过滤的完整流程
     */
    @Test
    fun demonstrateDeviceNameFiltering() = runBlocking {
        // 创建按设备名过滤的规则
        val iPhoneFilter = BluetoothFilterModel(
            id = "device_name_filter_1",
            alias = "允许iPhone设备",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,  // 明确指定为设备名过滤
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true,
            description = "白名单：只允许设备名包含'iPhone'的设备"
        )

        // 添加规则
        dataSource.addFilter(iPhoneFilter)

        // 使用规则进行设备名过滤
        val matchedNameFilters = dataSource.filterByDeviceName("iPhone 12 Pro")
        assert(matchedNameFilters.isNotEmpty()) { "应该找到匹配的设备名过滤规则" }
        assert(matchedNameFilters[0].matchType == BluetoothFilterModel.MatchType.DEVICE_NAME) { "规则应为设备名类型" }

        // MAC地址过滤应该不匹配此规则（因为targetType不同）
        val matchedMacFilters = dataSource.filterByMacAddress("00:1A:7D:DA:71:13")
        assert(matchedMacFilters.isEmpty()) { "MAC地址过滤不应该匹配设备名规则" }
    }

    /**
     * 集成测试2: 演示按MAC地址过滤的完整流程
     */
    @Test
    fun demonstrateMacAddressFiltering() = runBlocking {
        // 创建按MAC地址过滤的规则
        val macBlacklist = BluetoothFilterModel(
            id = "mac_filter_1",
            alias = "禁止特定MAC地址",
            filterRule = "AA:BB:CC:DD:EE:FF",
            matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,  // 明确指定为MAC地址过滤
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.BLACKLIST,
            isEnabled = true,
            description = "黑名单：禁止MAC地址为AA:BB:CC:DD:EE:FF的设备"
        )

        // 添加规则
        dataSource.addFilter(macBlacklist)

        // 使用规则进行MAC地址过滤
        val matchedMacFilters = dataSource.filterByMacAddress("AA:BB:CC:DD:EE:FF")
        assert(matchedMacFilters.isNotEmpty()) { "应该找到匹配的MAC地址过滤规则" }
        assert(matchedMacFilters[0].matchType == BluetoothFilterModel.MatchType.MAC_ADDRESS) { "规则应为MAC地址类型" }

        // 设备名过滤应该不匹配此规则（因为targetType不同）
        val matchedNameFilters = dataSource.filterByDeviceName("iPhone 12")
        assert(matchedNameFilters.isEmpty()) { "设备名过滤不应该匹配MAC地址规则" }
    }

    /**
     * 集成测试3: 演示混合使用设备名和MAC地址过滤规则
     */
    @Test
    fun demonstrateMixedFiltering() = runBlocking {
        // 创建设备名白名单规则
        val deviceNameWhitelist = BluetoothFilterModel(
            id = "name_whitelist",
            alias = "设备名白名单",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true
        )

        // 创建MAC地址黑名单规则
        val macBlacklist = BluetoothFilterModel(
            id = "mac_blacklist",
            alias = "MAC黑名单",
            filterRule = "AA:BB:CC:DD:EE:FF",
            matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.BLACKLIST,
            isEnabled = true
        )

        // 添加两个规则
        dataSource.addFilter(deviceNameWhitelist)
        dataSource.addFilter(macBlacklist)

        // 验证设备名过滤
        val nameFilters = dataSource.filterByDeviceName("iPhone 12")
        assert(nameFilters.size == 1) { "应该找到1个设备名规则" }
        assert(nameFilters[0].id == "name_whitelist") { "应该是设备名白名单规则" }

        // 验证MAC地址过滤
        val macFilters = dataSource.filterByMacAddress("AA:BB:CC:DD:EE:FF")
        assert(macFilters.size == 1) { "应该找到1个MAC地址规则" }
        assert(macFilters[0].id == "mac_blacklist") { "应该是MAC地址黑名单规则" }

        // 验证综合过滤逻辑
        val shouldFilterDevice1 = dataSource.shouldFilterDevice("iPhone 12", "00:11:22:33:44:55")
        assert(!shouldFilterDevice1) { "iPhone设备不在黑名单中，应该允许" }

        val shouldFilterDevice2 = dataSource.shouldFilterDevice("Android Phone", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilterDevice2) { "非iPhone设备且在黑名单中，应该被过滤" }
    }

    /**
     * 集成测试4: 演示正则表达式过滤
     */
    @Test
    fun demonstrateRegexFiltering() = runBlocking {
        // 创建使用正则表达式的设备名规则
        val regexDeviceFilter = BluetoothFilterModel(
            id = "regex_device_filter",
            alias = "正则表达式设备名过滤",
            filterRule = "^(iPhone|iPad|Apple Watch).*",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = true,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true,
            description = "白名单：只允许Apple设备"
        )

        dataSource.addFilter(regexDeviceFilter)

        // 测试正则表达式匹配
        val iPhoneMatches = dataSource.filterByDeviceName("iPhone 12 Pro")
        assert(iPhoneMatches.isNotEmpty()) { "应该匹配iPhone" }

        val iPadMatches = dataSource.filterByDeviceName("iPad Air")
        assert(iPadMatches.isNotEmpty()) { "应该匹配iPad" }

        val watchMatches = dataSource.filterByDeviceName("Apple Watch Series 7")
        assert(watchMatches.isNotEmpty()) { "应该匹配Apple Watch" }

        val androidMatches = dataSource.filterByDeviceName("Samsung Galaxy S21")
        assert(androidMatches.isEmpty()) { "不应该匹配Samsung设备" }
    }

    /**
     * 集成测试5: 演示规则启用/禁用
     */
    @Test
    fun demonstrateFilterEnableDisable() = runBlocking {
        val filter = BluetoothFilterModel(
            id = "enabled_filter",
            alias = "可启用的过滤规则",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true  // 初始为启用
        )

        dataSource.addFilter(filter)

        // 启用状态下应该能匹配
        var matches = dataSource.filterByDeviceName("iPhone 12")
        assert(matches.isNotEmpty()) { "启用状态下应该能匹配" }

        // 禁用规则
        val disabledFilter = filter.copy(isEnabled = false)
        dataSource.updateFilter(disabledFilter)

        // 禁用状态下不应该匹配
        matches = dataSource.filterByDeviceName("iPhone 12")
        assert(matches.isEmpty()) { "禁用状态下不应该能匹配" }
    }
}

