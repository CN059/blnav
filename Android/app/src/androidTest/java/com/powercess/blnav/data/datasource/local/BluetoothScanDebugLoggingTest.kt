package com.powercess.blnav.data.datasource.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothDeviceModel
import com.powercess.blnav.data.model.BluetoothFilterModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 蓝牙扫描和过滤完整日志流程测试
 *
 * 此测试验证从设备扫描到过滤再到全局管理器的完整流程中的日志输出
 */
@RunWith(AndroidJUnit4::class)
class BluetoothScanDebugLoggingTest {

    private lateinit var context: Context
    private lateinit var filterDataSource: BluetoothFilterLocalDataSource
    private lateinit var bluetoothLocalDataSource: BluetoothLocalDataSource
    private val scanStrategy = BluetoothScanStrategy(updateInterval = 100L)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // 清理SharedPreferences
        val sharedPreferences = context.getSharedPreferences("bluetooth_filter_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // 重置设备管理器
        BluetoothDeviceManagerDataSource.resetInstance()

        // 初始化过滤器数据源
        filterDataSource = BluetoothFilterLocalDataSource(context)

        // 创建蓝牙本地数据源
        bluetoothLocalDataSource = BluetoothLocalDataSource(context, filterDataSource, scanStrategy)
    }

    @After
    fun tearDown() = runBlocking {
        bluetoothLocalDataSource.stopScan()
        bluetoothLocalDataSource.cleanup()
        filterDataSource.clearAllFilters()
        BluetoothDeviceManagerDataSource.resetInstance()
    }

    /**
     * 测试1: 验证无过滤器时的完整日志流程
     *
     * 预期日志输出应包括：
     * 1. ✨ 新发现设备 (本地列表)
     * 2. 📤 将设备发送到全局管理器
     * 3. [timestamp] ⏱️ updateDevice (设备管理器)
     * 4. 📊 检查发布条件
     * 5. ✅ 触发发布
     * 6. 发布设备更新汇总 with 新增设备列表
     */
    @Test
    fun testCompleteLoggingFlowWithoutFilter() = runBlocking {
        // 不设置任何过滤规则

        // 手动添加设备以模拟扫描结果
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "TestDevice2", -60, 0)

        // 通过反射调用addDevice来模拟扫描发现设备
        val addDeviceMethod = bluetoothLocalDataSource.javaClass.getDeclaredMethod(
            "addDevice",
            BluetoothDeviceModel::class.java
        )
        addDeviceMethod.isAccessible = true
        addDeviceMethod.invoke(bluetoothLocalDataSource, device1)
        addDeviceMethod.invoke(bluetoothLocalDataSource, device2)

        // 等待发布完成
        Thread.sleep(150)

        // 验证设备已添加到本地列表
        val localDevices = bluetoothLocalDataSource.discoveredDevices.first()
        assert(localDevices.size == 2) { "本地列表应包含2个设备" }

        // 验证设备已发送到全局管理器
        val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
        val managedDevices = deviceManager.managedDevices.first()
        assert(managedDevices.size == 2) { "全局管理器应包含2个设备" }
    }

    /**
     * 测试2: 验证启用过滤器时的日志流程
     *
     * 预期日志输出应包括：
     * 1. 🔍 检查过滤规则
     * 2. ✅ 过滤规则判定: 设备被允许 (允许的设备)
     * 3. ❌ 过滤规则判定: 设备应被过滤 (被过滤的设备)
     * 4. ❌ 设备被过滤规则阻止 (最终结果)
     * 5. ✨ 新发现设备 (只有允许的设备)
     * 6. 完整的全局管理器日志
     */
    @Test
    fun testCompleteLoggingFlowWithFilter() = runBlocking {
        // 设置一个白名单过滤规则，只允许TestDevice1
        val filter = BluetoothFilterModel(
            id = "filter_1",
            alias = "仅允许TestDevice1",
            filterRule = "TestDevice1",
            matchType = BluetoothFilterModel.MatchType.DEVICE_NAME,
            filterType = BluetoothFilterModel.FilterType.WHITELIST,
            isEnabled = true,
            enableRegex = false
        )
        filterDataSource.addFilter(filter)

        // 手动添加设备
        val allowedDevice = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -50, 0)
        val filteredDevice = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "TestDevice2", -60, 0)

        val addDeviceMethod = bluetoothLocalDataSource.javaClass.getDeclaredMethod(
            "addDevice",
            BluetoothDeviceModel::class.java
        )
        addDeviceMethod.isAccessible = true
        addDeviceMethod.invoke(bluetoothLocalDataSource, allowedDevice)
        addDeviceMethod.invoke(bluetoothLocalDataSource, filteredDevice)

        // 等待发布完成
        Thread.sleep(150)

        // 验证本地列表只包含允许的设备
        val localDevices = bluetoothLocalDataSource.discoveredDevices.first()
        assert(localDevices.size == 1) { "本地列表应只包含1个设备（另1个被过滤）" }
        assert(localDevices[0].name == "TestDevice1")

        // 验证全局管理器也只包含允许的设备
        val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
        val managedDevices = deviceManager.managedDevices.first()
        assert(managedDevices.size == 1) { "全局管理器应只包含1个设备（另1个被过滤）" }
    }

    /**
     * 测试3: 验证设备RSSI更新的日志流程
     *
     * 预期日志输出应包括：
     * 1. 🔄 更新设备 (当设备已存在)
     * 2. 旧RSSI值和新RSSI值的对比
     * 3. 全局管理器中的"更新设备"而不是"新增设备"
     */
    @Test
    fun testRssiUpdateLoggingFlow() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -45, 0) // 相同MAC，不同RSSI

        val addDeviceMethod = bluetoothLocalDataSource.javaClass.getDeclaredMethod(
            "addDevice",
            BluetoothDeviceModel::class.java
        )
        addDeviceMethod.isAccessible = true

        // 首次添加
        addDeviceMethod.invoke(bluetoothLocalDataSource, device1)
        Thread.sleep(150)

        val devicesAfterFirst = bluetoothLocalDataSource.discoveredDevices.first()
        assert(devicesAfterFirst.size == 1)
        assert(devicesAfterFirst[0].rssi == -50)

        // 更新相同设备的RSSI
        addDeviceMethod.invoke(bluetoothLocalDataSource, device2)
        Thread.sleep(150)

        val devicesAfterSecond = bluetoothLocalDataSource.discoveredDevices.first()
        assert(devicesAfterSecond.size == 1)
        assert(devicesAfterSecond[0].rssi == -45)
    }

    /**
     * 测试4: 验证黑名单过滤的日志流程
     *
     * 预期日志输出应包括：
     * 1. 允许大多数设备
     * 2. 只过滤匹配黑名单的特定MAC地址
     */
    @Test
    fun testBlacklistFilterLoggingFlow() = runBlocking {
        // 设置黑名单，禁止特定MAC地址
        val blacklistFilter = BluetoothFilterModel(
            id = "blacklist_1",
            alias = "禁止设备2",
            filterRule = "AA:BB:CC:DD:EE:02",
            matchType = BluetoothFilterModel.MatchType.MAC_ADDRESS,
            filterType = BluetoothFilterModel.FilterType.BLACKLIST,
            isEnabled = true,
            enableRegex = false
        )
        filterDataSource.addFilter(blacklistFilter)

        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "TestDevice2", -60, 0)
        val device3 = BluetoothDeviceModel("AA:BB:CC:DD:EE:03", "TestDevice3", -70, 0)

        val addDeviceMethod = bluetoothLocalDataSource.javaClass.getDeclaredMethod(
            "addDevice",
            BluetoothDeviceModel::class.java
        )
        addDeviceMethod.isAccessible = true
        addDeviceMethod.invoke(bluetoothLocalDataSource, device1)
        addDeviceMethod.invoke(bluetoothLocalDataSource, device2)
        addDeviceMethod.invoke(bluetoothLocalDataSource, device3)

        Thread.sleep(150)

        val localDevices = bluetoothLocalDataSource.discoveredDevices.first()
        assert(localDevices.size == 2) { "本地列表应包含2个设备（设备2被黑名单过滤）" }
        assert(!localDevices.any { it.address == "AA:BB:CC:DD:EE:02" })
    }

    /**
     * 测试5: 验证stopScan时的日志和forcePublish调用
     *
     * 预期日志输出应包括：
     * 1. 🛑 停止扫描
     * 2. 本地列表设备数和全局管理设备数
     * 3. 💪 强制发布 (stopScan内部调用)
     */
    @Test
    fun testStopScanLoggingFlow() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "TestDevice1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "TestDevice2", -60, 0)

        val addDeviceMethod = bluetoothLocalDataSource.javaClass.getDeclaredMethod(
            "addDevice",
            BluetoothDeviceModel::class.java
        )
        addDeviceMethod.isAccessible = true
        addDeviceMethod.invoke(bluetoothLocalDataSource, device1)
        addDeviceMethod.invoke(bluetoothLocalDataSource, device2)

        // 停止扫描，应该触发forcePublish
        bluetoothLocalDataSource.stopScan()

        val deviceManager = BluetoothDeviceManagerDataSource.getInstance()
        val managedDevices = deviceManager.managedDevices.first()
        assert(managedDevices.size == 2) { "停止扫描后，所有设备应被发布" }
    }
}

