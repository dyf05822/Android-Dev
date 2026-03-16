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
import com.example.screenshotoftaskmanager.ui.LoginScreen // 导入登录页
import com.example.screenshotoftaskmanager.ui.MainScreen // 导入主屏幕
import com.example.screenshotoftaskmanager.ui.OnboardingScreen // 导入引导页
import com.example.screenshotoftaskmanager.ui.RegisterScreen // 导入注册页
import com.example.screenshotoftaskmanager.ui.DataSource // 导入DataSource用于预设账号
import com.example.screenshotoftaskmanager.ui.theme.ScreenshotofTaskManagerTheme // 导入项目主题
import kotlinx.coroutines.delay // 导入延迟函数
import kotlinx.coroutines.launch // 导入协程开启函数 启动一个协程 异步任务：任务在后台执行不阻塞当前程序

class MainActivity : ComponentActivity() { // MainActivity 类定义

    // 定义一个变量，用于控制启动页是否应该继续显示在屏幕上（默认初始为 true）
    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) { // onCreate 生命周期方法
        
        // 1. 安装启动页，并获取启动页对象
        val splashScreen = installSplashScreen() //调用官方splash api 系统启动时就会先显示启动页 返回一个splashscreen对象

        // 2. 设置启动页保持在屏幕上的条件：只要 keepSplashScreen 是 true，启动页就不消失
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        // ✅ 修改点：在生命周期协程中执行延时操作
        // 旋转 1s + 停顿 0.7s = 1.7s。转两圈并停顿两次总共需要 3.4s (3400ms)
        lifecycleScope.launch {
            delay(3400) // 强制让启动页在此停留 3400 毫秒 (3.4 秒)
            keepSplashScreen = false // 3.4 秒时间到，将变量设为 false，启动页平滑消失，进入欢迎页
        }

        super.onCreate(savedInstanceState) // 调用父类初始化
        
        // 预设默认用户：用户名"admin"，密码"123456"，以便调试时直接登录
        DataSource.registerUser(this, "admin", "123456") // 如果已存在，不会重复添加
        
        enableEdgeToEdge() // 开启全屏边缘到边缘的沉浸式体验
        
        setContent { // 开始设置 Compose 渲染内容
            ScreenshotofTaskManagerTheme { // 应用项目全局主题
                Surface(  //一个全新底板容器
                    modifier = Modifier.fillMaxSize(), // 让容器占满全屏
                    color = MaterialTheme.colorScheme.background // 使用主题中的背景颜色 好处：切深色模式时自动适配
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
    NavHost(navController = navController, startDestination = "onboarding") { // 定义导航宿主和起始页
        composable("onboarding") { // 引导页路由
            OnboardingScreen(navController = navController) // 渲染引导页
        }
        composable("login") { // 登录页路由
            LoginScreen(navController = navController) // 渲染登录页
        }
        composable("register") { // 注册页路由
            RegisterScreen(navController = navController) // 渲染注册页
        }
        composable("conversation_list") { // 会话列表页路由
            MainScreen(mainNavController = navController) // 渲染主界面（包含底部导航） Mainscreen作为主界面
        }
        composable(
            route = "conversation_detail/{conversationName}", // 聊天详情路由，带参数 动态路由模板
            arguments = listOf(navArgument("conversationName") { type = NavType.StringType }) // 定义字符串类型的参数 声明参数列表
        ) { backStackEntry ->    //返回栈条录
            val conversationName = backStackEntry.arguments?.getString("conversationName") ?: "" // 取出参数值
            ConversationDetailScreen(navController = navController, conversationName = conversationName) // 渲染详情页
        }
    }
}

@Preview(showBackground = true) // 预览模式设置
@Composable
fun AppPreview() { // 预览组件函数
    ScreenshotofTaskManagerTheme { // 应用主题
        TaskManagerApp() // 预览整体应用
    }
}
