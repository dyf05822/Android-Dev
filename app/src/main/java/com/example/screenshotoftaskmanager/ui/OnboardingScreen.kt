package com.example.screenshotoftaskmanager.ui   //包名声明 属于哪个包（文件路径）

import androidx.compose.foundation.ExperimentalFoundationApi // 导入一个标记，表示我们正在使用实验性的Foundation API
import androidx.compose.foundation.Image // 导入图片组件
import androidx.compose.foundation.layout.Arrangement // 导入用于在Row或Column中安排其子项位置的工具
import androidx.compose.foundation.layout.Box // 导入一个可以将其子项叠放在一起的布局组件
import androidx.compose.foundation.layout.Column // 导入一个可以将其子项垂直排列的布局组件
import androidx.compose.foundation.layout.Row // 导入一个可以将其子项水平排列的布局组件
import androidx.compose.foundation.layout.Spacer // 导入用于在布局中创建空白间隔的组件
import androidx.compose.foundation.layout.fillMaxSize // 导入一个修饰符，使组件填满其父容器的整个尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入一个修饰符，使组件填满其父容器的宽度
import androidx.compose.foundation.layout.height // 导入一个修饰符，用于设置组件的高度
import androidx.compose.foundation.layout.padding // 导入一个修饰符，用于在组件周围添加内边距
import androidx.compose.foundation.layout.width // 导入一个修饰符，用于设置组件的宽度
import androidx.compose.foundation.pager.HorizontalPager // 导入一个可以水平滑动的页面容器组件
import androidx.compose.foundation.pager.rememberPagerState // 导入一个函数，用于创建和记住Pager的状态
import androidx.compose.material3.Button // 导入按钮组件
import androidx.compose.material3.Switch // 导入开关（Switch）组件
import androidx.compose.material3.Text // 导入用于显示文本的组件
import androidx.compose.runtime.Composable // 导入声明一个函数为Compose组件的核心注解
import androidx.compose.runtime.getValue // 导入一个函数，用于方便地读取State对象的值
import androidx.compose.runtime.mutableStateOf // 导入一个函数，用于创建可变的、可被Compose观察的状态
import androidx.compose.runtime.remember // 导入一个函数，用于在多次重组之间“记住”一个值
import androidx.compose.runtime.setValue // 导入一个函数，用于方便地设置State对象的值
import androidx.compose.ui.Alignment // 导入用于在布局中对齐子项的工具
import androidx.compose.ui.Modifier // 导入用于修饰和扩展组件行为的修饰符对象
import androidx.compose.ui.graphics.Color // 导入颜色类
import androidx.compose.ui.layout.ContentScale // 导入图片缩放模式
import androidx.compose.ui.res.painterResource // 导入资源图片加载器
import androidx.compose.ui.unit.dp // 导入用于定义尺寸的单位（密度无关像素）
import androidx.compose.ui.unit.sp // 导入用于定义字体大小的单位（缩放无关像素）
import androidx.navigation.NavController // 导入导航控制器，用于管理页面之间的跳转
import com.example.screenshotoftaskmanager.R // 导入资源文件引用

@OptIn(ExperimentalFoundationApi::class)  //告诉你是实验性API
@Composable    //告诉函数是一个ui函数 可以画界面
fun OnboardingScreen(navController: NavController) {      //用于做页面跳转 对应第56行跳转到login页面
    val pages = listOf(
        R.drawable.p6, // 第一页图片
        R.drawable.p7, // 第二页图片
        R.drawable.p8, // 第三页图片
        R.drawable.p9  // 第四页图片
    )    //创建一个list 四张图片 每一个代表一页的内容
    val pagerState = rememberPagerState(pageCount = { pages.size })  //pagerstate用于控制paper在哪一页 如果没有remember那么页码可能会重置
    
    // 新增：创建一个状态来记住调试模式是否开启，默认为false（关闭）
    var debugMode by remember { mutableStateOf(false) }

    // 使用 Box 根布局实现真正的全屏沉浸式背景
    Box(    //叠放布局 把组件堆在一起
        modifier = Modifier.fillMaxSize()   //宽度和高度都占满
    ) {
        HorizontalPager(         //一个可以左右滑动的页面容器
            state = pagerState,
            modifier = Modifier.fillMaxSize()     //背景图片占满整个屏幕
        ) { page ->             //当前页的索引
            Image(
                painter = painterResource(id = pages[page]), // 加载图片资源
                contentDescription = "引导页图片", // 图片描述
                modifier = Modifier.fillMaxSize(), // 填满全屏
                contentScale = ContentScale.Crop // 裁剪填充，保证背景无死角
            )
        }

        // 按钮区域改为悬浮在最后一页底部
        if (pagerState.currentPage == pages.size - 1) {     //当前页等于最后一页
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter) // 悬浮对齐到屏幕底部中央
                    .padding(bottom = 60.dp) // 距离底边的间距
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally  //水平居中
            ) {
                Row( // 使用一个Row来水平排列“调试模式”文本和开关
                    verticalAlignment = Alignment.CenterVertically // 垂直居中对齐文本和开关
                ) {
                    Text("调试模式", color = Color.White) // 显示“调试模式”文本，白色在图片背景上更清晰
                    Spacer(modifier = Modifier.width(8.dp)) // 在文本和开关之间添加8dp的水平间隔
                    Switch( // 添加一个开关组件
                        checked = debugMode, // 开关的当前状态绑定到我们的debugMode变量
                        onCheckedChange = { debugMode = it } // 当用户点击开关时，更新debugMode变量的值   重点！！！！按钮的“取反”逻辑与案件不同
                    )
                }
                Spacer(modifier = Modifier.height(16.dp)) // 在开关和按钮之间添加16dp的垂直间隔

                Button(        //才显示按钮 创建按钮
                    onClick = {      //点击事件
                        val destination = if (debugMode) { // 检查调试模式是否开启
                            "conversation_list" // 如果开启，设置目标页面为会话列表
                        } else {
                            "login" // 如果关闭，设置目标页面为登录页
                        }
                        navController.navigate(destination) {    //跳转到我们刚才确定的目标页面
                            popUpTo("onboarding") { inclusive = true }   //跳转后从返回栈里删除 即用户进入login以后就不能返回到onboarding页面
                        }  //包含自己一起弹出 就回不去了
                    }
                ) {
                    Text("点击进入") // 将按钮文本从“点击登录”改为更通用的“点击进入”
                }
            }
        }
    }
}
