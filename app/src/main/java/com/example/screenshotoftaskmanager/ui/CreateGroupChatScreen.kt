@file:Suppress("UNUSED_VALUE") // 抑制仅用于状态重置时“值未读取”的提示
package com.example.screenshotoftaskmanager.ui // 当前文件所在包

import android.widget.Toast // Android 轻提示组件
import androidx.compose.foundation.clickable // 可点击修饰符
import androidx.compose.foundation.layout.Column // 纵向布局
import androidx.compose.foundation.layout.Row // 横向布局
import androidx.compose.foundation.layout.Spacer // 间隔占位组件
import androidx.compose.foundation.layout.fillMaxSize // 填满可用空间
import androidx.compose.foundation.layout.fillMaxWidth // 填满可用宽度
import androidx.compose.foundation.layout.height // 高度修饰符
import androidx.compose.foundation.layout.padding // 内外边距修饰符
import androidx.compose.foundation.layout.size // 固定尺寸修饰符
import androidx.compose.foundation.layout.width // 宽度修饰符
import androidx.compose.foundation.lazy.LazyColumn // 惰性列表
import androidx.compose.foundation.lazy.items // 列表项构建器
import androidx.compose.material.icons.Icons // Material 图标集合
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 自动镜像返回图标
import androidx.compose.material3.Button // Material3 按钮
import androidx.compose.material3.Card // Material3 卡片
import androidx.compose.material3.Checkbox // Material3 复选框
import androidx.compose.material3.ExperimentalMaterial3Api // Material3 实验性 API 标记
import androidx.compose.material3.Icon // 图标组件
import androidx.compose.material3.IconButton // 图标按钮
import androidx.compose.material3.MaterialTheme // 主题系统
import androidx.compose.material3.OutlinedTextField // 带边框输入框
import androidx.compose.material3.Scaffold // 页面脚手架
import androidx.compose.material3.Text // 文本组件
import androidx.compose.material3.TopAppBar // 顶部应用栏
import androidx.compose.material3.TopAppBarDefaults // 顶部栏默认配置
import androidx.compose.runtime.Composable // 可组合函数标记
import androidx.compose.runtime.LaunchedEffect // 启动副作用
import androidx.compose.runtime.mutableStateOf // 可变状态创建
import androidx.compose.runtime.remember // 记忆状态
import androidx.compose.runtime.getValue // by 委托读取
import androidx.compose.runtime.setValue // by 委托写入
import androidx.compose.ui.Alignment // 对齐方式
import androidx.compose.ui.Modifier // 修饰符入口
import androidx.compose.ui.graphics.Color // 颜色类型
import androidx.compose.ui.platform.LocalContext // 当前上下文
import androidx.compose.ui.text.font.FontWeight // 字重类型
import androidx.compose.ui.unit.dp // dp 单位
import androidx.compose.ui.unit.sp // sp 单位
import androidx.navigation.NavController // 导航控制器
import com.example.screenshotoftaskmanager.CloudChatManager // 云聊天管理器
import com.example.screenshotoftaskmanager.User // 用户数据模型

@OptIn(ExperimentalMaterial3Api::class) // 声明使用实验性 Material3 API
@Composable // 声明这是 Compose 页面函数
fun CreateGroupChatScreen(navController: NavController) { // 创建群聊页面入口
    val context = LocalContext.current // 获取当前 Context 用于 Toast

    var groupName by remember { mutableStateOf("") } // 群聊名称输入状态
    var selectedUserIds by remember { mutableStateOf(setOf<String>()) } // 已选择好友 UID 集合
    var isLoading by remember { mutableStateOf(false) } // 创建群聊加载状态
    var availableUsers by remember { mutableStateOf<List<User>>(emptyList()) } // 可选好友列表

    LaunchedEffect(Unit) { // 页面首次进入时加载可选好友
        availableUsers = DataSource.conversations // 从本地会话缓存读取
            .filter { conversation -> // 过滤符合条件的会话
                // ✅ 只提取私聊（一对一），不包括群聊   选好友不能把群聊选上
                conversation.chatType == "private" && conversation.otherUserUid.isNotBlank() // 私聊且对方 UID 非空
            } // 过滤结束
            .map { conversation -> // 会话映射为 User 对象
                User( // 构造用户模型
                    uid = conversation.otherUserUid, // 使用会话中的对方 UID
                    username = conversation.name // 使用会话显示名作为用户名
                ) // User 构造结束
            } // 映射结束
            .distinctBy { it.uid } // 按 UID 去重，避免重复显示
    } // LaunchedEffect 结束

    Scaffold( // 页面整体脚手架
        topBar = { // 顶部栏区域
            TopAppBar( // 顶部应用栏
                title = { Text("创建群聊") }, // 标题文本
                colors = TopAppBarDefaults.topAppBarColors( // 设置顶部栏配色
                    containerColor = MaterialTheme.colorScheme.primary // 背景色使用主题主色
                ), // 颜色设置结束
                navigationIcon = { // 左侧导航图标区域
                    IconButton(onClick = { navController.popBackStack() }) { // 点击返回上一页
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") // 返回图标
                    } // 导航按钮内容结束
                } // 导航图标区域结束
            ) // TopAppBar 结束
        } // topBar 结束
    ) { innerPadding -> // Scaffold 内容区，接收内边距
        Column( // 页面主内容纵向布局
            modifier = Modifier // 修饰符链起点
                .fillMaxSize() // 占满剩余空间
                .padding(innerPadding) // 应用脚手架内边距
                .padding(16.dp) // 再加统一内容边距
        ) { // Column 内容开始
            OutlinedTextField( // 群聊名称输入框
                value = groupName, // 绑定当前输入值
                onValueChange = { groupName = it }, // 输入变化更新状态
                label = { Text("群聊名称") }, // 输入框标签
                modifier = Modifier.fillMaxWidth(), // 输入框占满宽度
                enabled = !isLoading // 创建中禁用输入
            ) // 输入框结束

            Spacer(modifier = Modifier.height(16.dp)) // 与下方内容留间距

            Text( // 已选数量提示
                text = "已选择 ${selectedUserIds.size} 个好友", // 动态显示选中人数
                fontSize = 14.sp, // 文本字号
                color = Color.Gray // 次要信息用灰色
            ) // 数量提示结束

            Spacer(modifier = Modifier.height(8.dp)) // 与列表再留小间距

            LazyColumn( // 可选好友列表
                modifier = Modifier // 修饰符链起点
                    .fillMaxWidth() // 列表占满宽度
                    .weight(1f) // 占据剩余高度
            ) { // LazyColumn 内容开始
                items(availableUsers) { user -> // 渲染每个可选用户
                    UserSelectionItem( // 好友选择项组件
                        user = user, // 传入用户数据
                        isSelected = user.uid in selectedUserIds, // 当前用户是否已选中
                        onSelectionChange = { isSelected -> // 选中状态变更回调
                            if (isSelected) { // 勾选时
                                selectedUserIds = selectedUserIds + user.uid // 加入选中集合
                            } else { // 取消勾选时
                                selectedUserIds = selectedUserIds - user.uid // 从选中集合移除
                            } // 勾选分支结束
                        } // 选中回调结束
                    ) // UserSelectionItem 结束
                } // items 渲染结束
            } // LazyColumn 结束

            Spacer(modifier = Modifier.height(16.dp)) // 列表与按钮留间距

            Button( // 创建群聊按钮
                onClick = { // 点击创建逻辑
                    if (groupName.isBlank()) { // 校验群名不能为空
                        Toast.makeText(context, "请输入群聊名称", Toast.LENGTH_SHORT).show() // 提示输入群名
                        return@Button // 终止本次点击处理
                    } // 群名校验结束
                    if (selectedUserIds.isEmpty()) { // 校验至少选择 1 位好友
                        Toast.makeText(context, "请至少选择一个好友", Toast.LENGTH_SHORT).show() // 提示选择好友
                        return@Button // 终止本次点击处理
                    } // 选人校验结束

                    isLoading = true // 进入提交中状态

                    CloudChatManager.createGroupChat( // 调用云端创建群聊   这里很重要！！！调用云端一些判别方法
                        groupName = groupName, // 传入群聊名称
                        memberUids = selectedUserIds.toList(), // 传入选中 UID 列表
                        onComplete = { success, message -> // 创建完成回调
                            isLoading = false // 无论成功失败都结束加载态
                            if (success) { // 创建成功分支
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 提示成功信息
                                navController.popBackStack() // 返回上一页
                            } else { // 创建失败分支
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 提示失败原因
                            } // 成功失败分支结束
                        } // onComplete 回调结束
                    ) // createGroupChat 调用结束
                }, // onClick 结束
                modifier = Modifier // 按钮修饰符链起点
                    .fillMaxWidth() // 按钮占满宽度
                    .height(48.dp), // 按钮高度
                enabled = !isLoading && groupName.isNotBlank() && selectedUserIds.isNotEmpty() // 仅在输入有效且非加载中可点击
            ) { // 按钮内容开始
                Text(if (isLoading) "创建中..." else "创建群聊") // 根据状态显示按钮文字
            } // Button 结束
        } // Column 结束
    } // Scaffold 内容区结束
} // CreateGroupChatScreen 结束

@Composable // 声明好友选择项为可组合函数
fun UserSelectionItem( // 单个用户选择行
    user: User, // 当前行用户数据
    isSelected: Boolean, // 当前是否选中
    onSelectionChange: (Boolean) -> Unit // 选中状态变化回调
) { // 函数体开始
    Card( // 卡片容器用于包裹一行
        modifier = Modifier // 修饰符链起点
            .fillMaxWidth() // 卡片占满宽度
            .padding(vertical = 4.dp) // 每行上下间距
            .clickable { onSelectionChange(!isSelected) } // 点击整行切换选中状态
    ) { // Card 内容开始
        Row( // 横向布局：复选框 + 用户名
            modifier = Modifier // 修饰符链起点
                .fillMaxWidth() // 行占满宽度
                .padding(12.dp), // 行内边距
            verticalAlignment = Alignment.CenterVertically // 内容垂直居中
        ) { // Row 内容开始
            Checkbox( // 复选框组件
                checked = isSelected, // 绑定当前选中状态
                onCheckedChange = onSelectionChange, // 勾选变化回调
                modifier = Modifier.size(24.dp) // 复选框尺寸
            ) // Checkbox 结束

            Spacer(modifier = Modifier.width(8.dp)) // 复选框与文本之间间距

            Text( // 用户名文本
                text = user.username, // 显示用户名
                modifier = Modifier.weight(1f), // 文本占据剩余空间
                fontWeight = FontWeight.Medium // 中等字重提升可读性
            ) // Text 结束
        } // Row 结束
    } // Card 结束
} // UserSelectionItem 结束

