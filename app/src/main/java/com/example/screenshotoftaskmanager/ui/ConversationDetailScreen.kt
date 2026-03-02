package com.example.screenshotoftaskmanager.ui // 定义包名

import android.net.Uri // 导入处理图片路径的 Uri 类
import androidx.activity.compose.rememberLauncherForActivityResult // 导入用于处理 Activity 结果的 Launcher
import androidx.activity.result.PickVisualMediaRequest // 导入图片选择请求类
import androidx.activity.result.contract.ActivityResultContracts // 导入 Activity 结果协议
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.layout.Arrangement // 导入布局对齐方式
import androidx.compose.foundation.layout.Box // 导入 Box 布局
import androidx.compose.foundation.layout.Column // 导入 Column 布局
import androidx.compose.foundation.layout.Row // 导入 Row 布局
import androidx.compose.foundation.layout.Spacer // 导入间距组件
import androidx.compose.foundation.layout.fillMaxSize // 导入填充最大尺寸修饰符
import androidx.compose.foundation.layout.fillMaxWidth // 导入填充最大宽度修饰符
import androidx.compose.foundation.layout.height // 导入高度修饰符
import androidx.compose.foundation.layout.padding // 导入内边距修饰符
import androidx.compose.foundation.layout.size // 导入尺寸修饰符
import androidx.compose.foundation.layout.width // 导入宽度修饰符
import androidx.compose.foundation.lazy.LazyColumn // 导入延迟列表组件
import androidx.compose.foundation.lazy.items // 导入列表项函数
import androidx.compose.foundation.lazy.rememberLazyListState // 导入列表状态记录函数
import androidx.compose.foundation.shape.CircleShape // 导入圆形形状
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角矩形形状
import androidx.compose.material.icons.Icons // 导入图标库
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 导入自适应方向的返回图标
import androidx.compose.material.icons.filled.Add // 导入添加图标
import androidx.compose.material.icons.filled.Cloud // 导入云朵图标
import androidx.compose.material.icons.filled.Image // 导入图片图标
import androidx.compose.material.icons.filled.Send // 导入发送图标
import androidx.compose.material3.AlertDialog // 导入对话框组件
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API 标记
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮组件
import androidx.compose.material3.MaterialTheme // 导入主题库
import androidx.compose.material3.OutlinedTextField // 导入外轮廓输入框组件
import androidx.compose.material3.Scaffold // 导入脚手架布局
import androidx.compose.material3.Text // 导入文本组件
import androidx.compose.material3.TextButton // 导入文本按钮组件
import androidx.compose.material3.TopAppBar // 导入顶部应用栏组件
import androidx.compose.runtime.Composable // 导入可组合函数注解
import androidx.compose.runtime.LaunchedEffect // 导入副作用处理函数
import androidx.compose.runtime.getValue // 导入属性读取委托
import androidx.compose.runtime.mutableStateOf // 导入可变状态创建函数
import androidx.compose.runtime.remember // 导入状态记录函数
import androidx.compose.runtime.rememberCoroutineScope // ✅ 新增：导入协程作用域记录函数
import androidx.compose.runtime.setValue // 导入属性设置委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入修饰符接口
import androidx.compose.ui.draw.clip // 导入裁剪修饰符
import androidx.compose.ui.graphics.Color // 导入颜色类
import androidx.compose.ui.graphics.vector.ImageVector // 导入矢量图类
import androidx.compose.ui.layout.ContentScale // 导入内容缩放方式
import androidx.compose.ui.text.input.TextFieldValue // 导入输入框状态值类
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.navigation.NavController // 导入导航控制器类
import coil.compose.AsyncImage // 导入异步图片加载组件
import kotlinx.coroutines.launch // ✅ 新增：导入协程启动函数

@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 Material3 API
@Composable // 声明为可组合函数
fun ConversationDetailScreen(navController: NavController, conversationName: String) { // 聊天详情页，接收导航控制器和联系人姓名
    
    // 获取完整的会话对象
    val conversation = remember(conversationName) { DataSource.getConversation(conversationName) } // 根据姓名获取会话对象
    val messages = conversation.messages // 获取该会话的消息列表
    
    // ✅ 新增：获取协程作用域，用于发起网络请求
    val scope = rememberCoroutineScope() 

    // 只要进入此页面，立即将未读消息数重置为 0
    LaunchedEffect(Unit) { // Unit 表示只在组件首次加载时执行一次
        conversation.unreadCount = 0 // 清空未读数，这会立即触发列表页的 UI 更新（红点消失）
    }

    // 使用会话对象中保存的草稿来初始化输入框的状态
    var textState by remember { mutableStateOf(TextFieldValue(conversation.draft)) } // 初始化输入框，内容为已有的草稿
    
    var showExtraOptions by remember { mutableStateOf(false) } // 控制是否显示更多选项（图片、天气）
    val listState = rememberLazyListState() // 记录聊天列表的滚动状态
    var showImageSourceDialog by remember { mutableStateOf(false) } // 控制是否显示图片来源选择对话框

    // 创建相册图片选择器的启动器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(), // 设置合同类型
        onResult = { uri: Uri? -> // 选择结果回调
            uri?.let { // 如果选择了图片
                messages.add(Message(MessageSender.ME, "[图片]", type = "image", imageUri = it.toString())) // 添加图片消息
            }
        }
    )

    // 当消息列表大小改变时，自动滚动到底部
    LaunchedEffect(messages.size) { // 监听消息数量变化
        if (messages.isNotEmpty()) { // 如果消息列表不为空
            listState.animateScrollToItem(messages.size - 1) // 动画滚动到最后一条
        }
    }

    // 图片来源选择对话框逻辑
    if (showImageSourceDialog) { // 如果需要显示对话框
        AlertDialog( // 创建对话框
            onDismissRequest = { showImageSourceDialog = false }, // 点击外部消失
            title = { Text("选择图片来源") }, // 标题
            text = { Text("你想从哪里发送图片？") }, // 内容
            confirmButton = { // 本机相册按钮
                TextButton(onClick = {
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    showImageSourceDialog = false
                }) { Text("本机相册") }
            },
            dismissButton = { // 随机照片按钮
                TextButton(onClick = {
                    val randomImage = DataSource.drawableResources.random()
                    messages.add(Message(MessageSender.ME, "[图片]", type = "image", imageRes = randomImage))
                    showImageSourceDialog = false
                }) { Text("随机照片") }
            }
        )
    }

    Scaffold( // 页面布局
        topBar = {
            TopAppBar(
                title = { Text(conversationName) }, // 标题
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // 返回按钮
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = { // 底部输入栏
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textState,
                        onValueChange = {
                            textState = it
                            conversation.draft = it.text // 实时同步草稿到数据源
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入内容...") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (textState.text.isNotEmpty()) {
                            messages.add(Message(MessageSender.ME, textState.text)) // 发送消息
                            textState = TextFieldValue("") // 清空输入框
                            conversation.draft = "" // 同步清空数据源中的草稿
                        }
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "发送")
                    }
                    IconButton(onClick = { showExtraOptions = !showExtraOptions }) {
                        Icon(Icons.Default.Add, contentDescription = "更多")
                    }
                }
                if (showExtraOptions) { // 显示更多功能
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExtraOption(icon = Icons.Default.Image, text = "图片") { showImageSourceDialog = true }
                        ExtraOption(icon = Icons.Default.Cloud, text = "天气") {
                            // ✅ 关键改动：使用协程从网上抓取真实天气
                            scope.launch {
                                val realWeather = WeatherRepository.fetchWeather("110000") // 抓取北京的天气
                                messages.add(Message(MessageSender.ME, realWeather, type = "weather")) // 发送真实天气消息
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn( // 消息列表
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
        ) {
            items(messages) { message -> MessageBubble(message = message) }
        }
    }
}

@Composable
fun MessageBubble(message: Message) { // 消息气泡组件
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = if (message.sender == MessageSender.ME) Arrangement.End else Arrangement.Start
    ) {
        if (message.sender == MessageSender.OTHER) { // 如果是对方发的消息，显示其头像
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)) // 对方头像暂时用占位符
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box( // 消息气泡背景
            modifier = Modifier.background(
                if (message.sender == MessageSender.ME) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ).padding(12.dp)
        ) {
            when (message.type) {
                "text" -> Text(text = message.content)
                "image" -> AsyncImage(model = message.imageUri ?: message.imageRes, contentDescription = "Image", modifier = Modifier.size(150.dp), contentScale = ContentScale.Crop)
                "weather" -> {
                    // ✅ 关键改动：天气气泡现在显示的是实时抓取的内容
                    Text(text = message.content)
                }
            }
        }
        if (message.sender == MessageSender.ME) { // ✅ 改动点：如果是“我”发的消息，显示我的同步头像
            Spacer(modifier = Modifier.width(8.dp))
            // ✅ 使用 AsyncImage 来加载 DataSource.myAvatar 里的内容
            AsyncImage(
                model = DataSource.myAvatar, // 读取全局实时更新的我的头像
                contentDescription = "我的头像",
                modifier = Modifier
                    .size(40.dp) // 头像大小设为 40dp
                    .clip(CircleShape), // 裁剪成圆形
                contentScale = ContentScale.Crop // 缩放并裁剪，保证铺满圆形
            )
        }
    }
}

@Composable
fun ExtraOption(icon: ImageVector, text: String, onClick: () -> Unit) { // 更多功能按钮
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = text) }
        Text(text = text)
    }
}
