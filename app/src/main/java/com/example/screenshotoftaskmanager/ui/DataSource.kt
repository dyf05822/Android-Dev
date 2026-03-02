package com.example.screenshotoftaskmanager.ui // 定义包名

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
    val messages: SnapshotStateList<Message>,
    initialIsPinned: Boolean = false,
    initialUnreadCount: Int = 0,
    initialDraft: String = ""
) {
    var isPinned by mutableStateOf(initialIsPinned) // 代理置顶状态
    var unreadCount by mutableStateOf(initialUnreadCount) // 代理未读状态
    var draft by mutableStateOf(initialDraft) // 代理草稿状态
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
        addAll(initialConversations)
    }

    fun getConversation(name: String): Conversation {
        return conversations.find { it.name == name } ?: run {
            val newConv = Conversation(name = name, messages = mutableStateListOf())
            conversations.add(newConv)
            newConv
        }
    }

    fun getMessagesForConversation(newFriendName: String) {}

    // 聊天内容中可选的图片库（用于发送图片消息，保持 photo1-5 不变）
    val drawableResources = listOf(R.drawable.photo1, R.drawable.photo2, R.drawable.photo3, R.drawable.photo4, R.drawable.photo5)
}
