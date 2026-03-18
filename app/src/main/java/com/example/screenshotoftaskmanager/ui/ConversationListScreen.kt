package com.example.screenshotoftaskmanager.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.screenshotoftaskmanager.CloudChatManager
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class) // 标记此函数使用了实验性的 Material3 API
@Composable // 声明这是一个 Jetpack Compose 的可组合函数
fun ConversationListScreen(navController: NavController) { // 定义会话列表屏幕，接收一个 NavController 用于导航
    val context = LocalContext.current
    
    // 搜索添加好友的状态
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var searchStatus by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var foundUser by remember { mutableStateOf<com.example.screenshotoftaskmanager.User?>(null) }

    DisposableEffect(Unit) {
        val registration = CloudChatManager.listenMyConversations(
            onChange = { cloudConversations ->
                DataSource.replaceConversations(cloudConversations)
            },
            onError = { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )

        onDispose {
            registration?.remove()
        }
    }

    val sortedConversations: List<Conversation> = DataSource.conversations.sortedWith(
        compareByDescending<Conversation> { it.isPinned }
            .thenByDescending { it.lastTimestamp }
    )

    // 搜索并添加好友的对话框
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = {
                showSearchDialog = false
                searchInput = ""
                searchStatus = ""
                foundUser = null
                isSearching = false
            },
            title = { Text("添加好友") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("输入好友ID") },
                        enabled = !isSearching,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (searchStatus.isNotEmpty()) {
                        androidx.compose.material3.Text(
                            text = searchStatus,
                            fontSize = 12.sp,
                            color = if (foundUser != null) Color.Green else Color.Red,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (foundUser != null) {
                        androidx.compose.material3.Text(
                            text = "找到用户：${foundUser!!.username}",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (foundUser != null) {
                            // 添加好友
                            isSearching = true
                            CloudChatManager.createOrUpdateConversation(
                                otherUserUid = foundUser!!.uid,
                                otherUsername = foundUser!!.username,
                                onComplete = { success, message ->
                                    isSearching = false
                                    if (success) {
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        showSearchDialog = false
                                        searchInput = ""
                                        searchStatus = ""
                                        foundUser = null
                                    } else {
                                        searchStatus = message
                                    }
                                }
                            )
                        } else {
                            // 执行搜索
                            isSearching = true
                            CloudChatManager.searchUser(searchInput) { user, message ->
                                isSearching = false
                                foundUser = user
                                searchStatus = message
                            }
                        }
                    },
                    enabled = !isSearching
                ) {
                    Text(if (foundUser != null) "添加好友" else "搜索")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSearchDialog = false
                        searchInput = ""
                        searchStatus = ""
                        foundUser = null
                        isSearching = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold( // 使用 Material3 的 Scaffold 脚手架布局
        topBar = { // 定义顶部应用栏
            TopAppBar( // 使用 TopAppBar 组件
                title = { Text("消息") }, // 设置标题为"消息"
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary // 设置背景颜色为深蓝色
                ),
                actions = { // 定义右侧的操作按钮
                    IconButton(onClick = {
                        // 打开搜索添加好友对话框
                        showSearchDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加好友")
                    }
                }
            )
        }
    ) { innerPadding -> // Scaffold 的内容区域，innerPadding 用于处理 TopAppBar 遮挡
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(
                items = sortedConversations,
                key = { it.otherUserUid.ifBlank { it.name } }
            ) { conversation ->
                ConversationListItem(
                    conversation = conversation,
                    onClick = {
                        if (conversation.otherUserUid.isNotBlank()) {
                            navController.navigate("conversation_detail/${conversation.otherUserUid}")
                        } else {
                            Toast.makeText(context, "当前会话缺少用户标识，暂时无法进入", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable // 声明这是一个 Jetpack Compose 的可组合函数
fun ConversationListItem( // 定义单个会话列表项的 UI
    conversation: Conversation, // 接收会话数据 是函数的参数
    onClick: () -> Unit // 接收点击事件的回调 定义了一个点击事件的回调函数
) {
    val scope = rememberCoroutineScope() // 获取一个协程作用域，用于启动协程加载会话相关数据/动画等操作
    val density = LocalDensity.current // 获取当前的屏幕密度，用于 dp 和 px 之间的转换 不同设备密度不同实现页面正确适配

    // 两个按钮总宽度（置顶80 + 删除80）
    // 使用 remember 记住计算出的像素值，避免重复计算
    val openPx = remember(density) { with(density) { 160.dp.toPx() } } // 将 160.dp 转换为像素值  将屏幕密度转化为像素值 统一！

    // ✅ 用 Animatable 自己管理横向偏移：范围 [-openPx, 0]
    // 创建一个 Animatable 值来控制横向偏移，初始值为 0
    val offsetX = remember { Animatable(0f) }    //实现动画效果 改变偏移值 保证动画过程中偏移值的连续性和一致性。 支持拖拽跟手松手吸附点击收回动画丝滑！！

    fun clamp(x: Float): Float = x.coerceIn(-openPx, 0f) // 定义一个clamp函数，将偏移量限制在 [-openPx, 0f] 范围内 最多打开到按钮全部露出

    Box( // 使用 Box 布局，允许子组件堆叠
        modifier = Modifier
            .fillMaxWidth() // 填充父容器的宽度
            .padding(vertical = 4.dp) // 设置垂直方向的内边距
    ) {
        // 背景按钮层（在右侧） 实现会话列表交互功能
        Row( // 使用 Row 布局将按钮水平排列
            modifier = Modifier
                .fillMaxHeight() // 填充父容器的高度
                .align(Alignment.CenterEnd), // 对齐到父容器的末尾（右侧）
            verticalAlignment = Alignment.CenterVertically // 子项在垂直方向上居中对齐
        ) {
            // 置顶按钮
            Box( // 使用 Box 替代 IconButton 以获得完整的高度控制
                modifier = Modifier
                    .width(80.dp) // 设置按钮宽度为 80.dp
                    .fillMaxHeight() // 填充父容器的高度，使按钮高度满满的
                    .background(MaterialTheme.colorScheme.tertiaryContainer) // 设置背景颜色
                    .clickable { // 定义点击事件
                        conversation.isPinned = !conversation.isPinned // 切换会话的置顶状态
                        scope.launch { // 启动一个协程
                            offsetX.animateTo(0f, tween(180)) // 以动画方式将卡片移回原位 targetvalue动画目标值 动画规格、动画持续时间Tween 是基于时间的渐变动画，在起始值和结束值间创建平滑过渡，通过设定动画时长、延迟时间和缓动曲线来定义动画变化过程。
                        }
                    },
                contentAlignment = Alignment.Center // 内容居中对齐
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { // 使用 Column 将图标和文字垂直排列
                    Icon(Icons.Filled.PushPin, contentDescription = "置顶") // 显示置顶图标
                    Text("置顶", fontSize = 12.sp) // 显示"置顶"文字
                }
            }

            // 删除按钮
            Box( // 使用 Box 替代 IconButton 以获得完整的高度控制
                modifier = Modifier
                    .width(80.dp) // 设置按钮宽度为 80.dp
                    .fillMaxHeight() // 填充父容器的高度，使按钮高度满满的
                    .background(MaterialTheme.colorScheme.errorContainer) // 设置背景颜色
                    .clickable { // 定义点击事件
                        DataSource.conversations.remove(conversation) // 从数据源中删除此会话
                    },
                contentAlignment = Alignment.Center // 内容居中对齐
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { // 使用 Column 将图标和文字垂直排列
                    Icon(Icons.Filled.Delete, contentDescription = "删除") // 显示删除图标
                    Text("删除", fontSize = 12.sp) // 显示"删除"文字
                }
            }
        }

        // 前景卡片层（可拖拽）
        Card( // 使用 Card 组件作为前景 让每个聊天都在一个框框里！！！！！ 卡片容器组件
            modifier = Modifier
                .fillMaxWidth() // 填充父容器的宽度
                .offset { IntOffset(offsetX.value.roundToInt(), 0) } // 根据 offsetX 的值来偏移卡片位置
                .draggable( // 使卡片可以水平拖动
                    orientation = Orientation.Horizontal, // 设置拖动方向为水平
                    state = rememberDraggableState { delta -> // 记住拖动状态
                        // delta>0 向右拖，delta<0 向左拖
                        scope.launch { // 在协程中处理拖动
                            offsetX.snapTo(clamp(offsetX.value + delta)) // 立即更新偏移量，并限制在范围内 snapto 拖拽跟手
                        }
                    },
                    onDragStopped = { velocity -> // 拖动停止时的回调函数
                        // ✅ 松手后自动“吸附”：超过一半就打开，否则关闭
                        scope.launch { // 在协程中处理吸附动画
                            // 判断是否应该打开：偏移量超过一半，或者向左的快速滑动
                            val shouldOpen = abs(offsetX.value) > openPx * 0.5f || velocity < -800f    //偏移量大于多少 速度小于800 负数因为是负方向
                            val target = if (shouldOpen) -openPx else 0f // 设置目标位置（完全打开或完全关闭）
                            offsetX.animateTo(target, tween(180)) // 以动画方式移动到目标位置
                        }
                    }
                )
                .clickable { // 给卡片添加点击事件
                    // 如果已经打开，点一下先收回；否则进入详情
                    scope.launch { // 在协程中处理点击逻辑
                        if (offsetX.value != 0f) offsetX.animateTo(0f, tween(160)) // 如果已侧滑，则收回
                        else onClick() // 否则执行传入的 onClick 回调（进入详情页）
                    }
                }
        ) {
            Row( // 使用 Row 布局排列卡片内容，从左到右依次为头像、名字和消息摘要、时间和未读数
                modifier = Modifier.padding(16.dp), // 设置内边距
                verticalAlignment = Alignment.CenterVertically // 子项在垂直方向上居中对齐
            ) {
                AsyncImage( // 显示会话对方的圆形头像
                    model = conversation.avatar, // 头像资源，可以是 drawable 或 URI
                    contentDescription = "对方头像", // 内容描述，用于无障碍访问
                    modifier = Modifier // 修饰符
                        .size(40.dp) // 设置头像大小为 40dp
                        .clip(CircleShape), // 裁剪为圆形
                    contentScale = ContentScale.Crop // 缩放模式为裁剪，填满容器
                )

                Spacer(modifier = Modifier.width(8.dp)) // 添加水平间隔，距离头像 8dp

                Column(modifier = Modifier.weight(1f)) { // 中间内容区域，占据剩余空间，包含名字和消息摘要
                    Row(verticalAlignment = Alignment.CenterVertically) { // 名字和置顶图标行
                        Text( // 显示会话名称
                            text = conversation.name, // 会话名字
                            fontWeight = FontWeight.Bold, // 字体加粗
                            fontSize = 16.sp // 字体大小 16sp
                        )
                        if (conversation.isPinned) { // 如果会话已置顶
                            Spacer(modifier = Modifier.width(6.dp)) // 添加间隔
                            Icon( // 显示置顶图标
                                imageVector = Icons.Filled.PushPin, // 置顶图标
                                contentDescription = "已置顶", // 内容描述
                                modifier = Modifier.size(16.dp), // 图标大小
                                tint = MaterialTheme.colorScheme.primary // 图标颜色
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp)) // 添加垂直间隔，距离名字 4dp

                    val summaryText = if (conversation.draft.isNotEmpty()) { // 判断是否有草稿
                        "[草稿] ${conversation.draft}" // 如果有，显示草稿内容
                    } else {
                        conversation.previewText.ifBlank { "还没有消息" } // 否则显示云端同步过来的最后一条消息摘要
                    }

                    val summaryColor = if (conversation.draft.isNotEmpty()) { // 判断是否有草稿
                        MaterialTheme.colorScheme.error // 如果有，摘要文本颜色使用错误提示色
                    } else {
                        Color.Gray // 否则使用灰色
                    }

                    Text( // 显示消息摘要文本
                        text = summaryText, // 摘要内容
                        color = summaryColor, // 设置文本颜色
                        fontSize = 14.sp, // 设置字体大小 14sp
                        maxLines = 1, // 最多显示一行
                        overflow = TextOverflow.Ellipsis // 超出部分显示省略号
                    )
                }

                Spacer(modifier = Modifier.width(16.dp)) // 添加水平间隔，距离中间内容 16dp

                Column(horizontalAlignment = Alignment.End) { // 右侧时间与未读数区域，对齐到末尾
                    Text("12:11", color = Color.Gray, fontSize = 12.sp) // 显示消息时间（这里是硬编码的）
                    Spacer(modifier = Modifier.height(4.dp)) // 添加垂直间隔
                    if (conversation.unreadCount > 0) { // 如果有未读消息
                        Badge { Text(text = "${conversation.unreadCount}") } // 使用 Badge 显示未读消息数量
                    }
                }
            }
        }
    }
}
