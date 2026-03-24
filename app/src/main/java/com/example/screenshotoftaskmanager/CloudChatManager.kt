package com.example.screenshotoftaskmanager // 应用包名声明

import android.os.Handler // 导入 Handler，用于在主线程执行定时任务
import android.os.Looper // 导入 Looper，用于获取主线程消息队列
import android.util.Log // 导入日志工具，用于调试和错误追踪
import com.example.screenshotoftaskmanager.ui.Conversation // 导入会话类
import com.example.screenshotoftaskmanager.ui.DataSource // 导入本地数据源
import com.example.screenshotoftaskmanager.ui.Message as UiMessage // 导入 UI 消息类，重命名为 UiMessage 避免冲突
import com.example.screenshotoftaskmanager.ui.MessageSender // 导入消息发送者枚举（ME/OTHER）
import com.google.firebase.auth.FirebaseAuth // 导入 Firebase 认证库
import com.google.firebase.firestore.FirebaseFirestore // 导入 Firestore 数据库库
import com.google.firebase.firestore.ListenerRegistration // 导入监听注册类，用于管理实时监听
import com.google.firebase.firestore.SetOptions // 导入写入选项，支持 merge 合并写入
import java.util.concurrent.atomic.AtomicBoolean // 导入原子布尔值，用于并发安全的状态标记
import androidx.compose.runtime.mutableStateListOf // 导入可观察列表，用于实时更新 UI

// 云端聊天管理器：负责聊天列表监听、消息监听、发送消息、上传 admin 的预设聊天种子数据
object CloudChatManager { // 单例对象，管理所有云端聊天操作

    // Firebase Auth 实例，用于获取当前登录用户
    private val auth = FirebaseAuth.getInstance() // 获取全局 Firebase 认证实例

    // 上传预设聊天时的超时时间，避免网络异常导致流程一直不返回
    private const val SEED_UPLOAD_TIMEOUT_MS = 25_000L // 种子聊天上传超时时间为 25 秒

    // 使用动态 getter 获取 Firestore，避免静态字段持有 Context 触发泄漏告警
    private val db: FirebaseFirestore // Firestore 数据库引用（只读属性，每次访问都动态获取新实例）
        get() = FirebaseFirestore.getInstance() // 通过 getter 动态获取 Firestore 实例，避免长期持有 Context

    // 统一推断聊天是否为群聊，避免 chatType 字段被错误覆盖后走错分支
    private fun isGroupConversation(chat: Chat): Boolean { // 判断一个聊天是否为群聊（不仅看 chatType，还看其他特征）
        return chat.chatType.equals("group", ignoreCase = true) || // 聊天类型明确为 "group"
            chat.chatId.startsWith("group_") || // 或者 chatId 以 "group_" 开头
            chat.owner.isNotBlank() || // 或者有群主（owner 字段非空）
            chat.createdAt > 0L // 或者有创建时间戳（群聊特有）
    }

    // 预设聊天中的单条种子消息
    private data class SeedMessage( // 数据类：表示种子数据中的一条消息
        val text: String, // 消息文本
        val sentByAdmin: Boolean, // 是否由 admin 发送
        val type: String = "text" // 消息类型，默认为普通文本
    )

    // 预设聊天中的单个会话定义
    private data class SeedConversation( // 数据类：表示一个预设会话（包含多条消息）
        val targetUsername: String, // 对方用户名（预设会话的对象）
        val displayName: String, // 对方显示名称（用于 UI 展示）
        val messages: List<SeedMessage> // 该会话中的所有种子消息
    )

    // 原来 DataSource 中的固定聊天，现在迁移为"可上传到云端的种子数据"
    private val adminSeedConversations = listOf( // 存储admin预设的三个聊天会话  
        SeedConversation( // 第一个种子会话对象：小明
            targetUsername = "xiaoming", // 对方用户名设为xiaoming
            displayName = "小明", // 对方显示名设为小明
            messages = listOf( // 包含以下消息列表
                SeedMessage(text = "明天干饭去", sentByAdmin = false), // 小明发起邀约
                SeedMessage(text = "好啊，去哪吃？", sentByAdmin = true), // admin回应询问
                SeedMessage(text = "[图片]", sentByAdmin = false), // 小明发送图片
                SeedMessage(text = "这是哪？看起来不错。", sentByAdmin = true), // admin评论
                SeedMessage(text = "这是那家新开的串串店", sentByAdmin = false), // 小明说明
                SeedMessage(text = "位置我发你，明天直接在那碰头？", sentByAdmin = false) // 小明确认约定
            )
        ),
        SeedConversation( // 第二个种子会话对象：小华
            targetUsername = "xiaohua", // 对方用户名设为xiaohua
            displayName = "小华", // 对方显示名设为小华
            messages = listOf( // 包含以下消息列表
                SeedMessage(text = "周末有空吗？一起打球？", sentByAdmin = false), // 小华提议打球
                SeedMessage(text = "[图片]", sentByAdmin = true) // admin回复图片
            )
        ),
        SeedConversation( // 第三个种子会话对象：大光
            targetUsername = "daguang", // 对方用户名设为daguang
            displayName = "大光", // 对方显示名设为大光
            messages = listOf( // 包含以下消息列表
                SeedMessage(text = "在干嘛呢？", sentByAdmin = false), // 大光问候
                SeedMessage(text = "刚吃完饭，准备休息一下。", sentByAdmin = true), // admin回应
                SeedMessage(text = "你现在到哪了？", sentByAdmin = false) // 大光询问位置
            )
        )
    )

    // 为每个预设会话分配一个稳定的基础时间戳，确保重复上传时消息时间不会每次都被刷新成"现在"
    private fun getStableSeedBaseTimestamp(targetUsername: String): Long { // 根据目标用户名返回稳定的起始时间戳
        // 根据预设账号名返回固定时间戳，让三段种子聊天始终保持稳定顺序
        return when (targetUsername) { // 根据不同用户名返回不同的基础时间戳
            // 小明的聊天时间作为第一段基准时间
            "xiaoming" -> 1_700_000_000_000L // 小明聊天的起始时间戳
            // 小华的聊天时间整体往后顺延一天，保证会话之间有稳定先后顺序
            "xiaohua" -> 1_700_086_400_000L // 小华聊天时间向后延推一整天
            // 大光的聊天时间再顺延一天，避免三个预设会话时间完全相同
            "daguang" -> 1_700_172_800_000L // 大光聊天时间向后延推两天
            // 如果未来新增其他预设账号，就给一个默认基准时间兜底
            else -> 1_700_259_200_000L // 其他账号的默认基础时间戳兜底值
        }
    }

    // 监听当前登录用户的会话列表
    fun listenMyConversations( // 函数名：监听当前用户的所有会话
        onChange: (List<Conversation>) -> Unit, // 当会话列表变化时的回调函数
        onError: (String) -> Unit = { _ -> } // 发生错误时的回调函数，默认为空实现
    ): ListenerRegistration? { // 返回监听注册对象，用于后续取消监听
        val currentUid = auth.currentUser?.uid // 先从 Firebase Auth 获取当前用户的 UID

        if (currentUid.isNullOrBlank()) { // 如果当前用户未登录或 UID 为空
            onChange(emptyList()) // 直接返回空会话列表
            return null // 并返回 null（没有启动监听）
        }

        return db.collection("chats") // 获取 Firestore 的 chats 集合
            .whereArrayContains("participants", currentUid) // 只查询包含当前用户的聊天
            .addSnapshotListener { snapshot, error -> // 实时监听查询结果变化
                if (error != null) { // 如果发生错误
                    onError(error.message ?: "监听聊天列表失败") // 调用错误回调
                    return@addSnapshotListener // 提前返回
                }

                val chats = snapshot?.documents?.mapNotNull { document -> // 将文档列表转换为 Chat 对象列表
                    try {
                        // 安全地将 Firestore 文档转换为 Chat 对象
                        val chat = document.toObject(Chat::class.java) // 使用 Firestore 的反序列化方法
                        if (chat == null) { // 如果反序列化失败
                            Log.w("CloudChatManager", "Chat 对象为 null，文档数据: ${document.data}") // 打印警告日志
                            return@mapNotNull null // 跳过这条记录
                        }
                        
                        // 验证必要字段
                        if (chat.chatId.isBlank()) { // 如果 chatId 为空
                            Log.w("CloudChatManager", "Chat ID 为空") // 打印警告
                            return@mapNotNull null // 跳过这条记录
                        }
                        
                        // ✅ 防御性修复：优先通过结构信息推断群聊，避免 group_ 文档被当成私聊
                        // 这里做"类型归一化"，后续所有分支都基于归一化结果，减少串型风险
                        val inferredGroupChat = isGroupConversation(chat) // 调用判断函数检查是否为群聊
                        val normalizedType = if (inferredGroupChat) "group" else "private" // 根据推断结果确定类型
                        if (chat.chatType != normalizedType) { // 如果推断结果与原有 chatType 不一致
                            Log.w(
                                "CloudChatManager",
                                "🔧 检测到数据不一致：chatId=${chat.chatId}, 原chatType='${chat.chatType}', 修正为 '$normalizedType'"
                            ) // 打印不一致警告
                            chat.chatType = normalizedType // 自动修正 chatType 字段
                        }
                        
                        // 检查 participants 字段
                        if (chat.participants.isEmpty()) { // 如果参与者列表为空
                            Log.w("CloudChatManager", "Participants 为空，chatId: ${chat.chatId}, chatType: ${chat.chatType}") // 打印警告
                            // 对于群聊，participants 不应该为空，跳过这条记录
                            if (chat.chatType == "group") { // 如果这个聊天应该是群聊
                                Log.w("CloudChatManager", "❌ 群聊 ${chat.chatId} 的 participants 为空，跳过此记录") // 打印错误日志
                                return@mapNotNull null // 跳过这条记录
                            }
                        }
                        
                        // ✅ 额外验证：群聊必须有非空的 groupName
                        if (chat.chatType == "group" && chat.groupName.isBlank()) { // 如果是群聊但群名为空
                            Log.w("CloudChatManager", "⚠️ 群聊 ${chat.chatId} 的 groupName 为空，使用默认值") // 打印警告
                            chat.groupName = "群聊" // 用默认群名替换
                        }
                        
                        chat // 返回修正后的 chat 对象
                    } catch (e: Exception) { // 如果发生异常
                        // 捕获序列化异常
                        Log.e("CloudChatManager", "将 Firestore 文档转换为 Chat 失败: ${e.message}", e) // 打印错误日志及堆栈
                        null // 跳过这条记录
                    }
                } ?: emptyList() // 如果转换失败，返回空列表

                if (chats.isEmpty()) { // 如果没有找到任何聊天
                    onChange(emptyList()) // 通知上层会话列表为空
                    return@addSnapshotListener // 提前返回
                }

                val otherUserIds = chats // 提取所有聊天中的"对方 UID"
                    // 群聊不需要查"对方用户名"，只对私聊做 UID -> username 映射
                    .filter { chat -> !isGroupConversation(chat) } // ✅ 只处理一对一聊天，群聊不需要查询用户名
                    .mapNotNull { chat -> ChatUtils.getOtherUserId(chat, currentUid) } // 为每个私聊提取对方 UID
                    .filter { otherUserId -> otherUserId.isNotBlank() } // 过滤掉空的 UID
                    .toSet() // 去重后转成集合

                fetchUsernames(otherUserIds) { usernameMap -> // 批量查询这些 UID 对应的用户名
                    val conversations = chats // 将 Chat 对象列表转换为 Conversation UI 对象列表
                        .sortedByDescending { chat -> chat.lastTimestamp } // 按最后消息时间降序排列
                        .mapNotNull { chat -> // 遍历并转换每个聊天
                            try {
                                // 判断聊天类型：群聊或一对一
                                val isGroupChat = isGroupConversation(chat) // 再次检查是否为群聊
                                
                                if (isGroupChat) { // 如果是群聊
                                    // 检查群名是否为空，如果为空则使用默认名称
                                    val groupName = chat.groupName.ifBlank { "群聊" } // ✅ 添加默认名称
                                    val previewText = chat.lastMessage.ifBlank { "还没有消息" } // 最后消息文本，若为空则显示"还没有消息"
                                    
                                    // ✅ 严格验证：确保 groupName 不是成员名而是真实的群名
                                    if (chat.groupName.isBlank()) { // 如果群名为空
                                        Log.w("CloudChatManager", "⚠️ 警告：群聊 chatId=${chat.chatId} 的 groupName 为空，使用默认值") // 打印警告
                                    }
                                    
                                    Log.d("CloudChatManager", "✅ 加载群聊：chatId=${chat.chatId}, groupName=$groupName, participants=${chat.participants.size}人, " +
                                        "chatType=${chat.chatType}, owner=${chat.owner}") // 打印调试日志
                                    
                                    Conversation( // 构建群聊 UI 对象
                                        name = groupName, // 使用检查后的群聊名称 - 绝对不能是成员名
                                        messages = mutableStateListOf(), // 消息列表初始为空，后续监听消息时填充
                                        initialAvatar = DataSource.avatarForUsername(groupName), // 用群名生成头像
                                        initialOtherUserUid = "", // 群聊无对方 UID
                                        initialChatId = chat.chatId, // 群聊 ID
                                        initialPreviewText = previewText, // 列表页预览文本
                                        initialLastTimestamp = chat.lastTimestamp, // 最后消息时间
                                        initialChatType = "group", // 设置为群聊
                                        initialGroupName = groupName, // 设置群名 - 和 name 保持一致
                                        initialParticipants = chat.participants // ✅ 传入真实的成员列表
                                    )
                                } else { // 如果是一对一聊天
                                    val otherUserUid = ChatUtils.getOtherUserId(chat, currentUid) // 提取对方 UID

                                    if (otherUserUid.isBlank()) { // 如果对方 UID 为空
                                        Log.w("CloudChatManager", "⚠️ 一对一聊天无法获取对方UID: ${chat.chatId}") // 打印警告
                                        return@mapNotNull null // 跳过这条记录
                                    }

                                    val otherUsername = usernameMap[otherUserUid] ?: otherUserUid // 从 usernameMap 查询对方用户名，若没有则用 UID
                                    val previewText = chat.lastMessage.ifBlank { "还没有消息" } // 最后消息文本

                                    Log.d("CloudChatManager", "✅ 加载私聊：chatId=${chat.chatId}, username=$otherUsername, otherUserUid=$otherUserUid") // 打印调试日志

                                    Conversation( // 构建私聊 UI 对象
                                        name = otherUsername, // 对方用户名作为会话名
                                        messages = mutableStateListOf(), // 消息列表初始为空
                                        initialAvatar = DataSource.avatarForUsername(otherUsername), // 根据用户名生成头像
                                        initialOtherUserUid = otherUserUid, // 记录对方 UID
                                        initialChatId = chat.chatId, // 会话 ID
                                        initialPreviewText = previewText, // 列表页预览文本
                                        initialLastTimestamp = chat.lastTimestamp, // 最后消息时间
                                        initialChatType = "private", // 设置为一对一
                                        initialGroupName = "" // 一对一时无群名
                                    )
                                }
                            } catch (e: Exception) { // 如果发生异常
                                // 捕获异常防止闪退
                                Log.e("CloudChatManager", "加载会话失败: ${e.message}", e) // 打印错误日志
                                null // 返回 null 跳过这条记录
                            }
                        }

                    onChange(conversations) // 通知上层会话列表已更新
                }
            }
    }

    // 监听某个会话的消息列表（支持群聊和一对一）
    fun listenMessagesForConversation( // 实时监听指定会话的消息列表变化
        otherUserUid: String, // 参数：群聊 chatId 或私聊对方 UID
        onChange: (List<UiMessage>) -> Unit, // 消息列表更新时的回调
        onError: (String) -> Unit = { _ -> } // 错误回调，默认空实现
    ): ListenerRegistration? { // 返回监听注册对象，用于后续取消监听
        val currentUid = auth.currentUser?.uid // 获取当前登录用户 UID

        if (currentUid.isNullOrBlank() || otherUserUid.isBlank()) { // 如果当前用户未登录或参数为空
            onChange(emptyList()) // 直接返回空消息列表
            return null // 并返回 null（没有启动监听）
        }

        // 统一解析 chatId：群聊直接用传入 ID；如果已是 chatId（包含当前用户且有分隔符）也直接使用
        val chatId = when { // 根据输入参数的形式判断是否需要转换成 chatId
            otherUserUid.startsWith("group_") -> otherUserUid // 如果以 "group_" 开头，说明已是群聊 ID
            otherUserUid.contains("_") && otherUserUid.split("_").size == 2 && otherUserUid.split("_").contains(currentUid) -> otherUserUid // 如果已是私聊 chatId（格式：uidA_uidB）
            else -> ChatUtils.getChatId(currentUid, otherUserUid) // 否则计算私聊 chatId
        }

        // 缓存用户名映射（用于获取发送者的用户名）
        val usernameCache = mutableMapOf<String, String>() // 为了性能，缓存已查询过的用户名

        return db.collection("chats") // 从 Firestore 的 chats 集合开始
            .document(chatId) // 定位到指定的会话文档
            .collection("messages") // 进入该会话的 messages 子集合
            .orderBy("timestamp") // 按消息时间戳升序排列（最早的消息在前）
            .addSnapshotListener { snapshot, error -> // 实时监听消息列表的变化
                if (error != null) { // 如果监听发生错误
                    onError(error.message ?: "监听消息失败") // 调用错误回调
                    return@addSnapshotListener // 提前返回
                }

                val uiMessages = snapshot?.documents // 将 Firestore 文档转换为 UI 消息对象
                    ?.mapNotNull { document -> document.toObject(Message::class.java) } // 反序列化每条消息文档
                    ?.map { cloudMessage -> // 为每条 Firestore 消息添加 UI 所需的信息
                        // 获取发送者的用户名（用于群聊显示发送者名字）
                        val senderName = if (cloudMessage.senderId == currentUid) { // 如果是当前用户发送的
                            auth.currentUser?.email?.substringBefore("@") ?: "我" // 取邮箱前缀作为用户名，否则显示"我"
                        } else {
                            // 尝试从缓存获取，如果没有则从 Firestore 查询
                            usernameCache.getOrElse(cloudMessage.senderId) { // 先尝试从缓存中获取
                                // 同步查询获取用户名（建议后续优化为异步）
                                cloudMessage.senderId // 暂时使用 UID 作为显示名，后续需要优化
                            }
                        }
                        
                        UiMessage( // 构建 UI 消息对象
                            sender = if (cloudMessage.senderId == currentUid) MessageSender.ME else MessageSender.OTHER, // 判断是自己发还是别人发
                            content = cloudMessage.text, // 消息文本内容
                            type = if (cloudMessage.type == "weather") "weather" else "text", // 消息类型（天气或文本）
                            senderName = senderName // 发送者名字（群聊中显示）
                        )
                    }
                    ?: emptyList() // 如果反序列化失败，返回空列表

                onChange(uiMessages) // 调用回调，通知上层消息列表已更新
            }
    }

    // 发送消息到云端，并同步更新 chats/{chatId} 的摘要信息（支持群聊和一对一）
    fun sendMessage( // 发送一条消息并更新会话摘要
        otherUserUid: String, // 参数：群聊 chatId 或私聊对方 UID
        text: String, // 消息文本内容
        type: String = "text", // 消息类型，默认为普通文本
        onComplete: (Boolean, String) -> Unit // 发送完成回调（是否成功 + 提示文案）
    ) {
        val currentUid = auth.currentUser?.uid // 获取当前登录用户 UID

        if (currentUid.isNullOrBlank()) { // 如果当前用户未登录
            onComplete(false, "当前未登录") // 返回失败
            return // 终止函数
        }

        if (otherUserUid.isBlank()) { // 如果聊天对象 UID 为空
            onComplete(false, "聊天对象不存在") // 返回失败
            return // 终止函数
        }

        // 统一解析 chatId：优先使用显式群聊/现成 chatId，其次计算一对一 chatId
        val chatId = when { // 根据输入参数形式判断是否需要转换
            otherUserUid.startsWith("group_") -> otherUserUid // 如果以 "group_" 开头，已是群聊 ID
            otherUserUid.contains("_") && otherUserUid.split("_").size == 2 && otherUserUid.split("_").contains(currentUid) -> otherUserUid // 如果已是私聊 chatId
            else -> ChatUtils.getChatId(currentUid, otherUserUid) // 否则计算私聊 chatId
        }

        val timestamp = System.currentTimeMillis() // 获取当前时间戳（消息发送时间）
        val cloudMessage = Message( // 构建消息对象用于上传云端
            senderId = currentUid, // 发送者 UID（当前用户）
            receiverId = otherUserUid, // 接收者 UID（群聊时可能是群 ID）
            text = text, // 消息文本
            timestamp = timestamp, // 消息时间戳
            type = type // 消息类型
        )
        
        // 获取当前 chat 文档，仅用于补齐缺失字段；摘要更新只写增量字段，避免把群聊覆盖成私聊
        val chatRef = db.collection("chats").document(chatId) // 定位到会话文档
        val messageRef = chatRef.collection("messages").document() // 在 messages 子集合中新建消息文档

        chatRef // 首先尝试获取已存在的会话文档
            .get()
            .addOnSuccessListener { chatDoc -> // 获取成功
                val docChat = chatDoc.toObject(Chat::class.java) // 尝试反序列化会话对象
                // 先从文档结构推断聊天类型，再决定 participants 的兜底策略
                val isGroupChat = (docChat?.let { isGroupConversation(it) } == true) || chatId.startsWith("group_") // 判断是否为群聊

                @Suppress("UNCHECKED_CAST")
                val docParticipants = (chatDoc.get("participants") as? List<String>).orEmpty() // 获取已有的参与者列表
                val participants = when { // 根据情况确定最终的参与者列表
                    docParticipants.isNotEmpty() -> docParticipants // 如果文档中已有参与者列表，直接使用
                    isGroupChat -> listOf(currentUid) // 如果是群聊但没有参与者列表，先加入当前用户
                    else -> listOf(currentUid, otherUserUid) // 如果是私聊，添加双方
                }.distinct() // 去重处理

                val summaryUpdate = hashMapOf<String, Any>( // 构建会话摘要更新包
                    "chatId" to chatId, // 更新会话 ID
                    "lastMessage" to text, // 更新最后一条消息内容
                    "lastTimestamp" to timestamp, // 更新最后一条消息时间
                    "lastSenderId" to currentUid // 更新最后发言人
                )

                if (!chatDoc.exists()) { // 如果会话文档不存在（新会话）
                    // 新会话首次写入时补齐基础字段
                    summaryUpdate["chatType"] = if (isGroupChat) "group" else "private" // 写入聊天类型
                    summaryUpdate["participants"] = participants // 写入参与者列表
                } else { // 如果会话文档已存在
                    // 已存在会话仅修补缺字段，避免把已有群资料覆盖掉
                    if (docParticipants.isEmpty()) { // 如果原文档中参与者列表为空
                        summaryUpdate["participants"] = participants // 补齐参与者列表
                    }
                    if (isGroupChat && !chatDoc.getString("chatType").equals("group", ignoreCase = true)) { // 如果应该是群聊但 chatType 不是 "group"
                        summaryUpdate["chatType"] = "group" // 修正 chatType
                    }
                }

                val batch = db.batch() // 创建批量写入对象
                // 摘要与消息同批提交，保证列表预览和消息记录一致更新
                batch.set(chatRef, summaryUpdate, SetOptions.merge()) // 写入（或更新）会话摘要
                batch.set(messageRef, cloudMessage) // 写入消息正文

                batch.commit() // 提交批量写入
                    .addOnSuccessListener { // 批量写入成功
                        onComplete(true, "发送成功") // 调用成功回调
                    }
                    .addOnFailureListener { exception -> // 批量写入失败
                        onComplete(false, exception.message ?: "发送失败") // 调用失败回调
                    }
            }
            .addOnFailureListener { // 获取会话文档失败（可能文档不存在）
                val isGroupChat = chatId.startsWith("group_") // 从 chatId 格式判断是否为群聊
                val summaryUpdate = hashMapOf<String, Any>( // 构建新会话的摘要
                    "chatId" to chatId,
                    "chatType" to if (isGroupChat) "group" else "private", // 根据推断设置类型
                    "participants" to if (isGroupChat) listOf(currentUid) else listOf(currentUid, otherUserUid), // 初始化参与者
                    "lastMessage" to text,
                    "lastTimestamp" to timestamp,
                    "lastSenderId" to currentUid
                )

                val batch = db.batch() // 创建批量写入对象
                batch.set(chatRef, summaryUpdate, SetOptions.merge()) // 写入会话摘要
                batch.set(messageRef, cloudMessage) // 写入消息

                batch.commit() // 提交批量写入
                    .addOnSuccessListener { // 成功
                        onComplete(true, "发送成功")
                    }
                    .addOnFailureListener { newException -> // 失败
                        onComplete(false, newException.message ?: "发送失败")
                    }
            }
    }

    // 将原来 DataSource 中的固定聊天上传到云端，并绑定到 admin 与目标用户之间的会话中
    fun uploadAdminSeedChatsToCloud( // 上传 admin 的预设聊天数据到 Firestore 云端
        onProgress: (String) -> Unit = { _ -> }, // 进度回调（可选，默认空实现）
        onComplete: (Boolean, String) -> Unit // 完成回调（成功标记 + 提示文案）
    ) {
        // 使用原子标记确保最终回调只触发一次，避免超时与异步结果重复回调
        val completionHandled = AtomicBoolean(false) // 原子标记，防止回调被多次触发
        // 创建主线程 Handler，用于执行超时兜底逻辑
        val timeoutHandler = Handler(Looper.getMainLooper()) // 在主线程执行定时任务
        // 定义安全完成函数：只允许首次进入时真正结束流程
        fun completeOnce(success: Boolean, message: String) { // 定义一个只能执行一次的完成函数
            if (completionHandled.compareAndSet(false, true)) { // 如果这是第一次调用（原子操作）
                timeoutHandler.removeCallbacksAndMessages(null) // 取消超时定时器
                onComplete(success, message) // 调用外部回调函数
            }
        }
        // 定义超时兜底任务：超过固定时间仍未完成则主动结束，避免 UI 一直显示"同步中"
        val timeoutRunnable = Runnable { // 定义超时任务
            completeOnce(false, "同步超时：请检查网络/VPN 或 Firestore 规则后重试") // 超时后调用完成函数
        }
        // 启动超时计时器
        timeoutHandler.postDelayed(timeoutRunnable, SEED_UPLOAD_TIMEOUT_MS) // 延迟 25 秒后执行超时处理

        // 给出第一条进度文案，明确预设消息来源于代码内置种子而不是本地聊天缓存
        onProgress("正在上传代码内置预设消息（CloudChatManager.adminSeedConversations）...") // 通知外部开始上传

        val currentUser = auth.currentUser // 获取当前登录用户

        if (currentUser == null) { // 如果没有登录用户
            completeOnce(false, "请先登录 admin 账号") // 通知失败
            return // 终止函数
        }

        val currentUsername = currentUser.email?.substringBefore("@") ?: "" // 从邮箱提取用户名

        if (currentUsername != "admin") { // 如果当前用户不是 admin
            completeOnce(false, "只有 admin 账号可以上传预设聊天") // 通知失败
            return // 终止函数
        }

        ensureUserDocument(currentUser.uid, "admin") { adminSaved -> // 确保 admin 用户信息已保存
            if (!adminSaved) { // 如果保存失败
                completeOnce(false, "admin 用户资料写入失败，请检查 Firestore 权限") // 通知失败
                return@ensureUserDocument // 终止回调
            }

            db.collection("users") // 获取 users 集合中的所有用户
                .get()
                .addOnSuccessListener { snapshot -> // 获取成功
                    val userMap = snapshot.documents // 将文档列表转换为用户对象
                        .mapNotNull { document -> document.toObject(User::class.java) } // 反序列化为 User 对象
                        .associateBy { user -> user.username } // 按用户名做 Map 映射，方便查询

                    migrateSeedConversationsRecursively( // 开始递归上传种子聊天
                        index = 0, // 从第 0 个开始
                        adminUid = currentUser.uid, // admin 的 UID
                        userMap = userMap, // 用户名 -> User 的映射表
                        successCount = 0, // 成功计数初始为 0
                        failureCount = 0, // 失败计数初始为 0
                        onProgress = onProgress, // 进度回调
                        onComplete = { success, message -> // 递归函数的完成回调
                            completeOnce(success, message) // 转发给外层的唯一完成函数
                        }
                    )
                }
                .addOnFailureListener { exception -> // 获取用户列表失败
                    completeOnce(false, exception.message ?: "读取用户列表失败") // 通知失败
                }
        }
    }

    // 递归上传种子聊天，便于逐条反馈进度
    private fun migrateSeedConversationsRecursively( // 递归处理每个种子会话的上传
        index: Int, // 当前要处理的种子会话索引
        adminUid: String, // admin 用户的 UID
        userMap: Map<String, User>, // 用户名 -> User 的映射表
        successCount: Int, // 已成功上传的会话数
        failureCount: Int, // 上传失败的会话数
        onProgress: (String) -> Unit, // 进度回调
        onComplete: (Boolean, String) -> Unit // 最终完成回调
    ) {
        if (index >= adminSeedConversations.size) { // 如果已处理完所有种子会话
            val finalSuccess = successCount > 0 && failureCount == 0 // 只有成功且没有失败才算成功
            val finalMessage = if (successCount == 0) { // 根据结果确定最终提示信息
                "未上传任何聊天：请先手动注册 xiaoming / xiaohua / daguang" // 没有用户被找到
            } else if (failureCount == 0) { // 如果没有失败
                "预设聊天上传完成：成功 $successCount/${adminSeedConversations.size}" // 全部成功
            } else { // 如果有失败
                "预设聊天部分上传完成：成功 $successCount，失败 $failureCount" // 部分成功
            }

            onComplete(finalSuccess, finalMessage) // 调用最终完成回调
            return // 递归结束
        }

        val seedConversation = adminSeedConversations[index] // 获取当前要处理的种子会话
        val targetUser = userMap[seedConversation.targetUsername] // 从映射表中查找对方用户

        if (targetUser == null || targetUser.uid.isBlank()) { // 如果对方用户不存在或 UID 为空
            onProgress("⚠️ 未找到账号 ${seedConversation.targetUsername}，请先手动注册后再上传") // 通知用户账号不存在
            migrateSeedConversationsRecursively( // 继续递归处理下一个
                index = index + 1, // 索引加 1
                adminUid = adminUid,
                userMap = userMap,
                successCount = successCount, // 成功计数不变
                failureCount = failureCount + 1, // 失败计数加 1
                onProgress = onProgress,
                onComplete = onComplete
            )
            return // 终止本次递归分支
        }

        uploadSingleSeedConversation(adminUid, targetUser, seedConversation) { success, message -> // 上传当前会话
            onProgress(message) // 通知进度

            migrateSeedConversationsRecursively( // 继续递归处理下一个
                index = index + 1, // 索引加 1
                adminUid = adminUid,
                userMap = userMap,
                successCount = successCount + if (success) 1 else 0, // 如果成功就加 1
                failureCount = failureCount + if (success) 0 else 1, // 如果失败就加 1
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

        batch.commit() // 提交批量写入操作
            .addOnSuccessListener { // 批量写入成功
                onComplete(true, "✅ 已上传 ${seedConversation.displayName} 的预设聊天") // 调用成功回调，通知已上传
            }
            .addOnFailureListener { exception -> // 批量写入失败
                onComplete(false, "❌ 上传 ${seedConversation.displayName} 失败：${exception.message ?: "未知错误"}") // 调用失败回调，传递错误信息
            }
    }

    // 批量会话列表加载时，需要把 UID 转成用户名用于 UI 展示
    private fun fetchUsernames( // 批量查询用户名，将 UID 转换为用户名
        userIds: Set<String>, // 需要查询用户名的 UID 集合
        onComplete: (Map<String, String>) -> Unit // 完成回调，返回 UID -> 用户名的映射
    ) {
        if (userIds.isEmpty()) { // 如果 UID 集合为空
            onComplete(emptyMap()) // 直接返回空 Map
            return // 终止函数
        }

        db.collection("users") // 从 Firestore 的 users 集合查询
            .get() // 获取所有用户文档
            .addOnSuccessListener { snapshot -> // 查询成功
                val usernameMap = snapshot.documents // 将文档列表转换为 UID -> 用户名的映射
                    .mapNotNull { document -> document.toObject(User::class.java) } // 反序列化每个文档为 User 对象
                    .filter { user -> user.uid in userIds } // 只保留在 userIds 中的用户
                    .associate { user -> user.uid to user.username } // 构建 UID -> 用户名的映射

                onComplete(usernameMap) // 调用完成回调，返回映射结果
            }
            .addOnFailureListener { // 查询失败
                onComplete(emptyMap()) // 返回空 Map 作为兜底
            }
    }

    // 确保当前登录用户在 users 集合中存在资料文档
    private fun ensureUserDocument( // 确保用户信息文档已保存到 Firestore
        uid: String, // 用户 UID
        username: String, // 用户名
        onComplete: (Boolean) -> Unit // 完成回调（是否成功）
    ) {
        val user = User( // 构建用户对象
            uid = uid, // 用户 UID
            username = username, // 用户名
            createdAt = System.currentTimeMillis() // 记录创建时间戳
        )

        db.collection("users") // 定位到 users 集合
            .document(uid) // 用 UID 作为文档 ID
            .set(user, SetOptions.merge()) // 写入用户信息（merge 模式避免覆盖其他字段）
            .addOnSuccessListener { // 写入成功
                onComplete(true) // 通知成功
            }
            .addOnFailureListener { // 写入失败
                onComplete(false) // 通知失败
            }
    }

    // 搜索用户：根据用户名/ID查询用户
    fun searchUser( // 根据用户名搜索用户
        username: String, // 要搜索的用户名
        onComplete: (User?, String) -> Unit // 完成回调（搜索到的用户对象或 null，以及提示消息）
    ) {
        if (username.isBlank()) { // 如果用户名为空
            onComplete(null, "请输入有效的用户ID") // 返回错误提示
            return // 终止函数
        }

        val currentUid = auth.currentUser?.uid // 获取当前登录用户的 UID
        if (currentUid.isNullOrBlank()) { // 如果当前用户未登录
            onComplete(null, "当前未登录") // 返回错误提示
            return // 终止函数
        }

        if (username == auth.currentUser?.email?.substringBefore("@")) { // 如果搜索的是自己
            onComplete(null, "不能添加自己为好友") // 返回错误提示
            return // 终止函数
        }

        // 从 users 集合中搜索匹配的用户
        db.collection("users") // 从 users 集合查询
            .whereEqualTo("username", username) // 条件：用户名匹配
            .get() // 执行查询
            .addOnSuccessListener { snapshot -> // 查询成功
                val user = snapshot.documents.firstOrNull()?.toObject(User::class.java) // 取第一个结果
                if (user != null && user.uid.isNotBlank()) { // 如果找到用户且 UID 非空
                    onComplete(user, "") // 返回用户对象，空消息
                } else {
                    onComplete(null, "未找到该用户，请检查用户ID是否正确") // 返回未找到提示
                }
            }
            .addOnFailureListener { exception -> // 查询失败
                onComplete(null, exception.message ?: "搜索失败") // 返回错误信息
            }
    }

    // 创建或更新与指定用户的会话（建立好友关系）
    fun createOrUpdateConversation( // 创建或更新一对一会话（添加好友）
        otherUserUid: String, // 对方用户的 UID
        otherUsername: String, // 对方用户名（用于提示）
        onComplete: (Boolean, String) -> Unit // 完成回调（是否成功 + 消息）
    ) {
        val currentUid = auth.currentUser?.uid // 获取当前登录用户 UID
        if (currentUid.isNullOrBlank()) { // 如果当前用户未登录
            onComplete(false, "当前未登录") // 返回失败
            return // 终止函数
        }

        if (otherUserUid.isBlank()) { // 如果对方 UID 为空
            onComplete(false, "无效的用户ID") // 返回失败
            return // 终止函数
        }

        val chatId = ChatUtils.getChatId(currentUid, otherUserUid) // 根据双方 UID 生成稳定的会话 ID
        val timestamp = System.currentTimeMillis() // 获取当前时间戳

        // 创建会话记录
        val chat = Chat( // 构建会话对象
            chatId = chatId, // 会话 ID
            participants = listOf(currentUid, otherUserUid), // 参与者列表（双方）
            lastMessage = "", // 最后一条消息为空（新会话）
            lastTimestamp = timestamp, // 最后消息时间为当前时间
            lastSenderId = currentUid // 最后发言人为当前用户
        )

        db.collection("chats") // 定位到 chats 集合
            .document(chatId) // 用会话 ID 作为文档 ID
            .set(chat, SetOptions.merge()) // 写入会话记录（merge 模式）
            .addOnSuccessListener { // 写入成功
                onComplete(true, "成功添加好友：$otherUsername") // 返回成功
            }
            .addOnFailureListener { exception -> // 写入失败
                onComplete(false, exception.message ?: "添加好友失败") // 返回失败和错误信息
            }
    }

    // 创建群聊方法
    fun createGroupChat( // 创建一个新群聊
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
        val groupId = "group_${System.currentTimeMillis()}_${(0..999).random()}" // 生成唯一群 ID（时间戳 + 随机数） 我尼玛还有随机数
        val timestamp = System.currentTimeMillis() // 获取当前时间戳

        // 构建完整的参与者列表（包括群主和所有成员）
        val allParticipants = listOf(currentUid) + memberUids // ✅ 创建成员列表（包括群主）

        // 构建群聊数据对象
        val groupChat = Chat( // 构建聊天对象
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
        val batch = db.batch() // 创建批量写入对象
        batch.set( // 添加一个 set 操作
            db.collection("chats").document(groupId), // 目标：chats 集合中 groupId 对应的文档
            groupChat // 要写入的群聊数据
        )

        // 提交批量操作
        batch.commit() // 提交批量写入
            .addOnSuccessListener { _ -> // 批量写入成功
                Log.d("CloudChatManager", "群聊创建成功: $groupId，参与者: $allParticipants") // ✅ 打印调试日志
                // 本地预先添加群聊会话，避免列表因云端延迟被其他会话覆盖
                val newConversation = Conversation( // 构建 UI 会话对象
                    name = groupName.ifBlank { "群聊" }, // 会话名为群名，若为空则用默认值
                    messages = mutableStateListOf(), // 消息列表初始为空
                    initialAvatar = DataSource.avatarForUsername(groupName.ifBlank { "群聊" }), // 根据群名生成头像
                    initialOtherUserUid = "", // 群聊无对方 UID
                    initialChatId = groupId, // 群聊 ID
                    initialPreviewText = "还没有消息", // 列表页预览文本
                    initialLastTimestamp = timestamp, // 最后消息时间
                    initialChatType = "group", // 设置为群聊类型
                    initialGroupName = groupName, // 设置群名
                    initialParticipants = allParticipants // 设置参与者列表
                )
                DataSource.replaceConversations(DataSource.conversations + newConversation) // 本地加入新群聊
                onComplete(true, "群聊 '$groupName' 创建成功") // 返回成功
            }
            .addOnFailureListener { exception -> // 批量写入失败
                Log.e("CloudChatManager", "创建群聊失败: ${exception.message}") // ✅ 打印错误日志
                onComplete(false, exception.message ?: "创建群聊失败") // 返回失败和错误信息
            }
    }
}
