package com.example.screenshotoftaskmanager

import androidx.compose.runtime.mutableStateListOf
import android.os.Handler
import android.os.Looper
import com.example.screenshotoftaskmanager.ui.Conversation
import com.example.screenshotoftaskmanager.ui.DataSource
import com.example.screenshotoftaskmanager.ui.Message as UiMessage
import com.example.screenshotoftaskmanager.ui.MessageSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.atomic.AtomicBoolean

// 云端聊天管理器：负责聊天列表监听、消息监听、发送消息、上传 admin 的预设聊天种子数据
object CloudChatManager {

    // Firebase Auth 实例，用于获取当前登录用户
    private val auth = FirebaseAuth.getInstance()

    // 上传预设聊天时的超时时间，避免网络异常导致流程一直不返回
    private const val SEED_UPLOAD_TIMEOUT_MS = 25_000L

    // 使用动态 getter 获取 Firestore，避免静态字段持有 Context 触发泄漏告警
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // 预设聊天中的单条种子消息
    private data class SeedMessage(
        val text: String,
        val sentByAdmin: Boolean,
        val type: String = "text"
    )

    // 预设聊天中的单个会话定义
    private data class SeedConversation(
        val targetUsername: String,
        val displayName: String,
        val messages: List<SeedMessage>
    )

    // 原来 DataSource 中的固定聊天，现在迁移为“可上传到云端的种子数据”
    private val adminSeedConversations = listOf(
        SeedConversation(
            targetUsername = "xiaoming",
            displayName = "小明",
            messages = listOf(
                SeedMessage(text = "明天干饭去", sentByAdmin = false),
                SeedMessage(text = "好啊，去哪吃？", sentByAdmin = true),
                SeedMessage(text = "[图片]", sentByAdmin = false),
                SeedMessage(text = "这是哪？看起来不错。", sentByAdmin = true),
                SeedMessage(text = "这是那家新开的串串店", sentByAdmin = false),
                SeedMessage(text = "位置我发你，明天直接在那碰头？", sentByAdmin = false)
            )
        ),
        SeedConversation(
            targetUsername = "xiaohua",
            displayName = "小华",
            messages = listOf(
                SeedMessage(text = "周末有空吗？一起打球？", sentByAdmin = false),
                SeedMessage(text = "[图片]", sentByAdmin = true)
            )
        ),
        SeedConversation(
            targetUsername = "daguang",
            displayName = "大光",
            messages = listOf(
                SeedMessage(text = "在干嘛呢？", sentByAdmin = false),
                SeedMessage(text = "刚吃完饭，准备休息一下。", sentByAdmin = true),
                SeedMessage(text = "你现在到哪了？", sentByAdmin = false)
            )
        )
    )

    // 为每个预设会话分配一个稳定的基础时间戳，确保重复上传时消息时间不会每次都被刷新成“现在”
    private fun getStableSeedBaseTimestamp(targetUsername: String): Long {
        // 根据预设账号名返回固定时间戳，让三段种子聊天始终保持稳定顺序
        return when (targetUsername) {
            // 小明的聊天时间作为第一段基准时间
            "xiaoming" -> 1_700_000_000_000L
            // 小华的聊天时间整体往后顺延一天，保证会话之间有稳定先后顺序
            "xiaohua" -> 1_700_086_400_000L
            // 大光的聊天时间再顺延一天，避免三个预设会话时间完全相同
            "daguang" -> 1_700_172_800_000L
            // 如果未来新增其他预设账号，就给一个默认基准时间兜底
            else -> 1_700_259_200_000L
        }
    }

    // 监听当前登录用户的会话列表
    fun listenMyConversations(
        onChange: (List<Conversation>) -> Unit,
        onError: (String) -> Unit = { _ -> }
    ): ListenerRegistration? {
        val currentUid = auth.currentUser?.uid

        if (currentUid.isNullOrBlank()) {
            onChange(emptyList())
            return null
        }

        return db.collection("chats")
            .whereArrayContains("participants", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "监听聊天列表失败")
                    return@addSnapshotListener
                }

                val chats = snapshot?.documents
                    ?.mapNotNull { document -> document.toObject(Chat::class.java) }
                    ?: emptyList()

                if (chats.isEmpty()) {
                    onChange(emptyList())
                    return@addSnapshotListener
                }

                val otherUserIds = chats
                    .map { chat -> ChatUtils.getOtherUserId(chat, currentUid) }
                    .filter { otherUserId -> otherUserId.isNotBlank() }
                    .toSet()

                fetchUsernames(otherUserIds) { usernameMap ->
                    val conversations = chats
                        .sortedByDescending { chat -> chat.lastTimestamp }
                        .mapNotNull { chat ->
                            val otherUserUid = ChatUtils.getOtherUserId(chat, currentUid)

                            if (otherUserUid.isBlank()) {
                                return@mapNotNull null
                            }

                            val otherUsername = usernameMap[otherUserUid] ?: otherUserUid
                            val previewText = chat.lastMessage.ifBlank { "还没有消息" }

                            Conversation(
                                name = otherUsername,
                                messages = mutableStateListOf(),
                                initialAvatar = DataSource.avatarForUsername(otherUsername),
                                initialOtherUserUid = otherUserUid,
                                initialChatId = chat.chatId,
                                initialPreviewText = previewText,
                                initialLastTimestamp = chat.lastTimestamp
                            )
                        }

                    onChange(conversations)
                }
            }
    }

    // 监听某个会话的消息列表
    fun listenMessagesForConversation(
        otherUserUid: String,
        onChange: (List<UiMessage>) -> Unit,
        onError: (String) -> Unit = { _ -> }
    ): ListenerRegistration? {
        val currentUid = auth.currentUser?.uid

        if (currentUid.isNullOrBlank() || otherUserUid.isBlank()) {
            onChange(emptyList())
            return null
        }

        val chatId = ChatUtils.getChatId(currentUid, otherUserUid)

        return db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "监听消息失败")
                    return@addSnapshotListener
                }

                val uiMessages = snapshot?.documents
                    ?.mapNotNull { document -> document.toObject(Message::class.java) }
                    ?.map { cloudMessage ->
                        UiMessage(
                            sender = if (cloudMessage.senderId == currentUid) MessageSender.ME else MessageSender.OTHER,
                            content = cloudMessage.text,
                            type = if (cloudMessage.type == "weather") "weather" else "text"
                        )
                    }
                    ?: emptyList()

                onChange(uiMessages)
            }
    }

    // 发送消息到云端，并同步更新 chats/{chatId} 的摘要信息
    fun sendMessage(
        otherUserUid: String,
        text: String,
        type: String = "text",
        onComplete: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid

        if (currentUid.isNullOrBlank()) {
            onComplete(false, "当前未登录")
            return
        }

        if (otherUserUid.isBlank()) {
            onComplete(false, "聊天对象不存在")
            return
        }

        val chatId = ChatUtils.getChatId(currentUid, otherUserUid)
        val timestamp = System.currentTimeMillis()
        val cloudMessage = Message(
            senderId = currentUid,
            receiverId = otherUserUid,
            text = text,
            timestamp = timestamp,
            type = type
        )
        val chatSummary = Chat(
            chatId = chatId,
            participants = listOf(currentUid, otherUserUid),
            lastMessage = text,
            lastTimestamp = timestamp,
            lastSenderId = currentUid
        )
        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val batch = db.batch()

        batch.set(chatRef, chatSummary, SetOptions.merge())
        batch.set(messageRef, cloudMessage)

        batch.commit()
            .addOnSuccessListener {
                onComplete(true, "发送成功")
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message ?: "发送失败")
            }
    }

    // 将原来 DataSource 中的固定聊天上传到云端，并绑定到 admin 与目标用户之间的会话中
    fun uploadAdminSeedChatsToCloud(
        onProgress: (String) -> Unit = { _ -> },
        onComplete: (Boolean, String) -> Unit
    ) {
        // 使用原子标记确保最终回调只触发一次，避免超时与异步结果重复回调
        val completionHandled = AtomicBoolean(false)
        // 创建主线程 Handler，用于执行超时兜底逻辑
        val timeoutHandler = Handler(Looper.getMainLooper())
        // 定义安全完成函数：只允许首次进入时真正结束流程
        fun completeOnce(success: Boolean, message: String) {
            if (completionHandled.compareAndSet(false, true)) {
                timeoutHandler.removeCallbacksAndMessages(null)
                onComplete(success, message)
            }
        }
        // 定义超时兜底任务：超过固定时间仍未完成则主动结束，避免 UI 一直显示“同步中”
        val timeoutRunnable = Runnable {
            completeOnce(false, "同步超时：请检查网络/VPN 或 Firestore 规则后重试")
        }
        // 启动超时计时器
        timeoutHandler.postDelayed(timeoutRunnable, SEED_UPLOAD_TIMEOUT_MS)

        // 给出第一条进度文案，明确预设消息来源于代码内置种子而不是本地聊天缓存
        onProgress("正在上传代码内置预设消息（CloudChatManager.adminSeedConversations）...")

        val currentUser = auth.currentUser

        if (currentUser == null) {
            completeOnce(false, "请先登录 admin 账号")
            return
        }

        val currentUsername = currentUser.email?.substringBefore("@") ?: ""

        if (currentUsername != "admin") {
            completeOnce(false, "只有 admin 账号可以上传预设聊天")
            return
        }

        ensureUserDocument(currentUser.uid, "admin") { adminSaved ->
            if (!adminSaved) {
                completeOnce(false, "admin 用户资料写入失败，请检查 Firestore 权限")
                return@ensureUserDocument
            }

            db.collection("users")
                .get()
                .addOnSuccessListener { snapshot ->
                    val userMap = snapshot.documents
                        .mapNotNull { document -> document.toObject(User::class.java) }
                        .associateBy { user -> user.username }

                    migrateSeedConversationsRecursively(
                        index = 0,
                        adminUid = currentUser.uid,
                        userMap = userMap,
                        successCount = 0,
                        failureCount = 0,
                        onProgress = onProgress,
                        onComplete = { success, message ->
                            completeOnce(success, message)
                        }
                    )
                }
                .addOnFailureListener { exception ->
                    completeOnce(false, exception.message ?: "读取用户列表失败")
                }
        }
    }

    // 递归上传种子聊天，便于逐条反馈进度
    private fun migrateSeedConversationsRecursively(
        index: Int,
        adminUid: String,
        userMap: Map<String, User>,
        successCount: Int,
        failureCount: Int,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (index >= adminSeedConversations.size) {
            val finalSuccess = successCount > 0 && failureCount == 0
            val finalMessage = if (successCount == 0) {
                "未上传任何聊天：请先手动注册 xiaoming / xiaohua / daguang"
            } else if (failureCount == 0) {
                "预设聊天上传完成：成功 $successCount/${adminSeedConversations.size}"
            } else {
                "预设聊天部分上传完成：成功 $successCount，失败 $failureCount"
            }

            onComplete(finalSuccess, finalMessage)
            return
        }

        val seedConversation = adminSeedConversations[index]
        val targetUser = userMap[seedConversation.targetUsername]

        if (targetUser == null || targetUser.uid.isBlank()) {
            onProgress("⚠️ 未找到账号 ${seedConversation.targetUsername}，请先手动注册后再上传")
            migrateSeedConversationsRecursively(
                index = index + 1,
                adminUid = adminUid,
                userMap = userMap,
                successCount = successCount,
                failureCount = failureCount + 1,
                onProgress = onProgress,
                onComplete = onComplete
            )
            return
        }

        uploadSingleSeedConversation(adminUid, targetUser, seedConversation) { success, message ->
            onProgress(message)

            migrateSeedConversationsRecursively(
                index = index + 1,
                adminUid = adminUid,
                userMap = userMap,
                successCount = successCount + if (success) 1 else 0,
                failureCount = failureCount + if (success) 0 else 1,
                onProgress = onProgress,
                onComplete = onComplete
            )
        }
    }

    // 上传单个种子会话，并使用稳定消息 ID 保证重复点击时不会产生重复消息
    private fun uploadSingleSeedConversation(
        adminUid: String,
        targetUser: User,
        seedConversation: SeedConversation,
        onComplete: (Boolean, String) -> Unit
    ) {
        val chatId = ChatUtils.getChatId(adminUid, targetUser.uid) // 根据 admin 和目标用户 UID 生成稳定 chatId
        val chatRef = db.collection("chats").document(chatId) // 定位到要写入的会话文档
        val batch = db.batch() // 使用批量写入，确保摘要和消息一起成功
        val baseTimestamp = getStableSeedBaseTimestamp(seedConversation.targetUsername) // 读取当前预设会话的稳定起始时间
        val lastSeedMessage = seedConversation.messages.lastOrNull() // 取出最后一条预设消息，用于生成会话摘要
        val lastMessageIndex = seedConversation.messages.lastIndex // 记录最后一条消息的索引，便于精确计算 lastTimestamp
        val lastSenderId = if (lastSeedMessage?.sentByAdmin == true) adminUid else targetUser.uid // 根据最后一条消息的发送方设置 lastSenderId
        val chatSummary = Chat(
            chatId = chatId, // 写入当前会话的唯一标识符
            participants = listOf(adminUid, targetUser.uid), // 写入聊天双方 UID，供会话列表筛选使用
            lastMessage = lastSeedMessage?.text ?: "", // 把最后一条消息内容写到摘要里
            lastTimestamp = if (lastMessageIndex >= 0) baseTimestamp + (lastMessageIndex * 60_000L) else baseTimestamp, // 使用最后一条真实消息时间，修正之前多加一分钟的问题
            lastSenderId = lastSenderId // 写入最后发言人的 UID
        )

        batch.set(chatRef, chatSummary, SetOptions.merge()) // 先写入会话摘要，保证列表页能直接看到预览

        seedConversation.messages.forEachIndexed { messageIndex, seedMessage ->
            val senderId = if (seedMessage.sentByAdmin) adminUid else targetUser.uid // 判断这一条种子消息是谁发送的
            val receiverId = if (seedMessage.sentByAdmin) targetUser.uid else adminUid // 根据发送方反推出接收方 UID
            val messageDocId = "seed_${seedConversation.targetUsername}_${messageIndex + 1}" // 为每条预设消息生成稳定文档 ID，防止重复上传出重复消息
            val messageTimestamp = baseTimestamp + (messageIndex * 60_000L) // 让每条消息按分钟递增，保持原有聊天顺序
            val cloudMessage = Message(
                senderId = senderId, // 写入发送者 UID
                receiverId = receiverId, // 写入接收者 UID
                text = seedMessage.text, // 写入消息文本内容
                timestamp = messageTimestamp, // 写入稳定的消息时间戳
                type = seedMessage.type // 写入消息类型，方便后续继续扩展图片/天气等能力
            )

            batch.set(chatRef.collection("messages").document(messageDocId), cloudMessage, SetOptions.merge()) // 将单条预设消息写入 messages 子集合
        }

        batch.commit()
            .addOnSuccessListener {
                onComplete(true, "✅ 已上传 ${seedConversation.displayName} 的预设聊天")
            }
            .addOnFailureListener { exception ->
                onComplete(false, "❌ 上传 ${seedConversation.displayName} 失败：${exception.message ?: "未知错误"}")
            }
    }

    // 批量会话列表加载时，需要把 UID 转成用户名用于 UI 展示
    private fun fetchUsernames(
        userIds: Set<String>,
        onComplete: (Map<String, String>) -> Unit
    ) {
        if (userIds.isEmpty()) {
            onComplete(emptyMap())
            return
        }

        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                val usernameMap = snapshot.documents
                    .mapNotNull { document -> document.toObject(User::class.java) }
                    .filter { user -> user.uid in userIds }
                    .associate { user -> user.uid to user.username }

                onComplete(usernameMap)
            }
            .addOnFailureListener {
                onComplete(emptyMap())
            }
    }

    // 确保当前登录用户在 users 集合中存在资料文档
    private fun ensureUserDocument(
        uid: String,
        username: String,
        onComplete: (Boolean) -> Unit
    ) {
        val user = User(
            uid = uid,
            username = username,
            createdAt = System.currentTimeMillis()
        )

        db.collection("users")
            .document(uid)
            .set(user, SetOptions.merge())
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    // 搜索用户：根据用户名/ID查询用户
    fun searchUser(
        username: String,
        onComplete: (User?, String) -> Unit
    ) {
        if (username.isBlank()) {
            onComplete(null, "请输入有效的用户ID")
            return
        }

        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            onComplete(null, "当前未登录")
            return
        }

        if (username == auth.currentUser?.email?.substringBefore("@")) {
            onComplete(null, "不能添加自己为好友")
            return
        }

        // 从 users 集合中搜索匹配的用户
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.documents.firstOrNull()?.toObject(User::class.java)
                if (user != null && user.uid.isNotBlank()) {
                    onComplete(user, "")
                } else {
                    onComplete(null, "未找到该用户，请检查用户ID是否正确")
                }
            }
            .addOnFailureListener { exception ->
                onComplete(null, exception.message ?: "搜索失败")
            }
    }

    // 创建或更新与指定用户的会话（建立好友关系）
    fun createOrUpdateConversation(
        otherUserUid: String,
        otherUsername: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            onComplete(false, "当前未登录")
            return
        }

        if (otherUserUid.isBlank()) {
            onComplete(false, "无效的用户ID")
            return
        }

        val chatId = ChatUtils.getChatId(currentUid, otherUserUid)
        val timestamp = System.currentTimeMillis()

        // 创建会话记录
        val chat = Chat(
            chatId = chatId,
            participants = listOf(currentUid, otherUserUid),
            lastMessage = "",
            lastTimestamp = timestamp,
            lastSenderId = currentUid
        )

        db.collection("chats")
            .document(chatId)
            .set(chat, SetOptions.merge())
            .addOnSuccessListener {
                onComplete(true, "成功添加好友：$otherUsername")
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message ?: "添加好友失败")
            }
    }
}

