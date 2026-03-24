package com.example.screenshotoftaskmanager.ui // 定义包名

import android.Manifest // 导入权限常量
import android.net.Uri // 导入 Uri 类
import android.widget.Toast // 导入 Toast，用于提示云端发送或监听错误
import androidx.activity.compose.rememberLauncherForActivityResult // 导入结果启动器
import androidx.activity.result.PickVisualMediaRequest // 导入图片请求
import androidx.activity.result.contract.ActivityResultContracts // 导入结果协议
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.layout.Arrangement // 导入布局对齐方式
import androidx.compose.foundation.layout.Box // 导入 Box
import androidx.compose.foundation.layout.Column // 导入 Column
import androidx.compose.foundation.layout.Row // 导入 Row
import androidx.compose.foundation.layout.Spacer // 导入间距
import androidx.compose.foundation.layout.fillMaxSize // 导入全屏
import androidx.compose.foundation.layout.fillMaxWidth // 导入全宽
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.size // 导入尺寸
import androidx.compose.foundation.layout.width // 导入宽度
import androidx.compose.foundation.lazy.LazyColumn // 导入列表
import androidx.compose.foundation.lazy.items // 导入项
import androidx.compose.foundation.lazy.itemsIndexed // 导入带索引的 LazyColumn item 构建
import androidx.compose.foundation.lazy.rememberLazyListState // 导入列表状态
import androidx.compose.foundation.shape.CircleShape // 导入圆形
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角
import androidx.compose.material.icons.Icons // 导入图标
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 导入返回图标
import androidx.compose.material.icons.filled.Add // 导入添加图标
import androidx.compose.material.icons.filled.Cloud // 导入云朵图标
import androidx.compose.material.icons.filled.Image // 导入图片图标
import androidx.compose.material.icons.filled.Send // 导入发送图标
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入按钮图标
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedTextField // 导入输入框
import androidx.compose.material3.Scaffold // 导入脚手架
import androidx.compose.material3.Text // 导入文本
import androidx.compose.material3.TextButton // 导入文本按钮
import androidx.compose.material3.TopAppBar // 导入顶部栏
import androidx.compose.runtime.Composable // 导入可组合函数
import androidx.compose.runtime.DisposableEffect // 导入 DisposableEffect，用于页面销毁时释放监听
import androidx.compose.runtime.LaunchedEffect // 导入副作用
import androidx.compose.runtime.getValue // 导入读取委托
import androidx.compose.runtime.mutableStateOf // 导入状态创建
import androidx.compose.runtime.remember // 导入状态记住
import androidx.compose.runtime.rememberCoroutineScope // 导入协程范围记录
import androidx.compose.runtime.setValue // 导入写入委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.draw.clip // 导入裁剪
import androidx.compose.ui.graphics.Color // 导入颜色
import androidx.compose.ui.graphics.vector.ImageVector // 导入矢量图
import androidx.compose.ui.layout.ContentScale // 导入缩放
import androidx.compose.ui.platform.LocalContext // 导入获取当前上下文的函数
import androidx.compose.ui.text.input.TextFieldValue // 导入输入框值
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp（可扩展像素）
import androidx.navigation.NavController // 导入导航控制器
import coil.compose.AsyncImage // 导入异步图片加载
import com.example.screenshotoftaskmanager.CloudChatManager // 导入云端聊天管理器
import kotlinx.coroutines.launch // 导入协程启动

@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 声明可组合函数
fun ConversationDetailScreen(navController: NavController, otherUserUid: String) { // 详情页入口，参数改为对方 UID（或群聊 ID）
    val context = LocalContext.current // 获取当前 Android 上下文，用于位置请求和 Toast
    val conversation = DataSource.getOrCreateConversation(otherUserUid, otherUserUid) // 获取或创建当前运行时会话对象
    val messages = conversation.messages // 读取当前会话消息列表
    val scope = rememberCoroutineScope() // 获取协程范围  用于启动天气请求等异步逻辑

    // 聊天目标 ID：群聊用 chatId，一对一用对方 UID
    val conversationKey = if (conversation.chatType == "group" && conversation.chatId.isNotBlank()) { // 判断当前是否为有效群聊
        conversation.chatId // 群聊场景使用 chatId 作为会话键
    } else { // 非群聊或群聊 ID 为空时走私聊键
        otherUserUid // 一对一场景使用对方 UID 作为会话键
    } // 会话键判断结束

    // 定位权限申请器
    val locationPermissionLauncher = rememberLauncherForActivityResult( // 注册权限请求回调
        contract = ActivityResultContracts.RequestMultiplePermissions(), // 一次请求多项权限
        onResult = { permissions -> // 收到权限结果映射
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || // 检查精确定位是否授权
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true // 检查模糊定位是否授权
            if (!granted) { // 如果两种定位都未授权
                Toast.makeText(context, "未授予定位权限，将使用默认天气", Toast.LENGTH_SHORT).show() // 给出降级提示
            } // 权限未授予分支结束
        } // 权限结果处理结束
    ) // 权限申请器创建结束

    // 进入会话时清空未读数
    LaunchedEffect(conversationKey) { // 会话切换或首次进入时执行
        conversation.unreadCount = 0 // 清空当前会话未读数
    } // 清空未读副作用结束

    // 输入框状态，初始值来自草稿
    var textState by remember(conversationKey) { mutableStateOf(TextFieldValue(conversation.draft)) } // 会话变化时重置输入为该会话草稿

    // 更多选项显示开关
    var showExtraOptions by remember { mutableStateOf(false) } // 控制底部扩展功能区显示

    // 列表滚动状态
    val listState = rememberLazyListState() // 记录消息列表滚动位置

    // 图片来源对话框开关
    var showImageSourceDialog by remember { mutableStateOf(false) } // 控制图片来源对话框显示

    // 更换对方头像对话框开关
    var showChangeAvatarDialog by remember { mutableStateOf(false) } // 控制更换头像对话框显示

    // 图片选择器：图片本体暂不上传云端，这里发送统一占位文本 [图片]
    val photoPickerLauncher = rememberLauncherForActivityResult( // 注册图片选择结果回调
        contract = ActivityResultContracts.PickVisualMedia(), // 使用系统媒体选择器
        onResult = { uri: Uri? -> // 接收选中的图片 Uri（可空）
            uri?.let { // 仅在选择到图片时继续
                CloudChatManager.sendMessage(conversationKey, "[图片]") { success, message -> // 发送图片占位消息
                    if (!success) { // 发送失败时提示
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 展示失败原因
                    } // 发送失败分支结束
                } // 发送回调结束
            } // Uri 非空分支结束
        } // 图片选择结果处理结束
    ) // 图片选择器注册结束

    // 对方头像选择器：仅影响本机 UI 展示，不上传云端 ！！不上传到云端！！
    val otherPhotoPickerLauncher = rememberLauncherForActivityResult( // 注册对方头像选择回调
        contract = ActivityResultContracts.PickVisualMedia(), // 使用系统媒体选择器
        onResult = { uri: Uri? -> // 接收头像图片 Uri（可空）
            uri?.let { // 仅在选中图片时更新
                conversation.avatar = it.toString() // 将会话头像替换为本地 Uri 字符串
                showChangeAvatarDialog = false // 选择成功后关闭头像对话框
            } // Uri 非空分支结束
        } // 头像选择结果处理结束
    ) // 对方头像选择器注册结束

    // 页面展示时实时监听云端消息，并同步回 DataSource 的运行时会话缓存中
    DisposableEffect(conversationKey) { // 进入页面时建立监听，离开时自动释放
        val registration = CloudChatManager.listenMessagesForConversation( // 订阅当前会话消息流
            otherUserUid = conversationKey, // 传入当前会话键（群聊为 chatId，私聊为对方 UID）
            onChange = { cloudMessages -> // 云端消息变化回调
                val latestConversation = DataSource.getOrCreateConversation(conversationKey, conversation.name) // 获取最新会话对象引用
                latestConversation.messages.clear() // 清空本地旧消息
                latestConversation.messages.addAll(cloudMessages) // 写入最新云端消息
                latestConversation.previewText = cloudMessages.lastOrNull()?.content ?: "还没有消息" // 更新会话预览文案
                latestConversation.lastTimestamp = System.currentTimeMillis() // 更新时间戳用于列表排序
            }, // 消息变化回调定义结束
            onError = { errorMessage -> // 监听失败回调
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show() // 提示监听错误
            } // 错误回调结束
        ) // 监听注册结束

        onDispose { // 组件离开组合时执行清理
            registration?.remove() // 注销监听，避免内存泄漏和重复回调
        } // 清理回调结束
    } // DisposableEffect 结束

    // 有新消息时自动滚动到底部
    LaunchedEffect(messages.size) { // 当消息数量变化时触发
        if (messages.isNotEmpty()) { // 仅在列表非空时滚动
            listState.animateScrollToItem(messages.size - 1) // 平滑滚到最后一条消息
        } // 非空分支结束
    } // 自动滚动副作用结束

    // 图片来源对话框
    if (showImageSourceDialog) { // 当开关为 true 时展示对话框
        AlertDialog( // 图片来源选择弹窗
            onDismissRequest = { showImageSourceDialog = false }, // 点击外部时关闭弹窗
            title = { Text("选择图片来源") }, // 弹窗标题
            text = { Text("你想从哪里发送图片？") }, // 弹窗说明文案
            confirmButton = { // 右侧确认按钮区域
                TextButton(onClick = { // 点击本机相册
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) // 启动图片选择器
                    showImageSourceDialog = false // 发起选择后关闭弹窗
                }) { // 本机相册按钮内容
                    Text("本机相册") // 本机相册文案
                } // 本机相册按钮结束
            }, // 确认按钮区域结束
            dismissButton = { // 左侧按钮区域
                TextButton(onClick = { // 点击随机照片选项
                    CloudChatManager.sendMessage(conversationKey, "[图片]") { success, message -> // 直接发送占位图片消息
                        if (!success) { // 发送失败时提示
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 展示失败原因
                        } // 失败分支结束
                    } // 发送回调结束
                    showImageSourceDialog = false // 发送后关闭弹窗
                }) { // 随机照片按钮内容
                    Text("随机照片") // 随机照片文案
                } // 随机照片按钮结束
            } // 取消按钮区域结束
        ) // 对话框结束
    } // 图片来源对话框分支结束

    // 更换对方头像对话框
    if (showChangeAvatarDialog) { // 当开关为 true 时展示头像弹窗
        AlertDialog( // 更换头像弹窗
            onDismissRequest = { showChangeAvatarDialog = false }, // 点击外部时关闭弹窗
            title = { Text("更换对方头像") }, // 弹窗标题
            text = { Text("请选择头像来源：") }, // 弹窗说明文案
            confirmButton = { // 右侧按钮：本机相册
                TextButton( // 文本按钮容器
                    onClick = { // 点击后打开系统相册
                        otherPhotoPickerLauncher.launch( // 启动头像图片选择
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) // 限制仅选图片
                        ) // 启动调用结束
                    } // 点击事件结束
                ) { // 按钮内容
                    Text("本机相册") // 按钮文案
                } // 按钮内容结束
            }, // 确认按钮区域结束
            dismissButton = { // 左侧按钮：随机头像
                TextButton( // 文本按钮容器
                    onClick = { // 点击后随机头像
                        val randomRes = DataSource.profileResources.random() // 从头像池随机取一个资源
                        conversation.avatar = randomRes // 将会话头像替换为随机资源
                        showChangeAvatarDialog = false // 更新后关闭弹窗
                    } // 点击事件结束
                ) { // 按钮内容
                    Text("随机头像") // 按钮文案
                } // 按钮内容结束
            } // 取消按钮区域结束
        ) // 对话框结束
    } // 更换头像对话框分支结束

    Scaffold( // 页面主脚手架
        topBar = { // 顶部栏区域
            TopAppBar( // 顶部应用栏
                // 根据聊天类型显示不同的标题
                title = { // 标题内容区域
                    // 如果是群聊，显示群名 + 成员数
                    if (conversation.chatType == "group") { // 群聊标题分支
                        Column { // 垂直排布群聊标题与副标题
                            // ✅ 群名为空时显示默认值
                            Text(conversation.groupName.ifBlank { "群聊" }) // 显示群聊名称
                            Text( // 显示群成员数量
                                // ✅ 使用真实的 participants 列表，而不是消息发送者数
                                text = "成员数: ${conversation.participants.size}", // 成员数量文案
                                fontSize = 12.sp // 副标题字号
                            ) // 成员数量文本结束
                        } // 群聊标题列结束
                    } else { // 私聊标题分支
                        // 如果是一对一，显示用户名
                        Text(conversation.name) // 显示对方名称
                    } // 标题分支结束
                }, // 标题区域结束
                navigationIcon = { // 左侧返回按钮区域
                    IconButton(onClick = { navController.popBackStack() }) { // 点击返回上一页
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") // 返回图标
                    } // 返回按钮内容结束
                }, // 返回按钮区域结束
                actions = { // 右侧操作区
                    IconButton(onClick = { showChangeAvatarDialog = true }) { // 点击打开更换头像弹窗
                        Icon(Icons.Default.Image, contentDescription = "更换对方头像") // 头像操作图标
                    } // 头像按钮内容结束
                } // 操作区结束
            ) // TopAppBar 结束
        }, // topBar 结束
        bottomBar = { // 底部输入区
            Column { // 纵向排布输入行和扩展功能行
                Row( // 输入与按钮主行
                    modifier = Modifier // 行修饰符链起点
                        .fillMaxWidth() // 占满宽度
                        .padding(8.dp), // 设置外边距
                    verticalAlignment = Alignment.CenterVertically // 行内组件垂直居中
                ) { // Row 内容开始
                    OutlinedTextField( // 文本输入框
                        value = textState, // 绑定输入状态
                        onValueChange = { // 输入变化回调
                            textState = it // 更新输入状态
                            conversation.draft = it.text // 同步保存为会话草稿
                        }, // 输入回调结束
                        modifier = Modifier.weight(1f), // 输入框占据剩余空间
                        placeholder = { Text("输入内容...") } // 占位提示文本
                    ) // 输入框结束
                    Spacer(modifier = Modifier.width(8.dp)) // 输入框与按钮之间留间隔
                    IconButton(onClick = { // 发送按钮点击事件
                        if (textState.text.isNotEmpty()) { // 文本非空才允许发送
                            CloudChatManager.sendMessage(conversationKey, textState.text) { success, message -> // 发送文本消息
                                if (success) { // 发送成功分支
                                    textState = TextFieldValue("") // 清空输入框
                                    conversation.draft = "" // 清空草稿
                                } else { // 发送失败分支
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 提示失败原因
                                } // 发送结果分支结束
                            } // 发送回调结束
                        } // 非空判断结束
                    }) { // 发送按钮内容
                        Icon(Icons.Default.Send, contentDescription = "发送") // 发送图标
                    } // 发送按钮结束
                    IconButton(onClick = { showExtraOptions = !showExtraOptions }) { // 点击切换扩展面板
                        Icon(Icons.Default.Add, contentDescription = "更多") // 更多图标
                    } // 更多按钮结束
                } // 主输入行结束
                if (showExtraOptions) { // 仅在展开时显示扩展功能
                    Row( // 扩展功能行
                        modifier = Modifier // 行修饰符链起点
                            .fillMaxWidth() // 占满宽度
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp), // 设置左右下边距
                        horizontalArrangement = Arrangement.SpaceEvenly // 扩展按钮均匀分布
                    ) { // 扩展行内容开始
                        ExtraOption(icon = Icons.Default.Image, text = "图片") { // 图片功能入口
                            showImageSourceDialog = true // 打开图片来源对话框
                        } // 图片入口结束
                        ExtraOption(icon = Icons.Default.Cloud, text = "天气") { // 天气功能入口
                            scope.launch { // 在协程中执行定位与网络请求
                                val location = LocationHelper.getCurrentLocation(context) // 获取当前位置
                                if (location != null) { // 定位成功分支
                                    val adcode = WeatherRepository.getCityCodeByLocation(location.longitude, location.latitude) // 经纬度转城市编码
                                    val realWeather = WeatherRepository.fetchWeather(adcode) // 获取真实天气文本
                                    CloudChatManager.sendMessage(conversationKey, realWeather, type = "weather") { success, message -> // 发送天气消息
                                        if (!success) { // 发送失败分支
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 提示失败原因
                                        } // 失败分支结束
                                    } // 发送回调结束
                                } else { // 定位失败分支
                                    locationPermissionLauncher.launch( // 触发定位权限请求
                                        arrayOf( // 申请的权限数组
                                            Manifest.permission.ACCESS_FINE_LOCATION, // 精确定位权限
                                            Manifest.permission.ACCESS_COARSE_LOCATION // 模糊定位权限
                                        ) // 权限数组结束
                                    ) // 启动权限请求结束
                                    val defaultWeather = WeatherRepository.fetchWeather("110000") // 拉取北京默认天气
                                    CloudChatManager.sendMessage(conversationKey, "[定位失败，默认北京] $defaultWeather", type = "weather") { success, message -> // 发送降级天气消息
                                        if (!success) { // 发送失败分支
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 提示失败原因
                                        } // 失败分支结束
                                    } // 发送回调结束
                                } // 定位分支结束
                            } // 协程结束
                        } // 天气入口结束
                    } // 扩展功能行结束
                } // 扩展面板分支结束
            } // bottomBar Column 结束
        } // bottomBar 结束
        ) { innerPadding -> // Scaffold 内容区并接收系统内边距
        LazyColumn( // 消息列表
            state = listState, // 绑定滚动状态
            modifier = Modifier // 列表修饰符链起点
                .fillMaxSize() // 占满可用空间
                .padding(innerPadding) // 应用 Scaffold 提供的内边距
                .padding(horizontal = 16.dp) // 左右再加内容间距
        ) { // LazyColumn 内容开始
            // 移除自定义 key，使用默认索引防止重复 key 闪退
            itemsIndexed(messages) { _, message -> // 按索引遍历消息列表
                MessageBubble( // 渲染单条消息气泡
                    message = message, // 传入消息实体
                    otherAvatar = conversation.avatar, // 传入当前会话对方头像
                    conversation = conversation // 传入会话对象（用于判断群聊显示）
                ) // MessageBubble 调用结束
            } // itemsIndexed 结束
        } // LazyColumn 结束
    } // Scaffold 内容结束
} // ConversationDetailScreen 结束

@Composable // 声明消息气泡为可组合函数
fun MessageBubble( // 单条消息气泡组件
    message: Message, // 当前消息对象
    otherAvatar: Any, // 对方头像数据（资源或 Uri）
    conversation: Conversation // 添加会话参数
) { // 函数体开始
    Row( // 整条消息的水平容器
        modifier = Modifier // 行修饰符链起点
            .fillMaxWidth() // 占满宽度以便左右对齐
            .padding(vertical = 8.dp), // 每条消息上下留间距
        horizontalArrangement = if (message.sender == MessageSender.ME) Arrangement.End else Arrangement.Start // 我方靠右、对方靠左
    ) { // Row 内容开始
        if (message.sender == MessageSender.OTHER) { // 对方消息时显示左侧头像
            AsyncImage( // 渲染对方头像
                model = otherAvatar, // 头像数据源
                contentDescription = "对方头像", // 无障碍描述
                modifier = Modifier // 头像修饰符链起点
                    .size(40.dp) // 头像尺寸
                    .clip(CircleShape), // 裁剪为圆形
                contentScale = ContentScale.Crop // 居中裁剪填充
            ) // 对方头像结束
            Spacer(modifier = Modifier.width(8.dp)) // 头像与气泡之间间隔
        } // 对方头像分支结束
        Column( // 右侧内容列（昵称+气泡）
            modifier = Modifier.weight(1f, fill = false) // 内容列自适应宽度
        ) { // Column 内容开始
            // 群聊中显示发送者名字
            if (conversation.chatType == "group" && message.sender == MessageSender.OTHER) { // 群聊且对方消息时展示昵称
                // ✅ 发送者名字为空时显示 UID 或默认值
                val displayName = message.senderName.ifBlank { "未知用户" } // 兜底发送者名字
                Text( // 发送者昵称文本
                    text = displayName, // 昵称内容
                    fontSize = 12.sp, // 昵称字号
                    color = Color.Gray, // 昵称颜色
                    modifier = Modifier.padding(bottom = 4.dp) // 与气泡留间距
                ) // 昵称文本结束
            } // 群聊昵称分支结束
            Box( // 消息气泡容器
                modifier = Modifier // 气泡修饰符链起点
                    .background( // 设置气泡背景
                        if (message.sender == MessageSender.ME) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, // 我方与对方不同配色
                        shape = RoundedCornerShape(8.dp) // 气泡圆角
                    ) // 背景设置结束
                    .padding(12.dp) // 气泡内边距
            ) { // Box 内容开始
                when (message.type) { // 按消息类型分发渲染
                    "text" -> Text(text = message.content) // 文本消息直接显示
                    "image" -> AsyncImage( // 图片消息展示图片组件
                        model = message.imageUri ?: message.imageRes, // 优先 Uri，回退到本地资源
                        contentDescription = "Image", // 无障碍描述
                        modifier = Modifier.size(150.dp), // 图片尺寸
                        contentScale = ContentScale.Crop // 图片裁剪模式
                    ) // 图片组件结束
                    "weather" -> Text(text = message.content) // 天气消息按文本显示
                    else -> Text(text = message.content) // 未知类型兜底按文本显示
                } // 类型分发结束
            } // 气泡容器结束
        } // 内容列结束
        if (message.sender == MessageSender.ME) { // 我方消息时显示右侧我的头像
            Spacer(modifier = Modifier.width(8.dp)) // 气泡与头像之间留间隔
            AsyncImage( // 渲染我的头像
                model = DataSource.myAvatar, // 我的头像数据源
                contentDescription = "我的头像", // 无障碍描述
                modifier = Modifier // 头像修饰符链起点
                    .size(40.dp) // 头像尺寸
                    .clip(CircleShape), // 裁剪为圆形
                contentScale = ContentScale.Crop // 居中裁剪填充
            ) // 我的头像结束
        } // 我方头像分支结束
    } // Row 结束
} // MessageBubble 结束

@Composable // 声明扩展选项为可组合函数
fun ExtraOption(icon: ImageVector, text: String, onClick: () -> Unit) { // 扩展功能按钮组件
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) { // 图标与文字纵向排列
        IconButton(onClick = onClick) { Icon(icon, contentDescription = text) } // 上方图标按钮
        Text(text = text) // 下方文字说明
    } // Column 结束
} // ExtraOption 结束
