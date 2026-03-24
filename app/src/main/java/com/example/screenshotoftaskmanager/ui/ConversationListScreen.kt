@file:Suppress("UNUSED_VALUE") // 文件内多处状态重置用于副作用，抑制“值未被读取”警告
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.MoreVert // 更多选项图标
import androidx.compose.material3.DropdownMenu // 下拉菜单组件
import androidx.compose.material3.DropdownMenuItem // 下拉菜单选项组件
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
import androidx.compose.runtime.LaunchedEffect
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

    // 下拉菜单显示状态
    var showMenuDropdown by remember { mutableStateOf(false) } // true 表示展开菜单

    DisposableEffect(Unit) {     //一次性作用
        // 页面进入即订阅会话流；离开页面时注销监听，避免重复监听和内存泄漏
        val registration = CloudChatManager.listenMyConversations(        //这里进入监听，这里为什么监听呢，在消息列表的时候就要开始状态的更新了，万一收到了他人发的消息就可以更新了
            onChange = { cloudConversations ->
                DataSource.replaceConversations(cloudConversations)
            },
            onError = { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        )

        onDispose {       //离开导航/界面销毁注销监听
            registration?.remove() // 注销 Firestore 监听注册器，防止页面离开后继续回调
        }
    }

    val sortedConversations: List<Conversation> = DataSource.conversations.sortedWith( // 对会话列表做二级排序：先置顶、后时间
        compareByDescending<Conversation> { it.isPinned } // 置顶会话排在前面
            .thenByDescending { it.lastTimestamp } // 同为置顶/非置顶时，最新消息时间靠前
    )

    // 搜索并添加好友的对话框
    if (showSearchDialog) { // 当状态为 true 时展示搜索弹窗
        AlertDialog( // Material3 对话框容器
            onDismissRequest = { // 点击弹窗外部或返回键时触发
                @Suppress("UNUSED_VALUE") // 重置状态值仅用于副作用，忽略“未读取”警告
                run {
                    showSearchDialog = false // 关闭弹窗
                    searchInput = "" // 清空输入框
                    searchStatus = "" // 清空状态提示文本
                    foundUser = null // 清空搜索结果
                    isSearching = false // 重置搜索中状态
                }
            },
            title = { Text("添加好友") }, // 弹窗标题
            text = { // 弹窗正文区域
                Column { // 垂直排列输入框、状态、结果
                    OutlinedTextField( // 输入好友 ID 的文本框
                        value = searchInput, // 绑定输入内容
                        onValueChange = { searchInput = it }, // 输入变化时同步更新状态
                        label = { Text("输入好友ID") }, // 输入框标签
                        enabled = !isSearching, // 搜索中禁用输入，避免重复请求
                        modifier = Modifier.fillMaxWidth() // 输入框宽度铺满
                    )
                    if (searchStatus.isNotEmpty()) { // 有状态文案时显示（如未找到/成功提示）
                        Text(
                            text = searchStatus, // 显示当前状态
                            fontSize = 12.sp, // 状态文本字号
                            color = if (foundUser != null) Color.Green else Color.Red, // 找到用户显示绿色，否则红色
                            modifier = Modifier.padding(top = 8.dp) // 与输入框留出上间距
                        )
                    }
                    if (foundUser != null) { // 搜索到用户时显示用户信息
                        Text(
                            text = "找到用户：${foundUser!!.username}", // 展示匹配到的用户名
                            fontSize = 14.sp, // 用户信息字号稍大
                            modifier = Modifier.padding(top = 8.dp) // 与上方状态文案留间距
                        )
                    }
                }
            },
            confirmButton = { // 右下角主操作按钮（搜索/添加）
                TextButton(
                    onClick = { // 点击确认按钮时执行
                        if (foundUser != null) { // 已有搜索结果：执行添加好友/创建会话
                            // 添加好友
                            isSearching = true // 进入处理中状态，防止重复点击
                            CloudChatManager.createOrUpdateConversation( // 创建或更新与该用户的一对一会话
                                otherUserUid = foundUser!!.uid, // 目标用户 UID
                                otherUsername = foundUser!!.username, // 目标用户名（用于本地展示回退）
                                onComplete = { success, message -> // 云端操作完成回调
                                    isSearching = false // 结束处理中状态
                                    if (success) { // 添加成功分支
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show() // 弹成功提示
                                        @Suppress("UNUSED_VALUE") // 重置状态仅用于关闭弹窗
                                        run {
                                            showSearchDialog = false // 关闭弹窗
                                            searchInput = "" // 清空输入
                                            searchStatus = "" // 清空状态提示
                                            foundUser = null // 清空已找到用户
                                        }
                                    } else {
                                        searchStatus = message // 失败时在弹窗内显示错误原因
                                    }
                                }
                            )
                        } else { // 还未搜索到用户：先执行搜索
                            // 执行搜索
                            isSearching = true // 标记为搜索中，禁用输入和按钮
                            CloudChatManager.searchUser(searchInput) { user, message -> // 根据输入 ID 查询用户
                                isSearching = false // 搜索结束
                                foundUser = user // 记录查询结果（可能为 null）
                                searchStatus = message // 显示查询结果提示文案
                            }
                        }
                    },
                    enabled = !isSearching // 搜索/添加过程中禁用按钮
                ) {
                    Text(if (foundUser != null) "添加好友" else "搜索") // 根据状态动态切换按钮文案
                }
            },
            dismissButton = { // 左下角取消按钮
                TextButton(
                    onClick = { // 点击取消后清空状态并关闭弹窗
                        @Suppress("UNUSED_VALUE") // 重置状态仅用于关闭弹窗
                        run {
                            showSearchDialog = false // 关闭弹窗
                            searchInput = "" // 清空输入
                            searchStatus = "" // 清空提示文案
                            foundUser = null // 清空搜索结果
                            isSearching = false // 重置处理中状态
                        }
                    }
                ) {
                    Text("取消") // 取消按钮文案
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
                    Box { // 使用 Box 包裹更多按钮与菜单
                        IconButton(onClick = { showMenuDropdown = true }) { // 点击更多按钮展开菜单
                            Icon(Icons.Default.MoreVert, contentDescription = "更多选项") // 显示三个点图标
                        }
                        DropdownMenu( // 下拉菜单
                            expanded = showMenuDropdown, // 控制菜单展开/收起
                            onDismissRequest = { showMenuDropdown = false } // 点击外部或返回键时关闭
                        ) {
                            DropdownMenuItem( // 菜单项：创建群聊
                                text = { Text("创建群聊") }, // 文本：创建群聊
                                onClick = { // 点击回调
                                    showMenuDropdown = false // 先关闭菜单
                                    navController.navigate("create_group_chat") // 导航到创建群聊页面
                                }
                            )
                            DropdownMenuItem( // 菜单项：添加好友
                                text = { Text("添加好友") }, // 文本：添加好友
                                onClick = { // 点击回调
                                    showMenuDropdown = false // 先关闭菜单
                                    showSearchDialog = true // 打开原有添加好友对话框
                                }
                            )
                        }
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
                key = { conversation ->
                    // ✅ 使用相同的 key 策略：群聊用 "group_${chatId}"，私聊用 "private_${otherUserUid}"
                    // key 必须稳定且唯一，避免 LazyColumn 因重复 key 触发崩溃
                    if (conversation.chatType == "group") {
                        "group_${conversation.chatId}"
                    } else {
                        "private_${conversation.otherUserUid}"
                    }
                }
            ) { conversation ->
                ConversationListItem(
                    conversation = conversation,
                    onClick = {
                        // 导航入参与会话类型绑定：群聊传 chatId，私聊传 otherUserUid
                        // 判断聊天类型进行不同的导航
                        if (conversation.chatType == "group") { // 如果是群聊
                            // 群聊使用 chatId 导航
                            navController.navigate("conversation_detail/${conversation.chatId}")
                        } else if (conversation.otherUserUid.isNotBlank()) { // 如果是一对一聊天
                            // 一对一使用 otherUserUid 导航
                            navController.navigate("conversation_detail/${conversation.otherUserUid}")
                        } else { // 都不符合，显示错误提示
                            Toast.makeText(context, "当前会话缺少标识，暂时无法进入", Toast.LENGTH_SHORT).show()
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
                        // ✅ 添加日志：显示列表中每个会话的关键信息
                        LaunchedEffect(conversation.name) {
                            val typeStr = if (conversation.chatType == "group") "群聊" else "私聊"
                            android.util.Log.d(
                                "ConversationListScreen",
                                "$typeStr [${conversation.name}]: chatId=${conversation.chatId}, otherUserUid=${conversation.otherUserUid}"
                            )
                        }
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
