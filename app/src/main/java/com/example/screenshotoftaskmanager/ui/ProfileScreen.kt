package com.example.screenshotoftaskmanager.ui // 定义包名

import android.net.Uri // 导入处理图片路径的 Uri 类
import androidx.activity.compose.rememberLauncherForActivityResult // 导入 Activity 结果启动器
import androidx.activity.result.PickVisualMediaRequest // 导入图片选择请求类
import androidx.activity.result.contract.ActivityResultContracts // 导入结果处理协议
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.clickable // 导入点击事件修饰符
import androidx.compose.foundation.layout.Arrangement // 导入布局排列方式
import androidx.compose.foundation.layout.Box // 导入 Box 布局
import androidx.compose.foundation.layout.Column // 导入垂直布局
import androidx.compose.foundation.layout.Spacer // 导入间距组件
import androidx.compose.foundation.layout.fillMaxSize // 导入填充全屏修饰符
import androidx.compose.foundation.layout.height // 导入高度修饰符
import androidx.compose.foundation.layout.padding // 导入内边距修饰符
import androidx.compose.foundation.layout.size // 导入尺寸修饰符
import androidx.compose.foundation.shape.CircleShape // 导入圆形形状
import androidx.compose.material3.AlertDialog // 导入对话框组件
import androidx.compose.material3.Button // 导入按钮组件
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API 标记
import androidx.compose.material3.MaterialTheme // 导入主题库
import androidx.compose.material3.Scaffold // 导入脚手架布局
import androidx.compose.material3.Text // 导入文本组件
import androidx.compose.material3.TextButton // 导入文本按钮组件
import androidx.compose.material3.TopAppBar // 导入顶部栏组件
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

@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 声明为 UI 组件
fun ProfileScreen(mainNavController: NavController) { // “我的”屏幕组件
    
    // 控制是否显示更换头像的弹窗
    var showDialog by remember { mutableStateOf(false) } 

    // ✅ 图片选择器：处理从系统图库选择图片的逻辑
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(), // 选择媒体文件协议
        onResult = { uri: Uri? -> // 结果回调
            uri?.let { // 如果用户选择了图片
                DataSource.myAvatar = it.toString() // 将图片路径存入全局头像状态
                showDialog = false // 关闭弹窗
            }
        }
    )

    // ✅ 头像更换弹窗逻辑
    if (showDialog) { // 如果需要显示弹窗
        AlertDialog(
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

    Scaffold( // 页面基础结构
        topBar = {
            TopAppBar(title = { Text("我的") }) // 顶部显示“我的”
        }
    ) { innerPadding -> // 内容区域，处理顶部栏遮挡
        Column(
            modifier = Modifier
                .fillMaxSize() // 占满全屏
                .padding(innerPadding), // 应用内边距
            horizontalAlignment = Alignment.CenterHorizontally, // 内部子项水平居中
            verticalArrangement = Arrangement.Center // 内部子项垂直居中
        ) {
            // ✅ 头像显示区域
            Box(
                modifier = Modifier
                    .size(120.dp) // 头像显示大小为 120dp
                    .clip(CircleShape) // 裁剪成圆形
                    .background(Color.LightGray) // 设置灰色背景色（图片加载前的占位色）
                    .clickable { showDialog = true } // 点击头像弹出更换选择框
            ) {
                AsyncImage( // 使用 Coil 加载图片
                    model = DataSource.myAvatar, // 加载全局头像数据（支持 URI 或 ResID）
                    contentDescription = "我的头像", // 无障碍描述
                    modifier = Modifier.fillMaxSize(), // 填满 Box
                    contentScale = ContentScale.Crop // 裁剪模式填充，保证圆形不变形
                )
            }

            Spacer(modifier = Modifier.height(16.dp)) // 头像下方的间距

            Text(text = "点击头像更换", fontSize = 14.sp, color = Color.Gray) // 提示文本

            Spacer(modifier = Modifier.height(40.dp)) // 提示文本与按钮之间的间距

            // 退出登录按钮
            Button(onClick = {
                mainNavController.navigate("login") { // 导航回登录页
                    popUpTo(mainNavController.graph.startDestinationId) { // 清空所有返回栈
                        inclusive = true // 包含起始页一并清除
                    }
                    launchSingleTop = true // 避免重复创建登录页
                }
            }) {
                Text("退出登录") // 按钮文本
            }
        }
    }
}
