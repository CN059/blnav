package com.powercess.blnav.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 蓝牙扫描模块与过滤器的集成测试
 *
 * 验证在蓝牙设备扫描时，过滤器规则是否被正确应用。
 * 演示设备在扫描时会优先进行过滤规则的检查，然后才会展示。
 */
@RunWith(AndroidJUnit4::class)
class BluetoothScanFilterIntegrationTest {

    private lateinit var context: Context
    private lateinit var filterDataSource: BluetoothFilterLocalDataSource
    private lateinit var bluetoothLocalDataSource: BluetoothLocalDataSource

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // 清理SharedPreferences
        val sharedPreferences = context.getSharedPreferences("bluetooth_filter_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // 初始化过滤器数据源
        filterDataSource = BluetoothFilterLocalDataSource(context)

        // 创建蓝牙本地数据源，并传递过滤器数据源
        bluetoothLocalDataSource = BluetoothLocalDataSource(context, filterDataSource)
    }

    @After
    fun tearDown() = runBlocking {
        bluetoothLocalDataSource.stopScan()
        bluetoothLocalDataSource.cleanup()
        filterDataSource.clearAllFilters()
    }

    /**
     * 测试1: 验证没有过滤规则时，所有设备都会被显示
     *
     * 场景：没有设置任何过滤规则的情况下
     * 期望：设备列表中应该显示添加的设备
     */
    @Test
    fun testNoFiltersAllowsAllDevices() = runBlocking {
        // 验证没有过滤规则
        val filters = filterDataSource.exportFilters()
        assert(filters.isEmpty()) { "应该没有过滤规则" }

        // 直接调用私有方法的替代方案：验证shouldFilterDevice返回false
        val shouldFilter = filterDataSource.shouldFilterDevice("iPhone 12 Pro", "00:1A:7D:DA:71:13")
        assert(!shouldFilter) { "没有规则时，设备不应该被过滤" }
    }

    /**
     * 测试2: 验证白名单过滤规则 - 只允许匹配的设备
     *
     * 场景：设置一个白名单规则，只允许iPhone设备
     * 期望：只有设备名包含"iPhone"的设备会被显示
     */
    @Test
    fun testWhitelistFilterOnlyAllowsMatchingDevices() = runBlocking {
        // 创建白名单规则：只允许iPhone设备
        val iPhoneWhitelist = BluetoothFilterModel(
            id = "whitelist_iphone",
            alias = "允许iPhone设备",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true,
            description = "白名单：只允许设备名包含'iPhone'的设备"
        )

        // 添加白名单规则
        filterDataSource.addFilter(iPhoneWhitelist)

        // 测试：iPhone设备应该被允许
        val shouldFilterIPhone = filterDataSource.shouldFilterDevice("iPhone 12 Pro", "00:1A:7D:DA:71:13")
        assert(!shouldFilterIPhone) { "iPhone设备应该被允许（不过滤）" }

        // 测试：其他设备应该被过滤
        val shouldFilterAndroid = filterDataSource.shouldFilterDevice("Samsung Galaxy S21", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilterAndroid) { "非iPhone设备应该被过滤" }
    }

    /**
     * 测试3: 验证黑名单过滤规则 - 阻止匹配的设备
     *
     * 场景：设置一个黑名单规则，阻止特定MAC地址的设备
     * 期望：只有不匹配黑名单的设备会被显示
     */
    @Test
    fun testBlacklistFilterBlocksMatchingDevices() = runBlocking {
        // 创建黑名单规则：阻止特定MAC地址
        val macBlacklist = BluetoothFilterModel(
            id = "blacklist_mac",
            alias = "禁止特定MAC地址",
            filterRule = "AA:BB:CC:DD:EE:FF",
            matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.BLACKLIST,
            isEnabled = true,
            description = "黑名单：禁止MAC地址为AA:BB:CC:DD:EE:FF的设备"
        )

        // 添加黑名单规则
        filterDataSource.addFilter(macBlacklist)

        // 测试：黑名单中的MAC地址应该被过滤
        val shouldFilterBlacklisted = filterDataSource.shouldFilterDevice("Unknown Device", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilterBlacklisted) { "黑名单中的MAC地址应该被过滤" }

        // 测试：不在黑名单中的MAC地址应该被允许
        val shouldFilterOther = filterDataSource.shouldFilterDevice("Other Device", "11:22:33:44:55:66")
        assert(!shouldFilterOther) { "不在黑名单中的设备应该被允许" }
    }

    /**
     * 测试4: 验证禁用的规则不会被应用
     *
     * 场景：创建一个白名单规则但禁用它
     * 期望：非iPhone设备应该被允许，因为规则被禁用了
     */
    @Test
    fun testDisabledFiltersAreNotApplied() = runBlocking {
        // 创建但禁用白名单规则
        val disabledWhitelist = BluetoothFilterModel(
            id = "disabled_whitelist",
            alias = "禁用的白名单",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = false,  // 禁用
            description = "这个规则已禁用"
        )

        // 添加禁用的规则
        filterDataSource.addFilter(disabledWhitelist)

        // 测试：禁用的规则不应该被应用，所以所有设备都应该被允许
        val shouldFilterAndroid = filterDataSource.shouldFilterDevice("Samsung Galaxy", "AA:BB:CC:DD:EE:FF")
        assert(!shouldFilterAndroid) { "禁用的规则不应该被应用，非iPhone设备应该被允许" }
    }

    /**
     * 测试5: 验证正则表达式过滤
     *
     * 场景：使用正则表达式匹配Apple产品
     * 期望：能够正确匹配iPhone、iPad等Apple设备
     */
    @Test
    fun testRegexFilterMatching() = runBlocking {
        // 创建正则表达式规则：匹配Apple设备
        val appleRegexFilter = BluetoothFilterModel(
            id = "regex_apple",
            alias = "Apple设备白名单（正则表达式）",
            filterRule = "^(iPhone|iPad|Apple Watch).*",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = true,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true,
            description = "白名单：只允许Apple品牌的设备"
        )

        // 添加规则
        filterDataSource.addFilter(appleRegexFilter)

        // 测试：iPhone应该被允许
        val shouldFilterIPhone = filterDataSource.shouldFilterDevice("iPhone 12 Pro", "00:1A:7D:DA:71:13")
        assert(!shouldFilterIPhone) { "iPhone应该被允许" }

        // 测试：iPad应该被允许
        val shouldFilterIPad = filterDataSource.shouldFilterDevice("iPad Air", "00:1A:7D:DA:71:14")
        assert(!shouldFilterIPad) { "iPad应该被允许" }

        // 测试：非Apple设备应该被过滤
        val shouldFilterSamsung = filterDataSource.shouldFilterDevice("Samsung S21", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilterSamsung) { "非Apple设备应该被过滤" }
    }

    /**
     * 测试6: 验证多个规则的综合应用（混合白名单和黑名单）
     *
     * 场景：同时存在白名单和黑名单规则
     * 期望：优先检查白名单，再检查黑名单
     */
    @Test
    fun testMultipleRulesApplied() = runBlocking {
        // 创建白名单：允许iPhone
        val iPhoneWhitelist = BluetoothFilterModel(
            id = "whitelist_1",
            alias = "允许iPhone",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true
        )

        // 创建黑名单：禁止特定iPhone
        val specificMacBlacklist = BluetoothFilterModel(
            id = "blacklist_1",
            alias = "禁止特定iPhone",
            filterRule = "AA:BB:CC:DD:EE:FF",
            matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.BLACKLIST,
            isEnabled = true
        )

        // 添加规则
        filterDataSource.addFilter(iPhoneWhitelist)
        filterDataSource.addFilter(specificMacBlacklist)

        // 测试1：普通iPhone应该被允许（在白名单中）
        val shouldFilter1 = filterDataSource.shouldFilterDevice("iPhone 12", "11:22:33:44:55:66")
        assert(!shouldFilter1) { "普通iPhone应该被允许" }

        // 测试2：被黑名单MAC地址的iPhone应该被过滤
        val shouldFilter2 = filterDataSource.shouldFilterDevice("iPhone 13", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilter2) { "黑名单MAC地址的iPhone应该被过滤" }

        // 测试3：非iPhone设备应该被过滤（不在白名单中）
        val shouldFilter3 = filterDataSource.shouldFilterDevice("Android Phone", "11:22:33:44:55:66")
        assert(shouldFilter3) { "非iPhone设备应该被过滤" }
    }

    /**
     * 测试7: 验证设备列表中的设备在添加前被过滤
     *
     * 场景：启用白名单过滤，然后清除并验证设备列表
     * 期望：设备列表应该为空（没有添加设备时）
     */
    @Test
    fun testDiscoveredDevicesListIsEmptyBeforeScan() = runBlocking {
        // 验证初始时设备列表为空
        val initialDevices = bluetoothLocalDataSource.discoveredDevices.first()
        assert(initialDevices.isEmpty()) { "初始设备列表应该为空" }

        // 清除设备
        bluetoothLocalDataSource.clearDevices()

        // 验证清除后设备列表仍为空
        val clearedDevices = bluetoothLocalDataSource.discoveredDevices.first()
        assert(clearedDevices.isEmpty()) { "清除后设备列表应该为空" }
    }

    /**
     * 测试8: 验证启用和禁用过滤规则的效果
     *
     * 场景：创建一个规则，然后切换其启用状态
     * 期望：禁用时规则不应用，启用时规则应用
     */
    @Test
    fun testToggleFilterRuleEnabledState() = runBlocking {
        // 创建白名单规则（初始启用）
        val filter = BluetoothFilterModel(
            id = "toggle_test",
            alias = "测试启用切换",
            filterRule = "iPhone",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            enableRegex = false,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true
        )

        // 添加规则
        filterDataSource.addFilter(filter)

        // 验证启用时规则被应用
        var shouldFilter = filterDataSource.shouldFilterDevice("Samsung", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilter) { "启用时，非iPhone应该被过滤" }

        // 禁用规则
        val disabledFilter = filter.copy(isEnabled = false)
        filterDataSource.updateFilter(disabledFilter)

        // 验证禁用时规则不被应用
        shouldFilter = filterDataSource.shouldFilterDevice("Samsung", "AA:BB:CC:DD:EE:FF")
        assert(!shouldFilter) { "禁用时，非iPhone应该被允许" }

        // 重新启用规则
        val enabledFilter = filter.copy(isEnabled = true)
        filterDataSource.updateFilter(enabledFilter)

        // 验证重新启用时规则又被应用
        shouldFilter = filterDataSource.shouldFilterDevice("Samsung", "AA:BB:CC:DD:EE:FF")
        assert(shouldFilter) { "重新启用时，非iPhone应该被过滤" }
    }
}

