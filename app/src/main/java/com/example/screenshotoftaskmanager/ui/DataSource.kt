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
    val messages: SnapshotStateList<Message>,   //快照状态列表
    initialIsPinned: Boolean = false,
    initialUnreadCount: Int = 0,
    initialDraft: String = "",
    initialAvatar: Any = R.drawable.profile1, // 默认对方头像
    initialOtherUserUid: String = "", // 云端会话对应的对方 UID
    initialChatId: String = "", // 云端 chatId
    initialPreviewText: String = "", // 列表页预览文本
    initialLastTimestamp: Long = 0L // 最后一条消息时间
) {
    var isPinned by mutableStateOf(initialIsPinned) // 代理置顶状态
    var unreadCount by mutableStateOf(initialUnreadCount) // 代理未读状态
    var draft by mutableStateOf(initialDraft) // 代理草稿状态
    var avatar by mutableStateOf(initialAvatar) // 对方头像状态
    var otherUserUid by mutableStateOf(initialOtherUserUid) // 记录云端会话对应的对方 UID
    var chatId by mutableStateOf(initialChatId) // 记录云端 chatId
    var previewText by mutableStateOf(initialPreviewText) // 会话列表显示的摘要文本
    var lastTimestamp by mutableStateOf(initialLastTimestamp) // 最后一条消息时间
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

    // 会话列表不再预置固定聊天；现在这里仅作为“云端聊天的运行时缓存”
    val conversations = mutableStateListOf<Conversation>()

    // 根据对方 UID 生成稳定头像，保证不同账号登录时仍有一致头像表现
    fun avatarForUsername(username: String): Int {
        val safeIndex = (username.hashCode() and Int.MAX_VALUE) % profileResources.size
        return profileResources[safeIndex]
    }

    // 用云端返回的新列表整体替换本地缓存，并尽量保留已有 UI 状态（草稿、置顶、头像、已加载消息）
    fun replaceConversations(newConversations: List<Conversation>) {
        val existingMap = conversations.associateBy { conversation ->
            conversation.otherUserUid.ifBlank { conversation.name }
        }

        conversations.clear()

        newConversations.forEach { incomingConversation ->
            val conversationKey = incomingConversation.otherUserUid.ifBlank { incomingConversation.name }
            val existingConversation = existingMap[conversationKey]

            if (existingConversation != null) {
                incomingConversation.isPinned = existingConversation.isPinned
                incomingConversation.unreadCount = existingConversation.unreadCount
                incomingConversation.draft = existingConversation.draft
                incomingConversation.avatar = existingConversation.avatar

                if (existingConversation.messages.isNotEmpty()) {
                    incomingConversation.messages.clear()
                    incomingConversation.messages.addAll(existingConversation.messages)
                }
            }

            conversations.add(incomingConversation)
        }
    }

    // 通过对方 UID 获取会话；如果本地缓存中不存在，则创建一个占位会话，便于详情页先进入再等待云端数据刷新
    fun getOrCreateConversation(otherUserUid: String, displayName: String = "未命名用户"): Conversation {
        return conversations.find { it.otherUserUid == otherUserUid } ?: Conversation(
            name = displayName,
            messages = mutableStateListOf(),
            initialAvatar = avatarForUsername(displayName),
            initialOtherUserUid = otherUserUid,
            initialPreviewText = "还没有消息"
        ).also { conversations.add(it) }
    }

    // 根据对方 UID 查找会话；如果没有找到则返回 null
    fun getConversationByOtherUserUid(otherUserUid: String): Conversation? {
        return conversations.find { it.otherUserUid == otherUserUid }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getMessagesForConversation(newFriendName: String) { // 兼容旧调用，当前已切换为云端会话，不再在本地创建固定聊天
    }

    // 聊天内容中可选的图片库（用于发送图片消息，保持 photo1-5 不变）
    val drawableResources = listOf(R.drawable.photo1, R.drawable.photo2, R.drawable.photo3, R.drawable.photo4, R.drawable.photo5)

}