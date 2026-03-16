package com.example.screenshotoftaskmanager.ui // 定义包名

import android.widget.Toast // 导入Toast用于显示提示消息
import androidx.compose.foundation.layout.Arrangement // 导入布局排列
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
import androidx.compose.runtime.setValue // 导入状态设置委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入Modifier
import androidx.compose.ui.platform.LocalContext // 导入LocalContext获取上下文
import androidx.compose.ui.text.input.KeyboardType // 导入键盘类型
import androidx.compose.ui.text.input.PasswordVisualTransformation // 导入密码可视化转换
import androidx.compose.ui.unit.dp // 导入dp单位
import androidx.navigation.NavController // 导入NavController用于导航

@Composable // 声明这是一个Jetpack Compose可组合函数
fun RegisterScreen(navController: NavController) { // 定义注册屏幕函数，接收NavController用于导航
    var username by remember { mutableStateOf("") } // 记住用户名输入状态
    var password by remember { mutableStateOf("") } // 记住密码输入状态
    var confirmPassword by remember { mutableStateOf("") } // 记住确认密码输入状态
    val context = LocalContext.current // 获取当前Android上下文，用于Toast和SharedPreferences

    Column( // 使用Column布局垂直排列组件
        modifier = Modifier // 修饰符
            .fillMaxSize() // 填充整个屏幕
            .padding(32.dp) // 设置四周内边距32dp
            .statusBarsPadding(), // 自动避开状态栏
        horizontalAlignment = Alignment.CenterHorizontally, // 水平居中对齐
        verticalArrangement = Arrangement.Center // 垂直居中排列
    ) {
        OutlinedTextField( // 用户名输入框
            value = username, // 绑定用户名状态
            onValueChange = { username = it }, // 输入变化时更新状态
            label = { Text("用户名") }, // 输入框标签
            modifier = Modifier.fillMaxWidth() // 填充宽度
        )
        Spacer(modifier = Modifier.height(16.dp)) // 添加垂直间隔16dp
        OutlinedTextField( // 密码输入框
            value = password, // 绑定密码状态
            onValueChange = { password = it }, // 输入变化时更新状态
            label = { Text("密码") }, // 输入框标签
            visualTransformation = PasswordVisualTransformation(), // 密码可视化转换（显示为●）
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // 设置键盘类型为密码
            modifier = Modifier.fillMaxWidth() // 填充宽度
        )
        Spacer(modifier = Modifier.height(16.dp)) // 添加垂直间隔16dp
        OutlinedTextField( // 确认密码输入框
            value = confirmPassword, // 绑定确认密码状态
            onValueChange = { confirmPassword = it }, // 输入变化时更新状态
            label = { Text("确认密码") }, // 输入框标签
            visualTransformation = PasswordVisualTransformation(), // 密码可视化转换
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // 设置键盘类型为密码
            modifier = Modifier.fillMaxWidth() // 填充宽度
        )
        Spacer(modifier = Modifier.height(32.dp)) // 添加垂直间隔32dp
        Button( // 注册按钮
            onClick = { // 点击事件
                when { // 使用when进行条件判断
                    username.isBlank() -> { // 用户名为空
                        Toast.makeText(context, "用户名不能为空", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    password.isBlank() -> { // 密码为空
                        Toast.makeText(context, "密码不能为空", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    password != confirmPassword -> { // 两次密码不一致
                        Toast.makeText(context, "两次密码必须一致", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    !DataSource.registerUser(context, username, password) -> { // 注册失败（用户名已存在）
                        Toast.makeText(context, "账号已注册", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    else -> { // 注册成功
                        Toast.makeText(context, "注册成功，请登录", Toast.LENGTH_SHORT).show() // 显示Toast提示
                        navController.popBackStack() // 返回上一屏幕（登录屏幕）
                    }
                }
            },
            modifier = Modifier.fillMaxWidth() // 按钮填充宽度
        ) {
            Text("注册") // 按钮文本
        }
    }
}
