package com.example.screenshotoftaskmanager.ui // 定义包名

import android.net.Uri // 导入处理图片路径的 Uri 类
import android.widget.Toast // 导入 Toast，用于显示初始化结果提示
import androidx.activity.compose.rememberLauncherForActivityResult // 导入 Activity 结果启动器
import androidx.activity.result.PickVisualMediaRequest // 导入图片选择请求类
import androidx.activity.result.contract.ActivityResultContracts // 导入结果处理协议
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.clickable // 导入点击事件修饰符
import androidx.compose.foundation.layout.Arrangement // 导入布局排列方式
import androidx.compose.foundation.layout.Box // 导入 Box 布局
import androidx.compose.foundation.layout.Column // 导入垂直布局
import androidx.compose.foundation.layout.Row // 导入水平布局
import androidx.compose.foundation.layout.Spacer // 导入间距组件
import androidx.compose.foundation.layout.fillMaxSize // 导入填充全屏修饰符
import androidx.compose.foundation.layout.fillMaxWidth // 导入填充宽度修饰符
import androidx.compose.foundation.layout.height // 导入高度修饰符
import androidx.compose.foundation.layout.width // 导入宽度修饰符
import androidx.compose.foundation.layout.padding // 导入内边距修饰符
import androidx.compose.foundation.layout.size // 导入尺寸修饰符
import androidx.compose.foundation.shape.CircleShape // 导入圆形形状
import androidx.compose.material.icons.Icons // 导入图标库
import androidx.compose.material.icons.filled.Edit // 导入铅笔图标
import androidx.compose.material.icons.filled.Info // 导入信息图标
import androidx.compose.material3.AlertDialog // 导入对话框组件
import androidx.compose.material3.Button // 导入按钮组件
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API 标记
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮组件
import androidx.compose.material3.MaterialTheme // 导入主题库
import androidx.compose.material3.OutlinedTextField // 导入输入框组件
import androidx.compose.material3.Scaffold // 导入脚手架布局
import androidx.compose.material3.Text // 导入文本组件
import androidx.compose.material3.TextButton // 导入文本按钮组件
import androidx.compose.material3.TopAppBar // 导入顶部栏组件
import androidx.compose.material3.TopAppBarDefaults // 导入顶部栏默认样式
import androidx.compose.runtime.Composable // 导入可组合函数注解
import androidx.compose.runtime.getValue // 导入属性读取委托
import androidx.compose.runtime.mutableStateOf // 导入状态创建函数
import androidx.compose.runtime.remember // 导入状态记录函数
import androidx.compose.runtime.setValue // 导入属性设置委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入修饰符接口
import androidx.compose.ui.draw.clip // 导入裁剪修饰符
import androidx.compose.ui.graphics.Color // 导入颜色类
import androidx.compose.ui.layout.ContentScale // 导入内容缩放模式
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.compose.ui.unit.sp // 导入 sp 单位
import androidx.navigation.NavController // 导入导航控制器
import coil.compose.AsyncImage // 导入异步图片加载组件
import com.example.screenshotoftaskmanager.AuthManager // 导入认证管理工具
import com.example.screenshotoftaskmanager.CloudChatManager // 导入云端聊天管理器

@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 声明为 UI 组件
fun ProfileScreen(mainNavController: NavController) { // “我的”屏幕组件
    
    // 获取当前上下文，用于显示 Toast
    val context = androidx.compose.ui.platform.LocalContext.current

    // 读取当前登录用户名，用于判断是否是 admin
    val currentUsername = AuthManager.getCurrentUsername()

    // 是否为 admin 账号（只有 admin 才显示初始化按钮）
    val isAdminUser = currentUsername == "admin"

    // 是否正在执行预设聊天上传（用于禁用按钮，防止重复点击）
    var isInitializing by remember { mutableStateOf(false) }

    // 上传状态文本（展示给管理员）
    var initStatusText by remember { mutableStateOf("") }
    // 完整的上传进度日志（所有 onProgress 消息的累积）
    var uploadLogText by remember { mutableStateOf("") }

    // 控制是否显示更换头像的弹窗 显示对话框
    var showDialog by remember { mutableStateOf(false) } // 头像弹窗状态

    // 控制是否显示更改昵称弹窗
    var showNicknameDialog by remember { mutableStateOf(false) } // 昵称弹窗状态

    // 控制是否显示更改个性签名弹窗
    var showSignatureDialog by remember { mutableStateOf(false) } // 个性签名弹窗状态

    // 控制是否显示上传提示对话框
    var showUploadHintDialog by remember { mutableStateOf(false) } // 上传提示弹窗状态

    // 临时昵称输入状态，初始值为当前昵称，显式指定类型为String
    var nicknameInput by remember { mutableStateOf<String>(DataSource.myNickname) } // 昵称输入框内容

    // 临时个性签名输入状态，初始值为当前个性签名
    var signatureInput by remember { mutableStateOf<String>(DataSource.mySignature) } // 个性签名输入框内容

    // ✅ 图片选择器：处理从系统图库选择图片的逻辑  系统相册选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(    //创建一个启动器 可以调用它去打开系统功能
        contract = ActivityResultContracts.PickVisualMedia(), //用来选择媒体文件协议
        onResult = { uri: Uri? -> // 结果回调 用户选完照片以后回调回来
            uri?.let { // 如果用户选择了图片    //uri可能为空用户取消选择 这样的话就？. 就什么都不做 uri不为空就执行后面操作
                DataSource.myAvatar = it.toString() // 将图片路径存入全局头像状态 tostring作用是把uri对象变为字符串地址
                showDialog = false // 关闭弹窗
            }
        }
    )

    // ✅ 头像更换弹窗逻辑
    if (showDialog) { // 如果需要显示弹窗
        AlertDialog(          //弹出对话框
            onDismissRequest = { showDialog = false }, // 点击外部关闭弹窗
            title = { Text("更换头像") }, // 弹窗标题
            text = { Text("请选择头像来源：") }, // 弹窗内容说明
            confirmButton = { // 按钮 1：从相册选择
                TextButton(
                    onClick = {
                        photoPickerLauncher.launch( // 启动相册
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly) // 仅限图片
                        )
                    }
                ) {
                    Text("本机相册") // 按钮文本
                }
            },
            dismissButton = { // 按钮 2：随机头像
                TextButton(
                    onClick = {
                        // ✅ 修改点：从专门的 profileResources 列表中随机选取，只包含 profile1-5
                        val randomRes = DataSource.profileResources.random()
                        DataSource.myAvatar = randomRes // 更新全局头像状态
                        showDialog = false // 关闭弹窗
                    }
                ) {
                    Text("随机头像") // 按钮文本
                }
            }
        )
    }

    // ✅ 昵称更改弹窗逻辑
    if (showNicknameDialog) { // 如果需要显示更改昵称弹窗
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false }, // 点击外部关闭弹窗
            title = { Text("更改昵称") }, // 弹窗标题
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = nicknameInput, // 输入框内容
                    onValueChange = { nicknameInput = it }, // 输入内容变化
                    label = @Composable { Text("请输入新昵称") } // 输入框标签
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nicknameInput.isNotBlank()) { // 输入不为空
                        DataSource.myNickname = nicknameInput // 更新全局昵称
                        showNicknameDialog = false // 关闭弹窗
                    }
                }) {
                    Text("确认") // 按钮文本
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text("取消") // 按钮文本
                }
            }
        )
    }

    // ✅ 个性签名更改弹窗逻辑
    if (showSignatureDialog) { // 如果需要显示更改个性签名弹窗
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false }, // 点击外部关闭弹窗
            title = { Text("更改个性签名") }, // 弹窗标题
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = signatureInput, // 输入框内容
                    onValueChange = { signatureInput = it }, // 输入内容变化
                    label = @Composable { Text("请输入新个性签名") } // 输入框标签
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (signatureInput.isNotBlank()) { // 输入不为空
                        DataSource.mySignature = signatureInput // 更新全局个性签名
                        showSignatureDialog = false // 关闭弹窗
                    }
                }) {
                    Text("确认") // 按钮文本
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureDialog = false }) {
                    Text("取消") // 按钮文本
                }
            }
        )
    }

    // ✅ 上传提示对话框逻辑
    if (showUploadHintDialog) {
        AlertDialog(
            onDismissRequest = { showUploadHintDialog = false },
            title = { Text("预设聊天上传说明") },
            text = { Text("请先手动注册 xiaoming、xiaohua、daguang，然后再点击上传按钮上传 admin 的预设聊天。\n\n预设消息来自代码里的 CloudChatManager.adminSeedConversations，不是本地 DataSource 聊天缓存。") },
            confirmButton = {
                TextButton(onClick = { showUploadHintDialog = false }) {
                    Text("我知道了")
                }
            }
        )
    }

    Scaffold( // 页面基础结构脚手架
        topBar = {
            TopAppBar(
                title = { Text("我的") }, // 顶部显示"我的"
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary // 设置背景颜色为深蓝色
                ),
                actions = {
                    // 在右上角显示当前用户ID
                    Text(
                        text = "ID: $currentUsername",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 16.dp).align(Alignment.CenterVertically)
                    )
                }
            )
        }
    ) { innerPadding -> // 内容区域，处理顶部栏遮挡
        Column(
            modifier = Modifier
                .fillMaxSize() // 占满全屏
                .padding(innerPadding), // 应用内边距
            horizontalAlignment = Alignment.CenterHorizontally, // 内部子项水平居中
            verticalArrangement = Arrangement.Center // 内部子项垂直居中
        ) {
            // 头像显示区域与更换按钮
            Row(
                verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
                horizontalArrangement = Arrangement.Center // 水平居中排列
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp) // 头像显示大小为 120dp
                        .clip(CircleShape) // 裁剪成圆形
                        .background(Color.LightGray) // 设置灰色背景色（图片加载前的占位色）
                        .clickable { showDialog = true } // 点击头像弹出更换选择框
                ) {
                    AsyncImage( // 使用 Coil 异步加载图片
                        model = DataSource.myAvatar, // 加载全局头像数据（支持 URI 或 ResID）
                        contentDescription = "我的头像", // 无障碍描述
                        modifier = Modifier.fillMaxSize(), // 填满 Box
                        contentScale = ContentScale.Crop // 裁剪模式填充，保证圆形不变形
                    )
                }
                // 头像更换按钮（铅笔图标）
                IconButton(onClick = {
                    showDialog = true // 打开头像更换对话框
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit, // 铅笔图标
                        contentDescription = "编辑头像", // 无障碍描述
                        tint = Color.Gray // 图标颜色
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // 头像下方间距

            // 昵称显示与更改工具
            Row(
                verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
                horizontalArrangement = Arrangement.Center // 水平居中排列
            ) {
                Text(
                    text = DataSource.myNickname, // 昵称内容
                    fontSize = 18.sp, // 字体大小
                    color = Color.Black, // 字体颜色
                    modifier = Modifier.clickable {
                        nicknameInput = DataSource.myNickname // 初始化输入框内容
                        showNicknameDialog = true // 弹出更改昵称弹窗
                    }
                )
                IconButton(onClick = {
                    nicknameInput = DataSource.myNickname // 初始化输入框内容
                    showNicknameDialog = true // 弹出更改昵称弹窗
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit, // 铅笔图标
                        contentDescription = "编辑昵称", // 无障碍描述
                        tint = Color.Gray // 图标颜色
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // 昵称与个性签名间距

            // 个性签名显示与更改工具
            Row(
                verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
                horizontalArrangement = Arrangement.Center // 水平居中排列
            ) {
                Text(
                    text = DataSource.mySignature, // 个性签名内容
                    fontSize = 16.sp, // 字体大小
                    color = Color.DarkGray, // 字体颜色
                    modifier = Modifier.clickable {
                        signatureInput = DataSource.mySignature // 初始化输入框内容
                        showSignatureDialog = true // 弹出更改个性签名弹窗
                    }
                )
                IconButton(onClick = {
                    signatureInput = DataSource.mySignature // 初始化输入框内容
                    showSignatureDialog = true // 弹出更改个性签名弹窗
                }) {
                    Icon(
                        imageVector = Icons.Filled.Edit, // 铅笔图标
                        contentDescription = "编辑个性签名", // 无障碍描述
                        tint = Color.Gray // 图标颜色
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // 间距

            // 仅 admin 账号显示"上传预设聊天到云端"按钮
            if (isAdminUser) {
                // 在管理员按钮上方显示操作提示icon（点击可查看详细说明）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("上传预设聊天到云端")
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showUploadHintDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "查看上传说明",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        // 开始上传，先重置状态
                        isInitializing = true
                        initStatusText = "正在上传 admin 的预设聊天..."
                            uploadLogText = "" // 清空之前的日志

                        // 调用云端聊天迁移入口
                        CloudChatManager.uploadAdminSeedChatsToCloud(
                            onProgress = { progressMessage ->
                                // 持续更新状态文本
                                initStatusText = progressMessage
                                    // 同时累积到日志里，方便用户看完整过程
                                    uploadLogText += progressMessage + "\n"
                            },
                            onComplete = { success, finalMessage ->
                                // 上传结束，恢复按钮状态
                                isInitializing = false

                                // 展示最终结果
                                initStatusText = finalMessage
                                    uploadLogText += finalMessage + "\n"

                                // 用 Toast 给出明确反馈
                                Toast.makeText(
                                    context,
                                    if (success) "预设聊天上传完成" else "预设聊天上传未完全成功，请查看状态",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    },
                    enabled = !isInitializing // 初始化期间禁用按钮，防止并发触发
                ) {
                    // 按钮文本会根据状态动态变化
                    Text(if (isInitializing) "正在上传..." else "同步 admin 预设聊天到云端")
                }

                // 显示当前状态文本
                if (initStatusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp)) // 与按钮保持间距
                    Text(
                        text = initStatusText, // 显示当前初始化状态
                        fontSize = 13.sp, // 字体稍小，作为状态信息
                        color = Color.DarkGray // 使用深灰色区分主内容
                    )
                }

                // 显示完整的上传日志（卷轴式显示，避免占据整个屏幕）
                if (uploadLogText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp)) // 与状态文本保持间距
                    androidx.compose.material3.OutlinedTextField(
                        value = uploadLogText, // 显示完整上传日志
                        onValueChange = {}, // 只读，不允许编辑
                        label = { Text("上传日志") }, // 标签
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp), // 固定高度，内部卷轴
                        readOnly = true, // 设置为只读
                        enabled = false // 禁用但仍显示内容
                    )
                }

                Spacer(modifier = Modifier.height(16.dp)) // 与下方退出按钮拉开距离
            }

            Spacer(modifier = Modifier.height(40.dp)) // 间距与按钮之间的距离

            // 退出登录按钮
            Button(onClick = {
                // 先执行 Firebase 退出登录，确保认证态被清理
                AuthManager.logout()

                mainNavController.navigate("login") { // 导航回登录页
                    popUpTo(mainNavController.graph.startDestinationId) { // 清空所有返回栈
                        inclusive = true // 包含起始页一并清除  保证清空所有返回栈 要注意 栈底和栈顶先来的在底下 然后 pop是从上往下弹出的
                    }
                    launchSingleTop = true // 避免重复创建登录页
                }
            }) {
                Text("退出登录") // 按钮文本
            }
        }
    }
}
