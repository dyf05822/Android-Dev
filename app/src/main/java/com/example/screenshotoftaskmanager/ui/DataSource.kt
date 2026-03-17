package com.example.screenshotoftaskmanager.ui // 定义包名

import android.content.Context // 导入Context用于SharedPreferences
import androidx.compose.runtime.getValue // 属性读取委托
import androidx.compose.runtime.mutableStateListOf // 创建可观察列表
import androidx.compose.runtime.mutableStateOf // 创建可变状态
import androidx.compose.runtime.setValue // 属性设置委托
import androidx.compose.runtime.snapshots.SnapshotStateList // 列表状态类型
import com.example.screenshotoftaskmanager.R // 资源索引

enum class MessageSender { ME, OTHER } // 发送者标识枚举

data class Message( // 消息数据类
    val sender: MessageSender, // 谁发的
    val content: String, // 说了什么
    val type: String = "text", // 消息类型
    val imageRes: Int? = null, // 图片资源
    val imageUri: String? = null // 相册图片路径
)

// 会话类：包含姓名、消息记录及 UI 状态
class Conversation(
    val name: String,
    val messages: SnapshotStateList<Message>,   //快照状态列表
    initialIsPinned: Boolean = false,
    initialUnreadCount: Int = 0,
    initialDraft: String = "",
    initialAvatar: Any = R.drawable.profile1 // 默认对方头像
) {
    var isPinned by mutableStateOf(initialIsPinned) // 代理置顶状态
    var unreadCount by mutableStateOf(initialUnreadCount) // 代理未读状态
    var draft by mutableStateOf(initialDraft) // 代理草稿状态
    var avatar by mutableStateOf(initialAvatar) // 对方头像状态
}

object DataSource { // 全局单例数据源
    
    // ✅ 改进：默认头像改为 profile5，确保初始显示不是 photo 系列
    var myAvatar by mutableStateOf<Any>(R.drawable.profile5)

    // ✅ 新增：专门用于个人头像随机选择的资源列表，限制在 profile1-5
    val profileResources = listOf(
        R.drawable.profile1,
        R.drawable.profile2,
        R.drawable.profile3,
        R.drawable.profile4,
        R.drawable.profile5
    )

    // 新增：全局昵称状态，初始值为“未设置昵称”
    var myNickname by mutableStateOf("未设置昵称") // 全局昵称状态

    // 新增：全局个性签名状态，初始值为“行百里路者半九十”
    var mySignature by mutableStateOf("行百里路者半九十") // 全局个性签名状态

    private val initialConversations = listOf(
        Conversation(
            name = "小明",
            messages = mutableStateListOf(
                Message(MessageSender.OTHER, "明天干饭去"),
                Message(MessageSender.ME, "好啊，去哪吃？"),
                Message(MessageSender.OTHER, "[图片]", type = "image", imageRes = R.drawable.photo1),
                Message(MessageSender.ME, "这是哪？看起来不错。"),
                Message(MessageSender.OTHER, "这是那家新开的串串店"), 
                Message(MessageSender.OTHER, "位置我发你，明天直接在那碰头？") 
            ),
            initialUnreadCount = 2,
            initialIsPinned = true
        ),
        Conversation(
            name = "小华",
            messages = mutableStateListOf(
                Message(MessageSender.OTHER, "周末有空吗？一起打球？"),
                Message(MessageSender.ME, "[图片]", type = "image", imageRes = R.drawable.photo2)
            ),
            initialDraft = "待会回..."
        ),
        Conversation(
            name = "大光",
            messages = mutableStateListOf(
                Message(MessageSender.OTHER, "在干嘛呢？"),
                Message(MessageSender.ME, "刚吃完饭，准备休息一下。"),
                Message(MessageSender.OTHER, "你现在到哪了？")
            )
        )
    )

    val conversations = mutableStateListOf<Conversation>().apply {
        addAll(initialConversations)       //所有消息列表
    }

    fun getConversation(name: String): Conversation {    //根据名字查找聊天
        return conversations.find { it.name == name } ?: run {
            val newConv = Conversation(name = name, messages = mutableStateListOf())    //找不到这个聊天就创造一个聊天
            conversations.add(newConv)
            newConv
        }
    }

    fun getMessagesForConversation(newFriendName: String) {}   //

    // 聊天内容中可选的图片库（用于发送图片消息，保持 photo1-5 不变）
    val drawableResources = listOf(R.drawable.photo1, R.drawable.photo2, R.drawable.photo3, R.drawable.photo4, R.drawable.photo5)

    // 用户注册函数：检查用户名是否已存在，如果不存在则保存用户名和密码
    fun registerUser(context: Context, username: String, password: String): Boolean { // 返回注册是否成功
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) // 获取SharedPreferences实例
        val key = "username_$username" // 存储键格式：username_用户名
        if (prefs.contains(key)) { // 如果键已存在，表示用户名已注册
            return false // 注册失败
        }
        prefs.edit().putString(key, password).apply() // 保存密码（明文）
        return true // 注册成功
    }

    // 检查用户名是否已注册
    fun isUserRegistered(context: Context, username: String): Boolean { // 返回是否已注册
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) // 获取SharedPreferences实例
        val key = "username_$username" // 存储键格式
        return prefs.contains(key) // 检查键是否存在
    }

    // 用户登录函数：检查用户名和密码是否匹配
    fun loginUser(context: Context, username: String, password: String): Boolean { // 返回登录是否成功
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) // 获取SharedPreferences实例
        val key = "username_$username" // 存储键格式
        val storedPassword = prefs.getString(key, null) // 获取存储的密码
        return storedPassword == password // 比较密码
    }
}