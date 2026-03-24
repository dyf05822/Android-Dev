package com.example.screenshotoftaskmanager.ui // 主页面与底部导航相关 UI

import androidx.compose.foundation.layout.padding // 处理 Scaffold 内边距
import androidx.compose.material.icons.Icons // Material 图标集合
import androidx.compose.material.icons.filled.Chat // 会话图标
import androidx.compose.material.icons.filled.Person // 我的图标
import androidx.compose.material3.Icon // 图标组件
import androidx.compose.material3.NavigationBar // 底部导航栏
import androidx.compose.material3.NavigationBarItem // 底部导航栏单项
import androidx.compose.material3.Scaffold // 页面脚手架布局
import androidx.compose.material3.Text // 文本组件
import androidx.compose.runtime.Composable // 声明可组合函数
import androidx.compose.runtime.getValue // 委托读取状态值
import androidx.compose.ui.Modifier // UI 修饰符
import androidx.compose.ui.graphics.vector.ImageVector // 图标向量类型
import androidx.navigation.NavController // 导航控制器类型
import androidx.navigation.compose.NavHost // Compose 导航容器
import androidx.navigation.compose.composable // 声明路由页面
import androidx.navigation.compose.currentBackStackEntryAsState // 监听当前回退栈变化
import androidx.navigation.compose.rememberNavController // 记忆并创建导航控制器

// 定义底部导航可选项：每项包含路由、图标和文案
sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object ConversationList : BottomNavItem("conversation_list_main", Icons.Filled.Chat, "会话") // 会话页 Tab
    object Profile : BottomNavItem("profile_main", Icons.Filled.Person, "我的") // 我的页 Tab
}

@Composable
fun MainScreen(mainNavController: NavController) { // 登录成功后的主页面：包含底部导航和子导航
    // 子导航控制器：只负责底部两个 Tab 的页面切换
    val bottomNavController = rememberNavController()    //底部导航控制器

    // 底部导航项集合
    val items = listOf(
        BottomNavItem.ConversationList,
        BottomNavItem.Profile
    )

    Scaffold(
        bottomBar = {
            NavigationBar { // 底部导航栏
                // 监听当前路由变化，用于高亮当前选中的 Tab
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route, // 当前路由匹配则高亮
                        onClick = {
                            bottomNavController.navigate(item.route) {
                                // 切换 Tab 时回到子导航起点，避免同层级页面不断累积
                                popUpTo(bottomNavController.graph.startDestinationId)
                                // 如果目标页面已经在栈顶，则复用，避免重复入栈
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost( // 子导航：定义底部导航对应页面
            navController = bottomNavController,
            startDestination = BottomNavItem.ConversationList.route, // 默认先显示会话页
            modifier = Modifier.padding(innerPadding) // 避免内容被底部栏遮挡
        ) {
            composable(BottomNavItem.ConversationList.route) {
                // 会话页中的深层跳转（如聊天详情）交给主导航 mainNavController
                ConversationListScreen(navController = mainNavController)
            }
            composable(BottomNavItem.Profile.route) {
                // 我的页中的跳转同样交给主导航
                ProfileScreen(mainNavController = mainNavController)
            }
        }
    }

    // 导航结构说明：
    // MainActivity -> 主 NavHost -> MainScreen -> 子 NavHost(会话/我的)
}
