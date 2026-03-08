package com.example.screenshotoftaskmanager.ui // 定义包名

import android.Manifest // 导入权限常量
import android.net.Uri // 导入 Uri 类
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
import androidx.compose.foundation.layout.height // 导入高度
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.size // 导入尺寸
import androidx.compose.foundation.layout.width // 导入宽度
import androidx.compose.foundation.lazy.LazyColumn // 导入列表
import androidx.compose.foundation.lazy.items // 导入项
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
import androidx.compose.ui.platform.LocalContext // ✅ 关键：导入获取当前上下文的函数
import androidx.compose.ui.text.input.TextFieldValue // 导入输入框值
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.navigation.NavController // 导入导航控制器
import coil.compose.AsyncImage // 导入异步图片加载
import kotlinx.coroutines.launch // 导入协程启动

@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 声明可组合函数
fun ConversationDetailScreen(navController: NavController, conversationName: String) { // 详情页入口
    
    val context = LocalContext.current // 获取当前 Android 上下文，用于位置请求
    val conversation = remember(conversationName) { DataSource.getConversation(conversationName) } // 通过datasource获取会话对象
    val messages = conversation.messages // 获取消息列表
    val scope = rememberCoroutineScope() // 获取协程范围  用于启动协程如查天气需要同步

    // ✅ 新增：定义定位权限申请的启动器
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(), // 同时申请精确定位和粗略定位 请求多个权限
        onResult = { permissions -> // 申请结果回调
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true   //粗略定位和精确定位任意一个为true就为成功
            if (granted) {
                // 用户授权后可以再次点击天气尝试获取
            }
        }
    )

    // 重置未读数
    LaunchedEffect(Unit) {   //只在第一次进入这个compose时把未读数清0
        conversation.unreadCount = 0
    }

    var textState by remember { mutableStateOf(TextFieldValue(conversation.draft)) } // 输入框状态包含光标 就是草稿恢复
    var showExtraOptions by remember { mutableStateOf(false) } // 更多选项显示开关  是否显示图片天气那一行
    val listState = rememberLazyListState() // 滚动状态自动滚到最后一条
    var showImageSourceDialog by remember { mutableStateOf(false) } // 图片对话框开关是否弹出选图片来源的对话框

    // 图片选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { messages.add(Message(MessageSender.ME, "[图片]", type = "image", imageUri = it.toString())) }
        }
    )

    // 自动滚动
    LaunchedEffect(messages.size) {    //变了（增加了新消息）就自动滚动到最后
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 图片对话框
    if (showImageSourceDialog) {
        AlertDialog(    //弹出对话框
            onDismissRequest = { showImageSourceDialog = false },   //为true时弹出
            title = { Text("选择图片来源") },
            text = { Text("你想从哪里发送图片？") },
            confirmButton = {     //确认按钮
                TextButton(onClick = {      //调用系统功能并返回结果photopicker 选图片  pickvisualmedia就是调用系统图库
                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    showImageSourceDialog = false
                }) { Text("本机相册") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val randomImage = DataSource.drawableResources.random()    //随机选择照片
                    messages.add(Message(MessageSender.ME, "[图片]", type = "image", imageRes = randomImage))
                    showImageSourceDialog = false
                }) { Text("随机照片") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversationName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {    //返回按钮可以返回上一页
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(     //聊天区域概述
                        value = textState,
                        onValueChange = {
                            textState = it
                            conversation.draft = it.text    //同步写入草稿草稿实时保存
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入内容...") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (textState.text.isNotEmpty()) {
                            messages.add(Message(MessageSender.ME, textState.text))
                            textState = TextFieldValue("")  //发出去之后聊天框清零
                            conversation.draft = ""    //非空才发出去消息 发出去以后草稿清零
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
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ExtraOption(icon = Icons.Default.Image, text = "图片") { showImageSourceDialog = true }
                        
                        // ✅ 关键改动：点击天气按钮执行自动定位并获取天气
                        ExtraOption(icon = Icons.Default.Cloud, text = "天气") {
                            scope.launch {
                                // 1. 先尝试获取 GPS 位置
                                val location = LocationHelper.getCurrentLocation(context)
                                if (location != null) {
                                    // 2. 如果拿到经纬度，先去高德换取城市 adcode
                                    val adcode = WeatherRepository.getCityCodeByLocation(location.longitude, location.latitude)  //用经纬度换城市邮编
                                    // 3. 再拿着 adcode 去查真实天气
                                    val realWeather = WeatherRepository.fetchWeather(adcode)  //用邮编换天气
                                    messages.add(Message(MessageSender.ME, realWeather, type = "weather"))
                                } else {
                                    // 4. 如果位置为空，可能是没权限或 GPS 没开，申请权限提示用户
                                    locationPermissionLauncher.launch(   //失败可能是没开定位 弹窗允许定位
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                    // 兜底方案：显示北京天气
                                    val defaultWeather = WeatherRepository.fetchWeather("110000")
                                    messages.add(Message(MessageSender.ME, "[定位失败，默认北京] $defaultWeather", type = "weather"))
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
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
        ) {
            items(messages) { message -> MessageBubble(message = message) }   //每一条都用messagebubble渲染
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = if (message.sender == MessageSender.ME) Arrangement.End else Arrangement.Start   //自己消息靠右 对方消息靠左
    ) {
        if (message.sender == MessageSender.OTHER) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray))  //对方消息颜色是亮灰色
            Spacer(modifier = Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier.background(
                if (message.sender == MessageSender.ME) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)     //自己用pri 对方用sec
            ).padding(12.dp)
        ) {
            when (message.type) {   //不同消息类型
                "text" -> Text(text = message.content)             //下一行异步加载图片
                "image" -> AsyncImage(model = message.imageUri ?: message.imageRes, contentDescription = "Image", modifier = Modifier.size(150.dp), contentScale = ContentScale.Crop)
                "weather" -> Text(text = message.content)
            }
        }
        if (message.sender == MessageSender.ME) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(               //为什么要后台异步加载图片 因为图片加载很慢 不能阻塞ui线程 安卓只有一个ui主线程
                model = DataSource.myAvatar,
                contentDescription = "我的头像",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop     //contentscale  图片缩放模式 crop：填满容器其他多余部分裁剪掉
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
