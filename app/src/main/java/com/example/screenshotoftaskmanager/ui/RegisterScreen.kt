package com.example.screenshotoftaskmanager.ui // 定义包名

import android.widget.Toast // 导入Toast用于显示提示消息
import androidx.compose.foundation.layout.Arrangement // 导入布局排列
import com.example.screenshotoftaskmanager.AuthManager // 导入 AuthManager 用于 Firebase 认证
import androidx.compose.foundation.layout.Column // 导入Column布局
import androidx.compose.foundation.layout.Spacer // 导入Spacer用于间隔
import androidx.compose.foundation.layout.fillMaxSize // 导入填充最大尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填充宽度
import androidx.compose.foundation.layout.height // 导入高度修饰符
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.statusBarsPadding // 导入状态栏内边距
import androidx.compose.foundation.text.KeyboardOptions // 导入键盘选项
import androidx.compose.material3.Button // 导入Material3按钮
import androidx.compose.material3.OutlinedTextField // 导入OutlinedTextField输入框
import androidx.compose.material3.Text // 导入Text组件
import androidx.compose.runtime.Composable // 导入Composable注解
import androidx.compose.runtime.getValue // 导入状态读取委托
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入remember函数
import androidx.compose.runtime.rememberCoroutineScope // 导入协程作用域记忆函数
import androidx.compose.runtime.setValue // 导入状态设置委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入Modifier
import androidx.compose.ui.platform.LocalContext // 导入LocalContext获取上下文
import androidx.compose.ui.text.input.KeyboardType // 导入键盘类型
import androidx.compose.ui.text.input.PasswordVisualTransformation // 导入密码可视化转换
import androidx.compose.ui.unit.dp // 导入dp单位
import androidx.navigation.NavController // 导入NavController用于导航
import kotlinx.coroutines.delay // 导入协程延迟函数，用于注册超时处理
import kotlinx.coroutines.launch // 导入协程启动函数，用于启动超时计时任务

@Composable // 声明这是一个Jetpack Compose可组合函数
fun RegisterScreen(navController: NavController) { // 定义注册屏幕函数，接收NavController用于导航
    // 使用账号而不是邮箱，因为 Firebase 内部会将账号转换为邮箱格式
    var username by remember { mutableStateOf("") } // 记住账号输入状态
    var password by remember { mutableStateOf("") } // 记住密码输入状态
    var confirmPassword by remember { mutableStateOf("") } // 记住确认密码输入状态
    var isLoading by remember { mutableStateOf(false) } // 记录是否正在注册，用于禁用按钮和显示加载状态
    var registerRequestId by remember { mutableStateOf(0) } // 记录当前注册请求编号，用来忽略已经过期的旧回调
    val context = LocalContext.current // 获取当前 Android 上下文，用于 Toast 消息显示
    val scope = rememberCoroutineScope() // 获取协程作用域，用于启动注册超时倒计时

    Column( // 使用 Column 布局垂直排列组件
        modifier = Modifier // 修饰符
            .fillMaxSize() // 填充整个屏幕
            .padding(32.dp) // 设置四周内边距 32dp
            .statusBarsPadding(), // 自动避开状态栏
        horizontalAlignment = Alignment.CenterHorizontally, // 水平居中对齐
        verticalArrangement = Arrangement.Center // 垂直居中排列
    ) {
        OutlinedTextField( // 账号输入框
            value = username, // 绑定账号状态
            onValueChange = { username = it }, // 账号输入变化时更新状态
            label = { Text("账号") }, // 输入框标签
            modifier = Modifier.fillMaxWidth(), // 填充宽度
            enabled = !isLoading // 加载时禁用输入框
        )
        Spacer(modifier = Modifier.height(16.dp)) // 添加垂直间隔 16dp
        OutlinedTextField( // 密码输入框
            value = password, // 绑定密码状态
            onValueChange = { password = it }, // 密码输入变化时更新状态
            label = { Text("密码") }, // 输入框标签
            visualTransformation = PasswordVisualTransformation(), // 密码显示为●（隐藏真实内容）
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // 设置键盘类型为密码
            modifier = Modifier.fillMaxWidth(), // 填充宽度
            enabled = !isLoading // 加载时禁用输入框
        )
        Spacer(modifier = Modifier.height(16.dp)) // 添加垂直间隔 16dp
        OutlinedTextField( // 确认密码输入框
            value = confirmPassword, // 绑定确认密码状态
            onValueChange = { confirmPassword = it }, // 确认密码输入变化时更新状态
            label = { Text("确认密码") }, // 输入框标签
            visualTransformation = PasswordVisualTransformation(), // 密码显示为●（隐藏真实内容）
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // 设置键盘类型为密码
            modifier = Modifier.fillMaxWidth(), // 填充宽度
            enabled = !isLoading // 加载时禁用输入框
        )
        Spacer(modifier = Modifier.height(32.dp)) // 添加垂直间隔 32dp
        Button( // 注册按钮
            onClick = { // 点击事件
                // 验证账号和密码不为空以及两次密码一致
                when { // 使用 when 进行条件判断
                    username.isBlank() -> { // 账号为空
                        Toast.makeText(context, "账号不能为空", Toast.LENGTH_SHORT).show() // 显示账号空值提示
                    }
                    password.isBlank() -> { // 密码为空
                        Toast.makeText(context, "密码不能为空", Toast.LENGTH_SHORT).show() // 显示密码空值提示
                    }
                    password != confirmPassword -> { // 两次密码不一致
                        Toast.makeText(context, "两次密码必须一致", Toast.LENGTH_SHORT).show() // 显示密码不一致提示
                    }
                    password.length < 6 -> { // 密码长度不足 6 位（Firebase 最小要求）
                        Toast.makeText(context, "密码至少需要 6 位", Toast.LENGTH_SHORT).show() // 显示密码长度不足提示
                    }
                    else -> { // 所有验证通过，开始注册
                        // 设置加载状态为 true，禁用按钮并显示加载动画
                        isLoading = true
                        registerRequestId += 1 // 每次点击注册都生成一个新的请求编号
                        val currentRequestId = registerRequestId // 记录本次注册对应的请求编号
                        
                        scope.launch { // 启动一个协程，为本次注册请求添加超时保护
                            delay(15000) // 最多等待 15 秒，防止网络卡住时页面一直灰掉
                            if (isLoading && registerRequestId == currentRequestId) { // 只有当前请求仍然有效且仍在加载时才执行超时处理
                                isLoading = false // 超时后恢复页面可操作状态
                                Toast.makeText(context, "注册超时，请检查网络或 VPN 后重试", Toast.LENGTH_SHORT).show() // 提示用户当前请求已经超时
                            }
                        }
                        
                        // 调用 AuthManager 的 register 函数进行 Firebase 注册   很重要！！
                        AuthManager.register(username, password) registerCallback@ { success, message ->
                            if (registerRequestId != currentRequestId) { // 如果这已经不是当前最新的一次注册请求，就直接忽略旧回调
                                return@registerCallback // 防止超时后的旧回调再次改动界面状态
                            }
                            
                            // 注册完成后的回调函数
                            isLoading = false // 设置加载状态为 false，恢复按钮可用
                            
                            if (success) {
                                // 注册成功：显示成功消息并返回登录页
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 显示注册成功消息
                                navController.popBackStack() // 返回上一屏幕（登录屏幕）
                            } else {
                                // 注册失败：显示错误消息
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 显示注册失败的原因
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(), // 按钮填充宽度
            enabled = !isLoading // 加载时禁用按钮
        ) {
            Text(if (isLoading) "注册中..." else "注册") // 根据加载状态动态显示按钮文本
        }
    }
}
