package com.powercess.blnav.presentation.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.powercess.blnav.data.model.BluetoothFilterModel
import com.powercess.blnav.presentation.viewmodel.BluetoothViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider

/**
 * 高级蓝牙设置屏幕集成测试
 *
 * 验证UI与ViewModel的交互和过滤规则的可视化
 */
@RunWith(AndroidJUnit4::class)
class AdvancedBluetoothSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: BluetoothViewModel
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        viewModel = BluetoothViewModel(context)
    }

    /**
     * 测试: 验证空状态下显示占位符
     */
    @Test
    fun testEmptyStatePlaceholder() {
        composeTestRule.setContent {
            AdvancedBluetoothSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("暂无过滤规则").assertExists()
        composeTestRule.onNodeWithText("点击\"＋\"按钮添加蓝牙设备过滤规则").assertExists()
    }

    /**
     * 测试: 验证标题显示
     */
    @Test
    fun testScreenTitle() {
        composeTestRule.setContent {
            AdvancedBluetoothSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("高级蓝牙设置").assertExists()
        composeTestRule.onNodeWithText("蓝牙设备过滤规则").assertExists()
    }

    /**
     * 测试: 验证添加按钮存在
     */
    @Test
    fun testAddButtonExists() {
        composeTestRule.setContent {
            AdvancedBluetoothSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("添加规则").assertExists()
    }
}

