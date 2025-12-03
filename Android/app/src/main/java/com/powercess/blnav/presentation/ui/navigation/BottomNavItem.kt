package com.powercess.blnav.presentation.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * # 底部导航栏项目
 *
 * ## 定义应用中的主要导航目的地
 *
 * 这是一个 密封类 ，
 *
 * > 用来表示受限的类继承结构：当一个值为有限几种的类型、而不能有任何其他类型时。
 * 在某种意义上，他们是枚举类的扩展：枚举类型的值集合也是受限的，但每个枚举常量只存在一个实例，
 * 而密封类的一个子类可以有可包含状态的多个实例。
 *
 * 只能在同一个文件中被继承。这确保所有子类都是已知的，便于进行完整的 when 表达式判断。
 */

sealed class BottomNavItem(
    // 这里面的参数就是构造函数的参数
    val route: String,// 导航路由，用于页面跳转时的唯一标识
    val title: String,// 显示的标题文本（如"蓝牙"、"地图"）
    val icon: ImageVector// 显示的图标
) {// 这里面是三个 object 单例对象，分别代表三个导航项
    // 什么是 object 单例对象？在我们的程序中起到了什么作用？
    /** 首页 - 蓝牙设备管理 */
    object Home : BottomNavItem(
        route = "home",
        title = "蓝牙",
        icon = Icons.Default.Bluetooth
    )

    /** 地图页 - 室内导航 */
    object Map : BottomNavItem(
        route = "map",
        title = "地图",
        icon = Icons.Default.Map
    )

    /** 设置页 - 应用配置 */
    object Settings : BottomNavItem(
        route = "settings",
        title = "设置",
        icon = Icons.Default.Settings
    )
}

/** # 获取所有底部导航项
 *
 *  > 这是一个全局常量列表，包含应用中的所有底部导航栏项目。
 *  在UI中可以通过遍历这个列表来动态生成底部导航栏的内容。
 *  */
val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Map,
    BottomNavItem.Settings
)

