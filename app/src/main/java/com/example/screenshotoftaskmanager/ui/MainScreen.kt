package com.example.screenshotoftaskmanager.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
                        //定义底部导航的菜单项
sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object ConversationList : BottomNavItem("conversation_list_main", Icons.Filled.Chat, "会话")
    object Profile : BottomNavItem("profile_main", Icons.Filled.Person, "我的")
}

@Composable
fun MainScreen(mainNavController: NavController) {    //什么是mainsrceen?登陆成功以后进入的主页面 有底部导航
    val bottomNavController = rememberNavController()
    val items = listOf(
        BottomNavItem.ConversationList,       //两个navcontroller
        BottomNavItem.Profile
    )

    Scaffold(
        bottomBar = {
            NavigationBar {    //底部栏
                val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route   //获取当前路由
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,      //用来高亮选中的tab
                        onClick = {
                            bottomNavController.navigate(item.route) {
                                popUpTo(bottomNavController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(      //核心 定义了底部导航的页面内容
            navController = bottomNavController,
            startDestination = BottomNavItem.ConversationList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.ConversationList.route) {
                ConversationListScreen(navController = mainNavController)
            }
            composable(BottomNavItem.Profile.route) {
                ProfileScreen(mainNavController = mainNavController)
            }
        }
    }  //MainActivity
  //  ↓
    //NavHost(主导航)
    //↓
    //MainScreen
    //↓
   // NavHost(子导航)
    //├── ConversationList
    //└── Profile
}
