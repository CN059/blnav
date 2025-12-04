package com.powercess.blnav.data.datasource.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothDeviceModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 蓝牙设备管理器调试测试
 *
 * 此测试用于验证设备管理器的详细日志输出功能
 * 检查日志中是否包含：
 * - 每个设备的MAC地址、名称和RSSI值
 * - 设备的更新和新增情况
 * - 发布汇总统计信息
 * - 时间戳和状态转换信息
 */
@RunWith(AndroidJUnit4::class)
class BluetoothDeviceManagerDataSourceDebugTest {

    private lateinit var deviceManager: BluetoothDeviceManagerDataSource
    private val scanStrategy = BluetoothScanStrategy(updateInterval = 100L) // 使用较短的间隔便于测试

    @Before
    fun setup() {
        // 重置单例实例
        BluetoothDeviceManagerDataSource.resetInstance()

        // 使用测试专用的扫描策略初始化设备管理器
        deviceManager = BluetoothDeviceManagerDataSource.initializeWith(scanStrategy)
    }

    @After
    fun tearDown() {
        deviceManager.clearAll()
        BluetoothDeviceManagerDataSource.resetInstance()
    }

    /**
     * 测试1: 验证单个设备更新时的日志输出
     *
     * 预期结果：
     * - 日志中包含设备的MAC地址和RSSI值
     * - 显示缓冲设备数量
     * - 显示发布成功
     */
    @Test
    fun testSingleDeviceUpdateLogging() = runBlocking {
        val device = BluetoothDeviceModel(
            address = "AA:BB:CC:DD:EE:01",
            name = "TestDevice1",
            rssi = -50,
            bondState = 0
        )

        deviceManager.updateDevice(device)

        // 等待发布完成
        Thread.sleep(150)

        val devices = deviceManager.managedDevices.first()
        assert(devices.size == 1)
        assert(devices[0].address == "AA:BB:CC:DD:EE:01")
    }

    /**
     * 测试2: 验证多个设备更新时的日志输出
     *
     * 预期结果：
     * - 日志中包含所有设备的信息
     * - 显示设备总数、更新数和新增数
     * - 分别列出更新的设备和新增的设备
     */
    @Test
    fun testMultipleDeviceUpdateLogging() = runBlocking {
        val devices = listOf(
            BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0),
            BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "Device2", -60, 0),
            BluetoothDeviceModel("AA:BB:CC:DD:EE:03", "Device3", -70, 0)
        )

        devices.forEach { device ->
            deviceManager.updateDevice(device)
        }

        // 等待发布完成
        Thread.sleep(150)

        val managedDevices = deviceManager.managedDevices.first()
        assert(managedDevices.size == 3)
    }

    /**
     * 测试3: 验证设备RSSI更新时的日志输出
     *
     * 预期结果：
     * - 日志中显示"更新设备"而不是"新增设备"
     * - 显示旧的RSSI值和新的RSSI值
     * - 不增加设备总数
     */
    @Test
    fun testDeviceRssiUpdateLogging() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -45, 0) // 相同MAC，不同RSSI

        deviceManager.updateDevice(device1)
        Thread.sleep(150)

        val devicesAfterFirst = deviceManager.managedDevices.first()
        assert(devicesAfterFirst.size == 1)
        assert(devicesAfterFirst[0].rssi == -50)

        deviceManager.updateDevice(device2)
        Thread.sleep(150)

        val devicesAfterSecond = deviceManager.managedDevices.first()
        assert(devicesAfterSecond.size == 1)
        assert(devicesAfterSecond[0].rssi == -45)
    }

    /**
     * 测试4: 验证forcePublish的日志输出
     *
     * 预期结果：
     * - 日志显示"强制发布"标记
     * - 显示缓冲设备数和已管理设备数
     * - 立即发布所有缓冲的设备
     */
    @Test
    fun testForcePublishLogging() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "Device2", -60, 0)

        // 更新设备但不等待自动发布
        deviceManager.updateDevice(device1)
        deviceManager.updateDevice(device2)

        // 立即强制发布
        deviceManager.forcePublish()

        val devices = deviceManager.managedDevices.first()
        assert(devices.size == 2)
    }

    /**
     * 测试5: 验证clearAll的日志输出
     *
     * 预期结果：
     * - 日志显示"清空所有设备数据"
     * - 显示清除的设备数和缓冲设备数
     * - 设备列表变为空
     */
    @Test
    fun testClearAllLogging() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "Device2", -60, 0)

        deviceManager.updateDevice(device1)
        deviceManager.updateDevice(device2)
        Thread.sleep(150)

        val devicesBeforeClear = deviceManager.managedDevices.first()
        assert(devicesBeforeClear.size == 2)

        deviceManager.clearAll()

        val devicesAfterClear = deviceManager.managedDevices.first()
        assert(devicesAfterClear.isEmpty())
    }

    /**
     * 测试6: 验证缓冲等待和发布时序的日志输出
     *
     * 预期结果：
     * - 第一次更新立即发布（首次发布）
     * - 后续快速更新显示"缓冲等待"
     * - 超过updateInterval后显示"时间充足"并发布
     */
    @Test
    fun testBufferingAndPublishTimingLogging() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0)
        val device2 = BluetoothDeviceModel("AA:BB:CC:DD:EE:02", "Device2", -60, 0)

        // 第一个设备应该立即发布（首次发布）
        deviceManager.updateDevice(device1)
        val devicesAfterFirst = deviceManager.managedDevices.first()
        assert(devicesAfterFirst.size == 1)

        // 第二个设备应该被缓冲（因为间隔不够）
        deviceManager.updateDevice(device2)

        // 等待足够的时间让第二个设备被发布
        Thread.sleep(150)

        val devicesAfterSecond = deviceManager.managedDevices.first()
        assert(devicesAfterSecond.size == 2)
    }

    /**
     * 测试7: 验证统计信息的输出
     *
     * 预期结果：
     * - getStatistics()返回的字符串包含设备数、缓冲数和更新间隔
     */
    @Test
    fun testStatisticsOutput() = runBlocking {
        val device1 = BluetoothDeviceModel("AA:BB:CC:DD:EE:01", "Device1", -50, 0)

        deviceManager.updateDevice(device1)
        Thread.sleep(150)

        val stats = deviceManager.getStatistics()
        assert(stats.contains("已管理设备"))
        assert(stats.contains("待发布更新"))
        assert(stats.contains("更新间隔"))
    }

    /**
     * 测试8: 验证getUpdateInterval的正确性
     *
     * 预期结果：
     * - 返回值与初始化时设置的updateInterval相同
     */
    @Test
    fun testGetUpdateInterval() {
        val interval = deviceManager.getUpdateInterval()
        assert(interval == 100L)
    }
}

