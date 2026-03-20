package com.example.screenshotoftaskmanager // 包名声明

import android.os.Bundle // 导入 Bundle 类，用于处理 Activity 状态就是装数据的小容器 可以用来保存/恢复状态 “包”
import androidx.activity.ComponentActivity // 导入基础 Activity 类   “支持生命周期、SavedState、Activity Result 等现代组件。”
import androidx.activity.compose.setContent // 导入 Compose 内容设置函数Compose 的关键入口：把“Compose UI 树”放进 Activity 的窗口里。
import androidx.activity.enableEdgeToEdge // 导入全屏沉浸式体验函数开启边到边显示
import androidx.compose.foundation.layout.fillMaxSize // 导入全屏填充修饰符 全屏
import androidx.compose.material3.MaterialTheme // 导入 Material3 主题样式库
import androidx.compose.material3.Surface // 导入 Surface 容器组件  底板容器 设置背景色 阴影形状 做整体页面根容器
import androidx.compose.runtime.Composable // 导入可组合函数注解 标记函数是可组合函数
import androidx.compose.ui.Modifier // 导入修饰符接口  Compose的装修工具：尺寸边距点击背景对齐……
import androidx.compose.ui.tooling.preview.Preview // 导入预览模块
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // 导入官方启动页安装函数 启动页面
import androidx.lifecycle.lifecycleScope // 导入生命周期范围，用于开启协程  避免内存泄漏
import androidx.navigation.NavType // 导入导航参数类型
import androidx.navigation.compose.NavHost // 导入导航宿主
import androidx.navigation.compose.composable // 导入导航路由定义函数
import androidx.navigation.compose.rememberNavController // 导入导航控制器记录函数
import androidx.navigation.navArgument // 导入导航参数定义函数
import com.example.screenshotoftaskmanager.ui.ConversationDetailScreen // 导入聊天详情页
import com.example.screenshotoftaskmanager.ui.CreateGroupChatScreen // 导入创建群聊页
import com.example.screenshotoftaskmanager.ui.LoginScreen // 导入登录页
import com.example.screenshotoftaskmanager.ui.MainScreen // 导入主屏幕
import com.example.screenshotoftaskmanager.ui.OnboardingScreen // 导入引导页
import com.example.screenshotoftaskmanager.ui.RegisterScreen // 导入注册页
import com.example.screenshotoftaskmanager.ui.theme.ScreenshotofTaskManagerTheme // 导入项目主题
import com.google.firebase.FirebaseApp // 导入FirebaseApp用于初始化Firebase服务
import kotlinx.coroutines.delay // 导入延迟函数
import kotlinx.coroutines.launch// 导入协程开启函数 启动一个协程 异步任务：任务在后台执行不阻塞当前程序

class MainActivity : ComponentActivity() { // MainActivity 类定义

    // 定义一个变量，用于控制启动页是否应该继续显示在屏幕上（默认初始为 true）
    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) { // onCreate 生命周期方法
        val splashScreen = installSplashScreen() // 调用官方 splash api，系统启动时先显示启动页
        splashScreen.setKeepOnScreenCondition { keepSplashScreen } // 只要 keepSplashScreen 是 true，启动页就不消失

        lifecycleScope.launch {
            delay(3400) // 强制让启动页在此停留 3400 毫秒 (3.4 秒)
            keepSplashScreen = false // 时间到后平滑消失，进入欢迎页
        }

        super.onCreate(savedInstanceState) // 调用父类初始化

        FirebaseApp.initializeApp(this) // 初始化 Firebase 服务
        enableEdgeToEdge() // 开启全屏边缘到边缘的沉浸式体验

        setContent {
            ScreenshotofTaskManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TaskManagerApp() // 加载主导航应用
                }
            }
        }
    }
}

@Composable
fun TaskManagerApp() { // 应用主导航函数
    val navController = rememberNavController() // 创建导航控制器
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") {
            OnboardingScreen(navController = navController)
        }
        composable("login") {
            LoginScreen(navController = navController)
        }
        composable("register") {
            RegisterScreen(navController = navController)
        }
        composable("conversation_list") {
            MainScreen(mainNavController = navController)
        }
        composable(
            route = "conversation_detail/{otherUserUid}", // 聊天详情路由参数改为对方 UID，方便从云端唯一定位会话
            arguments = listOf(navArgument("otherUserUid") { type = NavType.StringType })
        ) { backStackEntry ->
            val otherUserUid = backStackEntry.arguments?.getString("otherUserUid") ?: ""
            ConversationDetailScreen(navController = navController, otherUserUid = otherUserUid)
        }
        composable("create_group_chat") { // 创建群聊页面路由
            CreateGroupChatScreen(navController = navController) // 导航到创建群聊页
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    ScreenshotofTaskManagerTheme {
        TaskManagerApp()
    }
}
