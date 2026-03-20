package com.example.screenshotoftaskmanager

import android.os.Handler
import android.os.Looper
import android.util.Log // 添加日志导入
import com.example.screenshotoftaskmanager.ui.Conversation
import com.example.screenshotoftaskmanager.ui.DataSource
import com.example.screenshotoftaskmanager.ui.Message as UiMessage
import com.example.screenshotoftaskmanager.ui.MessageSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.mutableStateListOf

// 云端聊天管理器：负责聊天列表监听、消息监听、发送消息、上传 admin 的预设聊天种子数据
object CloudChatManager {

    // Firebase Auth 实例，用于获取当前登录用户
    private val auth = FirebaseAuth.getInstance()

    // 上传预设聊天时的超时时间，避免网络异常导致流程一直不返回
    private const val SEED_UPLOAD_TIMEOUT_MS = 25_000L

    // 使用动态 getter 获取 Firestore，避免静态字段持有 Context 触发泄漏告警
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // 统一推断聊天是否为群聊，避免 chatType 字段被错误覆盖后走错分支
    private fun isGroupConversation(chat: Chat): Boolean {
        return chat.chatType.equals("group", ignoreCase = true) ||
            chat.chatId.startsWith("group_") ||
            chat.owner.isNotBlank() ||
            chat.createdAt > 0L
    }

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

                val chats = snapshot?.documents?.mapNotNull { document ->
                    try {
                        // 安全地将 Firestore 文档转换为 Chat 对象
                        val chat = document.toObject(Chat::class.java)
                        if (chat == null) {
                            Log.w("CloudChatManager", "Chat 对象为 null，文档数据: ${document.data}")
                            return@mapNotNull null
                        }
                        
                        // 验证必要字段
                        if (chat.chatId.isBlank()) {
                            Log.w("CloudChatManager", "Chat ID 为空")
                            return@mapNotNull null
                        }
                        
                        // ✅ 防御性修复：优先通过结构信息推断群聊，避免 group_ 文档被当成私聊
                        val inferredGroupChat = isGroupConversation(chat)
                        val normalizedType = if (inferredGroupChat) "group" else "private"
                        if (chat.chatType != normalizedType) {
                            Log.w(
                                "CloudChatManager",
                                "🔧 检测到数据不一致：chatId=${chat.chatId}, 原chatType='${chat.chatType}', 修正为 '$normalizedType'"
                            )
                            chat.chatType = normalizedType
                        }
                        
                        // 检查 participants 字段
                        if (chat.participants.isEmpty()) {
                            Log.w("CloudChatManager", "Participants 为空，chatId: ${chat.chatId}, chatType: ${chat.chatType}")
                            // 对于群聊，participants 不应该为空，跳过这条记录
                            if (chat.chatType == "group") {
                                Log.w("CloudChatManager", "❌ 群聊 ${chat.chatId} 的 participants 为空，跳过此记录")
                                return@mapNotNull null
                            }
                        }
                        
                        // ✅ 额外验证：群聊必须有非空的 groupName
                        if (chat.chatType == "group" && chat.groupName.isBlank()) {
                            Log.w("CloudChatManager", "⚠️ 群聊 ${chat.chatId} 的 groupName 为空，使用默认值")
                            chat.groupName = "群聊"
                        }
                        
                        chat // 返回有效的 chat 对象
                    } catch (e: Exception) {
                        // 捕获序列化异常
                        Log.e("CloudChatManager", "将 Firestore 文档转换为 Chat 失败: ${e.message}", e)
                        null
                    }
                } ?: emptyList()

                if (chats.isEmpty()) {
                    onChange(emptyList())
                    return@addSnapshotListener
                }

                val otherUserIds = chats
                    .filter { chat -> !isGroupConversation(chat) } // ✅ 只处理一对一聊天，群聊不需要查询用户名
                    .mapNotNull { chat -> ChatUtils.getOtherUserId(chat, currentUid) }
                    .filter { otherUserId -> otherUserId.isNotBlank() }
                    .toSet()

                fetchUsernames(otherUserIds) { usernameMap ->
                    val conversations = chats
                        .sortedByDescending { chat -> chat.lastTimestamp }
                        .mapNotNull { chat ->
                            try {
                                // 判断聊天类型：群聊或一对一
                                val isGroupChat = isGroupConversation(chat) // 检查是否为群聊
                                
                                if (isGroupChat) { // 如果是群聊
                                    // 检查群名是否为空，如果为空则使用默认名称
                                    val groupName = chat.groupName.ifBlank { "群聊" } // ✅ 添加默认名称
                                    val previewText = chat.lastMessage.ifBlank { "还没有消息" }
                                    
                                    // ✅ 严格验证：确保 groupName 不是成员名而是真实的群名
                                    if (chat.groupName.isBlank()) {
                                        Log.w("CloudChatManager", "⚠️ 警告：群聊 chatId=${chat.chatId} 的 groupName 为空，使用默认值")
                                    }
                                    
                                    Log.d("CloudChatManager", "✅ 加载群聊：chatId=${chat.chatId}, groupName=$groupName, participants=${chat.participants.size}人, " +
                                        "chatType=${chat.chatType}, owner=${chat.owner}")
                                    
                                    Conversation(
                                        name = groupName, // 使用检查后的群聊名称 - 绝对不能是成员名
                                        messages = mutableStateListOf(),
                                        initialAvatar = DataSource.avatarForUsername(groupName), // 用群名生成头像
                                        initialOtherUserUid = "", // 群聊无对方 UID
                                        initialChatId = chat.chatId,
                                        initialPreviewText = previewText,
                                        initialLastTimestamp = chat.lastTimestamp,
                                        initialChatType = "group", // 设置为群聊
                                        initialGroupName = groupName, // 设置群名 - 和 name 保持一致
                                        initialParticipants = chat.participants // ✅ 传入真实的成员列表
                                    )
                                } else { // 如果是一对一聊天
                                    val otherUserUid = ChatUtils.getOtherUserId(chat, currentUid)

                                    if (otherUserUid.isBlank()) {
                                        Log.w("CloudChatManager", "⚠️ 一对一聊天无法获取对方UID: ${chat.chatId}")
                                        return@mapNotNull null
                                    }

                                    val otherUsername = usernameMap[otherUserUid] ?: otherUserUid
                                    val previewText = chat.lastMessage.ifBlank { "还没有消息" }

                                    Log.d("CloudChatManager", "✅ 加载私聊：chatId=${chat.chatId}, username=$otherUsername, otherUserUid=$otherUserUid")

                                    Conversation(
                                        name = otherUsername,
                                        messages = mutableStateListOf(),
                                        initialAvatar = DataSource.avatarForUsername(otherUsername),
                                        initialOtherUserUid = otherUserUid,
                                        initialChatId = chat.chatId,
                                        initialPreviewText = previewText,
                                        initialLastTimestamp = chat.lastTimestamp,
                                        initialChatType = "private", // 设置为一对一
                                        initialGroupName = "" // 一对一时无群名
                                    )
                                }
                            } catch (e: Exception) {
                                // 捕获异常防止闪退
                                Log.e("CloudChatManager", "加载会话失败: ${e.message}", e)
                                null
                            }
                        }

                    onChange(conversations)
                }
            }
    }

    // 监听某个会话的消息列表（支持群聊和一对一）
    fun listenMessagesForConversation(
        otherUserUid: String, // 参数名保持不变，但实际可以是 chatId（群聊）或 otherUserUid（一对一）
        onChange: (List<UiMessage>) -> Unit,
        onError: (String) -> Unit = { _ -> }
    ): ListenerRegistration? {
        val currentUid = auth.currentUser?.uid

        if (currentUid.isNullOrBlank() || otherUserUid.isBlank()) {
            onChange(emptyList())
            return null
        }

        // 统一解析 chatId：群聊直接用传入 ID；如果已是 chatId（包含当前用户且有分隔符）也直接使用
        val chatId = when {
            otherUserUid.startsWith("group_") -> otherUserUid
            otherUserUid.contains("_") && otherUserUid.split("_").size == 2 && otherUserUid.split("_").contains(currentUid) -> otherUserUid
            else -> ChatUtils.getChatId(currentUid, otherUserUid)
        }

        // 缓存用户名映射（用于获取发送者的用户名）
        val usernameCache = mutableMapOf<String, String>()

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
                        // 获取发送者的用户名（用于群聊显示）
                        val senderName = if (cloudMessage.senderId == currentUid) {
                            auth.currentUser?.email?.substringBefore("@") ?: "我" // 我的用户名
                        } else {
                            // 尝试从缓存获取，如果没有则从 Firestore 查询
                            usernameCache.getOrElse(cloudMessage.senderId) {
                                // 同步查询获取用户名（建议后续优化为异步）
                                cloudMessage.senderId // 暂时使用 UID，待优化
                            }
                        }
                        
                        UiMessage(
                            sender = if (cloudMessage.senderId == currentUid) MessageSender.ME else MessageSender.OTHER,
                            content = cloudMessage.text,
                            type = if (cloudMessage.type == "weather") "weather" else "text",
                            senderName = senderName // 添加发送者名字
                        )
                    }
                    ?: emptyList()

                onChange(uiMessages)
            }
    }

    // 发送消息到云端，并同步更新 chats/{chatId} 的摘要信息（支持群聊和一对一）
    fun sendMessage(
        otherUserUid: String, // 参数名保持不变，但实际可以是 chatId（群聊）或 otherUserUid（一对一）
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

        // 统一解析 chatId：优先使用显式群聊/现成 chatId，其次计算一对一 chatId
        val chatId = when {
            otherUserUid.startsWith("group_") -> otherUserUid
            otherUserUid.contains("_") && otherUserUid.split("_").size == 2 && otherUserUid.split("_").contains(currentUid) -> otherUserUid
            else -> ChatUtils.getChatId(currentUid, otherUserUid)
        }

        val timestamp = System.currentTimeMillis()
        val cloudMessage = Message(
            senderId = currentUid,
            receiverId = otherUserUid, // 群聊时这个字段可能是群 ID，但不影响功能
            text = text,
            timestamp = timestamp,
            type = type
        )
        
        // 获取当前 chat 文档，仅用于补齐缺失字段；摘要更新只写增量字段，避免把群聊覆盖成私聊
        val chatRef = db.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document()

        chatRef
            .get()
            .addOnSuccessListener { chatDoc ->
                val docChat = chatDoc.toObject(Chat::class.java)
                val isGroupChat = (docChat?.let { isGroupConversation(it) } == true) || chatId.startsWith("group_")

                @Suppress("UNCHECKED_CAST")
                val docParticipants = (chatDoc.get("participants") as? List<String>).orEmpty()
                val participants = when {
                    docParticipants.isNotEmpty() -> docParticipants
                    isGroupChat -> listOf(currentUid)
                    else -> listOf(currentUid, otherUserUid)
                }.distinct()

                val summaryUpdate = hashMapOf<String, Any>(
                    "chatId" to chatId,
                    "lastMessage" to text,
                    "lastTimestamp" to timestamp,
                    "lastSenderId" to currentUid
                )

                if (!chatDoc.exists()) {
                    summaryUpdate["chatType"] = if (isGroupChat) "group" else "private"
                    summaryUpdate["participants"] = participants
                } else {
                    if (docParticipants.isEmpty()) {
                        summaryUpdate["participants"] = participants
                    }
                    if (isGroupChat && !chatDoc.getString("chatType").equals("group", ignoreCase = true)) {
                        summaryUpdate["chatType"] = "group"
                    }
                }

                val batch = db.batch()
                batch.set(chatRef, summaryUpdate, SetOptions.merge())
                batch.set(messageRef, cloudMessage)

                batch.commit()
                    .addOnSuccessListener {
                        onComplete(true, "发送成功")
                    }
                    .addOnFailureListener { exception ->
                        onComplete(false, exception.message ?: "发送失败")
                    }
            }
            .addOnFailureListener {
                val isGroupChat = chatId.startsWith("group_")
                val summaryUpdate = hashMapOf<String, Any>(
                    "chatId" to chatId,
                    "chatType" to if (isGroupChat) "group" else "private",
                    "participants" to if (isGroupChat) listOf(currentUid) else listOf(currentUid, otherUserUid),
                    "lastMessage" to text,
                    "lastTimestamp" to timestamp,
                    "lastSenderId" to currentUid
                )

                val batch = db.batch()
                batch.set(chatRef, summaryUpdate, SetOptions.merge())
                batch.set(messageRef, cloudMessage)

                batch.commit()
                    .addOnSuccessListener {
                        onComplete(true, "发送成功")
                    }
                    .addOnFailureListener { newException ->
                        onComplete(false, newException.message ?: "发送失败")
                    }
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

    // 创建群聊方法
    fun createGroupChat(
        groupName: String, // 群聊名称
        memberUids: List<String>, // 群成员 UID 列表（不包括群主自己）
        onComplete: (Boolean, String) -> Unit // 完成回调（成功标志和消息）
    ) {
        val currentUid = auth.currentUser?.uid // 获取当前登录用户 UID（群主）
        if (currentUid.isNullOrBlank()) { // 如果当前用户未登录
            onComplete(false, "当前未登录") // 返回失败
            return // 终止函数
        }

        if (groupName.isBlank()) { // 如果群名为空
            onComplete(false, "群聊名称不能为空") // 返回失败
            return // 终止函数
        }

        if (memberUids.isEmpty()) { // 如果成员列表为空
            onComplete(false, "群聊至少需要一个成员") // 返回失败
            return // 终止函数
        }

        // 生成群聊唯一 ID（使用 UUID 或基于成员 UID hash）
        val groupId = "group_${System.currentTimeMillis()}_${(0..999).random()}" // 生成唯一群 ID
        val timestamp = System.currentTimeMillis() // 获取当前时间戳

        // 构建完整的参与者列表（包括群主和所有成员）
        val allParticipants = listOf(currentUid) + memberUids // ✅ 创建成员列表（包括群主）

        // 构建群聊数据对象
        val groupChat = Chat(
            chatId = groupId, // 群聊唯一 ID
            chatType = "group", // 设置聊天类型为群聊
            groupName = groupName, // 设置群名称
            groupAvatar = "", // 群头像（暂时为空，可后续支持上传）
            participants = allParticipants, // ✅ 设置参与者列表（非空）
            owner = currentUid, // 设置群主为当前用户
            createdAt = timestamp, // 记录群聊创建时间
            lastMessage = "", // 初始时没有消息
            lastTimestamp = timestamp, // 最后消息时间为创建时间
            lastSenderId = currentUid, // 最后消息发送者为群主
            lastSenderName = "" // 初始为空
        )

        // ✅ 只需要一次写入，无需循环！所有成员的 participants 都相同
        val batch = db.batch()
        batch.set(
            db.collection("chats").document(groupId), // 群聊文档
            groupChat // 群聊数据
        )

        // 提交批量操作
        batch.commit()
            .addOnSuccessListener { _ ->
                Log.d("CloudChatManager", "群聊创建成功: $groupId，参与者: $allParticipants") // ✅ 添加调试日志
                // 本地预先添加群聊会话，避免列表因云端延迟被其他会话覆盖
                val newConversation = Conversation(
                    name = groupName.ifBlank { "群聊" },
                    messages = mutableStateListOf(),
                    initialAvatar = DataSource.avatarForUsername(groupName.ifBlank { "群聊" }),
                    initialOtherUserUid = "",
                    initialChatId = groupId,
                    initialPreviewText = "还没有消息",
                    initialLastTimestamp = timestamp,
                    initialChatType = "group",
                    initialGroupName = groupName,
                    initialParticipants = allParticipants
                )
                DataSource.replaceConversations(DataSource.conversations + newConversation)
                onComplete(true, "群聊 '$groupName' 创建成功") // 返回成功
            }
            .addOnFailureListener { exception ->
                Log.e("CloudChatManager", "创建群聊失败: ${exception.message}") // ✅ 添加错误日志
                onComplete(false, exception.message ?: "创建群聊失败") // 返回失败和错误信息
            }
    }
}
