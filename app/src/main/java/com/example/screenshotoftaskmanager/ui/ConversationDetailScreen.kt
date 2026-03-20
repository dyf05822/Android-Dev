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
    val conversationKey = if (conversation.chatType == "group" && conversation.chatId.isNotBlank()) {
        conversation.chatId
    } else {
        otherUserUid
    }

    // 定位权限申请器
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (!granted) {
                Toast.makeText(context, "未授予定位权限，将使用默认天气", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // 进入会话时清空未读数
    LaunchedEffect(conversationKey) {
        conversation.unreadCount = 0
    }

    // 输入框状态，初始值来自草稿
    var textState by remember(conversationKey) { mutableStateOf(TextFieldValue(conversation.draft)) }

    // 更多选项显示开关
    var showExtraOptions by remember { mutableStateOf(false) }

    // 列表滚动状态
    val listState = rememberLazyListState()

    // 图片来源对话框开关
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // 更换对方头像对话框开关
    var showChangeAvatarDialog by remember { mutableStateOf(false) }

    // 图片选择器：图片本体暂不上传云端，这里发送统一占位文本 [图片]
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                CloudChatManager.sendMessage(conversationKey, "[图片]") { success, message ->
                    if (!success) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    // 对方头像选择器：仅影响本机 UI 展示，不上传云端
    val otherPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                conversation.avatar = it.toString()
                showChangeAvatarDialog = false
            }
        }
    )

    // 页面展示时实时监听云端消息，并同步回 DataSource 的运行时会话缓存中
    DisposableEffect(conversationKey) {
        val registration = CloudChatManager.listenMessagesForConversation(
            otherUserUid = conversationKey,
            onChange = { cloudMessages ->
                val latestConversation = DataSource.getOrCreateConversation(conversationKey, conversation.name)
                latestConversation.messages.clear()
                latestConversation.messages.addAll(cloudMessages)
                latestConversation.previewText = cloudMessages.lastOrNull()?.content ?: "还没有消息"
                latestConversation.lastTimestamp = System.currentTimeMillis()
            },
            onError = { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )

        onDispose {
            registration?.remove()
        }
    }

    // 有新消息时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 图片来源对话框
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("选择图片来源") },
            text = { Text("你想从哪里发送图片？") },
            confirmButton = {
                TextButton(onClick = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    showImageSourceDialog = false
                }) {
                    Text("本机相册")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    CloudChatManager.sendMessage(conversationKey, "[图片]") { success, message ->
                        if (!success) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    showImageSourceDialog = false
                }) {
                    Text("随机照片")
                }
            }
        )
    }

    // 更换对方头像对话框
    if (showChangeAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showChangeAvatarDialog = false },
            title = { Text("更换对方头像") },
            text = { Text("请选择头像来源：") },
            confirmButton = {
                TextButton(
                    onClick = {
                        otherPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("本机相册")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val randomRes = DataSource.profileResources.random()
                        conversation.avatar = randomRes
                        showChangeAvatarDialog = false
                    }
                ) {
                    Text("随机头像")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // 根据聊天类型显示不同的标题
                title = { 
                    // 如果是群聊，显示群名 + 成员数
                    if (conversation.chatType == "group") {
                        Column {
                            // ✅ 群名为空时显示默认值
                            Text(conversation.groupName.ifBlank { "群聊" })
                            Text(
                                // ✅ 使用真实的 participants 列表，而不是消息发送者数
                                text = "成员数: ${conversation.participants.size}",
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        // 如果是一对一，显示用户名
                        Text(conversation.name)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showChangeAvatarDialog = true }) {
                        Icon(Icons.Default.Image, contentDescription = "更换对方头像")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textState,
                        onValueChange = {
                            textState = it
                            conversation.draft = it.text
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入内容...") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (textState.text.isNotEmpty()) {
                            CloudChatManager.sendMessage(conversationKey, textState.text) { success, message ->
                                if (success) {
                                    textState = TextFieldValue("")
                                    conversation.draft = ""
                                } else {
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                    IconButton(onClick = { showExtraOptions = !showExtraOptions }) {
                        Icon(Icons.Default.Add, contentDescription = "更多")
                    }
                }
                if (showExtraOptions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExtraOption(icon = Icons.Default.Image, text = "图片") {
                            showImageSourceDialog = true
                        }
                        ExtraOption(icon = Icons.Default.Cloud, text = "天气") {
                            scope.launch {
                                val location = LocationHelper.getCurrentLocation(context)
                                if (location != null) {
                                    val adcode = WeatherRepository.getCityCodeByLocation(location.longitude, location.latitude)
                                    val realWeather = WeatherRepository.fetchWeather(adcode)
                                    CloudChatManager.sendMessage(conversationKey, realWeather, type = "weather") { success, message ->
                                        if (!success) {
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                    val defaultWeather = WeatherRepository.fetchWeather("110000")
                                    CloudChatManager.sendMessage(conversationKey, "[定位失败，默认北京] $defaultWeather", type = "weather") { success, message ->
                                        if (!success) {
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 移除自定义 key，使用默认索引防止重复 key 闪退
            itemsIndexed(messages) { _, message ->
                MessageBubble(
                    message = message,
                    otherAvatar = conversation.avatar,
                    conversation = conversation
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    otherAvatar: Any,
    conversation: Conversation // 添加会话参数
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (message.sender == MessageSender.ME) Arrangement.End else Arrangement.Start
    ) {
        if (message.sender == MessageSender.OTHER) {
            AsyncImage(
                model = otherAvatar,
                contentDescription = "对方头像",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // 群聊中显示发送者名字
            if (conversation.chatType == "group" && message.sender == MessageSender.OTHER) {
                // ✅ 发送者名字为空时显示 UID 或默认值
                val displayName = message.senderName.ifBlank { "未知用户" }
                Text(
                    text = displayName,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        if (message.sender == MessageSender.ME) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                when (message.type) {
                    "text" -> Text(text = message.content)
                    "image" -> AsyncImage(
                        model = message.imageUri ?: message.imageRes,
                        contentDescription = "Image",
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop
                    )
                    "weather" -> Text(text = message.content)
                    else -> Text(text = message.content)
                }
            }
        }
        if (message.sender == MessageSender.ME) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = DataSource.myAvatar,
                contentDescription = "我的头像",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun ExtraOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = text) }
        Text(text = text)
    }
}
