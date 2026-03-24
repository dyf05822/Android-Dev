package com.example.screenshotoftaskmanager // 声明包名，确保文件属于正确的命名空间

import com.google.firebase.firestore.PropertyName // 导入Firestore属性注解，用于序列化/反序列化

// ================== 聊天会话数据类 ==================
/**
 * Chat 数据类：表示聊天会话（支持一对一和群聊）
 * 这个类用于存储在 Firestore 的 chats 集合中
 * 结构：chats/{chatId}
 */ // 是整个聊天系统的数据模型中心
data class Chat(
    // 聊天会话的唯一标识符（一对一时由两个 UID 生成，群聊时为 UUID）
    var chatId: String = "",
    
    // 聊天类型：'private' 表示一对一，'group' 表示群聊
    @PropertyName("chatType")
    var chatType: String = "private",
    
    // 群聊的名称（一对一时为空）
    @PropertyName("groupName")
    var groupName: String = "",
    
    // 群聊的头像 URL（一对一时为空）
    @PropertyName("groupAvatar")
    var groupAvatar: String = "",
    
    // 参与这个聊天的用户 UID 列表（一对一时包含 2 个用户，群聊时可以是多个）
    @PropertyName("participants")
    var participants: List<String> = emptyList(),        //emptylist默认是空列表 表示暂时没有参与者

    // 群聊的创建者/群主 UID（一对一时为空）
    @PropertyName("owner")
    var owner: String = "",
    
    // 群聊创建时的时间戳（一对一时为 0）
    @PropertyName("createdAt")
    var createdAt: Long = 0L,
    
    // 聊天中的最后一条消息内容（用于列表显示预览）
    @PropertyName("lastMessage")
    var lastMessage: String = "",
    
    // 最后一条消息的时间戳（毫秒，用于排序聊天列表）
    @PropertyName("lastTimestamp")
    var lastTimestamp: Long = 0L,
    
    // 最后一条消息的发送者 UID（可选，用于显示"谁发送了最后一条消息"）
    @PropertyName("lastSenderId")
    var lastSenderId: String = "",
    
    // 最后一条消息的发送者名称（用于群聊显示）
    @PropertyName("lastSenderName")
    var lastSenderName: String = ""
)

// ================== 单条消息数据类 ==================
/**
 * Message 数据类：表示聊天中的单条消息
 * 这个类用于存储在 Firestore 的 chats/{chatId}/messages 子集合中
 * 结构：chats/{chatId}/messages/{messageId}
 */
data class Message(
    // 发送消息的用户 UID（谁发的这条消息）
    @PropertyName("senderId")
    var senderId: String = "",
    
    // 接收消息的用户 UID（发送给谁）
    @PropertyName("receiverId")
    var receiverId: String = "",
    
    // 消息的文本内容（聊天的实际内容）
    @PropertyName("text")
    var text: String = "",
    
    // 消息发送时间的时间戳（毫秒，用于排序消息）
    @PropertyName("timestamp")
    var timestamp: Long = 0L,
    
    // 消息类型（可扩展：text 文本、image 图片、file 文件等）
    @PropertyName("type")
    var type: String = "text"
)

// ================== 聊天管理工具类 ==================
/**
 * ChatUtils 对象：提供聊天相关的工具方法
 */
object ChatUtils {
    
    /**
     * 生成聊天 ID 的方法
     * 根据两个用户的 UID 生成唯一的聊天 ID
     * 通过字符串排序确保 A->B 和 B->A 生成同样的 ID
     *
     * @param uid1 第一个用户的 UID
     * @param uid2 第二个用户的 UID
     * @return 生成的聊天 ID（格式：uid1_uid2，其中 uid1 < uid2）
     *
     * 示例：
     * getChatId("userA", "userB") // 返回 "userA_userB"
     * getChatId("userB", "userA") // 也返回 "userA_userB"（一样的结果，确保唯一性）
     */
    fun getChatId(uid1: String, uid2: String): String {
        // 比较两个 UID 的字符顺序，确保较小的 UID 总是放在前面  较小的uid在前面
        return if (uid1 < uid2) {
            // 如果 uid1 < uid2，按这个顺序连接
            "${uid1}_${uid2}"
        } else {
            // 否则交换顺序
            "${uid2}_${uid1}"
        }
    }
    
    /**
     * 从聊天中获取另一个用户的 UID
     * 当知道当前用户的 UID 时，可以从聊天的参与者列表中获取对方的 UID
     *
     * @param chat 聊天对象
     * @param currentUid 当前登录用户的 UID
     * @return 对方用户的 UID，如果找不到则返回空字符串
     *
     * 示例：
     * chat.participants = ["userA", "userB"]
     * getOtherUserId(chat, "userA") // 返回 "userB"
     * getOtherUserId(chat, "userB") // 返回 "userA"
     */
    fun getOtherUserId(chat: Chat, currentUid: String): String {
        // 群聊没有“对方”；防御性返回空避免误用群聊 ID
        if (chat.chatType == "group") return ""
        // 一对一场景下兜底：需要至少 2 人才能取到另一方
        if (chat.participants.size < 2) return ""
        return chat.participants.firstOrNull { it != currentUid } ?: ""
    }
}

// ================== 用户信息数据类 (可选，用于后续扩展) ==================
/**
 * User 数据类：表示用户的基本信息
 * 这个类用于存储在 Firestore 的 users 集合中
 * 结构：users/{uid}
 *
 * 注：这个类暂时是可选的，等后续实现显示用户名时再使用
 */
data class User(
    // 用户的唯一标识符（由 Firebase Auth 提供）
    var uid: String = "",
    
    // 用户的账号/用户名（注册时输入）
    @PropertyName("username")
    var username: String = "",
    
    // 用户创建账户的时间戳
    @PropertyName("createdAt")
    var createdAt: Long = 0L
)
