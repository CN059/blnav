package com.powercess.blnav.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.powercess.blnav.presentation.ui.navigation.BottomNavItem
import com.powercess.blnav.presentation.ui.navigation.bottomNavItems
import com.powercess.blnav.presentation.ui.screens.AdvancedBluetoothSettingsScreen
import com.powercess.blnav.presentation.ui.screens.HomeScreen
import com.powercess.blnav.presentation.ui.screens.MapScreen
import com.powercess.blnav.presentation.ui.screens.SettingsScreen
import com.powercess.blnav.presentation.viewmodel.BluetoothViewModel

/**
 * 主屏幕 - 包含底部导航栏和内容区域
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    // Compose Navigation 提供的函数，创建或复用一个 NavController 实例；
    // 返回值是 NavHostController 类型，这个类型有什么用处？
    val navController = rememberNavController()

    // 获取当前导航状态，用于判断是否显示底部导航栏
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 判断是否显示底部导航栏（高级蓝牙设置页面时隐藏）
    val showBottomBar = currentRoute != "advanced_bluetooth_settings"

    // Scaffold 布局，脚手架布局，定义了这个MainScreen的整体结构，能否理解为是一个动态UI声明文件？
    // Scaffold 是 Material 3 的基础布局容器，支持 topBar、bottomBar、floatingActionButton 等；
    Scaffold(
        // 这里传递两个参数，modifier和bottomBar分别起到什么作用，这里的等号运算符的意思就是无序传参吗？
        modifier = modifier,
        // bottomBar 参数定义是 bottomBar: @Composable () -> Unit = {}，相当于传递一个注解为 @Composable 的函数吗？也就是UI布局函数？
        // 传入一个 Composable Lambda，用于渲染底部栏；
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) {
        // 这里面是什么意思，innerPadding 是从哪里来的，起到了什么作用？
        // 由 Scaffold 自动计算（考虑状态栏/导航栏高度），防止内容被遮挡；
        innerPadding ->
        // 导航图 - 管理页面切换

        // 内容区域通过 NavigationGraph(...) 填充，并应用 Modifier.padding(innerPadding)。
        NavigationGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * # 底部导航栏
 *
 * ## Material 3 风格的导航栏，支持三个页面切换
 *
 * 渲染底部三个图标，响应点击跳转；
 */
@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // 获取当前导航状态
    // currentBackStackEntryAsState()：返回一个 State<NavBackStackEntry?>；
    // by 解构为 currentBackStackEntry，自动订阅导航状态变化；
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    // destination?.route：获取当前页面的路由字符串（如 "home"）
    val currentRoute = navBackStackEntry?.destination?.route

    // 下面的代码就是渲染底部导航栏，这个东西在 androidx.compose.material3.* ,这个包里面定义了各种 Material 3 风格的UI组件？
    NavigationBar(
        // 为什么要传递 modifier 参数进去，这个 modifier 参数起到了什么作用？
        modifier = modifier
    ) {
        // 这里获取了我们在另外一个文件中定义的 bottomNavItems 列表
        // 遍历每个导航项，生成对应的 NavigationBarItem 组件
        // 就相当于Vue3里面的 v-for 指令吗？把这些展开了渲染？
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                // 我们为底部导航栏的每一项指定四个属性，分别是icon图标，label标签，selected选中状态，onClick点击事件
                // 分别对应了显示的图标，文本，是否选中的样式，点击时会发生什么
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                },
                selected = currentRoute == item.route,// 判断目前遍历渲染的导航项是否与这个文件保存的 currentRoute 相等，也就是判断是否选中当前渲染路由
                onClick = {
                    // 点击导航项时跳转
                    navController.navigate(item.route) {
                        /** 这里是通过lambda配置导航行为
                         * 将回退栈（back stack）弹出，直到起始目的地（通常是首页），但保留起始页的状态。
                         * `navController.graph.startDestinationId`获取导航图（NavGraph）中定义的 startDestination 的 唯一 ID（不是字符串 route，而是一个内部生成的整数 ID）。*/
                        popUpTo(navController.graph.startDestinationId) {
                            // popUpTo 方法的作用是：从当前栈顶开始，逐个弹出页面，直到 navController.graph.startDestinationId 所在页面成为栈顶
                            saveState = true// 这个也是lambda表达式的一部分吗？
                            // 在弹出过程中，保存被弹出页面的状态（如滚动位置、输入框内容等）。
                        }

                        // 如果目标页面已在回退栈的顶部，则不新建实例，直接复用。
                        launchSingleTop = true//    防止用户连续点击“首页”按钮时，重复压入相同的 Home 页面；
                        // 与 popUpTo 配合，确保底部导航项 永远只有一个实例在栈中。

                        // 恢复状态，保持之前的滚动位置等
                        // 如果目标页面之前被保存过状态（如通过 saveState），则恢复它。
                        restoreState = true
                        /**
                         *     Compose Navigation 支持 自动保存/恢复 Composable 的状态（通过 rememberSaveable）；
                         *     当页面被 pop 弹出时，若 saveState = true，其状态会被临时保存；
                         *     当再次 navigate 到该页面，且 restoreState = true → 自动恢复之前的状态（如 LazyColumn 的滚动位置、文本输入内容等）。*/
                    }
                }
            )
        }
    }
}

/**
 * 导航图 - 定义路由和对应的页面
 */
@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // 为了在composable中使用ViewModel，需要通过LocalContext获取context
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel = remember { BluetoothViewModel(context) }

    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        modifier = modifier
    ) {

        // 首页路由
        composable(route = BottomNavItem.Home.route) {
            HomeScreen()
        }

        // 地图页路由
        composable(route = BottomNavItem.Map.route) {
            MapScreen()
        }

        // 设置页路由
        composable(route = BottomNavItem.Settings.route) {
            SettingsScreen(
                onNavigateToAdvancedBluetooth = {
                    navController.navigate("advanced_bluetooth_settings")
                }
            )
        }

        // 高级蓝牙设置页路由
        composable(route = "advanced_bluetooth_settings") {
            AdvancedBluetoothSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}