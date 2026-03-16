package com.example.screenshotoftaskmanager.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(navController: NavController) {     //定义登录屏幕函数
    var selectedTabIndex by remember { mutableStateOf(0) }    //选中的选项卡索引初始的状态是0  并且记住状态
    val tabs = listOf("用户名密码登录", "手机登录")   //两个标题

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {   //全屏 顶部自动避开状态栏 竖向布局
        TabRow(selectedTabIndex = selectedTabIndex) {      //告诉当前选中的是哪个
            tabs.forEachIndexed { index, title ->       //遍历拿到0/1和标题
                Tab(selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },     //触发重组ui自动切到对应内容
                    text = { Text(title) })
            }
        }

        when (selectedTabIndex) {     //两种登录方式 条件渲染
            0 -> UsernamePasswordLogin(navController)   //账号密码登录
            1 -> PhoneLogin(navController)
        }
    }
}

@Composable
fun UsernamePasswordLogin(navController: NavController) {
    var username by remember { mutableStateOf("") }     //输入框的状态
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current           //拿到Androidcontext为了弹Toast  （不只是当前的输入文本还有系统环境信息）

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),  //四周内边距32dp
        horizontalAlignment = Alignment.CenterHorizontally,    //水平垂直都居中
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(          //用户名输入框
            value = username,            //绑定状态
            onValueChange = { username = it },           //每输入一个字就更新状态
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth()           //输入框占满宽度
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),      //把输入转换成●●●
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),    //弹出更适合密码的键盘
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { // 使用Row水平排列登录和注册按钮
            Button(onClick = { // 登录按钮点击事件
                when { // 使用when进行条件判断
                    !DataSource.isUserRegistered(context, username) -> { // 用户名未注册
                        Toast.makeText(context, "账号未注册", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    !DataSource.loginUser(context, username, password) -> { // 密码错误
                        Toast.makeText(context, "密码错误", Toast.LENGTH_SHORT).show() // 显示Toast提示
                    }
                    else -> { // 登录成功
                        navController.navigate("conversation_list") { popUpTo("login") { inclusive = true } } // 导航到聊天列表并清除返回栈
                    }
                }
            }) {
                Text("登录") // 按钮文本
            }
            Button(onClick = { // 注册按钮点击事件
                navController.navigate("register") // 导航到注册屏幕
            }) {
                Text("注册") // 按钮文本
            }
        }
    }
}

@Composable
fun PhoneLogin(navController: NavController) {
    var phoneNumber by remember { mutableStateOf("") }      //手机号
    var verificationCode by remember { mutableStateOf("") }   //验证码
    var isCountingDown by remember { mutableStateOf(false) }    //是否正在倒计时
    var countdown by remember { mutableStateOf(60) }            //倒计时秒数
    val context = LocalContext.current   //拿到Androidcontext为了弹Toast

    LaunchedEffect(isCountingDown) {       //启动协程倒计时
        if (isCountingDown) {
            while (countdown > 0) {
                delay(1000)   //延迟一秒1000ms
                countdown--    //倒计时每秒等一下
            }
            isCountingDown = false    //告诉系统当倒计时结束了  秒数恢复成初始值
            countdown = 60
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("手机号") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  //让键盘弹出数字键盘
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it },
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { isCountingDown = true }, enabled = !isCountingDown) {   //倒计时中禁用按钮
                Text(if (isCountingDown) "$countdown s" else "获取验证码")   //触发launchedeffect开始倒计时
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            if (phoneNumber.isNotEmpty() && phoneNumber.all { it.isDigit() } && verificationCode.isNotEmpty() && verificationCode.all { it.isDigit() }) {
                navController.navigate("conversation_list") { popUpTo("login") { inclusive = true } }
            } else {
                Toast.makeText(context, "手机号和验证码都必须是数字且不能为空", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("登录")
        }
    }
}
